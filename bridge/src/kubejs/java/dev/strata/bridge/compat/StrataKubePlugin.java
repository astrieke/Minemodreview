package dev.strata.bridge.compat;

import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.strata.core.api.StrataAPI;

/**
 * KubeJS (nur 1.21.1 und 26.1): stellt Skripten {@code StrataAPI} (Kodex, Epochen) und {@code Strata}
 * (Helfer für Strata Epochs, siehe {@link StrataScripts}) zur Verfügung.
 * Angemeldet über {@code kubejs.plugins.txt}; ohne KubeJS wird nichts davon geladen.
 *
 * <p>Freischaltungen kommen als NeoForge-Event: {@code NativeEvents.onEvent('dev.strata.core.api.CodexNodeUnlockedEvent', e => ...)}.
 */
public class StrataKubePlugin implements KubeJSPlugin {

    @Override
    public void registerBindings(BindingRegistry bindings) {
        bindings.add("StrataAPI", StrataAPI.class);
        bindings.add("Strata", StrataScripts.class);
    }
}
