package dev.strata.epochs;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Frische Harzspur der Harzschnecke: 1 px hoch, klebrig (bremst wie Honig, über die Block-Eigenschaften) und schwach
 * leuchtend. In einer Epoche härtet sie im Zufallstick zur Bernsteinplatte aus ({@link EpochsContent#AMBER_PLATE}),
 * die beim Abbau Bernstein gibt; außerhalb einer Epoche löst sie sich auf (keine Bernsteinfarm in der Oberwelt).
 */
public class ResinTrailBlock extends CarpetBlock {

    /** Chance je Zufallstick (im Mittel etwa 1365 Ticks): 0,05 ergibt im Mittel etwa 23 min bis zur Platte. */
    public static final float HARDEN_CHANCE = 0.05f;
    public static final float DISSOLVE_CHANCE = 0.25f;

    public ResinTrailBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (EpochTravel.epochOf(level).isEmpty()) {
            if (random.nextFloat() < DISSOLVE_CHANCE) level.removeBlock(pos, false);
            return;
        }
        var tuning = dev.strata.core.api.StrataAPI.mobTuning(level.getServer(), dev.strata.epochs.mob.ResinSnail.DEF.id());
        if (random.nextFloat() < tuning.ability("trail", "harden_chance", HARDEN_CHANCE)) harden(level, pos);
    }

    /** Härtet zur Bernsteinplatte aus (auch für den Selbsttest). */
    public static void harden(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).is(EpochsContent.RESIN_TRAIL.get())) {
            level.setBlockAndUpdate(pos, EpochsContent.AMBER_PLATE.get().defaultBlockState());
        }
    }
}
