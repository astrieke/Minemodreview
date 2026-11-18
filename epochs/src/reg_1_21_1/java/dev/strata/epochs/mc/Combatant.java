package dev.strata.epochs.mc;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Verwundbarer Testspieler für die Balance-Messung (Bosskampf): nimmt Schaden und schlägt voll aufgeladen zu. */
public class Combatant extends FakePlayer {

    public Combatant(ServerLevel level, GameProfile profile) {
        super(level, profile);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return false;   // FakePlayer ist in 1.21.1 gegen alles unverwundbar
    }

    /**
     * Spawn-Schutz aus: Ein neuer ServerPlayer ist 60 Ticks unverwundbar, heruntergezählt in ServerPlayer.tick, das ein
     * FakePlayer auslässt; ohne das bliebe er für immer geschützt. Das Feld ist privat (Laufzeit: Mojang-Namen).
     */
    public void dropSpawnProtection() {
        try {
            var field = net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            field.setAccessible(true);
            field.setInt(this, 0);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("ServerPlayer.spawnInvulnerableTime fehlt", e);
        }
    }

    private static java.lang.reflect.Method detectEquipment;

    /**
     * Was ServerPlayer.tick sonst je Tick erledigt und für den Kampf zählt: Treffer-Abklingzeit und Ausrüstungswechsel.
     * Ohne Letzteres wirken Schwert und Rüstung nicht (ihre Attribute übernimmt LivingEntity.tick); der Spieler kämpfte
     * dann mit der Faust und ohne Rüstungsschutz.
     */
    public void tickCombat() {
        if (invulnerableTime > 0) invulnerableTime--;
        if (hurtTime > 0) hurtTime--;
        try {
            if (detectEquipment == null) {
                detectEquipment = net.minecraft.world.entity.LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates");
                detectEquipment.setAccessible(true);
            }
            detectEquipment.invoke(this);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("LivingEntity.detectEquipmentUpdates fehlt", e);
        }
    }

    /** Schlag voll aufladen (zählt sonst ServerPlayer.tick hoch, das ein FakePlayer auslässt). */
    public void charge() {
        attackStrengthTicker = 1000;
    }
}
