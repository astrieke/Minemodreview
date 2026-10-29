package dev.strata.core;

import java.util.List;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Prüft beim Serverstart die Datapack-Inhalte und meldet Fehler verständlich im Log. */
final class StrataValidation {

    private StrataValidation() {}

    static void onServerStarted(ServerStartedEvent event) {
        var server = event.getServer();
        List<String> errors = StrataRegistries.codex(server).validate();
        errors.forEach(e -> StrataCore.LOG.error("[Strata] {}", e));
        StrataCore.LOG.info("[Strata] {} Epochen und {} Kodex-Knoten geladen, {} Fehler",
                StrataRegistries.epochs(server).size(),
                StrataRegistries.codex(server).ids().size(),
                errors.size());
    }
}
