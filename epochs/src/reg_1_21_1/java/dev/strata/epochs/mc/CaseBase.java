package dev.strata.epochs.mc;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Block, der erfährt, wenn er verschwindet (Abbau, Explosion, Kolben, Befehl). 1.21.1: über {@code onRemove}. */
public abstract class CaseBase extends Block {

    protected CaseBase(Properties props) {
        super(props);
    }

    /** Der Block ist weg (der neue Zustand steht schon in der Welt). */
    protected abstract void removed(ServerLevel level, BlockPos pos);

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState now, boolean moved) {
        super.onRemove(state, level, pos, now, moved);
        if (!now.is(this) && level instanceof ServerLevel server) removed(server, pos);
    }
}
