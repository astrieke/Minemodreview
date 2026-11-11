package dev.strata.epochs.mc;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Block, der erfährt, wenn er verschwindet (Abbau, Explosion, Kolben, Befehl). Ab 1.21.11: {@code affectNeighborsAfterRemoval}. */
public abstract class CaseBase extends Block {

    protected CaseBase(Properties props) {
        super(props);
    }

    /** Der Block ist weg (der neue Zustand steht schon in der Welt). */
    protected abstract void removed(ServerLevel level, BlockPos pos);

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
        removed(level, pos);
    }
}
