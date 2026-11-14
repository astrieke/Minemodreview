package dev.strata.epochs.mc;

import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
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

    /** Ein Spieler hat einen Block abgebaut (nur wenn kein anderer Listener den Abbau abgebrochen hat). */
    public interface Broken {
        void accept(LevelAccessor level, BlockPos pos, BlockState state, Player player);
    }

    public static void onBroken(Broken listener) {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (BreakBlockEvent event) ->
                listener.accept(event.getLevel(), event.getPos(), event.getState(), event.getPlayer()));
    }
}
