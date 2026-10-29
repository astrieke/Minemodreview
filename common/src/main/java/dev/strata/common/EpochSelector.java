package dev.strata.common;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Bestimmt, welche Epoche unter einer Region liegt.
 *
 * <p>Die Auswahl hängt nur vom Welt-Seed und der Region ab. Die Oberwelt-Generierung
 * wird dafür nicht verändert, deshalb funktioniert das in jeder Welt (auch Skyblock)
 * und kollidiert nicht mit Worldgen-Mods.
 */
public final class EpochSelector {

    /** Eine Epoche mit ihrem Gewicht. Höheres Gewicht bedeutet häufiger. */
    public record Entry(String id, int weight) {
        public Entry {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id fehlt");
            if (weight < 0) throw new IllegalArgumentException("weight < 0 für " + id);
        }
    }

    private final List<Entry> entries;
    private final int totalWeight;
    private final int regionSizeChunks;

    public EpochSelector(List<Entry> entries, int regionSizeChunks) {
        if (regionSizeChunks < 1) throw new IllegalArgumentException("regionSizeChunks < 1");
        // Feste Reihenfolge, damit die Ladereihenfolge der Datapacks das Ergebnis nicht ändert.
        List<Entry> sorted = new ArrayList<>(entries);
        sorted.removeIf(e -> e.weight() == 0);
        sorted.sort(Comparator.comparing(Entry::id));
        this.entries = List.copyOf(sorted);
        this.totalWeight = sorted.stream().mapToInt(Entry::weight).sum();
        this.regionSizeChunks = regionSizeChunks;
    }

    public Optional<Entry> select(long worldSeed, int chunkX, int chunkZ) {
        if (totalWeight == 0) return Optional.empty();
        int regionX = Math.floorDiv(chunkX, regionSizeChunks);
        int regionZ = Math.floorDiv(chunkZ, regionSizeChunks);
        long roll = Long.remainderUnsigned(hash(worldSeed, regionX, regionZ), totalWeight);
        for (Entry entry : entries) {
            roll -= entry.weight();
            if (roll < 0) return Optional.of(entry);
        }
        throw new IllegalStateException("unerreichbar");
    }

    static long hash(long seed, int x, int z) {
        long h = seed ^ 0x5DEECE66DL;
        h = mix(h + x * 0x9E3779B97F4A7C15L);
        h = mix(h + z * 0xC2B2AE3D27D4EB4FL);
        return h;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
