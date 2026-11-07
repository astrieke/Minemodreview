package dev.strata.epochs;

import dev.strata.core.api.StrataAPI;
import dev.strata.core.mc.Mc;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Rechtsklick-Verhalten über Events statt eigener Item- und Block-Klassen.
 * Das hält den Code in allen Minecraft-Versionen gleich.
 */
final class EpochInteractions {

    static final int DRILL_COOLDOWN_TICKS = 40;

    private EpochInteractions() {}

    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        boolean gate = event.getLevel().getBlockState(event.getPos()).is(EpochsContent.EPOCH_GATE.get());
        boolean drill = EpochsContent.drillTier(stack) > 0;
        if (!gate && !drill) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) return;

        if (gate) {
            useGate(player, stack);
        } else {
            useDrill(player, stack, event.getHand(), event.getPos());
        }
    }

    /** Epochentore in Epochen-Dimensionen sind der einzige Rückweg, deshalb unzerstörbar. */
    static boolean isProtectedGate(LevelAccessor level, BlockState state) {
        return state.is(EpochsContent.EPOCH_GATE.get())
                && level instanceof ServerLevel serverLevel
                && EpochTravel.epochOf(serverLevel).isPresent();
    }

    private static void useDrill(ServerPlayer player, ItemStack drill, InteractionHand hand, BlockPos pos) {
        if (Mc.isOnCooldown(player, drill)) return;
        ServerLevel level = (ServerLevel) player.level();
        if (EpochTravel.epochOf(level).isPresent()) {
            Mc.actionbar(player, Component.translatable("message.strata_epochs.drill.inside_epoch"));
            return;
        }
        Optional<String> epoch = StrataAPI.epochAt(level.getServer(), Mc.seed(level), pos.getX() >> 4, pos.getZ() >> 4);
        if (epoch.isEmpty()) {
            Mc.actionbar(player, Component.translatable("message.strata_epochs.drill.nothing"));
            return;
        }
        int depth = StrataAPI.epochs(level.getServer()).get(epoch.get()).depth();
        int tier = EpochsContent.drillTier(drill);
        if (depth > tier) {
            // Der Bohrer stößt an: Die Epoche wird verraten, der Kern aber nicht gezogen.
            drill.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            Mc.addCooldown(player, drill, DRILL_COOLDOWN_TICKS);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ANVIL_LAND, net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.6f);
            Mc.actionbar(player, Component.translatable("message.strata_epochs.drill.too_deep",
                    CoreSamples.epochName(epoch.get()), depth).withStyle(ChatFormatting.GOLD));
            return;
        }
        ItemStack sample = CoreSamples.create(epoch.get());
        if (!player.getInventory().add(sample)) {
            level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY(), player.getZ(), sample));
        }
        drill.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        Mc.addCooldown(player, drill, DRILL_COOLDOWN_TICKS);
        Mc.actionbar(player, Component.translatable("message.strata_epochs.drill.sample", CoreSamples.epochName(epoch.get())));
        StrataAPI.unlock(player, StrataEpochs.id("first_core"));
    }

    private static void useGate(ServerPlayer player, ItemStack held) {
        ServerLevel level = (ServerLevel) player.level();
        if (EpochTravel.epochOf(level).isPresent()) {
            // Schleichen: Peilung der Biome statt Rückreise.
            if (player.isShiftKeyDown()) BiomeBearing.tell(player);
            else EpochTravel.leave(player);
            return;
        }
        Optional<String> epoch = CoreSamples.epochOf(held);
        if (epoch.isEmpty()) {
            Mc.actionbar(player, Component.translatable("message.strata_epochs.gate.need_sample"));
            return;
        }
        Optional<String> dimension = StrataAPI.epochs(level.getServer()).getOrDefault(epoch.get(),
                new dev.strata.core.Epoch(0, 0, Optional.empty())).dimension();
        if (dimension.isEmpty() || Mc.level(level.getServer(), dimension.get()) == null) {
            Mc.actionbar(player, Component.translatable("message.strata_epochs.gate.unreachable",
                    CoreSamples.epochName(epoch.get())).withStyle(ChatFormatting.GRAY));
            return;
        }
        double bonus = CoreSamples.isAnalyzed(held) ? CoreSamples.ANALYSIS_BONUS : 0;
        if (!player.getAbilities().instabuild) held.shrink(1);
        EpochTravel.enter(player, Mc.level(level.getServer(), dimension.get()), bonus);
        StrataAPI.unlock(player, StrataEpochs.id("visited_" + epoch.get().substring(epoch.get().indexOf(':') + 1)));
    }
}
