package dev.strata.epochs.mc;

import java.util.function.BiPredicate;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/** Blockabbau verhindern: ab Minecraft 26.1 ({@code BreakBlockEvent}). */
public final class BreakHook {

    private BreakHook() {}

    public static void preventBreaking(BiPredicate<LevelAccessor, BlockState> shouldPrevent) {
        NeoForge.EVENT_BUS.addListener((BreakBlockEvent event) -> {
            if (shouldPrevent.test(event.getLevel(), event.getState())) event.setCanceled(true);
        });
    }
}
