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

    /**
     * Versteinert bei diesem Schritt der Uhr ein Gegenstand im Inventar? Erst versteinern nur Blöcke; der erste
     * Gegenstand folgt, wenn die erlaubte Zeit um ein volles Intervall überzogen ist, danach einer pro Intervall.
     * Gezählt wird nach der Uhr des Spielers, eine Sporenwolke beschleunigt also auch das.
     *
     * @param intervalTicks Abstand in Ticks (0 = Gegenstände versteinern nie)
     */
    public static boolean petrifiesItem(long ticksBefore, long ticksNow, long allowedTicks, long intervalTicks) {
        if (intervalTicks <= 0 || allowedTicks == Long.MAX_VALUE || ticksNow < allowedTicks) return false;
        long before = Math.max(0, ticksBefore - allowedTicks) / intervalTicks;
        long now = (ticksNow - allowedTicks) / intervalTicks;
        return now > before;
    }

    /** Versteinerte Gegenstände lösen sich, sobald der Spieler nicht (mehr) verfestigt. */
    public static boolean thawsItems(Stage stage) {
        return stage != Stage.SOLIDIFYING;
    }

    public static Stage stage(long ticksInEpoch, long allowedTicks) {
        if (allowedTicks == Long.MAX_VALUE) return Stage.STABLE;
        if (ticksInEpoch >= allowedTicks) return Stage.SOLIDIFYING;
        if (ticksInEpoch >= allowedTicks * WARNING_SHARE) return Stage.WARNING;
        return Stage.STABLE;
    }
}
