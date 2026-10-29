package dev.strata.epochs;

import com.mojang.logging.LogUtils;
import dev.strata.epochs.mc.BreakHook;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Strata Epochs: Kernbohrer, Bohrkerne, Epochentore, Verfestigung und die Epochen-Dimensionen.
 *
 * <p>Die Dimensionen selbst sind reine Daten (siehe {@code epochs/worldgen/epochs.json}).
 */
@Mod(StrataEpochs.MOD_ID)
public final class StrataEpochs {

    public static final String MOD_ID = "strata_epochs";
    public static final Logger LOG = LogUtils.getLogger();

    public StrataEpochs(IEventBus modBus) {
        EpochsContent.register(modBus);
        NeoForge.EVENT_BUS.addListener(EpochInteractions::onRightClickBlock);
        BreakHook.preventBreaking(EpochInteractions::isProtectedGate);
        NeoForge.EVENT_BUS.addListener(Solidification::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(SelfTest::onServerStarted);
    }

    public static String id(String path) {
        return MOD_ID + ":" + path;
    }
}
