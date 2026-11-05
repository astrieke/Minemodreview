package dev.strata.epochs.mob;

import dev.strata.core.mc.Mc;
import dev.strata.epochs.StrataEpochs;
import dev.strata.epochs.mc.MobCompat;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Sporenbalg: pilziger Sack der Sporenzeit, Gegner. Er macht keinen Schaden, setzt den Spieler aber unter Zeitdruck.
 *
 * <p>Fähigkeiten (Werte per {@code strata_core:mob_tuning} einstellbar):
 * <ul>
 *   <li><b>Sporenstoß</b> ({@code spore_blast}): pumpt sich 0,6 s auf und stößt eine Wolke aus, in der die
 *       Verfestigung schneller läuft. Ein Treffer während des Aufpumpens bricht ab.</li>
 *   <li><b>Wurzelgriff</b> ({@code root_grip}): landet er nach einem Sprung neben dem Spieler, gibt es Langsamkeit II.</li>
 *   <li><b>Letzter Atemzug</b> ({@code last_breath}): platzt beim Tod und hinterlässt eine schwächere Wolke.</li>
 * </ul>
 */
public class SporeBellows extends StrataMob {

    public static final StrataMobDef DEF = new StrataMobDef(StrataEpochs.MOD_ID, "spore_bellows",
            List.of("pump", "hop"), "idle", null, List.of("spores"), 0.45f);

    /** Augen und Bernsteinpunkte glimmen schwach, beim Aufpumpen leuchten sie voll. */
    public static final float BASE_GLOW = 0.3f;

    private final SporeBlast blast = new SporeBlast(this);
    private final RootGrip rootGrip = new RootGrip(this);

    public SporeBellows(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        abilities.add(blast);
        abilities.add(rootGrip);
        setGlow(BASE_GLOW);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return MobCompat.baseAttributes()
                .add(Attributes.MAX_HEALTH, 14.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 20.0);
    }

    @Override
    public StrataMobDef def() {
        return DEF;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new HopGoal());
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0f));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel && level().getDifficulty() == Difficulty.PEACEFUL) discard();
    }

    // ---- Sounds ----

    @Override
    protected SoundEvent getAmbientSound() {
        return dev.strata.epochs.EpochsMobs.SPORE_BELLOWS_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return dev.strata.epochs.EpochsMobs.SPORE_BELLOWS_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return dev.strata.epochs.EpochsMobs.SPORE_BELLOWS_DEATH.get();
    }

    // ---- Hilfen ----

    private ServerLevel server() {
        return (ServerLevel) level();
    }

    private void sound(SoundEvent sound, float volume, float pitch) {
        level().playSound(null, getX(), getY(), getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    boolean isPumping() {
        return blast.phase() == StrataAbility.Phase.WARNING;
    }

    // ---- Sporenstoß ----

    static final class SporeBlast extends StrataAbility<SporeBellows> {
        SporeBlast(SporeBellows mob) {
            super(mob, "spore_blast", 12, 1, 160);
        }

        @Override
        protected void onWarn() {
            mob.getNavigation().stop();
            mob.playAction("pump");
            mob.setGlow(1.0f);
            mob.sound(SoundEvents.PUFFER_FISH_BLOW_UP, 1.0f, 0.8f);
        }

        @Override
        protected void onStart() {
            Vec3 at = mob.position();
            SporeClouds.spawn(mob.server(), at, value("radius", 2.5), (int) value("cloud_duration", 100), value("factor", 2.5));
            mob.sound(SoundEvents.FUNGUS_BREAK, 1.2f, 0.7f);
        }

        @Override
        protected void onEnd() {
            mob.setGlow(BASE_GLOW);
        }

        /** Treffer mit Rückstoß während des Aufpumpens: Die Wolke kommt nicht. */
        @Override
        protected void onIncomingDamage(LivingIncomingDamageEvent event) {
            if (phase() == Phase.WARNING && event.getSource().getEntity() != null) {
                interrupt();
                mob.stopAction();
                mob.server().sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.6, mob.getZ(), 6, 0.2, 0.2, 0.2, 0.02);
            }
        }
    }

    // ---- Wurzelgriff ----

    static final class RootGrip extends StrataAbility<SporeBellows> {
        RootGrip(SporeBellows mob) {
            super(mob, "root_grip", 0, 1, 160);
        }

        @Override
        protected void onStart() {
            ServerLevel level = mob.server();
            double range = value("range", 2.0);
            int ticks = (int) value("effect_duration", 40);
            int amplifier = (int) value("amplifier", 1);
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, mob.getBoundingBox().inflate(range, 1, range))) {
                if (p.isCreative() || p.isSpectator()) continue;
                p.addEffect(new MobEffectInstance(Mc.slowness(), ticks, amplifier));
            }
            BlockParticleOption mycelium = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MYCELIUM.defaultBlockState());
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI / 8;
                double x = mob.getX() + Math.cos(a) * range * 0.8, z = mob.getZ() + Math.sin(a) * range * 0.8;
                level.sendParticles(mycelium, x, mob.getY() + 0.1, z, 2, 0.05, 0.05, 0.05, 0.05);
            }
            level.sendParticles(ParticleTypes.FALLING_SPORE_BLOSSOM, mob.getX(), mob.getY() + 0.8, mob.getZ(), 10, range / 2, 0.3, range / 2, 0.0);
            mob.sound(SoundEvents.ROOTS_BREAK, 1.0f, 0.8f);
            mob.sound(SoundEvents.MUD_STEP, 1.0f, 0.8f);
        }
    }

    // ---- Hüpfen ----

    /** Fortbewegung nur in Sprüngen: auf das Ziel zu, sonst ab und zu in eine zufällige Richtung. */
    private final class HopGoal extends Goal {
        private static final int TAKEOFF_DELAY = 2;
        private int cooldown;
        private int takeoff = -1;
        private boolean airborne;
        private float hopYaw;

        HopGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target != null) getLookControl().setLookAt(target, 30f, 30f);

            // Landung: Wurzelgriff, wenn der Spieler nah genug ist.
            if (airborne && onGround()) {
                airborne = false;
                if (target instanceof Player && distanceTo(target) <= rootGrip.value("range", 2.0)) rootGrip.trigger();
            }
            if (!onGround()) {
                airborne = true;
                return;
            }
            if (isPumping()) return;

            // Nah dran: Sporenstoß statt Sprung.
            if (target != null && distanceTo(target) <= blast.value("trigger_range", 3.0) && blast.trigger()) return;

            if (takeoff >= 0 && --takeoff == 0) {
                takeoff = -1;
                launch();
                return;
            }
            if (--cooldown > 0 || takeoff >= 0) return;

            if (target != null) {
                hopYaw = (float) (Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * (180 / Math.PI)) - 90f;
                cooldown = 22 + getRandom().nextInt(10);
            } else if (getRandom().nextInt(6) == 0) {
                hopYaw = getRandom().nextFloat() * 360f;
                cooldown = 60 + getRandom().nextInt(60);
            } else {
                cooldown = 20;
                return;
            }
            setYRot(hopYaw);
            yBodyRot = hopYaw;
            playActionFor("hop", 12);
            takeoff = TAKEOFF_DELAY;
        }

        private void launch() {
            float rad = hopYaw * (float) (Math.PI / 180);
            double speed = 0.32;
            // Flach springen, das Modell hebt den Körper selbst um 5 px; vor einer Kante höher.
            BlockPos ahead = BlockPos.containing(getX() - Math.sin(rad) * 0.8, getY() + 0.5, getZ() + Math.cos(rad) * 0.8);
            boolean step = !level().getBlockState(ahead).getCollisionShape(level(), ahead).isEmpty();
            setDeltaMovement(-Math.sin(rad) * speed, step ? 0.48 : 0.3, Math.cos(rad) * speed);
            airborne = true;
            if (rootGrip.phase() == StrataAbility.Phase.READY && getTarget() != null) {
                // Ankündigung des Wurzelgriffs: Die Wurzeln blitzen beim Absprung auf.
                server().sendParticles(ParticleTypes.GLOW, getX(), getY() + 0.2, getZ(), 6, 0.25, 0.1, 0.25, 0.02);
            }
        }
    }

    // ---- Letzter Atemzug ----

    /** Beim Tod: kurz aufblähen, platzen, eine schwächere Wolke bleibt liegen. */
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof SporeBellows mob) || !(mob.level() instanceof ServerLevel level)) return;
        if (!mob.tuning().abilityEnabled("last_breath")) return;
        mob.playAction("pump");
        Vec3 at = mob.position();
        SporeClouds.spawn(level, at, mob.tuning().ability("last_breath", "radius", 2.0),
                (int) mob.tuning().ability("last_breath", "cloud_duration", 40),
                mob.tuning().ability("last_breath", "factor", 1.5));
        level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.5, at.z, 12, 0.3, 0.3, 0.3, 0.05);
        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, at.x, at.y + 0.5, at.z, 60, 0.8, 0.5, 0.8, 0.05);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.0f, 0.6f);
    }

    // ---- Spawn ----

    public static boolean canSpawnHere(net.minecraft.world.level.ServerLevelAccessor level, BlockPos pos) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) return false;
        var tuning = dev.strata.core.api.StrataAPI.mobTuning(level.getLevel().getServer(), DEF.id());
        if (tuning.spawn("enabled", 1) == 0) return false;
        if (level.getRawBrightness(pos, 0) > tuning.spawn("max_light", 7)) return false;
        int maxNearby = (int) tuning.spawn("max_nearby", 6);
        return level.getEntitiesOfClass(SporeBellows.class, new AABB(pos).inflate(32)).size() < maxNearby;
    }
}
