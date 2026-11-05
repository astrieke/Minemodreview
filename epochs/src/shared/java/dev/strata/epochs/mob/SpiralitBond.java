package dev.strata.epochs.mob;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Bindung eines Spiralits an seinen Spieler, als Data Attachment am Mob gespeichert
 * (gleich in allen Versionen, ohne eigenes NBT).
 *
 * @param owner   UUID des Besitzers als Text, leer = noch niemandem zugeordnet
 * @param staying true, wenn er auf Befehl an Ort und Stelle wartet
 */
public record SpiralitBond(String owner, boolean staying) {

    public static final SpiralitBond NONE = new SpiralitBond("", false);

    public static final Codec<SpiralitBond> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("owner", "").forGetter(SpiralitBond::owner),
            Codec.BOOL.optionalFieldOf("staying", false).forGetter(SpiralitBond::staying)
    ).apply(i, SpiralitBond::new));

    public SpiralitBond withStaying(boolean value) {
        return new SpiralitBond(owner, value);
    }
}
