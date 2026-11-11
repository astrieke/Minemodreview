package dev.strata.epochs;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Vitrine fürs Museum der Erdgeschichte (siehe {@link DisplayCases}): Wird sie entfernt, fällt das Exponat heraus. */
public final class DisplayCaseBlock extends dev.strata.epochs.mc.CaseBase {

    public DisplayCaseBlock(Properties props) {
        super(props);
    }

    @Override
    protected void removed(ServerLevel level, BlockPos pos) {
        DisplayCases.dropExhibit(level, pos);
    }
}
