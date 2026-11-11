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
            "strata_epochs:strata_codex/visited_spore_age",
            "strata_epochs:strata_codex/visited_ash_age",
            "strata_epochs:strata_codex/fossil_crystal_age",
            "strata_epochs:strata_codex/mastered_crystal_age",
            "strata_epochs:strata_codex/fossil_spore_age",
            "strata_epochs:strata_codex/mastered_spore_age",
            "strata_epochs:strata_codex/fossil_ash_age",
            "strata_epochs:strata_codex/mastered_ash_age",
            "strata_epochs:strata_codex/met_geode_crawler",
            "strata_epochs:strata_codex/met_spore_bellows",
            "strata_epochs:strata_codex/met_petrified_witness",
            "strata_epochs:strata_codex/met_spiralit",
            "strata_epochs:strata_codex/met_ember_burrower",
            "strata_epochs:strata_codex/first_transplant",
            "strata_epochs:strata_codex/sealed_rift",
            "strata_epochs:strata_codex/museum",
            "strata_epochs:strata_codex/first_analysis",
            "strata_epochs:strata_codex/witness_echo");

    public StrataBridge(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(StrataBridge::onNodeUnlocked);
        NeoForge.EVENT_BUS.addListener(StrataBridge::onLogin);
        NeoForge.EVENT_BUS.addListener(StrataBridge::onServerStarted);
        if (net.neoforged.fml.ModList.get().isLoaded("curios")) dev.strata.bridge.compat.CuriosCompat.register();
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

        // Curios: Zeitanker im Talisman-Slot zählt wie im Inventar.
        if (net.neoforged.fml.ModList.get().isLoaded("curios")) {
            ServerPlayer wearer = FakePlayerFactory.get(server.overworld(),
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "strata_curios_test"));
            boolean before = dev.strata.epochs.Solidification.hasTimeAnchor(wearer);
            boolean equipped = dev.strata.bridge.compat.CuriosCompat.equipForTest(wearer);
            boolean after = dev.strata.epochs.Solidification.hasTimeAnchor(wearer);
            if (!equipped) {
                LOG.info("[Strata-Bridge-Test] Curios: Test-Spieler ohne Talisman-Slot (Curios 14+), übersprungen");
            } else {
                LOG.info("[Strata-Bridge-Test] Curios: vorher={}, angelegt={}, nachher={} -> {}", before, equipped, after,
                        !before && after ? "OK" : "FEHLER");
            }
        }
    }

}
