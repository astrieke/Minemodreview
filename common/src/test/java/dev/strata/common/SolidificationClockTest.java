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
}
