package dev.strata.epochs.mc;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BeetrootBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Basis der Epochen-Pflanzen: Knochenmehl wirkt nicht (sonst wäre die langsame Farm in der Epoche sofort
 * ausgehebelt). Die Signatur von {@code isValidBonemealTarget} ändert sich in 26.3, daher pro Versionsgruppe.
 */
public abstract class CropBase extends BeetrootBlock {

    protected CropBase(Properties props) {
        super(props);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return false;
    }

    /** Auch direktes Düngen durch andere Mods (ohne vorherige Prüfung) lässt sie nicht wachsen. */
    @Override
    public void performBonemeal(net.minecraft.server.level.ServerLevel level, net.minecraft.util.RandomSource random,
                                BlockPos pos, BlockState state) {
    }

    /** Für den Selbsttest: nimmt die Pflanze Knochenmehl an? */
    public static boolean acceptsBonemeal(BeetrootBlock crop, LevelReader level, BlockPos pos) {
        return crop.isValidBonemealTarget(level, pos, level.getBlockState(pos));
    }
}
