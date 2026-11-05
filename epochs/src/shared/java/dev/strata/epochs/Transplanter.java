package dev.strata.epochs;

import dev.strata.core.Epoch;
import dev.strata.core.Landscape;
import dev.strata.core.api.StrataAPI;
import dev.strata.core.mc.Mc;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Epochen-Transplantator: Rechtsklick mit einem Bohrkern holt dessen Epoche in die Oberwelt.
 *
 * <p>Voraussetzungen: Die Epoche ist im Kodex erforscht ({@code mastered_<epoche>}: besucht und ein Fossil präpariert),
 * ein präpariertes Fossil dieser Epoche liegt im Inventar (wird verbraucht), und es läuft dort noch keine Transplantation.
 * Die Landschaft kommt aus dem Feld {@code landscape} der Epoche im Datapack, Radius und Tempo aus der Config.
 */
public final class Transplanter {

    private static final List<TransplantJob> JOBS = new ArrayList<>();

    private Transplanter() {}

    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!event.getLevel().getBlockState(event.getPos()).is(EpochsContent.TRANSPLANTER.get())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) return;
        use(player, event.getItemStack(), event.getPos());
    }

    private static void use(ServerPlayer player, ItemStack held, BlockPos pos) {
        ServerLevel level = (ServerLevel) player.level();
        Optional<String> epochId = CoreSamples.epochOf(held);
        if (epochId.isEmpty()) {
            fail(player, "need_sample");
            return;
        }
        String id = epochId.get();
        Epoch epoch = StrataAPI.epochs(level.getServer()).get(id);
        if (!EpochsConfig.TRANSPLANTER.get()) {
            fail(player, "disabled");
            return;
        }
        if (!"minecraft:overworld".equals(Mc.levelId(level))) {
            fail(player, "overworld_only");
            return;
        }
        if (epoch == null || epoch.landscape().isEmpty()) {
            fail(player, "no_landscape", CoreSamples.epochName(id));
            return;
        }
        String name = id.substring(id.indexOf(':') + 1);
        if (id.startsWith(StrataEpochs.MOD_ID + ":") && !StrataAPI.hasNode(player, StrataEpochs.id("mastered_" + name))) {
            fail(player, "not_mastered", CoreSamples.epochName(id));
            return;
        }
        if (JOBS.stream().anyMatch(j -> j.level == level && j.center.distSqr(pos) < 32 * 32)) {
            fail(player, "busy");
            return;
        }
        if (JOBS.size() >= EpochsConfig.TRANSPLANT_MAX_JOBS.get()) {
            fail(player, "too_many");
            return;
        }
        Item fossil = FossilPrep.fossilFor(id);
        if (!player.isCreative()) {
            int slot = findSlot(player, fossil);
            if (slot < 0) {
                fail(player, "need_fossil", net.minecraft.network.chat.Component.translatable(fossil.getDescriptionId()));
                return;
            }
            player.getInventory().getItem(slot).shrink(1);
            held.shrink(1);
        }
        start(level, pos, id, epoch.landscape().get(), EpochsConfig.TRANSPLANT_RADIUS.get(), player);
    }

    /** Startet eine Transplantation (auch für Tests und Befehle). */
    public static TransplantJob start(ServerLevel level, BlockPos pos, String epochId, Landscape landscape, int radius,
                                      ServerPlayer player) {
        TransplantJob job = create(level, pos.below(), radius, epochId, landscape,
                player == null ? "" : player.getUUID().toString());
        JOBS.add(job);
        save(level);
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5f, 0.6f);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
                60, 0.6, 0.6, 0.6, 0.3);
        if (player != null) {
            Mc.actionbar(player, Component.translatable("message.strata_epochs.transplanter.start",
                    CoreSamples.epochName(epochId)).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        return job;
    }

    private static TransplantJob create(ServerLevel level, BlockPos center, int radius, String epochId, Landscape landscape,
                                        String owner) {
        TransplantJob job = new TransplantJob(level, center, radius, epochId, landscape, done -> {
            level.playSound(null, center.above(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 2.0f, 0.6f);
            if (!done.instant) StrataEpochs.LOG.info("[Strata] Transplantation fertig: {} bei {}, {} Blöcke", epochId,
                    center.toShortString(), done.changed());
            ServerPlayer player = owner.isEmpty() ? null
                    : level.getServer().getPlayerList().getPlayer(java.util.UUID.fromString(owner));
            if (player != null) {
                Mc.actionbar(player, Component.translatable("message.strata_epochs.transplanter.done",
                        CoreSamples.epochName(epochId)).withStyle(ChatFormatting.GREEN));
                StrataAPI.unlock(player, StrataEpochs.id("first_transplant"));
            }
        });
        job.owner = owner;
        job.onPhaseChange = () -> save(level);
        return job;
    }

    /** Laufende Transplantationen dieser Dimension am Level speichern (überstehen so einen Neustart). */
    private static void save(ServerLevel level) {
        List<TransplantRecord> records = JOBS.stream()
                .filter(j -> j.level == level && j.phase() != TransplantJob.Phase.DONE)
                .map(j -> new TransplantRecord(j.center.asLong(), j.radius, j.epochId, j.owner, j.phase().ordinal()))
                .toList();
        if (!records.isEmpty() || !level.getData(EpochsContent.TRANSPLANTS.get()).isEmpty()) {
            level.setData(EpochsContent.TRANSPLANTS.get(), records);
        }
    }

    /** Nach dem Serverstart: gespeicherte Transplantationen fortsetzen. */
    static void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            List<TransplantRecord> records = level.getData(EpochsContent.TRANSPLANTS.get());
            for (TransplantRecord r : records) {
                Epoch epoch = StrataAPI.epochs(event.getServer()).get(r.epoch());
                if (epoch == null || epoch.landscape().isEmpty()) continue;
                TransplantJob job = create(level, BlockPos.of(r.center()), r.radius(), r.epoch(), epoch.landscape().get(), r.owner());
                TransplantJob.Phase[] phases = TransplantJob.Phase.values();
                job.resumeAt(phases[Math.max(0, Math.min(phases.length - 1, r.phase()))]);
                JOBS.add(job);
                StrataEpochs.LOG.info("[Strata] Transplantation fortgesetzt: {} bei {} ({})", r.epoch(),
                        BlockPos.of(r.center()).toShortString(), job.phase());
            }
            if (!records.isEmpty()) save(level);
        }
    }

    private static int findSlot(ServerPlayer player, Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(item)) return i;
        }
        return -1;
    }

    private static void fail(ServerPlayer player, String key, Object... args) {
        Mc.actionbar(player, Component.translatable("message.strata_epochs.transplanter." + key, args)
                .withStyle(ChatFormatting.GRAY));
    }

    /** Führt einen Befehl als Server aus, ohne Ausgabe im Chat (für /fillbiome, /place, /summon). */
    static void command(ServerLevel level, String command) {
        var source = level.getServer().createCommandSourceStack().withSuppressedOutput().withLevel(level)
                .withCallback((success, result) -> {
                    if (!success) StrataEpochs.LOG.warn("[Strata] Befehl fehlgeschlagen: {}", command);
                });
        level.getServer().getCommands().performPrefixedCommand(source, command);
    }

    static void onLevelTick(LevelTickEvent.Post event) {
        if (JOBS.isEmpty() || !(event.getLevel() instanceof ServerLevel level)) return;
        boolean[] ended = {false};
        JOBS.removeIf(job -> {
            boolean done = job.level == level && !job.tick();
            ended[0] |= done;
            return done;
        });
        if (ended[0]) save(level);
    }

    /** Beim Herunterfahren: Displays aufräumen, Stand speichern (die Jobs laufen nach dem Start weiter). */
    static void onServerStopping(ServerStoppingEvent event) {
        for (TransplantJob job : JOBS) {
            job.parkDisplays();
            save(job.level);
        }
        JOBS.clear();
    }
}
