package dev.strata.core;

import dev.strata.common.CodexGraph;
import dev.strata.common.EpochSelector;
import dev.strata.core.mc.Mc;
import dev.strata.core.mc.McDatapack;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;

/** Die beiden Datapack-Registries von Strata: Epochen und Kodex-Knoten. */
public final class StrataRegistries {

    public static final ResourceKey<Registry<Epoch>> EPOCH = Mc.registryKey("epoch");
    public static final ResourceKey<Registry<CodexNode>> CODEX_NODE = Mc.registryKey("codex_node");

    /** Regiongröße für die Epochenwahl: 8 × 8 Chunks. */
    public static final int REGION_SIZE_CHUNKS = 8;

    private StrataRegistries() {}

    static void register(IEventBus modBus) {
        // Beide Registries werden zum Client synchronisiert (für Rezeptanzeigen und Tooltips).
        McDatapack.register(modBus, EPOCH, Epoch.CODEC);
        McDatapack.register(modBus, CODEX_NODE, CodexNode.CODEC);
    }

    public static Map<String, Epoch> epochs(MinecraftServer server) {
        return Mc.entries(server, EPOCH);
    }

    public static EpochSelector epochSelector(MinecraftServer server) {
        List<EpochSelector.Entry> entries = new ArrayList<>();
        epochs(server).forEach((id, epoch) -> entries.add(new EpochSelector.Entry(id, epoch.weight())));
        return new EpochSelector(entries, REGION_SIZE_CHUNKS);
    }

    public static CodexGraph codex(MinecraftServer server) {
        List<CodexGraph.Node> nodes = new ArrayList<>();
        Mc.entries(server, CODEX_NODE).forEach((id, node) ->
                nodes.add(new CodexGraph.Node(id, new HashSet<>(node.requires()))));
        return new CodexGraph(nodes);
    }
}
