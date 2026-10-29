package dev.strata.epochs;

import dev.strata.common.SolidificationClock;
import dev.strata.core.Epoch;
import dev.strata.core.api.StrataAPI;
import dev.strata.core.mc.Mc;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Wer zu lange in einer Epoche bleibt, verfestigt: erst Warnung, dann Verlangsamung
 * und versteinernde Blöcke in der Nähe. Ein Zeitanker im Inventar verdoppelt die Zeit.
 */
final class Solidification {

    private static final int CHECK_INTERVAL = 20;
    private static final int PETRIFY_INTERVAL = 100;
    private static final int PETRIFY_RADIUS = 3;

    private Solidification() {}

    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % CHECK_INTERVAL != 0 || player.isCreative() || player.isSpectator()) return;

        ServerLevel level = (ServerLevel) player.level();
        Optional<String> epochId = EpochTravel.epochOf(level);
        EpochVisit visit = player.getData(EpochsContent.VISIT.get());
        if (epochId.isEmpty()) {
            if (visit.ticks() != 0) player.setData(EpochsContent.VISIT.get(), visit.withTicks(0));
            return;
        }

        long ticks = visit.ticks() + CHECK_INTERVAL;
        player.setData(EpochsContent.VISIT.get(), visit.withTicks(ticks));

        Epoch epoch = StrataAPI.epochs(level.getServer()).get(epochId.get());
        double bonus = player.getInventory().contains(EpochsContent.TIME_ANCHOR.get().getDefaultInstance()) ? 1.0 : 0.0;
        long allowed = SolidificationClock.allowedTicks(epoch.solidifySeconds() * 20L, bonus);

        switch (SolidificationClock.stage(ticks, allowed)) {
            case STABLE -> { }
            case WARNING -> Mc.actionbar(player, Component.translatable("message.strata_epochs.solidify.warning",
                    (allowed - ticks) / 20).withStyle(ChatFormatting.GOLD));
            case SOLIDIFYING -> {
                Mc.actionbar(player, Component.translatable("message.strata_epochs.solidify.active")
                        .withStyle(ChatFormatting.RED));
                player.addEffect(new MobEffectInstance(Mc.slowness(), 60, 1));
                player.addEffect(new MobEffectInstance(Mc.miningFatigue(), 60, 1));
                if (ticks % PETRIFY_INTERVAL == 0) petrifyNear(level, player.blockPosition());
            }
        }
    }

    /** Verwandelt einen zufälligen festen Block in der Nähe in versteinerten Stein. */
    static void petrifyNear(ServerLevel level, BlockPos center) {
        var random = level.getRandom();
        for (int attempt = 0; attempt < 8; attempt++) {
            BlockPos pos = center.offset(
                    random.nextInt(PETRIFY_RADIUS * 2 + 1) - PETRIFY_RADIUS,
                    random.nextInt(PETRIFY_RADIUS * 2 + 1) - PETRIFY_RADIUS,
                    random.nextInt(PETRIFY_RADIUS * 2 + 1) - PETRIFY_RADIUS);
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getDestroySpeed(level, pos) < 0 || level.getBlockEntity(pos) != null
                    || state.is(EpochsContent.EPOCH_GATE.get()) || state.is(EpochsContent.PETRIFIED_STONE.get())
                    || !level.getFluidState(pos).isEmpty()) {
                continue;
            }
            level.setBlockAndUpdate(pos, EpochsContent.PETRIFIED_STONE.get().defaultBlockState());
            return;
        }
    }
}
