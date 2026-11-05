package dev.strata.epochs.mob;

import dev.strata.epochs.EpochsContent;
import dev.strata.epochs.StrataEpochs;
import dev.strata.epochs.mc.MobCompat;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Geodenkriecher: träge Urzeitechse der Kristallzeit, neutral.
 *
 * <p>Fähigkeiten (Werte per {@code strata_core:mob_tuning} einstellbar):
 * <ul>
 *   <li><b>Erzwitterung</b> ({@code ore_sense}): wittert Kristallquarz-Erz, zeigt mit einer Partikelspur
 *       die Richtung und läuft hin.</li>
 *   <li><b>Kratzfund</b> ({@code ore_scratch}): kratzt 3 s am Erz und lockert einen Kristallquarz,
 *       ohne den Block abzubauen. Mit Kristallquarz angelockt geht es schneller wieder.</li>
 *   <li><b>Kristallpanzer</b> ({@code crystal_shell}): rollt sich nach einem Treffer ein, nimmt weniger
 *       Schaden und gibt Nahkämpfern Splitterschaden zurück. Fernkampf löst nichts aus.</li>
 * </ul>
 */
public class GeodeCrawler extends StrataMob {

    public static final StrataMobDef DEF = new StrataMobDef(StrataEpochs.MOD_ID, "geode_crawler",
            List.of("scratch", "sniff", "curl"), "idle", "walk", List.of("crystals"), 0.5f);

    /** Grundleuchten der Kristalle; vor dem Einrollen blitzen sie auf 1,0 auf. */
    public static final float BASE_GLOW = 0.6f;
    private static final int TRAIL_COLOR = 0xb48cff;

    private final CrystalShell shell = new CrystalShell(this);
    private int scratchReadyAt;
    private int lastTemptedTick = -10_000;

    public GeodeCrawler(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        abilities.add(shell);
        setGlow(BASE_GLOW);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return MobCompat.baseAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.18)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    public StrataMobDef def() {
        return DEF;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new CurledGoal());
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true));
        goalSelector.addGoal(3, new TemptGoal(this, 1.1, this::isLure, false) {
            @Override
            public void tick() {
                super.tick();
                lastTemptedTick = GeodeCrawler.this.tickCount;
            }
        });
        goalSelector.addGoal(4, new OreSeekGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    private boolean isLure(ItemStack stack) {
        return stack.is(EpochsContent.CRYSTAL_QUARTZ.get());
    }

    public boolean isCurled() {
        return shell.phase() == StrataAbility.Phase.ACTIVE;
    }

    // ---- Sounds (eigene Ereignisse mit Untertiteln, Klang aus Vanilla) ----

    @Override
    protected SoundEvent getAmbientSound() {
        return dev.strata.epochs.EpochsMobs.GEODE_CRAWLER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return dev.strata.epochs.EpochsMobs.GEODE_CRAWLER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return dev.strata.epochs.EpochsMobs.GEODE_CRAWLER_DEATH.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 240;
    }

    // ---- Hilfen ----

    private ServerLevel server() {
        return (ServerLevel) level();
    }

    private Vec3 crystalTop() {
        return position().add(0, getBbHeight() + 0.2, 0);
    }

    private void sound(SoundEvent sound, float volume, float pitch) {
        level().playSound(null, getX(), getY(), getZ(), sound, SoundSource.NEUTRAL, volume, pitch);
    }

    /** Sucht das nächste freiliegende Kristallquarz-Erz im Umkreis. */
    private BlockPos findOre(int radius) {
        BlockPos center = blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    pos.setWithOffset(center, dx, dy, dz);
                    if (!level().getBlockState(pos).is(EpochsContent.CRYSTAL_QUARTZ_ORE.get())) continue;
                    double dist = pos.distSqr(center);
                    if (dist < bestDist && exposedFace(pos) != null) {
                        bestDist = dist;
                        best = pos.immutable();
                    }
                }
            }
        }
        return best;
    }

    /** Eine Seite des Blocks, vor der Luft ist (dort steht der Kriecher beim Kratzen). */
    private Direction exposedFace(BlockPos ore) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (level().getBlockState(ore.relative(d)).isAir()) return d;
        }
        return level().getBlockState(ore.above()).isAir() ? Direction.UP : null;
    }

    // ---- Kristallpanzer ----

    static final class CrystalShell extends StrataAbility<GeodeCrawler> {

        CrystalShell(GeodeCrawler mob) {
            super(mob, "crystal_shell", 8, 60, 400);
        }

        @Override
        protected void onWarn() {
            mob.setGlow(1.0f);
            mob.sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.2f);
            Vec3 top = mob.crystalTop();
            mob.server().sendParticles(ParticleTypes.ELECTRIC_SPARK, top.x, top.y, top.z, 8, 0.3, 0.2, 0.3, 0.05);
        }

        @Override
        protected void onStart() {
            mob.playAction("curl");
            mob.getNavigation().stop();
            mob.server().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CALCITE.defaultBlockState()),
                    mob.getX(), mob.getY() + 0.4, mob.getZ(), 16, 0.4, 0.2, 0.4, 0.1);
        }

        @Override
        protected void onActiveTick() {
            mob.getNavigation().stop();
            Vec3 v = mob.getDeltaMovement();
            mob.setDeltaMovement(0, v.y, 0);
        }

        @Override
        protected void onEnd() {
            mob.setGlow(BASE_GLOW);
            if ("curl".equals(mob.currentAction())) mob.stopAction();
        }

        @Override
        protected void onIncomingDamage(LivingIncomingDamageEvent event) {
            DamageSource source = event.getSource();
            if (phase() == Phase.READY) {
                if (source.getEntity() != null && source.getEntity() != mob) trigger();
                return;
            }
            if (phase() != Phase.ACTIVE) return;
            event.setAmount(event.getAmount() * (float) value("damage_factor", 0.4));
            Entity attacker = source.getEntity();
            boolean melee = attacker instanceof LivingEntity && source.getDirectEntity() == attacker
                    && attacker.distanceToSqr(mob) < 16;
            if (melee) {
                MobCompat.hurt(attacker, mob.damageSources().thorns(mob), (float) value("thorns", 2.0));
                mob.server().sendParticles(MobCompat.dust(TRAIL_COLOR, 1.0f),
                        attacker.getX(), attacker.getY() + 1.0, attacker.getZ(), 10, 0.3, 0.3, 0.3, 0.0);
                mob.sound(SoundEvents.AMETHYST_CLUSTER_HIT, 1.0f, 1.0f);
            }
        }
    }

    /** Hält alle Bewegungs-Ziele an, solange der Kriecher eingerollt ist. */
    private final class CurledGoal extends Goal {
        CurledGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return isCurled();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }

    // ---- Erzwitterung und Kratzfund ----

    private final class OreSeekGoal extends Goal {
        private static final int SNIFF_TICKS = 24;
        private static final int WALK_TIMEOUT = 200;
        private static final int SCRATCH_TICKS = 60;

        private enum Step { SNIFF, WALK, SCRATCH }

        private int nextSense;
        private BlockPos ore;
        private Step step;
        private int timer;

        OreSeekGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private double sense(String key, double fallback) {
            return tuning().ability("ore_sense", key, fallback);
        }

        private double scratch(String key, double fallback) {
            return tuning().ability("ore_scratch", key, fallback);
        }

        @Override
        public boolean canUse() {
            if (tickCount < nextSense || getTarget() != null || isCurled()) return false;
            if (!tuning().abilityEnabled("ore_sense")) return false;
            int min = (int) sense("interval_min", 400);
            int max = (int) sense("interval_max", 800);
            nextSense = tickCount + min + getRandom().nextInt(Math.max(1, max - min));
            ore = findOre((int) sense("radius", 12));
            return ore != null;
        }

        @Override
        public boolean canContinueToUse() {
            return ore != null && step != null && getTarget() == null && !isCurled()
                    && level().getBlockState(ore).is(EpochsContent.CRYSTAL_QUARTZ_ORE.get());
        }

        @Override
        public void start() {
            step = Step.SNIFF;
            timer = SNIFF_TICKS;
            getNavigation().stop();
            playAction("sniff");
            sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.4f);
            showTrail();
        }

        @Override
        public void stop() {
            step = null;
            ore = null;
            getNavigation().stop();
            String action = currentAction();
            if ("scratch".equals(action) || "sniff".equals(action)) stopAction();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            getLookControl().setLookAt(ore.getX() + 0.5, ore.getY() + 0.5, ore.getZ() + 0.5);
            timer--;
            switch (step) {
                case SNIFF -> {
                    if (timer == SNIFF_TICKS / 2) showTrail();
                    if (timer <= 0) {
                        step = Step.WALK;
                        timer = WALK_TIMEOUT;
                        getNavigation().moveTo(ore.getX() + 0.5, ore.getY(), ore.getZ() + 0.5, 1.0);
                    }
                }
                case WALK -> {
                    boolean close = position().distanceToSqr(Vec3.atCenterOf(ore)) < 2.6 * 2.6;
                    if (close) {
                        getNavigation().stop();
                        if (tickCount >= scratchReadyAt && tuning().abilityEnabled("ore_scratch")) {
                            step = Step.SCRATCH;
                            timer = (int) scratch("duration", SCRATCH_TICKS);
                            playAction("scratch");
                        } else {
                            ore = null;
                        }
                    } else if (timer <= 0) {
                        ore = null;
                    } else if (getNavigation().isDone()) {
                        getNavigation().moveTo(ore.getX() + 0.5, ore.getY(), ore.getZ() + 0.5, 1.0);
                    }
                }
                case SCRATCH -> scratchTick();
            }
        }

        private void scratchTick() {
            BlockState state = level().getBlockState(ore);
            Vec3 paw = position().add(getLookAngle().scale(0.8)).add(0, 0.2, 0);
            if (timer % 28 == 0 && timer > 0) playAction("scratch");
            if (timer % 5 == 0) {
                server().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                        paw.x, paw.y, paw.z, 4, 0.15, 0.1, 0.15, 0.05);
            }
            if (timer % 7 == 0) sound(SoundEvents.AMETHYST_CLUSTER_HIT, 0.8f, 0.9f + getRandom().nextFloat() * 0.3f);
            if (timer > 0) return;

            // Fund: ein Kristallquarz fällt heraus, der Block bleibt.
            server().sendParticles(ParticleTypes.CRIT, paw.x, paw.y + 0.2, paw.z, 12, 0.2, 0.2, 0.2, 0.3);
            sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
            ItemEntity drop = new ItemEntity(level(), paw.x, paw.y + 0.3, paw.z, new ItemStack(EpochsContent.CRYSTAL_QUARTZ.get()));
            drop.setDefaultPickUpDelay();
            level().addFreshEntity(drop);
            boolean lured = tickCount - lastTemptedTick < 200;
            scratchReadyAt = tickCount + (int) (lured ? scratch("cooldown_lured", 1200) : scratch("cooldown", 2400));
            ore = null;
        }

        /** Violette Spur von der Schnauze 3 Blöcke in Richtung Erz, dazu Funken an den Kristallen. */
        private void showTrail() {
            Vec3 from = position().add(0, 0.5, 0);
            Vec3 dir = Vec3.atCenterOf(ore).subtract(from).normalize();
            for (int i = 1; i <= 12; i++) {
                Vec3 p = from.add(dir.scale(i * 0.25));
                server().sendParticles(MobCompat.dust(TRAIL_COLOR, 1.0f), p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
            }
            Vec3 top = crystalTop();
            server().sendParticles(ParticleTypes.ELECTRIC_SPARK, top.x, top.y, top.z, 5, 0.25, 0.15, 0.25, 0.02);
        }
    }

    // ---- Spawn ----

    /** Spawnregel ohne Typen, die sich zwischen den Versionen unterscheiden. */
    public static boolean canSpawnHere(net.minecraft.world.level.ServerLevelAccessor level, BlockPos pos) {
        ServerLevel server = level.getLevel();
        var tuning = dev.strata.core.api.StrataAPI.mobTuning(server.getServer(), DEF.id());
        if (tuning.spawn("enabled", 1) == 0) return false;
        if (level.getRawBrightness(pos, 0) > tuning.spawn("max_light", 7)) return false;
        int maxNearby = (int) tuning.spawn("max_nearby", 4);
        return level.getEntitiesOfClass(GeodeCrawler.class, new AABB(pos).inflate(32)).size() < maxNearby;
    }
}
