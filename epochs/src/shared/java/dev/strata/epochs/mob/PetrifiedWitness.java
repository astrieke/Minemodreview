package dev.strata.epochs.mob;

import dev.strata.core.mc.Mc;
import dev.strata.epochs.EpochsContent;
import dev.strata.epochs.Solidification;
import dev.strata.epochs.StrataEpochs;
import dev.strata.epochs.mc.MobCompat;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Steinzeuge: das Echo eines Reisenden, der zu lange in einer Epoche blieb. Gegner und Vorlage für den Mini-Boss.
 *
 * <p>Fähigkeiten (Werte per {@code strata_core:mob_tuning} einstellbar):
 * <ul>
 *   <li><b>Erwachen aus Stein</b> ({@code awaken}, siehe {@link WitnessAwakening}): entsteht aus versteinertem Stein
 *       bei Spielern, die schon verfestigen. Während des Erwachens unverwundbar.</li>
 *   <li><b>Hammerschlag</b> ({@code hammer}): langes Ausholen, Doppelfaust, Bodenwelle mit Langsamkeit.</li>
 *   <li><b>Sanduhr-Puls</b> ({@code pulse}): goldene Welle, zieht erlaubte Zeit ab. Nur auf 3–8 Blöcke, braucht Sichtlinie.</li>
 *   <li><b>Steinhaut</b> ({@code stone_skin}): einmal unter 50 % Leben; ein Spitzhackentreffer von vorn bricht sie.</li>
 * </ul>
 */
public class PetrifiedWitness extends StrataMob {

    public static final StrataMobDef DEF = new StrataMobDef(StrataEpochs.MOD_ID, "petrified_witness",
            List.of("attack", "awaken", "pulse", "harden"), "idle", "walk", List.of("core", "veins"), 0.9f);

    static final int GLOW_CORE = 0;
    static final int GLOW_VEINS = 1;
    static final int GOLD = 0xffe08a;
    private static final int AWAKEN_TICKS = 44;

    private final Pulse pulse = new Pulse(this);
    private final StoneSkin stoneSkin = new StoneSkin(this);
    private int awakening;
    private int stunned;
    private boolean skinUsed;

    public PetrifiedWitness(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        abilities.add(pulse);
        abilities.add(stoneSkin);
        setGlow(GLOW_CORE, 0.5f);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return MobCompat.baseAttributes()
                .add(Attributes.MAX_HEALTH, 50.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.ATTACK_DAMAGE, 9.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    public StrataMobDef def() {
        return DEF;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new HoldStillGoal());
        goalSelector.addGoal(2, new HammerGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** Beginnt das Erwachen (vom {@link WitnessAwakening} direkt nach dem Erscheinen aufgerufen). */
    public void startAwakening() {
        awakening = AWAKEN_TICKS;
        playAction("awaken");
        setGlow(GLOW_CORE, 1.0f);
        sound(SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.2f, 0.7f);
    }

    public boolean isAwakening() {
        return awakening > 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            discard();
            return;
        }
        if (awakening > 0) {
            if (awakening % 6 == 0) {
                level.sendParticles(MobCompat.dust(GOLD, 1.2f), getX(), getY() + 1.1, getZ() - 0.4, 6, 0.15, 0.2, 0.1, 0.0);
            }
            if (--awakening == 0) setGlow(GLOW_CORE, 0.5f);
        }
        if (stunned > 0) stunned--;
        // Kern pulsiert langsam, solange nichts anderes läuft.
        if (awakening == 0 && pulse.phase() != StrataAbility.Phase.WARNING) {
            setGlow(GLOW_CORE, 0.45f + 0.15f * (float) Math.sin(tickCount * 0.12));
        }
        LivingEntity target = getTarget();
        if (target instanceof Player && awakening == 0 && stunned == 0) {
            double d = distanceTo(target);
            if (d >= pulse.value("min_range", 3.0) && d <= pulse.value("radius", 8.0) && hasLineOfSight(target)) pulse.trigger();
        }
    }

    boolean busy() {
        return awakening > 0 || stunned > 0 || pulse.phase() == StrataAbility.Phase.WARNING;
    }

    // ---- Sounds ----

    @Override
    protected SoundEvent getAmbientSound() {
        return dev.strata.epochs.EpochsMobs.PETRIFIED_WITNESS_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return dev.strata.epochs.EpochsMobs.PETRIFIED_WITNESS_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return dev.strata.epochs.EpochsMobs.PETRIFIED_WITNESS_DEATH.get();
    }

    private ServerLevel server() {
        return (ServerLevel) level();
    }

    void sound(SoundEvent sound, float volume, float pitch) {
        level().playSound(null, getX(), getY(), getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    // ---- Schaden: unverwundbar beim Erwachen, Steinhaut ----

    @Override
    protected void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (awakening > 0) {
            event.setAmount(0);
            return;
        }
        super.onIncomingDamage(event);
        if (!skinUsed && getHealth() - event.getAmount() < getMaxHealth() / 2 && stoneSkin.enabled()) {
            skinUsed = true;
            stoneSkin.trigger();
        }
    }

    // ---- Sanduhr-Puls ----

    static final class Pulse extends StrataAbility<PetrifiedWitness> {
        Pulse(PetrifiedWitness mob) {
            super(mob, "pulse", 20, 1, 240);
        }

        @Override
        protected void onWarn() {
            mob.getNavigation().stop();
            mob.playAction("pulse");
            mob.setGlow(GLOW_CORE, 1.0f);
            mob.server().sendParticles(ParticleTypes.END_ROD, mob.getX(), mob.getY() + 1.1, mob.getZ(), 6, 0.2, 0.2, 0.2, 0.01);
        }

        @Override
        protected void onStart() {
            ServerLevel level = mob.server();
            double radius = value("radius", 8.0);
            double min = value("min_range", 3.0);
            Vec3 c = mob.position().add(0, 1.0, 0);
            for (int ring = 2; ring <= (int) radius; ring += 2) {
                for (int i = 0; i < ring * 6; i++) {
                    double a = i * 2 * Math.PI / (ring * 6);
                    level.sendParticles(MobCompat.dust(GOLD, 1.4f), c.x + Math.cos(a) * ring, c.y, c.z + Math.sin(a) * ring,
                            1, 0, 0.05, 0, 0);
                }
            }
            mob.sound(SoundEvents.BELL_RESONATE, 1.5f, 0.5f);
            long steal = (long) value("time_penalty", 200);
            int fatigue = (int) value("fatigue_duration", 80);
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, mob.getBoundingBox().inflate(radius))) {
                double d = p.distanceTo(mob);
                if (d < min || d > radius || p.isCreative() || p.isSpectator() || !mob.hasLineOfSight(p)) continue;
                Solidification.advance(p, steal);
                p.addEffect(new MobEffectInstance(Mc.miningFatigue(), fatigue, 0));
            }
        }

        @Override
        protected void onEnd() {
            mob.setGlow(GLOW_CORE, 0.5f);
        }
    }

    // ---- Steinhaut ----

    static final class StoneSkin extends StrataAbility<PetrifiedWitness> {
        StoneSkin(PetrifiedWitness mob) {
            super(mob, "stone_skin", 0, 80, 0);
        }

        @Override
        protected void onStart() {
            mob.playAction("harden");
            mob.setGlow(GLOW_VEINS, 1.0f);
            mob.heal((float) value("heal", 4.0));
            mob.getNavigation().stop();
            mob.server().sendParticles(ParticleTypes.WAX_ON, mob.getX(), mob.getY() + 1.2, mob.getZ(), 20, 0.5, 0.8, 0.5, 0.1);
            mob.sound(SoundEvents.DEEPSLATE_PLACE, 1.2f, 0.6f);
            mob.sound(SoundEvents.GRINDSTONE_USE, 0.8f, 0.6f);
        }

        @Override
        protected void onEnd() {
            mob.setGlow(GLOW_VEINS, 0f);
            if ("harden".equals(mob.currentAction())) mob.stopAction();
        }

        @Override
        protected void onIncomingDamage(LivingIncomingDamageEvent event) {
            if (phase() != Phase.ACTIVE) return;
            Entity attacker = event.getSource().getEntity();
            boolean pickaxeOnCore = attacker instanceof LivingEntity living
                    && event.getSource().getDirectEntity() == attacker
                    && living.getMainHandItem().is(ItemTags.PICKAXES)
                    && inFront(attacker);
            if (pickaxeOnCore) {
                // Der Kniff: Spitzhacke auf den Kern bricht die Steinhaut und betäubt ihn kurz.
                interrupt();
                mob.stunned = (int) value("stun", 20);
                mob.getNavigation().stop();
                mob.server().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                        EpochsContent.PETRIFIED_STONE.get().defaultBlockState()), mob.getX(), mob.getY() + 1.2, mob.getZ(),
                        30, 0.4, 0.6, 0.4, 0.15);
                mob.sound(SoundEvents.STONE_BREAK, 1.5f, 0.6f);
                return;
            }
            event.setAmount(event.getAmount() * (float) value("damage_factor", 0.2));
        }

        private boolean inFront(Entity attacker) {
            Vec3 look = mob.getLookAngle();
            Vec3 to = attacker.position().subtract(mob.position()).normalize();
            return look.x * to.x + look.z * to.z > 0.3;
        }
    }

    /** Steht still beim Erwachen, Betäubtsein, Puls-Vorwarnung und in der Steinhaut. */
    private final class HoldStillGoal extends Goal {
        HoldStillGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return busy() || stoneSkin.phase() == StrataAbility.Phase.ACTIVE;
        }

        @Override
        public void start() {
            getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            getNavigation().stop();
        }
    }

    // ---- Hammerschlag ----

    private final class HammerGoal extends Goal {
        /** Schlag bei 0,62 s: 0,5 s Ausholen mit Halten über dem Kopf als Vorwarnung. */
        private static final int IMPACT_TICK = 12;
        private int swing = -1;
        private int cooldown;

        HammerGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private double v(String key, double fallback) {
            return tuning().ability("hammer", key, fallback);
        }

        @Override
        public boolean canUse() {
            return getTarget() != null && getTarget().isAlive() && !busy();
        }

        @Override
        public boolean canContinueToUse() {
            return (swing >= 0 || canUse()) && awakening == 0 && stunned == 0;
        }

        @Override
        public void stop() {
            swing = -1;
            getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (cooldown > 0) cooldown--;
            if (swing >= 0) {
                if (target != null) getLookControl().setLookAt(target, 30f, 30f);
                if (++swing == IMPACT_TICK) impact(target);
                if (swing >= 20) swing = -1;
                return;
            }
            if (target == null) return;
            getLookControl().setLookAt(target, 30f, 30f);
            double reach = v("reach", 3.0);
            if (distanceTo(target) > reach - 0.3) {
                if (tickCount % 10 == 0 || getNavigation().isDone()) getNavigation().moveTo(target, 1.0);
            } else {
                getNavigation().stop();
                if (cooldown == 0 && tuning().abilityEnabled("hammer")) {
                    swing = 0;
                    cooldown = (int) v("cooldown", 50);
                    playAction("attack");
                    sound(SoundEvents.DEEPSLATE_BREAK, 1.2f, 0.5f);
                }
            }
        }

        private void impact(LivingEntity target) {
            ServerLevel level = server();
            Vec3 front = position().add(getLookAngle().multiply(1.6, 0, 1.6));
            var ground = level.getBlockState(net.minecraft.core.BlockPos.containing(front.x, getY() - 0.5, front.z));
            if (!ground.isAir()) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), front.x, getY() + 0.1, front.z,
                        30, 0.8, 0.1, 0.8, 0.15);
            }
            level.sendParticles(ParticleTypes.EXPLOSION, front.x, getY() + 0.3, front.z, 1, 0, 0, 0, 0);
            level.playSound(null, front.x, getY(), front.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.4f, 0.6f);
            if (target != null && distanceTo(target) <= v("reach", 3.0) + 0.5) {
                float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
                MobCompat.hurt(target, damageSources().mobAttack(PetrifiedWitness.this), damage);
                target.push(0, v("lift", 0.4), 0);
            }
            double wave = v("wave_radius", 2.0);
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new net.minecraft.world.phys.AABB(front, front).inflate(wave, 1, wave))) {
                if (p.isCreative() || p.isSpectator()) continue;
                p.addEffect(new MobEffectInstance(Mc.slowness(), (int) v("slow_duration", 40), 0));
            }
        }
    }
}
