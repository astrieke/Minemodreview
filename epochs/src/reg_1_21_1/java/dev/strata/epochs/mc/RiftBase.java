package dev.strata.epochs.mc;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * Basis des Zeitrisses: nimmt keine Flüssigkeit an. Ohne Kollision würde fließendes Wasser ihn sonst wie eine
 * Fackel wegspülen und den Riss ohne Zeitanker schließen. Die Signatur von {@code canPlaceLiquid} ändert sich
 * ab 1.21.11 (Player -> LivingEntity), daher pro Versionsgruppe.
 */
public abstract class RiftBase extends Block implements LiquidBlockContainer {

    protected RiftBase(Properties props) {
        super(props);
    }

    @Override
    public boolean canPlaceLiquid(net.minecraft.world.entity.player.Player entity, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        return false;
    }
}
