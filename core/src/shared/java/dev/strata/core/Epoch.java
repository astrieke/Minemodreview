package dev.strata.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strata.core.mc.Mc;
import java.util.Optional;

/**
 * Eine Epoche, wie sie im Datapack unter {@code data/<ns>/strata_core/epoch/<name>.json} steht.
 *
 * @param weight          wie häufig die Epoche unter der Oberwelt vorkommt (0 = nie)
 * @param solidifySeconds wie lange man ohne Stabilisator bleiben kann (0 = keine Verfestigung)
 * @param dimension       die Dimension der Epoche, falls ein Content-Mod sie liefert
 */
public record Epoch(int weight, int solidifySeconds, Optional<String> dimension) {

    public static final Codec<Epoch> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(0, 10_000).fieldOf("weight").forGetter(Epoch::weight),
            Codec.intRange(0, 86_400).optionalFieldOf("solidify_seconds", 600).forGetter(Epoch::solidifySeconds),
            Mc.ID_CODEC.optionalFieldOf("dimension").forGetter(Epoch::dimension)
    ).apply(i, Epoch::new));
}
