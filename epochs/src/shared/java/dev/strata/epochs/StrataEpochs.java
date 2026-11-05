package dev.strata.epochs;

import com.mojang.logging.LogUtils;
import dev.strata.epochs.mc.BreakHook;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
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

    public StrataEpochs(IEventBus modBus, ModContainer container) {
        // Ab 26.3 heißt die Art "LOCAL" statt "COMMON" (gilt pro Installation, auch auf dem Server).
        ModConfig.Type local = java.util.Arrays.stream(ModConfig.Type.values())
                .filter(t -> t.name().equals("COMMON") || t.name().equals("LOCAL")).findFirst().orElseThrow();
        container.registerConfig(local, EpochsConfig.SPEC);
        EpochsContent.register(modBus);
        EpochsMobs.register(modBus);
        NeoForge.EVENT_BUS.addListener(EpochInteractions::onRightClickBlock);
        BreakHook.preventBreaking(EpochInteractions::isProtectedGate);
        NeoForge.EVENT_BUS.addListener(Solidification::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(SelfTest::onServerStarted);
    }

    public static String id(String path) {
        return MOD_ID + ":" + path;
    }
}
