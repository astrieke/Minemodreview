package dev.strata.epochs.mc;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Kernanalyse-Tisch. Er hat keine BlockEntity, deshalb muss er die Item-Schnittstelle ({@link TablePort}) beim
 * Setzen und Entfernen selbst für ungültig erklären; sonst behalten Rohre mit Cache den alten Tisch.
 */
public final class AnalysisTableBlock extends Block {

    public AnalysisTableBlock(Properties props) {
        super(props);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!old.is(this)) level.invalidateCapabilities(pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState now, boolean moved) {
        super.onRemove(state, level, pos, now, moved);
        if (!now.is(this)) level.invalidateCapabilities(pos);
    }
}
