package dev.strata.epochs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Was Strata über den Aufenthalt eines Spielers in einer Epoche weiß.
 *
 * @param returnDimension Dimension, aus der er gekommen ist (leer = unbekannt)
 * @param ticks           Zeit in der aktuellen Epoche, für die Verfestigung
 * @param bonus           Zusatzzeit als Anteil (0,25 = +25 %), z. B. weil der Bohrkern analysiert war
 */
public record EpochVisit(String returnDimension, double x, double y, double z, long ticks, double bonus) {

    public static final EpochVisit NONE = new EpochVisit("", 0, 0, 0, 0, 0);

    public static final Codec<EpochVisit> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("return_dimension", "").forGetter(EpochVisit::returnDimension),
            Codec.DOUBLE.optionalFieldOf("x", 0.0).forGetter(EpochVisit::x),
            Codec.DOUBLE.optionalFieldOf("y", 0.0).forGetter(EpochVisit::y),
            Codec.DOUBLE.optionalFieldOf("z", 0.0).forGetter(EpochVisit::z),
            Codec.LONG.optionalFieldOf("ticks", 0L).forGetter(EpochVisit::ticks),
            Codec.DOUBLE.optionalFieldOf("bonus", 0.0).forGetter(EpochVisit::bonus)
    ).apply(i, EpochVisit::new));

    public EpochVisit withTicks(long newTicks) {
        return new EpochVisit(returnDimension, x, y, z, newTicks, bonus);
    }
}
