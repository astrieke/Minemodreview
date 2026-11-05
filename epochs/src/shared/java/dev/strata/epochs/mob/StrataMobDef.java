package dev.strata.epochs.mob;

import java.util.List;

/**
 * Ein Mob als Daten: Modell, Animationen, Leucht-Ebenen und Darstellung.
 *
 * <p>Renderer und Modell sind generisch. Ein neuer Mob braucht nur eine Def, eine
 * Entity-Klasse und die Dateien aus {@code mobs/<name>/} (Modell, Textur, Leucht-Ebenen).
 *
 * @param modId      Mod, in deren Jar Modell und Texturen liegen
 * @param name       Name ohne Namespace, z. B. {@code geode_crawler}
 * @param actions    Animationen, die der Server als Aktion starten kann (Reihenfolge = Netzwerk-Index)
 * @param idle       Animation, die immer läuft (Atmen, Schwanz), oder null
 * @param walk       Laufanimation, eingeblendet nach Tempo, oder null
 * @param glowLayers Leucht-Ebenen ({@code textures/entity/<name>_glow_<ebene>.png})
 * @param shadow     Schattenradius in Blöcken
 */
public record StrataMobDef(String modId, String name, List<String> actions, String idle, String walk,
                           List<String> glowLayers, float shadow) {

    public String id() {
        return modId + ":" + name;
    }

    public MobModelData model() {
        return MobModelData.load(modId, name);
    }
}
