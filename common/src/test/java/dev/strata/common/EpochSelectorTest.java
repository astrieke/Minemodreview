package dev.strata.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EpochSelectorTest {

    private static final List<EpochSelector.Entry> EPOCHS = List.of(
            new EpochSelector.Entry("strata:crystal_age", 3),
            new EpochSelector.Entry("strata:spore_age", 1));

    @Test
    void sameRegionGivesSameEpoch() {
        EpochSelector selector = new EpochSelector(EPOCHS, 8);
        assertEquals(selector.select(42L, 0, 0), selector.select(42L, 7, 7));
    }

    @Test
    void loadOrderDoesNotMatter() {
        EpochSelector a = new EpochSelector(EPOCHS, 8);
        EpochSelector b = new EpochSelector(List.of(EPOCHS.get(1), EPOCHS.get(0)), 8);
        for (int x = -50; x < 50; x++) {
            assertEquals(a.select(7L, x * 8, x * 3), b.select(7L, x * 8, x * 3));
        }
    }

    @Test
    void weightsAreRoughlyRespected() {
        EpochSelector selector = new EpochSelector(EPOCHS, 1);
        Map<String, Integer> counts = new HashMap<>();
        for (int x = 0; x < 200; x++) {
            for (int z = 0; z < 200; z++) {
                counts.merge(selector.select(1234L, x, z).orElseThrow().id(), 1, Integer::sum);
            }
        }
        double crystalShare = counts.get("strata:crystal_age") / 40000.0;
        assertTrue(Math.abs(crystalShare - 0.75) < 0.02, "Anteil war " + crystalShare);
    }

    @Test
    void emptyOrZeroWeightGivesNothing() {
        assertTrue(new EpochSelector(List.of(), 8).select(1L, 0, 0).isEmpty());
        assertTrue(new EpochSelector(List.of(new EpochSelector.Entry("a:b", 0)), 8).select(1L, 0, 0).isEmpty());
    }

    @Test
    void negativeChunksUseCorrectRegion() {
        EpochSelector selector = new EpochSelector(EPOCHS, 8);
        assertEquals(selector.select(9L, -1, -1), selector.select(9L, -8, -8));
    }
}
