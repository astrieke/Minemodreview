package dev.strata.epochs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Der sichtbare Zeitriss: ein senkrechter, leuchtender Spalt ohne Kollision. Abbauen geht nicht, nur Versiegeln
 * mit einem Zeitanker (siehe {@link TimeRifts}). Die Partikel kommen vom Client, der Server ergänzt die Farbe der Epoche.
 */
public final class TimeRiftBlock extends Block {

    public TimeRiftBlock(Properties props) {
        super(props);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 3; i++) {
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
            double y = pos.getY() + random.nextDouble() * 1.6;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
            level.addParticle(ParticleTypes.REVERSE_PORTAL, x, y, z, (random.nextDouble() - 0.5) * 0.3, 0.05,
                    (random.nextDouble() - 0.5) * 0.3);
        }
    }
}
