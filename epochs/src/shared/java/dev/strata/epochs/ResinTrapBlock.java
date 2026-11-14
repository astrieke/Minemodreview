package dev.strata.epochs;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Harzfalle mit gefangener Bernsteinmotte: härtet in einer Epoche im Zufallstick aus (Chance {@link #HARDEN_CHANCE} je Tick, im Mittel
 * etwa 4,5 min bei normaler Tickrate) und wird dann zur ausgehärteten Falle, die Bernstein mit Einschluss gibt.
 * Die leere und die ausgehärtete Falle sind einfache Blöcke ({@link EpochsContent#RESIN_TRAP},
 * {@link EpochsContent#RESIN_TRAP_HARDENED}); angelockt wird die Motte von {@link dev.strata.epochs.mob.AmberMoth}.
 */
public class ResinTrapBlock extends Block {

    public static final float HARDEN_CHANCE = 0.25f;

    public ResinTrapBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Nur in einer Epoche: Wer Fossilien farmen will, muss dort sein, wo die Verfestigung läuft (keine AFK-Farm
        // in transplantierten Gebieten der Oberwelt). Chance im Datapack unter abilities.trap.harden_chance.
        if (EpochTravel.epochOf(level).isEmpty()) return;
        var tuning = dev.strata.core.api.StrataAPI.mobTuning(level.getServer(), dev.strata.epochs.mob.AmberMoth.DEF.id());
        if (random.nextFloat() < tuning.ability("trap", "harden_chance", HARDEN_CHANCE)) harden(level, pos);
    }

    /** Härtet aus (auch für den Selbsttest). */
    public static void harden(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(EpochsContent.RESIN_TRAP_MOTH.get())) return;
        level.setBlockAndUpdate(pos, EpochsContent.RESIN_TRAP_HARDENED.get().defaultBlockState());
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 0.8f, 1.2f);
    }
}
