package dev.strata.epochs.mob;

import dev.strata.core.api.StrataAPI;
import dev.strata.epochs.EpochsConfig;
import dev.strata.epochs.EpochsContent;
import dev.strata.epochs.EpochsMobs;
import dev.strata.epochs.mc.MobCompat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Erwachen aus Stein: Bei Spielern, die schon verfestigen, wird ein versteinerter Block in der Nähe zum Steinzeugen.
 *
 * <p>Ablauf: 1 s Risse im Block (Abbau-Anzeige), dann verschwindet der Block und der Steinzeuge erwacht 2,2 s lang,
 * in dieser Zeit unverwundbar. Höchstens einer pro Spieler gleichzeitig, danach Abklingzeit.
 */
public final class WitnessAwakening {

    private static final int CRACK_TICKS = 20;

    private record Pending(ServerLevel level, BlockPos pos, UUID player, long start, int breakerId) {}

    private static final List<Pending> PENDING = new ArrayList<>();
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
    private static final Map<UUID, UUID> ACTIVE = new HashMap<>();

    private WitnessAwakening() {}

    /** Aus der Verfestigung aufgerufen (Stufe SOLIDIFYING). Sucht einen passenden versteinerten Block. */
    public static void tryAwaken(ServerLevel level, ServerPlayer player) {
        if (!EpochsConfig.PETRIFIED_WITNESS.get()) return;
        var tuning = StrataAPI.mobTuning(level.getServer(), PetrifiedWitness.DEF.id());
        if (!tuning.abilityEnabled("awaken")) return;
        long now = level.getGameTime();
        synchronized (PENDING) {
            if (COOLDOWN.getOrDefault(player.getUUID(), 0L) > now) return;
            UUID active = ACTIVE.get(player.getUUID());
            if (active != null && level.getEntity(active) instanceof PetrifiedWitness w && w.isAlive()) return;
            if (PENDING.stream().anyMatch(p -> p.player.equals(player.getUUID()))) return;
        }
        int range = (int) tuning.ability("awaken", "range", 8);
        BlockPos found = null;
        BlockPos center = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-range, -3, -range), center.offset(range, 3, range))) {
            if (pos.distSqr(center) < 9 || !level.getBlockState(pos).is(EpochsContent.PETRIFIED_STONE.get())) continue;
            if (hasRoom(level, pos)) {
                found = pos.immutable();
                break;
            }
        }
        if (found == null) return;
        synchronized (PENDING) {
            PENDING.add(new Pending(level, found, player.getUUID(), now, level.getRandom().nextInt(Integer.MAX_VALUE)));
            COOLDOWN.put(player.getUUID(), now + (long) tuning.ability("awaken", "cooldown", 1200));
        }
    }

    /** Fester Boden darunter und 2 × 3 Blöcke Platz (die Hitbox ist 1,3 × 2,4). */
    static boolean hasRoom(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) return false;
        for (int dy = 1; dy <= 2; dy++) {
            if (!level.getBlockState(pos.above(dy)).isAir()) return false;
        }
        int free = 0;
        for (var dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(dir);
            if (level.getBlockState(side.above()).isAir() && level.getBlockState(side.above(2)).isAir()) free++;
        }
        return free >= 1;
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        List<Pending> done = new ArrayList<>();
        synchronized (PENDING) {
            for (Pending p : PENDING) {
                if (p.level != level) continue;
                long age = now - p.start;
                if (!level.getBlockState(p.pos).is(EpochsContent.PETRIFIED_STONE.get())) {
                    level.destroyBlockProgress(p.breakerId, p.pos, -1);
                    done.add(p);
                    continue;
                }
                if (age < CRACK_TICKS) {
                    level.destroyBlockProgress(p.breakerId, p.pos, (int) (age * 10 / CRACK_TICKS));
                    if (age % 5 == 0) {
                        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(p.pos)),
                                p.pos.getX() + 0.5, p.pos.getY() + 1.0, p.pos.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0.05);
                    }
                    continue;
                }
                level.destroyBlockProgress(p.breakerId, p.pos, -1);
                level.setBlockAndUpdate(p.pos, Blocks.AIR.defaultBlockState());
                PetrifiedWitness w = new PetrifiedWitness(EpochsMobs.PETRIFIED_WITNESS.get(), level);
                w.setPos(p.pos.getX() + 0.5, p.pos.getY(), p.pos.getZ() + 0.5);
                if (level.getEntity(p.player) instanceof ServerPlayer player) {
                    double dx = player.getX() - w.getX(), dz = player.getZ() - w.getZ();
                    float yaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90f;
                    w.setYRot(yaw);
                    w.setYHeadRot(yaw);
                    w.yBodyRot = yaw;
                }
                level.addFreshEntity(w);
                w.startAwakening();
                level.playSound(null, p.pos, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.5f, 0.6f);
                level.sendParticles(MobCompat.dust(PetrifiedWitness.GOLD, 1.5f), w.getX(), w.getY() + 1.0, w.getZ(),
                        20, 0.4, 0.6, 0.4, 0.0);
                ACTIVE.put(p.player, w.getUUID());
                done.add(p);
            }
            PENDING.removeAll(done);
        }
    }
}
