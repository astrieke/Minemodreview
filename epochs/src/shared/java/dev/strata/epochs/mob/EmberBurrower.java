package dev.strata.epochs.mob;

import dev.strata.epochs.EpochTravel;
import dev.strata.epochs.EpochsMobs;
import dev.strata.epochs.FossilPrep;
import dev.strata.epochs.StrataEpochs;
import dev.strata.epochs.mc.MobCompat;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Glutwühler: Grab-Salamander der Aschezeit, Gegner. Der Boden selbst wird gefährlich.
 *
 * <p>Ablauf (Werte per {@code strata_core:mob_tuning}, Fähigkeiten {@code burrow}, {@code emerge}, {@code crest},
 * {@code claw}):
 * <ol>
 *   <li><b>Abtauchen</b>: Hat er ein Ziel in 4–16 Blöcken und steht auf lockerem Boden
 *       ({@code strata_epochs:burrowable}), gräbt er sich ein. Abgetaucht ragt nur der Glutkamm heraus; er zieht
 *       schneller auf das Ziel zu und nimmt 75 % weniger Schaden. Manchmal wühlt er einen Fossilbrocken frei.</li>
 *   <li><b>Hervorbrechen</b>: Nah am Ziel grollt der Boden 0,8 s (Risse leuchten), dann bricht er hervor,
 *       macht Schaden und schleudert das Ziel hoch.</li>
 *   <li><b>Glutkamm</b>: Danach ist er einige Sekunden aufgetaucht; Treffer von hinten machen mehr Schaden.</li>
 *   <li><b>Klauenhieb</b>: Aufgetaucht kämpft er im Nahkampf.</li>
 * </ol>
 * Auf festem Boden (Basalt, Zeitgestein, Bauten) kann er nicht abtauchen: Das ist der Gegenzug.
 */
public class EmberBurrower extends StrataMob {

    public static final StrataMobDef DEF = new StrataMobDef(StrataEpochs.MOD_ID, "ember_burrower",
            List.of("burrow", "burrowed", "tremor", "emerge", "attack"), "idle", "walk", List.of("crest", "veins"), 0.6f);

    /** Kamm glimmt an der Oberfläche schwach, abgetaucht deutlich, aufgetaucht nach dem Hervorbrechen voll. */
    public static final float BASE_GLOW = 0.45f;
    private static final float BURROWED_GLOW = 0.8f;
    /** Die Glutrisse glimmen immer schwach (Konzept), vor dem Hervorbrechen voll. */
    private static final float VEIN_GLOW = 0.25f;
    private static final int MAX_BURROWED = 200;
    private static final int STUCK_TICKS = 30;
    public static final String BURROWABLE = StrataEpochs.id("burrowable");
    private static final TagKey<Block> BURROWABLE_TAG = MobCompat.blockTag(BURROWABLE);
    private static final int DIG_TICKS = 20;
    private static final int EMERGE_TICKS = 16;

    public enum State { SURFACE, DIGGING, BURROWED, TREMOR, EMERGING, EXPOSED }

    private State state = State.SURFACE;
    private int stateTicks;
    private int cooldown;          // erster Kontakt: sofort abtauchen und als Flosse anrücken
    private int lostTarget;
    private float lockedYaw;

    public EmberBurrower(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setGlow(0, BASE_GLOW);
        setGlow(1, VEIN_GLOW);
    }

    /** Abgetaucht ist die Hitbox flach (nur der Kamm ragt heraus); die Größe folgt der laufenden Aktion. */
    @Override
    protected net.minecraft.world.entity.EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose pose) {
        String action = currentAction();
        if ("burrowed".equals(action)) return net.minecraft.world.entity.EntityDimensions.scalable(0.9f, 0.55f);
        if ("tremor".equals(action)) return net.minecraft.world.entity.EntityDimensions.scalable(0.9f, 0.75f);
        return super.getDefaultDimensions(pose);
    }

    private String sizedFor = "";

    /** Hitbox nur anpassen, wenn sich die Aktion ändert (nicht bei jedem Leucht-Wechsel). */
    @Override
    public void onSyncedDataUpdated(net.minecraft.network.syncher.EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        String action = String.valueOf(currentAction());
        if (!action.equals(sizedFor)) {
            sizedFor = action;
            refreshDimensions();
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return MobCompat.baseAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.23)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
    }

    @Override
    public StrataMobDef def() {
        return DEF;
    }

    public State state() {
        return state;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new ClawGoal());
        goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0f));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** Steht er mit der ganzen Grundfläche auf Boden, in den er sich eingraben kann (alle 4 Ecken)? */
    public boolean onBurrowableGround() {
        var box = getBoundingBox();
        int y = net.minecraft.util.Mth.floor(box.minY - 0.01);
        for (double x : new double[] {box.minX + 0.05, box.maxX - 0.05}) {
            for (double z : new double[] {box.minZ + 0.05, box.maxZ - 0.05}) {
                BlockPos pos = new BlockPos(net.minecraft.util.Mth.floor(x), y, net.minecraft.util.Mth.floor(z));
                if (!level().getBlockState(pos).is(BURROWABLE_TAG)) return false;
            }
        }
        return true;
    }

    /** Steht (oder springt) das Ziel über lockerem Boden? Sucht bis 3 Blöcke unter den Füßen den ersten festen Block. */
    private boolean burrowableAt(LivingEntity e) {
        BlockPos.MutableBlockPos pos = e.getOnPos().mutable();      // auch bei Böden unter voller Blockhöhe richtig
        for (int i = 0; i < 3; i++) {
            BlockState state = level().getBlockState(pos);
            if (!state.isAir()) return state.is(BURROWABLE_TAG);
            pos.move(0, -1, 0);
        }
        return false;
    }

    public static boolean canSpawnHere(ServerLevelAccessor level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return below.is(BURROWABLE_TAG) || below.isSolid();
    }

    // ---- Zustände ----

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            discard();
            return;
        }
        stateTicks++;
        LivingEntity target = getTarget();
        switch (state) {
            case SURFACE -> {
                if (cooldown > 0) cooldown--;
                if (cooldown == 0 && target != null && tuning().abilityEnabled("burrow") && onBurrowableGround()) {
                    double d = distanceTo(target);
                    if (d >= tuning().ability("burrow", "min_range", 4) && d <= tuning().ability("burrow", "max_range", 16)) {
                        enter(State.DIGGING);
                        getNavigation().stop();
                        playAction("burrow");
                        sound(SoundEvents.SAND_BREAK, 1.0f, 0.7f);
                    }
                }
            }
            case DIGGING -> {
                getNavigation().stop();
                groundParticles(level, 6);
                if (stateTicks >= DIG_TICKS) {
                    enter(State.BURROWED);
                    holdAction("burrowed");
                    setGlow(0, BURROWED_GLOW);
                    maybeDropFossil(level);
                }
            }
            case BURROWED -> tickBurrowed(level, target);
            case TREMOR -> {
                getNavigation().stop();
                setGlow(0, (stateTicks / 2) % 2 == 0 ? 1.0f : 0.4f);      // Kamm flackert
                if (stateTicks % 3 == 0) {
                    level.sendParticles(ParticleTypes.LAVA, getX(), getY() + 0.2, getZ(), 1, 0.4, 0.1, 0.4, 0);
                    groundParticles(level, 8);
                }
                if (stateTicks % 4 == 0) dangerRing(level);
                if (stateTicks >= warningTicks()) emerge(level, target);
            }
            case EMERGING -> {
                holdYaw();
                if (stateTicks >= EMERGE_TICKS) {
                    enter(State.EXPOSED);
                    setGlow(1, VEIN_GLOW);
                }
            }
            case EXPOSED -> {
                if (stateTicks < STUCK_TICKS) holdYaw();          // steckt noch fest: Rücken bleibt zum Spieler
                if (stateTicks % 6 == 0) {
                    level.sendParticles(ParticleTypes.SMALL_FLAME, getX(), getY() + 0.9, getZ(), 2, 0.15, 0.1, 0.25, 0.005);
                }
                if (stateTicks >= (int) tuning().ability("crest", "duration", 80)) {
                    enter(State.SURFACE);
                    setGlow(0, BASE_GLOW);
                    cooldown = (int) tuning().ability("burrow", "cooldown", 160);
                }
            }
        }
    }

    private void tickBurrowed(ServerLevel level, LivingEntity target) {
        if (stateTicks % 4 == 0) groundParticles(level, 3);
        if (stateTicks % 10 == 0) sound(SoundEvents.SAND_BREAK, 0.5f, 0.6f);
        // Fester Boden: Er wird herausgedrückt (ohne Angriff).
        if (!onBurrowableGround() || stateTicks > MAX_BURROWED) {
            surface(level);
            return;
        }
        if (target == null || !target.isAlive()) {
            if (++lostTarget > 60) surface(level);
            return;
        }
        lostTarget = 0;
        if (distanceTo(target) <= 2.5) {
            enter(State.TREMOR);
            playActionFor("tremor", warningTicks());
            setGlow(1, 1f);
            sound(SoundEvents.BASALT_BREAK, 1.5f, 0.5f);
            return;
        }
        if (stateTicks % 5 == 0) getNavigation().moveTo(target, tuning().ability("burrow", "speed", 1.6));
    }

    private int warningTicks() {
        return (int) tuning().ability("emerge", "warning", 16);
    }

    /** Partikelring auf dem echten Wirkradius: So groß ist die Gefahrenzone. */
    private void dangerRing(ServerLevel level) {
        BlockState ground = level.getBlockState(blockPosition().below());
        if (ground.isAir()) return;
        double range = tuning().ability("emerge", "range", 2.0);
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, ground);
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            level.sendParticles(dust, getX() + Math.cos(a) * range, getY() + 0.1, getZ() + Math.sin(a) * range,
                    1, 0.05, 0.02, 0.05, 0.02);
        }
    }

    /** Blickrichtung festhalten (nach dem Auftauchen steckt er kurz fest). */
    private void holdYaw() {
        getNavigation().stop();
        setYRot(lockedYaw);
        yBodyRot = lockedYaw;
        yHeadRot = lockedYaw;
    }

    private void emerge(ServerLevel level, LivingEntity target) {
        enter(State.EMERGING);
        playAction("emerge");
        setGlow(0, 1f);
        // Er bricht mit dem Rücken zum Ziel hervor: Der Glutkamm ist sofort erreichbar.
        // Blickrichtung (-sin, cos) vom Ziel weg: Vektor Ziel -> Mob.
        lockedYaw = target == null ? getYRot()
                : (float) (Math.atan2(-(getX() - target.getX()), getZ() - target.getZ()) * 180 / Math.PI);
        holdYaw();
        sound(SoundEvents.RAVAGER_ROAR, 0.6f, 1.4f);
        double range = tuning().ability("emerge", "range", 2.0);
        float damage = (float) tuning().ability("emerge", "damage", 6);
        double launch = tuning().ability("emerge", "launch", 0.7);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(range, 0.5, range),
                e -> e != this && !(e instanceof EmberBurrower))) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            double dx = e.getX() - getX(), dz = e.getZ() - getZ();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            if (horizontal > range) continue;                                   // Kreis statt Quadrat
            if (horizontal > 1.0 && !burrowableAt(e)) continue;                 // fester Boden schützt
            MobCompat.hurt(e, damageSources().mobAttack(this), damage);
            Vec3 v = e.getDeltaMovement();
            e.setDeltaMovement(v.x, Math.max(v.y, launch), v.z);
            syncMotion(e);
        }
        groundParticles(level, 30);
        level.sendParticles(ParticleTypes.LAVA, getX(), getY() + 0.4, getZ(), 6, 0.5, 0.2, 0.5, 0);
    }

    /** Aus dem Boden, ohne anzugreifen (fester Boden, Ziel verloren oder zu lange abgetaucht). */
    private void surface(ServerLevel level) {
        enter(State.EMERGING);
        playAction("emerge");
        lockedYaw = getYRot();
        setGlow(0, 1f);
        setGlow(1, VEIN_GLOW);
        groundParticles(level, 12);
    }

    private void enter(State next) {
        state = next;
        stateTicks = 0;
    }

    private void maybeDropFossil(ServerLevel level) {
        String epoch = EpochTravel.epochOf(level).orElse(null);
        if (epoch == null || random.nextDouble() >= tuning().ability("burrow", "fossil_chance", 0.08)) return;
        level.addFreshEntity(new ItemEntity(level, getX(), getY() + 0.3, getZ(), FossilPrep.chunk(epoch)));
    }

    private void groundParticles(ServerLevel level, int count) {
        BlockState ground = level.getBlockState(blockPosition().below());
        if (ground.isAir()) return;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), getX(), getY() + 0.1, getZ(),
                count, 0.35, 0.05, 0.35, 0.05);
    }

    /**
     * Neue Geschwindigkeit sofort an den Client schicken (sonst merkt ein Spieler den Wurf nicht, z. B. nach
     * einem Schildblock). Bis 1.21.11 heißt das Flag {@code hurtMarked}, ab 26.x {@code needsSync}.
     */
    private static final java.lang.reflect.Field SYNC_FLAG = findSyncFlag();

    private static java.lang.reflect.Field findSyncFlag() {
        for (String name : new String[] {"hurtMarked", "needsSync"}) {
            try {
                return net.minecraft.world.entity.Entity.class.getField(name);
            } catch (NoSuchFieldException ignored) {
                // nächster Name
            }
        }
        return null;
    }

    static void syncMotion(net.minecraft.world.entity.Entity entity) {
        if (SYNC_FLAG == null) return;
        try {
            SYNC_FLAG.setBoolean(entity, true);
        } catch (IllegalAccessException ignored) {
            // dann eben ohne sofortige Übertragung
        }
    }

    // ---- Schaden ----

    @Override
    protected void onIncomingDamage(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (source.is(DamageTypeTags.IS_FIRE)) {
            event.setCanceled(true);
            return;
        }
        if (state == State.DIGGING || state == State.BURROWED || state == State.TREMOR) {
            event.setAmount(event.getAmount() * (float) tuning().ability("burrow", "damage_factor", 0.25));
        } else if (state == State.EXPOSED && source.getEntity() != null && fromBehind(source.getEntity().position())) {
            event.setAmount(event.getAmount() * (float) tuning().ability("crest", "back_bonus", 1.5));
            if (level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.9, getZ(), 8, 0.2, 0.2, 0.3, 0.05);
            }
            sound(SoundEvents.FIRE_EXTINGUISH, 0.8f, 1.4f);
        }
        super.onIncomingDamage(event);
    }

    /** Liegt die Position hinter ihm (mehr als ~110° von der Blickrichtung)? */
    private boolean fromBehind(Vec3 from) {
        double yaw = Math.toRadians(yBodyRot);
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 to = new Vec3(from.x - getX(), 0, from.z - getZ());
        return to.lengthSqr() > 1.0e-4 && forward.dot(to.normalize()) < -0.35;
    }

    // ---- Sounds ----

    @Override
    protected SoundEvent getAmbientSound() {
        return state == State.SURFACE || state == State.EXPOSED ? EpochsMobs.EMBER_BURROWER_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return EpochsMobs.EMBER_BURROWER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return EpochsMobs.EMBER_BURROWER_DEATH.get();
    }

    private void sound(SoundEvent sound, float volume, float pitch) {
        level().playSound(null, getX(), getY(), getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    // ---- Klauenhieb ----

    /** Aufgetaucht: zum Ziel laufen und mit den Klauen zuschlagen (Treffer 0,3 s nach dem Ausholen). */
    private final class ClawGoal extends Goal {
        /** Treffer beim Abwärtsschlag (0,55 s), nach 0,45 s Ausholen. */
        private static final int IMPACT = 11;
        private int swing = -1;
        private int cooldownTicks;

        ClawGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            boolean free = state == State.SURFACE || (state == State.EXPOSED && stateTicks >= STUCK_TICKS);
            return free && getTarget() != null && getTarget().isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void stop() {
            swing = -1;
            getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) return;
            getLookControl().setLookAt(target, 30f, 30f);
            if (cooldownTicks > 0) cooldownTicks--;
            double distance = distanceTo(target);
            if (swing >= 0) {
                if (++swing == IMPACT && distance <= 2.6) {
                    MobCompat.hurt(target, damageSources().mobAttack(EmberBurrower.this),
                            (float) tuning().ability("claw", "damage", 4));
                    Vec3 push = target.position().subtract(position()).normalize().scale(0.4);
                    target.setDeltaMovement(target.getDeltaMovement().add(push.x, 0.15, push.z));
                    syncMotion(target);
                    sound(SoundEvents.RAVAGER_ATTACK, 0.7f, 1.6f);
                }
                if (swing > 18) swing = -1;
                return;
            }
            if (distance > 2.0) {
                if (getNavigation().isDone() || tickCount % 10 == 0) getNavigation().moveTo(target, 1.0);
            } else {
                getNavigation().stop();
                if (cooldownTicks == 0) {
                    swing = 0;
                    cooldownTicks = (int) tuning().ability("claw", "cooldown", 30);
                    playAction("attack");
                }
            }
        }
    }
}
