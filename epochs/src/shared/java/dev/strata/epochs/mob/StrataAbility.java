package dev.strata.epochs.mob;

import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Gerüst für eine Mob-Fähigkeit: Vorwarnung, Wirkung, Abklingzeit.
 *
 * <p>Jede Fähigkeit kündigt sich an ({@link #onWarn}), wirkt dann eine Weile ({@link #onStart},
 * {@link #onActiveTick}, {@link #onEnd}) und braucht danach Zeit, bis sie wieder bereit ist.
 * Dauer, Vorwarnzeit und Abklingzeit kommen aus {@code strata_core:mob_tuning}
 * ({@code warning}, {@code duration}, {@code cooldown}, in Ticks), sonst aus den Vorgaben.
 */
public abstract class StrataAbility<M extends StrataMob> {

    public enum Phase { READY, WARNING, ACTIVE, COOLDOWN }

    protected final M mob;
    private final String id;
    private final int defaultWarning;
    private final int defaultDuration;
    private final int defaultCooldown;
    private Phase phase = Phase.READY;
    private int timer;

    protected StrataAbility(M mob, String id, int warning, int duration, int cooldown) {
        this.mob = mob;
        this.id = id;
        this.defaultWarning = warning;
        this.defaultDuration = duration;
        this.defaultCooldown = cooldown;
    }

    public String id() {
        return id;
    }

    public Phase phase() {
        return phase;
    }

    public boolean enabled() {
        return mob.tuning().abilityEnabled(id);
    }

    /** Ein Wert dieser Fähigkeit aus dem Datapack, sonst {@code fallback}. */
    public double value(String key, double fallback) {
        return mob.tuning().ability(id, key, fallback);
    }

    protected int warningTicks() {
        return (int) value("warning", defaultWarning);
    }

    protected int durationTicks() {
        return (int) value("duration", defaultDuration);
    }

    protected int cooldownTicks() {
        return (int) value("cooldown", defaultCooldown);
    }

    /** Startet die Fähigkeit, wenn sie bereit und eingeschaltet ist. */
    public boolean trigger() {
        if (phase != Phase.READY || !enabled()) return false;
        phase = Phase.WARNING;
        timer = warningTicks();
        onWarn();
        if (timer <= 0) activate();
        return true;
    }

    /** Bricht die Fähigkeit ab (z. B. bei Rückstoß) und startet die Abklingzeit. */
    public void interrupt() {
        if (phase == Phase.WARNING || phase == Phase.ACTIVE) {
            onEnd();
            phase = Phase.COOLDOWN;
            timer = cooldownTicks();
        }
    }

    final void tick() {
        switch (phase) {
            case READY -> { }
            case WARNING -> {
                onWarnTick();
                if (--timer <= 0) activate();
            }
            case ACTIVE -> {
                onActiveTick();
                if (--timer <= 0) {
                    onEnd();
                    phase = Phase.COOLDOWN;
                    timer = cooldownTicks();
                }
            }
            case COOLDOWN -> {
                if (--timer <= 0) phase = Phase.READY;
            }
        }
    }

    private void activate() {
        phase = Phase.ACTIVE;
        timer = durationTicks();
        onStart();
    }

    protected void onWarn() {}

    /** Jeden Tick der Vorwarnung (z. B. Warnkreise am Boden). */
    protected void onWarnTick() {}

    protected void onStart() {}

    protected void onActiveTick() {}

    protected void onEnd() {}

    /** Eingehender Schaden am Mob (Server). Fähigkeiten können ihn ändern oder darauf reagieren. */
    protected void onIncomingDamage(LivingIncomingDamageEvent event) {}
}
