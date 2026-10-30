package dev.strata.core.api;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * Wird auf {@code NeoForge.EVENT_BUS} gefeuert, wenn ein Spieler einen Kodex-Knoten freischaltet.
 *
 * <p>Andere Mods und Skripte können darauf reagieren, z. B. KubeJS mit
 * {@code NativeEvents.onEvent('dev.strata.core.api.CodexNodeUnlockedEvent', e => ...)}.
 */
public class CodexNodeUnlockedEvent extends Event {

    private final ServerPlayer player;
    private final String node;

    public CodexNodeUnlockedEvent(ServerPlayer player, String node) {
        this.player = player;
        this.node = node;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    /** Die ID des Knotens, z. B. {@code "strata_epochs:first_core"}. */
    public String getNode() {
        return node;
    }
}
