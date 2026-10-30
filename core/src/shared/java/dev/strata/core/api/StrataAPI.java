package dev.strata.core.api;

import dev.strata.common.CodexGraph;
import dev.strata.common.EpochSelector;
import dev.strata.core.CodexProgress;
import dev.strata.core.Epoch;
import dev.strata.core.StrataRegistries;
import dev.strata.core.mc.Mc;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Öffentliche Schnittstelle von Strata Core für andere Mods (Strata Epochs, Bridge, Addons).
 *
 * <p>IDs sind Text wie {@code "strata_epochs:crystal_age"}, damit die API in allen
 * Minecraft-Versionen gleich aussieht.
 */
public final class StrataAPI {

    private StrataAPI() {}

    public static boolean hasNode(ServerPlayer player, String nodeId) {
        return Mc.getProgress(player).unlocked().contains(nodeId);
    }

    /**
     * Schaltet einen Kodex-Knoten frei, wenn er existiert und seine Voraussetzungen erfüllt sind.
     *
     * @return true, wenn der Knoten neu freigeschaltet wurde
     */
    public static boolean unlock(ServerPlayer player, String nodeId) {
        CodexGraph graph = StrataRegistries.codex(player.level().getServer());
        CodexProgress progress = Mc.getProgress(player);
        if (!graph.canUnlock(nodeId, progress.unlocked())) return false;
        Mc.setProgress(player, progress.with(nodeId));
        NeoForge.EVENT_BUS.post(new CodexNodeUnlockedEvent(player, nodeId));
        return true;
    }

    public static Map<String, Epoch> epochs(MinecraftServer server) {
        return StrataRegistries.epochs(server);
    }

    /** Welche Epoche liegt unter diesem Chunk? */
    public static Optional<String> epochAt(MinecraftServer server, long seed, int chunkX, int chunkZ) {
        EpochSelector selector = StrataRegistries.epochSelector(server);
        return selector.select(seed, chunkX, chunkZ).map(EpochSelector.Entry::id);
    }

    /** Wie {@link #epochOfDimension(MinecraftServer, String)}, aber auch auf dem Client nutzbar. */
    public static Optional<String> epochOfDimension(net.minecraft.core.RegistryAccess access, String dimensionId) {
        for (Map.Entry<String, Epoch> e : Mc.entries(access, StrataRegistries.EPOCH).entrySet()) {
            if (e.getValue().dimension().filter(dimensionId::equals).isPresent()) return Optional.of(e.getKey());
        }
        return Optional.empty();
    }

    /** Zu welcher Epoche gehört diese Dimension? Leer, wenn es keine Epochen-Dimension ist. */
    public static Optional<String> epochOfDimension(MinecraftServer server, String dimensionId) {
        for (Map.Entry<String, Epoch> e : epochs(server).entrySet()) {
            if (e.getValue().dimension().filter(dimensionId::equals).isPresent()) return Optional.of(e.getKey());
        }
        return Optional.empty();
    }
}
