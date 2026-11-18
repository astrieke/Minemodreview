package dev.strata.epochs.mc;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Verwundbarer Testspieler für die Balance-Messung (Bosskampf): nimmt Schaden und schlägt voll aufgeladen zu. */
public class Combatant extends FakePlayer {

    public Combatant(ServerLevel level, GameProfile profile) {
        super(level, profile);
        // FakePlayer macht sich unverwundbar: bis 26.2 über setInvulnerable, ab 26.3 über setPermanentlyInvulnerable.
        for (String name : new String[] {"setPermanentlyInvulnerable", "setInvulnerable"}) {
            try {
                net.minecraft.world.entity.Entity.class.getMethod(name, boolean.class).invoke(this, false);
            } catch (ReflectiveOperationException ignored) {
                // gibt es in dieser Version nicht
            }
        }
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
        } catch (ReflectiveOperationException ignored) {
            // ab 26.1 gibt es das Feld nicht mehr; dort schützt "Client nicht geladen", siehe isInvulnerableTo
        }
    }

    /** Ein Testspieler hat keinen Client, der sich als geladen meldet; ServerPlayer hielte ihn sonst für geschützt. */
    @Override
    public boolean isInvulnerableTo(ServerLevel level, net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    private static java.lang.reflect.Method detectEquipment;
    private static java.lang.reflect.Field hurtCooldown;

    /**
     * Was ServerPlayer.tick sonst je Tick erledigt und für den Kampf zählt: Treffer-Abklingzeit und Ausrüstungswechsel.
     * Ohne Letzteres wirken Schwert und Rüstung nicht (ihre Attribute übernimmt LivingEntity.tick); der Spieler kämpfte
     * dann mit der Faust und ohne Rüstungsschutz.
     */
    public void tickCombat() {
        try {   // ab 26.3 eigenes Feld in LivingEntity (damageCooldownTime), davor Entity.invulnerableTime
            if (hurtCooldown == null) {
                try {
                    hurtCooldown = net.minecraft.world.entity.LivingEntity.class.getDeclaredField("damageCooldownTime");
                } catch (NoSuchFieldException e) {
                    hurtCooldown = net.minecraft.world.entity.Entity.class.getDeclaredField("invulnerableTime");
                }
                hurtCooldown.setAccessible(true);
            }
            int left = hurtCooldown.getInt(this);
            if (left > 0) hurtCooldown.setInt(this, left - 1);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Entity.invulnerableTime fehlt", e);
        }
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
