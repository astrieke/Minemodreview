package dev.strata.epochs.mob;

import dev.strata.core.mc.Mc;
import dev.strata.epochs.Solidification;
import dev.strata.epochs.mc.MobCompat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Sporenwolken: machen keinen Schaden, lassen aber die Verfestigungs-Uhr schneller laufen.
 *
 * <p>Die Wolke bleibt liegen, auch wenn der Sporenbalg weiterhüpft oder stirbt. Sie lebt nur im
 * Speicher (nach einem Neustart ist sie weg), das reicht für wenige Sekunden.
 */
public final class SporeClouds {

    public static final int GREEN = 0x4f7a3a;
    private static final int APPLY_INTERVAL = 10;

    private record Cloud(ServerLevel level, Vec3 pos, double radius, long until, double factor, Set<UUID> warned) {}

    private static final List<Cloud> CLOUDS = new ArrayList<>();

    private SporeClouds() {}

    /** Legt eine Wolke an: {@code factor} = wie viel schneller die Uhr läuft, {@code ticks} = Lebensdauer. */
    public static void spawn(ServerLevel level, Vec3 pos, double radius, int ticks, double factor) {
        synchronized (CLOUDS) {
            CLOUDS.add(new Cloud(level, pos, radius, level.getGameTime() + ticks, factor, new HashSet<>()));
        }
        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, pos.x, pos.y + 0.5, pos.z, 40, radius / 2, 0.4, radius / 2, 0.02);
        level.sendParticles(MobCompat.dust(GREEN, 1.6f), pos.x, pos.y + 0.6, pos.z, 30, radius / 2, 0.4, radius / 2, 0.0);
    }

    public static int count() {
        synchronized (CLOUDS) {
            return CLOUDS.size();
        }
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        synchronized (CLOUDS) {
            CLOUDS.removeIf(c -> c.level == level && c.until <= now);
            for (Cloud c : CLOUDS) {
                if (c.level != level) continue;
                if (now % 4 == 0) {
                    level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, c.pos.x, c.pos.y + 0.5, c.pos.z, 6,
                            c.radius / 2, 0.5, c.radius / 2, 0.01);
                    level.sendParticles(MobCompat.dust(GREEN, 1.2f), c.pos.x, c.pos.y + 0.7, c.pos.z, 4,
                            c.radius / 2, 0.4, c.radius / 2, 0.0);
                }
                if (now % APPLY_INTERVAL != 0) continue;
                AABB box = new AABB(c.pos, c.pos).inflate(c.radius, 1.5, c.radius);
                for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box)) {
                    double dx = player.getX() - c.pos.x, dz = player.getZ() - c.pos.z;
                    if (dx * dx + dz * dz > c.radius * c.radius || player.isCreative() || player.isSpectator()) continue;
                    Solidification.speedUp(player, c.factor, APPLY_INTERVAL + 10);
                    // Hinweis in der Statuszeile, grün wie die Wolke: Die Uhr läuft gerade schneller.
                    if (c.warned.add(player.getUUID()) || now % 20 == 0) {
                        Mc.actionbar(player, Component.translatable("message.strata_epochs.spores",
                                String.format(java.util.Locale.ROOT, "%.1f", c.factor)).withStyle(ChatFormatting.GREEN));
                    }
                }
            }
        }
    }
}
