package dev.strata.common;

import static dev.strata.common.SolidificationClock.Stage.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SolidificationClockTest {

    @Test
    void stagesFollowTime() {
        long allowed = SolidificationClock.allowedTicks(1000, 0);
        assertEquals(STABLE, SolidificationClock.stage(0, allowed));
        assertEquals(WARNING, SolidificationClock.stage(750, allowed));
        assertEquals(SOLIDIFYING, SolidificationClock.stage(1000, allowed));
    }

    @Test
    void stabilizerExtendsTime() {
        assertEquals(1500, SolidificationClock.allowedTicks(1000, 0.5));
        assertEquals(1000, SolidificationClock.allowedTicks(1000, -3));
    }

    @Test
    void zeroBaseDisablesSolidification() {
        assertEquals(STABLE, SolidificationClock.stage(1_000_000, SolidificationClock.allowedTicks(0, 0)));
    }

    @Test
    void itemsPetrifyOneIntervalAfterBlocks() {
        long allowed = 1000, interval = 400;
        assertFalse(SolidificationClock.petrifiesItem(980, 1000, allowed, interval), "erst nur Blöcke");
        assertFalse(SolidificationClock.petrifiesItem(1360, 1380, allowed, interval));
        assertTrue(SolidificationClock.petrifiesItem(1380, 1400, allowed, interval), "erster Gegenstand nach einem Intervall");
        assertFalse(SolidificationClock.petrifiesItem(1400, 1420, allowed, interval));
        assertTrue(SolidificationClock.petrifiesItem(1780, 1800, allowed, interval));
        // Schneller Zeitfaktor: ein Schritt über mehrere Intervalle zählt trotzdem nur einmal.
        assertTrue(SolidificationClock.petrifiesItem(1400, 2600, allowed, interval));
    }

    @Test
    void itemsNeverPetrifyWhenDisabled() {
        assertFalse(SolidificationClock.petrifiesItem(1380, 1400, 1000, 0), "Intervall 0 = aus");
        assertFalse(SolidificationClock.petrifiesItem(0, 5_000_000, SolidificationClock.allowedTicks(0, 0), 400));
        // Zurückgedrehte Uhr (Keimfrische) unter die Grenze: nichts versteinert.
        assertFalse(SolidificationClock.petrifiesItem(1500, 900, 1000, 400));
    }

    @Test
    void itemsThawOutsideSolidifying() {
        assertTrue(SolidificationClock.thawsItems(STABLE));
        assertTrue(SolidificationClock.thawsItems(WARNING));
        assertFalse(SolidificationClock.thawsItems(SOLIDIFYING));
    }
}
