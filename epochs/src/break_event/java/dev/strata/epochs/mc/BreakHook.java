package dev.strata.epochs.mc;

import java.util.function.BiPredicate;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Blockabbau verhindern: Minecraft 1.21.1 bis 1.21.11 ({@code BlockEvent.BreakEvent}). */
public final class BreakHook {

    private BreakHook() {}

    public static void preventBreaking(BiPredicate<LevelAccessor, BlockState> shouldPrevent) {
        NeoForge.EVENT_BUS.addListener((BlockEvent.BreakEvent event) -> {
            if (shouldPrevent.test(event.getLevel(), event.getState())) event.setCanceled(true);
        });
    }
}
