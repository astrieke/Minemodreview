package dev.strata.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strata.core.mc.Mc;
import java.util.List;

/**
 * Wie eine Epoche aussieht, wenn ein Transplantator sie in die Oberwelt holt (Feld {@code landscape} der Epoche).
 *
 * @param biome    Biom, das das Gebiet bekommt
 * @param top      oberster Block
 * @param under    die drei Blöcke darunter
 * @param filler   Gestein darunter bis zur Tiefe {@code depth}
 * @param depth    wie tief umgewandelt wird (Blöcke unter der Oberfläche)
 * @param features was danach „wächst“: konfigurierte Features (z. B. {@code minecraft:huge_red_mushroom}) oder die
 *                 Wuchsformen von Strata Epochs ({@code strata_epochs:crystal_spire}, {@code strata_epochs:basalt_cluster})
 * @param featureCount wie viele davon pro Transplantation (bei Radius 12; skaliert mit der Fläche)
 */
public record Landscape(String biome, String top, String under, String filler, int depth,
                        List<String> features, int featureCount) {

    public static final Codec<Landscape> CODEC = RecordCodecBuilder.create(i -> i.group(
            Mc.ID_CODEC.fieldOf("biome").forGetter(Landscape::biome),
            Mc.ID_CODEC.fieldOf("top").forGetter(Landscape::top),
            Mc.ID_CODEC.fieldOf("under").forGetter(Landscape::under),
            Mc.ID_CODEC.fieldOf("filler").forGetter(Landscape::filler),
            Codec.intRange(1, 32).optionalFieldOf("depth", 10).forGetter(Landscape::depth),
            Mc.ID_CODEC.listOf().optionalFieldOf("features", List.of()).forGetter(Landscape::features),
            Codec.intRange(0, 64).optionalFieldOf("feature_count", 6).forGetter(Landscape::featureCount)
    ).apply(i, Landscape::new));
}
