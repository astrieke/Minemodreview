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

/** Die Datapack-Registries von Strata: Epochen, Kodex-Knoten und Mob-Werte. */
public final class StrataRegistries {

    public static final ResourceKey<Registry<Epoch>> EPOCH = Mc.registryKey("epoch");
    public static final ResourceKey<Registry<CodexNode>> CODEX_NODE = Mc.registryKey("codex_node");
    public static final ResourceKey<Registry<MobTuning>> MOB_TUNING = Mc.registryKey("mob_tuning");

    /** Regiongröße für die Epochenwahl: 8 × 8 Chunks. */
    public static final int REGION_SIZE_CHUNKS = 8;

    private StrataRegistries() {}

    static void register(IEventBus modBus) {
        // Beide Registries werden zum Client synchronisiert (für Rezeptanzeigen und Tooltips).
        McDatapack.register(modBus, EPOCH, Epoch.CODEC);
        McDatapack.register(modBus, CODEX_NODE, CodexNode.CODEC);
        McDatapack.register(modBus, MOB_TUNING, MobTuning.CODEC);
    }

    public static Map<String, Epoch> epochs(MinecraftServer server) {
        return Mc.entries(server, EPOCH);
    }

    /** Werte für einen Mob (per Entity-ID), oder {@link MobTuning#EMPTY}, wenn kein Datapack welche setzt. */
    public static MobTuning mobTuning(MinecraftServer server, String entityId) {
        for (MobTuning t : Mc.entries(server, MOB_TUNING).values()) {
            if (t.entity().equals(entityId)) return t;
        }
        return MobTuning.EMPTY;
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
