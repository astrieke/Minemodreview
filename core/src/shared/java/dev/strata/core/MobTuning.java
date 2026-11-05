package dev.strata.core;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strata.core.mc.Mc;
import java.util.Map;

/**
 * Werte eines Mobs, einstellbar per Datapack unter {@code data/<ns>/strata_core/mob_tuning/<name>.json}.
 *
 * <pre>{@code
 * {
 *   "entity": "strata_epochs:geode_crawler",
 *   "attributes": { "max_health": 20, "attack_damage": 3, "armor": 4, "movement_speed": 0.18 },
 *   "spawn": { "max_light": 7, "max_nearby": 4 },
 *   "abilities": { "crystal_shell": { "enabled": true, "duration": 60, "damage_factor": 0.4 } }
 * }
 * }</pre>
 *
 * <p>Zeiten stehen in Ticks. Fehlende Werte nimmt der Mob aus seinen eigenen Vorgaben.
 * {@code "enabled": false} schaltet eine Fähigkeit ab.
 */
public record MobTuning(String entity, Map<String, Double> attributes, Map<String, Double> spawn,
                        Map<String, Map<String, Double>> abilities) {

    public static final MobTuning EMPTY = new MobTuning("", Map.of(), Map.of(), Map.of());

    /** Zahl oder Wahrheitswert (true = 1, false = 0). */
    private static final Codec<Double> NUMBER = Codec.either(Codec.BOOL, Codec.DOUBLE)
            .xmap(e -> e.map(b -> b ? 1.0 : 0.0, d -> d), Either::right);
    private static final Codec<Map<String, Double>> VALUES = Codec.unboundedMap(Codec.STRING, NUMBER);

    public static final Codec<MobTuning> CODEC = RecordCodecBuilder.create(i -> i.group(
            Mc.ID_CODEC.fieldOf("entity").forGetter(MobTuning::entity),
            VALUES.optionalFieldOf("attributes", Map.of()).forGetter(MobTuning::attributes),
            VALUES.optionalFieldOf("spawn", Map.of()).forGetter(MobTuning::spawn),
            Codec.unboundedMap(Codec.STRING, VALUES).optionalFieldOf("abilities", Map.of()).forGetter(MobTuning::abilities)
    ).apply(i, MobTuning::new));

    public double attribute(String name, double fallback) {
        return attributes.getOrDefault(name, fallback);
    }

    public double spawn(String name, double fallback) {
        return spawn.getOrDefault(name, fallback);
    }

    public double ability(String ability, String name, double fallback) {
        Map<String, Double> values = abilities.get(ability);
        return values == null ? fallback : values.getOrDefault(name, fallback);
    }

    public boolean abilityEnabled(String ability) {
        return ability(ability, "enabled", 1.0) != 0.0;
    }
}
