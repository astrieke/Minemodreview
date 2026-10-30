package dev.strata.bridge;

import com.mojang.logging.LogUtils;
import dev.strata.core.api.CodexNodeUnlockedEvent;
import dev.strata.core.api.StrataAPI;
import dev.strata.core.mc.Mc;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * Strata Bridge: Integrationen mit anderen Mods.
 *
 * <ul>
 *   <li><b>Quest-Mods</b> (FTB Quests, Heracles, …): Für jeden Kodex-Knoten {@code ns:pfad} wird das Advancement
 *       {@code ns:strata_codex/pfad} vergeben, falls es existiert. Quests können darauf als Advancement-Aufgabe prüfen.
 *       Umgekehrt schaltet {@code /strata codex grant @p <knoten>} als Quest-Belohnung Knoten frei.</li>
 *   <li><b>KubeJS</b>: hört direkt auf {@link CodexNodeUnlockedEvent}, kein Plugin nötig.</li>
 *   <li><b>JEI</b> und <b>Jade</b>: Plugins unter {@code dev.strata.bridge.compat}. Sie werden nur geladen,
 *       wenn die jeweilige Mod installiert ist.</li>
 * </ul>
 */
@Mod(StrataBridge.MOD_ID)
public final class StrataBridge {

    public static final String MOD_ID = "strata_bridge";
    public static final Logger LOG = LogUtils.getLogger();

    /** Advancements, die Strata Bridge für die Knoten von Strata Epochs mitbringt. */
    static final List<String> BUILTIN_ADVANCEMENTS = List.of(
            "strata_epochs:strata_codex/root",
            "strata_epochs:strata_codex/first_core",
            "strata_epochs:strata_codex/visited_crystal_age",
            "strata_epochs:strata_codex/visited_spore_age");

    public StrataBridge(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(StrataBridge::onNodeUnlocked);
        NeoForge.EVENT_BUS.addListener(StrataBridge::onLogin);
        NeoForge.EVENT_BUS.addListener(StrataBridge::onServerStarted);
    }

    /** {@code strata_epochs:first_core} → {@code strata_epochs:strata_codex/first_core} */
    public static String advancementFor(String nodeId) {
        int colon = nodeId.indexOf(':');
        return nodeId.substring(0, colon) + ":strata_codex/" + nodeId.substring(colon + 1);
    }

    private static void onNodeUnlocked(CodexNodeUnlockedEvent event) {
        Mc.awardAdvancement(event.getPlayer(), advancementFor(event.getNode()));
    }

    /** Nachziehen beim Einloggen, falls ein Advancement erst nach der Freischaltung hinzukam. */
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        for (String node : Mc.getProgress(player).unlocked()) {
            Mc.awardAdvancement(player, advancementFor(node));
        }
    }

    private static void onServerStarted(ServerStartedEvent event) {
        if (!Boolean.getBoolean("strata.selftest")) return;
        var server = event.getServer();
        long found = BUILTIN_ADVANCEMENTS.stream().filter(id -> Mc.hasAdvancement(server, id)).count();
        BUILTIN_ADVANCEMENTS.stream().filter(id -> !Mc.hasAdvancement(server, id))
                .forEach(id -> LOG.error("[Strata-Bridge-Test] Advancement fehlt: {}", id));
        LOG.info("[Strata-Bridge-Test] {} von {} Advancements geladen", found, BUILTIN_ADVANCEMENTS.size());

        // Kette prüfen: Knoten freischalten -> Event -> Advancement, mit einem simulierten Spieler.
        ServerPlayer fake = FakePlayerFactory.getMinecraft(server.overworld());
        int[] events = {0};
        java.util.function.Consumer<CodexNodeUnlockedEvent> counter = e -> events[0]++;
        NeoForge.EVENT_BUS.addListener(counter);
        boolean unlocked = StrataAPI.unlock(fake, "strata_epochs:first_core");
        NeoForge.EVENT_BUS.unregister(counter);
        boolean stored = Mc.getProgress(fake).unlocked().contains("strata_epochs:first_core");
        boolean ok = unlocked && stored && events[0] == 1;
        LOG.info("[Strata-Bridge-Test] Freischaltung: unlock={}, gespeichert={}, Events={} -> {}",
                unlocked, stored, events[0], ok ? "OK" : "FEHLER");
    }

}
