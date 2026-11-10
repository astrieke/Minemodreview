package dev.strata.epochs.mc;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BeetrootBlock;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Basis der Epochen-Pflanzen: Knochenmehl wirkt nicht (sonst wäre die langsame Farm in der Epoche sofort
 * ausgehebelt). Ab 26.3 bekommt {@code isValidBonemealTarget} zusätzlich die Quelle des Düngers.
 */
public abstract class CropBase extends BeetrootBlock {

    protected CropBase(Properties props) {
        super(props);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
        return false;
    }

    /** Auch direktes Düngen durch andere Mods (ohne vorherige Prüfung) lässt sie nicht wachsen. */
    @Override
    public void performBonemeal(net.minecraft.server.level.ServerLevel level, net.minecraft.util.RandomSource random,
                                BlockPos pos, BlockState state, net.minecraft.world.level.block.BonemealSource source) {
    }

    /** Für den Selbsttest: nimmt die Pflanze Knochenmehl an (Quelle: Spieler mit Knochenmehl)? */
    public static boolean acceptsBonemeal(BeetrootBlock crop, LevelReader level, BlockPos pos) {
        return crop.isValidBonemealTarget(level, pos, level.getBlockState(pos), BonemealSource.INTERACTION);
    }
}
