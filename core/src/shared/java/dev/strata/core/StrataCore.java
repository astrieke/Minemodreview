package dev.strata.core;

import com.mojang.logging.LogUtils;
import dev.strata.core.mc.Mc;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Einstieg von Strata Core.
 *
 * <p>Der Code in {@code core/src/shared} ist für alle Minecraft-Versionen gleich.
 * Alles, was sich zwischen Versionen unterscheidet, steckt hinter {@link Mc}.
 */
@Mod(StrataCore.MOD_ID)
public final class StrataCore {

    public static final String MOD_ID = "strata_core";
    public static final Logger LOG = LogUtils.getLogger();

    public StrataCore(IEventBus modBus) {
        Mc.registerAttachments(modBus);
        StrataRegistries.register(modBus);
        NeoForge.EVENT_BUS.addListener(StrataCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(StrataValidation::onServerStarted);
        LOG.info("Strata Core geladen (Minecraft {})", Mc.MINECRAFT_LINE);
    }
}
