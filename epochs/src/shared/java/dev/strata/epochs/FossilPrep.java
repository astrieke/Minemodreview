package dev.strata.epochs;

import dev.strata.core.api.StrataAPI;
import dev.strata.core.mc.Mc;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Fossilien präparieren: Meißel in der Haupthand, Fossilbrocken in der zweiten Hand, Rechtsklick
 * auf den Präparationstisch. Fünf Schläge legen das Fossil frei.
 *
 * <p>Das Bruchrisiko hängt vom Tempo ab: Wer sofort nachschlägt, riskiert pro Schlag 30 %,
 * wer gut eine Sekunde wartet, nur 3 %. So entsteht ein Rhythmus statt Dauerklicken.
 * Alles läuft über Events und Datenkomponenten, ohne eigene Item- oder Block-Klassen.
 */
public final class FossilPrep {

    public static final int STEPS = 5;
    /** So viele Ticks Ruhe machen einen Schlag sicher. */
    public static final int CALM_TICKS = 24;
    private static final int MIN_GAP = 4;
    private static final double RISK_RUSHED = 0.30;
    private static final double RISK_CALM = 0.03;
    private static final double WARN_RISK = 0.15;

    private static final Map<UUID, Long> LAST_STROKE = new ConcurrentHashMap<>();

    /** Welches Fossil in welcher Epoche steckt. Unbekannte Epochen (aus Datapacks) liefern ein Ammonit. */
    private static final Map<String, Supplier<Item>> FOSSILS = Map.of(
            StrataEpochs.id("crystal_age"), EpochsContent.AMMONITE_FOSSIL,
            StrataEpochs.id("spore_age"), EpochsContent.FERN_FOSSIL,
            StrataEpochs.id("ash_age"), EpochsContent.ANCIENT_SKULL);

    private FossilPrep() {}

    /** Ein Fossilbrocken aus dieser Epoche (z. B. für Loot oder Tests). */
    public static ItemStack chunk(String epochId) {
        ItemStack stack = new ItemStack(EpochsContent.FOSSIL_CHUNK.get());
        stack.set(EpochsContent.SAMPLE_EPOCH.get(), epochId);
        updateLore(stack, 0);
        return stack;
    }

    /** Das fertige Fossil zu einer Epoche. */
    public static Item fossilFor(String epochId) {
        return FOSSILS.getOrDefault(epochId, EpochsContent.AMMONITE_FOSSIL).get();
    }

    /** Die Epoche eines präparierten Fossils (Umkehrung von {@link #fossilFor}). */
    public static Optional<String> epochOfFossil(ItemStack stack) {
        return FOSSILS.entrySet().stream().filter(e -> stack.is(e.getValue().get())).map(Map.Entry::getKey).findFirst();
    }

    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!event.getLevel().getBlockState(event.getPos()).is(EpochsContent.FOSSIL_TABLE.get())) return;
        ItemStack chisel = event.getItemStack();
        if (chisel.is(EpochsContent.AMMONITE_FOSSIL.get())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
                revive(player, chisel, event.getPos());
            }
            return;
        }
        if (!chisel.is(EpochsContent.CHISEL.get())) {
            if (chisel.is(EpochsContent.FOSSIL_CHUNK.get()) && event.getEntity() instanceof ServerPlayer player) {
                Mc.actionbar(player, Component.translatable("message.strata_epochs.fossil.how").withStyle(ChatFormatting.GRAY));
            }
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) return;
        long now = player.level().getGameTime();
        Long last = LAST_STROKE.get(player.getUUID());
        long gap = last == null ? CALM_TICKS : now - last;
        if (gap < MIN_GAP) return;
        LAST_STROKE.put(player.getUUID(), now);
        ServerLevel level = (ServerLevel) player.level();
        stroke(player, chisel, event.getPos(), gap, () -> level.getRandom().nextDouble());
    }

    /** Bruchrisiko eines Schlags nach {@code gap} Ticks Pause: 30 % sofort, 3 % nach {@link #CALM_TICKS}. */
    public static double risk(long gap) {
        double calm = Math.min(1.0, gap / (double) CALM_TICKS);
        return RISK_RUSHED + (RISK_CALM - RISK_RUSHED) * calm;
    }

    /** Ein Meißelschlag. {@code roll} liefert die Zufallszahl für den Bruch (für Tests austauschbar). */
    static void stroke(ServerPlayer player, ItemStack chisel, BlockPos table, long gap, java.util.function.DoubleSupplier roll) {
        ServerLevel level = (ServerLevel) player.level();

        ItemStack piece = player.getOffhandItem();
        if (!piece.is(EpochsContent.FOSSIL_CHUNK.get())) {
            Mc.actionbar(player, Component.translatable("message.strata_epochs.fossil.how").withStyle(ChatFormatting.GRAY));
            return;
        }
        // Nur ein Brocken liegt auf dem Tisch, der Rest wandert ins Inventar. Die zweite Hand wird dafür
        // kurz geleert, sonst stapelt das Inventar den Rest gleich wieder auf den Brocken in der Hand.
        if (piece.getCount() > 1) {
            ItemStack rest = piece.copyWithCount(piece.getCount() - 1);
            piece = piece.copyWithCount(1);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            give(player, rest);
            player.setItemInHand(InteractionHand.OFF_HAND, piece);
        }

        double risk = risk(gap);
        chisel.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        double x = table.getX() + 0.5, y = table.getY() + 1.05, z = table.getZ() + 0.5;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CALCITE.defaultBlockState()),
                x, y, z, 6, 0.15, 0.02, 0.15, 0.05);

        if (roll.getAsDouble() < risk) {
            piece.shrink(1);
            give(player, new ItemStack(Items.BONE_MEAL, 1 + level.getRandom().nextInt(2)));
            level.playSound(null, x, y, z, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.0f, 0.6f);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()),
                    x, y, z, 20, 0.2, 0.1, 0.2, 0.1);
            Mc.actionbar(player, Component.translatable(gap < CALM_TICKS ? "message.strata_epochs.fossil.broken_rushed"
                    : "message.strata_epochs.fossil.broken").withStyle(ChatFormatting.RED));
            return;
        }

        int progress = piece.getOrDefault(EpochsContent.PREP_PROGRESS.get(), 0) + 1;
        if (progress < STEPS) {
            piece.set(EpochsContent.PREP_PROGRESS.get(), progress);
            updateLore(piece, progress);
            level.playSound(null, x, y, z, SoundEvents.BRUSH_GRAVEL, SoundSource.PLAYERS, 1.0f, 0.8f + progress * 0.1f);
            Component bar = Component.literal("■".repeat(progress) + "□".repeat(STEPS - progress));
            Mc.actionbar(player, Component.translatable(risk >= WARN_RISK ? "message.strata_epochs.fossil.rushed"
                    : "message.strata_epochs.fossil.progress", bar)
                    .withStyle(risk >= WARN_RISK ? ChatFormatting.GOLD : ChatFormatting.YELLOW));
            return;
        }

        String epoch = piece.getOrDefault(EpochsContent.SAMPLE_EPOCH.get(), StrataEpochs.id("crystal_age"));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(fossilFor(epoch)));
        level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.0f);
        level.playSound(null, x, y, z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.4f, 1.4f);
        Mc.actionbar(player, Component.translatable("message.strata_epochs.fossil.done",
                Component.translatable(fossilFor(epoch).getDescriptionId())).withStyle(ChatFormatting.GREEN));
        onPrepared(player, epoch);
    }

    /** {scharf seit, letzter Klick} für die Bestätigung beim Ersetzen eines nicht geladenen Spiralits. */
    private static final Map<UUID, long[]> REPLACE_ARMED = new ConcurrentHashMap<>();

    /**
     * Zweiter Klick innerhalb von 3 s nach einer Pause? Gehaltene Maustaste (Wiederholung alle 4 Ticks) zählt
     * als ein Druck, wie beim Epochentor.
     */
    static boolean confirmed(ServerPlayer player, long now) {
        long[] state = REPLACE_ARMED.get(player.getUUID());
        boolean holding = state != null && now - state[1] <= 5;
        if (state != null && !holding && now - state[0] <= 60) {
            REPLACE_ARMED.remove(player.getUUID());
            return true;
        }
        if (holding) state[1] = now;
        else REPLACE_ARMED.put(player.getUUID(), new long[] {now, now});
        return false;
    }

    /** Ammonit in der Haupthand, Keimfrische in der zweiten: Ein Spiralit schlüpft über dem Tisch. */
    static void revive(ServerPlayer player, ItemStack fossil, BlockPos table) {
        ItemStack tonic = player.getOffhandItem();
        if (!tonic.is(EpochsContent.GERM_TONIC.get())) {
            Mc.actionbar(player, Component.translatable("message.strata_epochs.spiralit.how").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (dev.strata.epochs.mob.Spiralit.hasSpiralit(player)) {
            Mc.actionbar(player, Component.translatable("message.strata_epochs.spiralit.only_one").withStyle(ChatFormatting.GRAY));
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        boolean replaces = dev.strata.epochs.mob.Spiralit.replacesUnloaded(player);
        if (replaces && !confirmed(player, level.getGameTime())) {
            // Erst warnen, dann verbrauchen: Vielleicht will man lieber den alten Spiralit holen.
            Mc.actionbar(player, Component.translatable("message.strata_epochs.spiralit.confirm_replace").withStyle(ChatFormatting.YELLOW));
            return;
        }
        double x = table.getX() + 0.5, y = table.getY() + 1.1, z = table.getZ() + 0.5;
        if (!player.isCreative()) {
            fossil.shrink(1);
            tonic.shrink(1);
        }
        dev.strata.epochs.mob.Spiralit.hatch(level, new net.minecraft.world.phys.Vec3(x, y, z), player);
        if (replaces) {
            player.sendSystemMessage(Component.translatable("message.strata_epochs.spiralit.replaced").withStyle(ChatFormatting.GRAY));
        }
        level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 0.8f);
        level.playSound(null, x, y, z, SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.END_ROD, x, y + 0.3, z, 16, 0.3, 0.3, 0.3, 0.03);
        Mc.actionbar(player, Component.translatable("message.strata_epochs.spiralit.hatched").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** Kodex: erstes Fossil einer Epoche, danach gilt die Epoche als erforscht (Voraussetzung für den Transplantator). */
    private static void onPrepared(ServerPlayer player, String epoch) {
        if (!epoch.startsWith(StrataEpochs.MOD_ID + ":")) return;
        String name = epoch.substring(epoch.indexOf(':') + 1);
        StrataAPI.unlock(player, StrataEpochs.id("fossil_" + name));
        StrataAPI.unlock(player, StrataEpochs.id("mastered_" + name));
    }

    private static void updateLore(ItemStack stack, int progress) {
        String epoch = stack.get(EpochsContent.SAMPLE_EPOCH.get());
        List<Component> lines = new java.util.ArrayList<>();
        if (epoch != null) lines.add(CoreSamples.epochName(epoch));
        if (progress > 0) lines.add(Component.translatable("tooltip.strata_epochs.fossil_progress", progress, STEPS));
        stack.set(DataComponents.LORE, new ItemLore(lines));
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.level().addFreshEntity(new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack));
        }
    }
}
