package dev.strata.common;

/**
 * Wie weit die Verfestigung eines Spielers in einer Epoche fortgeschritten ist.
 *
 * <p>Stabilisatoren verlängern die erlaubte Zeit um einen Faktor.
 */
public final class SolidificationClock {

    public enum Stage { STABLE, WARNING, SOLIDIFYING }

    /** Ab diesem Anteil der erlaubten Zeit wird gewarnt. */
    public static final double WARNING_SHARE = 0.75;

    private SolidificationClock() {}

    public static long allowedTicks(long baseTicks, double stabilizerBonus) {
        if (baseTicks <= 0) return Long.MAX_VALUE; // 0 = Verfestigung für diese Epoche aus
        return Math.round(baseTicks * (1.0 + Math.max(0.0, stabilizerBonus)));
    }

    public static Stage stage(long ticksInEpoch, long allowedTicks) {
        if (allowedTicks == Long.MAX_VALUE) return Stage.STABLE;
        if (ticksInEpoch >= allowedTicks) return Stage.SOLIDIFYING;
        if (ticksInEpoch >= allowedTicks * WARNING_SHARE) return Stage.WARNING;
        return Stage.STABLE;
    }
}
