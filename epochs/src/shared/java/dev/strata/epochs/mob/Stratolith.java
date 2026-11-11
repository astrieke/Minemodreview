package dev.strata.epochs.mob;

import dev.strata.core.api.StrataAPI;
import dev.strata.core.mc.Mc;
import dev.strata.epochs.EpochsContent;
import dev.strata.epochs.EpochsMobs;
import dev.strata.epochs.StrataEpochs;
import dev.strata.epochs.mc.MobCompat;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Stratolith: Mini-Boss aus drei Gesteinsschichten. Beschworen mit einem Schichtkern auf Zeitgestein
 * (siehe {@code StratolithSummon}); er steigt aus dem Boden und ist dabei unverwundbar.
 *
 * <p>Phasen nach Leben, bei jedem Wechsel bricht die oberste Schicht ab und fällt als Beute herunter:
 * <ol start="0">
 *   <li><b>Kristallschicht</b> (100–67 %): Kristallspitzen ({@code shards}) brechen nach Vorwarnung unter den
 *       Spielern aus dem Boden.</li>
 *   <li><b>Sporenschicht</b> (67–33 %): Sporenstoß ({@code spores}) mit Langsamkeit, dazu zwei Sporenbälge.</li>
 *   <li><b>Grundgestein</b> (33–0 %): Zurückspulen ({@code rewind}): nach 2 s Vorwarnung holt er sich das Leben der
 *       letzten 8 s zurück. Ein Spitzhackentreffer von vorn auf den Kern während der Vorwarnung bricht es ab
 *       und betäubt ihn.</li>
 * </ol>
 * Immer: Hammerschlag ({@code slam}) im Nahkampf. Boss-Leiste für Spieler in der Nähe.
 * Belohnung (Beutetabelle): Herz der Tiefenzeit; alle Spieler in 32 Blöcken bekommen den Kodex-Knoten
 * {@code strata_epochs:stratolith}.
 */
public class Stratolith extends StrataMob {

    public static final StrataMobDef DEF = new StrataMobDef(StrataEpochs.MOD_ID, "stratolith",
            List.of("slam", "shards", "spores", "rewind", "shed_crystal", "shed_spore", "rise"), "idle", "walk",
            List.of("core", "crystals"), 1.4f);

    static final int GLOW_CORE = 0;
    static final int GLOW_CRYSTALS = 1;
    static final int GOLD = 0xffe08a;
    static final int QUARTZ = 0x9b87ff;
    static final int SPORE = 0x7fb069;
    public static final int RISE_TICKS = 60;
    /** So lange fällt eine Schicht ab (Animation shed_*), erst danach ist sie weg. */
    static final int SHED_TICKS = 20;
    /** Höhe je Phase in Blöcken: mit Krone, ohne Krone, nur Grundgestein. Breite inkl. Arme. */
    static final float[] HEIGHTS = {3.1f, 2.3f, 1.5f};
    static final float WIDTH = 2.3f;
    /** Leben je Sekunde der letzten {@link #HISTORY} Sekunden, für das Zurückspulen. */
    static final int HISTORY = 10;

    private final Shards shards = new Shards(this);
    private final Spores spores = new Spores(this);
    private final Rewind rewind = new Rewind(this);
    private final ServerBossEvent bossBar = dev.strata.epochs.mc.BossBars.create(Component.translatable("entity.strata_epochs.stratolith"));
    private final float[] healthHistory = new float[HISTORY];
    private int rising;
    private int stunned;
    /** Läuft gerade ein Schichtabwurf: Ticks bis zum Ende, sonst 0. */
    private int shedding;

    public Stratolith(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        abilities.add(shards);
        abilities.add(spores);
        abilities.add(rewind);
        setGlow(GLOW_CORE, 0.6f);
        setGlow(GLOW_CRYSTALS, 0.8f);
        setPersistenceRequired();
        java.util.Arrays.fill(healthHistory, -1);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return MobCompat.baseAttributes()
                .add(Attributes.MAX_HEALTH, 240.0)
                .add(Attributes.MOVEMENT_SPEED, 0.18)
                .add(Attributes.ATTACK_DAMAGE, 12.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    public StrataMobDef def() {
        return DEF;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new HoldStillGoal());
        goalSelector.addGoal(2, new SlamGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    /** Steigt aus dem Boden (direkt nach der Beschwörung aufgerufen). */
    public void startRise() {
        rising = RISE_TICKS;
        playAction("rise");
        setGlow(GLOW_CORE, 1.0f);
        sound(SoundEvents.WARDEN_EMERGE, 1.5f, 0.8f);
    }

    public boolean isRising() {
        return rising > 0;
    }

    /** Für den Selbsttest: Zurückspulen auslösen und seinen Zustand lesen. */
    public boolean triggerRewind() {
        return rewind.trigger();
    }

    public StrataAbility.Phase rewindPhase() {
        return rewind.phase();
    }

    public boolean isStunned() {
        return stunned > 0;
    }

    /** 0 Kristallschicht, 1 Sporenschicht, 2 Grundgestein. */
    public int layer() {
        return stage();
    }

    // ---- Ablauf ----

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            discard();
            return;
        }
        bossBar.setProgress(getHealth() / getMaxHealth());
        int saved = getData(EpochsContent.STRATOLITH_LAYER.get());   // nach dem Laden: Phase wiederherstellen
        if (saved != stage() && shedding == 0) {
            setStage(saved);
            refreshDimensions();
            if (saved > 0) setGlow(GLOW_CRYSTALS, 0f);
        }
        if (rising > 0) {
            if (rising % 4 == 0) {
                var ground = level.getBlockState(blockPosition().below());
                if (!ground.isAir()) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), getX(), getY() + 0.2, getZ(),
                            20, 1.0, 0.2, 1.0, 0.15);
                }
            }
            if (--rising == 0) setGlow(GLOW_CORE, 0.6f);
            return;
        }
        if (tickCount % 20 == 0) {
            System.arraycopy(healthHistory, 0, healthHistory, 1, HISTORY - 1);
            healthHistory[0] = getHealth();
        }
        if (stunned > 0) stunned--;
        checkLayer();
        if (rewind.phase() != StrataAbility.Phase.WARNING) {
            setGlow(GLOW_CORE, 0.5f + 0.2f * (float) Math.sin(tickCount * 0.1));
        }
        LivingEntity target = getTarget();
        if (!(target instanceof Player) || stunned > 0) return;
        switch (layer()) {
            case 0 -> { if (distanceTo(target) <= shards.value("range", 16.0) && hasLineOfSight(target)) shards.trigger(); }
            case 1 -> { if (distanceTo(target) <= spores.value("range", 10.0)) spores.trigger(); }
            default -> { if (lostRecently() >= rewind.value("min_loss", 12.0)) rewind.trigger(); }
        }
    }

    /** Wie viel Leben er in den letzten 8 s verloren hat (Grundlage fürs Zurückspulen). */
    float lostRecently() {
        float then = healthHistory[Math.min(HISTORY - 1, 8)];
        return then < 0 ? 0 : Math.max(0, then - getHealth());
    }

    /** Leben vor 8 s (oder das jetzige, wenn er noch nicht so lange da ist). */
    float healthBefore() {
        float then = healthHistory[Math.min(HISTORY - 1, 8)];
        return then < 0 ? getHealth() : then;
    }

    /** Wechselt die Phase, wenn das Leben unter 2/3 bzw. 1/3 fällt: die oberste Schicht fällt ab (1 s). */
    void checkLayer() {
        if (shedding > 0) {
            if (--shedding == 0) finishShed();
            return;
        }
        int want = getHealth() <= getMaxHealth() / 3 ? 2 : getHealth() <= getMaxHealth() * 2 / 3 ? 1 : 0;
        if (layer() < want) startShed();
    }

    private void startShed() {
        shedding = SHED_TICKS;
        getNavigation().stop();
        if (layer() == 0) {
            shards.interrupt();
            playAction("shed_crystal");
            sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0f, 0.4f);
        } else {
            spores.interrupt();
            playAction("shed_spore");
            sound(SoundEvents.MOSS_BREAK, 2.0f, 0.5f);
        }
    }

    /** Ende des Abwurfs: Schicht weg (Phase +1), Beute fällt, Trefferfläche schrumpft. */
    void finishShed() {
        ServerLevel level = server();
        int layer = layer();
        setStage(layer + 1);
        setData(EpochsContent.STRATOLITH_LAYER.get(), layer + 1);   // übersteht Entladen/Neustart
        refreshDimensions();
        List<ItemStack> loot = new ArrayList<>();
        if (layer == 0) {
            setGlow(GLOW_CRYSTALS, 0f);
            loot.add(new ItemStack(EpochsContent.CRYSTAL_QUARTZ.get(), 4 + random.nextInt(3)));
            loot.add(new ItemStack(EpochsContent.QUARTZ_THISTLE_SEEDS.get()));
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CALCITE.defaultBlockState()),
                    getX(), getY() + 2.4, getZ(), 50, 0.6, 0.3, 0.6, 0.2);
            sound(SoundEvents.AMETHYST_CLUSTER_BREAK, 2.0f, 0.6f);
        } else {
            loot.add(new ItemStack(EpochsContent.AMBER_SHARD.get(), 4 + random.nextInt(3)));
            loot.add(new ItemStack(EpochsContent.SPORE_SAC.get()));
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MYCELIUM.defaultBlockState()),
                    getX(), getY() + 1.8, getZ(), 50, 0.7, 0.3, 0.7, 0.2);
        }
        sound(SoundEvents.DEEPSLATE_BREAK, 2.0f, 0.5f);
        for (ItemStack stack : loot) {
            ItemEntity item = new ItemEntity(level, getX(), getY() + HEIGHTS[layer] - 0.5, getZ(), stack);
            item.setDeltaMovement((random.nextDouble() - 0.5) * 0.4, 0.4, (random.nextDouble() - 0.5) * 0.4);
            level.addFreshEntity(item);
        }
        for (ServerPlayer p : bossBar.getPlayers()) {
            Mc.actionbar(p, Component.translatable("message.strata_epochs.stratolith.shed." + (layer + 1)).withStyle(net.minecraft.ChatFormatting.GOLD));
        }
    }

    @Override
    protected net.minecraft.world.entity.EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose pose) {
        return net.minecraft.world.entity.EntityDimensions.scalable(WIDTH, HEIGHTS[Math.max(0, Math.min(2, stage()))]);
    }

    @Override
    public void onSyncedDataUpdated(net.minecraft.network.syncher.EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        refreshDimensions();   // Phase kommt beim Client an: Trefferfläche dort ebenfalls anpassen
    }

    boolean busy() {
        return rising > 0 || stunned > 0 || shards.phase() == StrataAbility.Phase.WARNING
                || spores.phase() == StrataAbility.Phase.WARNING || rewind.phase() == StrataAbility.Phase.WARNING
                || shedding > 0;
    }

    // ---- Boss-Leiste, Belohnung ----

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    public ServerBossEvent bossBar() {
        return bossBar;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            if (source.getEntity() instanceof ServerPlayer killer) StrataAPI.unlock(killer, StrataEpochs.id("stratolith"));
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(32))) {
                StrataAPI.unlock(p, StrataEpochs.id("stratolith"));
            }
            level.sendParticles(MobCompat.dust(GOLD, 2.0f), getX(), getY() + 1.5, getZ(), 60, 1.0, 1.2, 1.0, 0.0);
        }
    }

    // ---- Sounds ----

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.RAVAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.55f + random.nextFloat() * 0.1f;
    }

    private ServerLevel server() {
        return (ServerLevel) level();
    }

    void sound(SoundEvent sound, float volume, float pitch) {
        level().playSound(null, getX(), getY(), getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    // ---- Schaden ----

    @Override
    protected void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (rising > 0) {
            event.setAmount(0);
            return;
        }
        super.onIncomingDamage(event);
    }

    // ---- Kristallspitzen ----

    static final class Shards extends StrataAbility<Stratolith> {
        private final List<Vec3> marks = new ArrayList<>();

        Shards(Stratolith mob) {
            super(mob, "shards", 24, 1, 120);
        }

        @Override
        protected void onWarn() {
            mob.getNavigation().stop();
            mob.playAction("shards");
            mob.setGlow(GLOW_CRYSTALS, 1.0f);
            marks.clear();
            double range = value("range", 16.0);
            for (ServerPlayer p : mob.server().getEntitiesOfClass(ServerPlayer.class, mob.getBoundingBox().inflate(range))) {
                if (p.isCreative() || p.isSpectator()) continue;
                marks.add(new Vec3(p.getX(), Math.floor(p.getY()), p.getZ()));
            }
            mob.sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0f, 0.6f);
        }

        @Override
        protected void onWarnTick() {
            for (Vec3 m : marks) {   // Warnkreis am Boden: dort brechen gleich die Spitzen hervor
                for (int i = 0; i < 6; i++) {
                    double a = mob.tickCount * 0.3 + i * Math.PI / 3;
                    mob.server().sendParticles(MobCompat.dust(QUARTZ, 1.0f), m.x + Math.cos(a) * 1.2, m.y + 0.1,
                            m.z + Math.sin(a) * 1.2, 1, 0, 0, 0, 0);
                }
            }
        }

        @Override
        protected void onStart() {
            ServerLevel level = mob.server();
            float damage = (float) value("damage", 8.0);
            double radius = value("radius", 1.4);
            for (Vec3 m : marks) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_CLUSTER.defaultBlockState()),
                        m.x, m.y + 0.5, m.z, 30, 0.5, 0.6, 0.5, 0.2);
                level.sendParticles(MobCompat.dust(QUARTZ, 1.6f), m.x, m.y + 1.0, m.z, 20, 0.4, 0.8, 0.4, 0);
                level.playSound(null, m.x, m.y, m.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.5f, 0.7f);
                for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(m, m).inflate(radius, 2, radius))) {
                    if (p.isCreative() || p.isSpectator()) continue;
                    MobCompat.hurt(p, mob.damageSources().mobAttack(mob), damage);
                    p.push(0, value("lift", 0.6), 0);
                }
            }
            marks.clear();
        }

        @Override
        protected void onEnd() {
            mob.setGlow(GLOW_CRYSTALS, mob.layer() == 0 ? 0.8f : 0f);
        }
    }

    // ---- Sporenstoß ----

    static final class Spores extends StrataAbility<Stratolith> {
        Spores(Stratolith mob) {
            super(mob, "spores", 20, 1, 200);
        }

        @Override
        protected void onWarn() {
            mob.getNavigation().stop();
            mob.playAction("spores");
            mob.sound(SoundEvents.PUFFER_FISH_BLOW_UP, 2.0f, 0.5f);
        }

        @Override
        protected void onStart() {
            ServerLevel level = mob.server();
            double radius = value("radius", 6.0);
            SporeClouds.spawn(level, mob.position(), radius, (int) value("cloud_ticks", 100), value("factor", 2.5));
            level.sendParticles(MobCompat.dust(SPORE, 2.0f), mob.getX(), mob.getY() + 2.0, mob.getZ(), 80, radius / 2, 0.8, radius / 2, 0);
            level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, mob.getX(), mob.getY() + 1.5, mob.getZ(), 60, radius / 2, 1.0, radius / 2, 0);
            mob.sound(SoundEvents.FUNGUS_BREAK, 2.0f, 0.5f);
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, mob.getBoundingBox().inflate(radius))) {
                if (p.isCreative() || p.isSpectator()) continue;
                p.addEffect(new MobEffectInstance(Mc.slowness(), (int) value("slow_duration", 80), 1));
            }
            // Zwei Sporenbälge, solange nicht schon genug da sind.
            int cap = (int) value("bellows_cap", 4);
            int present = level.getEntitiesOfClass(SporeBellows.class, mob.getBoundingBox().inflate(16)).size();
            for (int i = 0; i < 2 && present + i < cap; i++) {
                SporeBellows bellows = new SporeBellows(EpochsMobs.SPORE_BELLOWS.get(), level);
                double a = mob.random.nextDouble() * Math.PI * 2;
                bellows.setPos(mob.getX() + Math.cos(a) * 2.5, mob.getY() + 0.5, mob.getZ() + Math.sin(a) * 2.5);
                level.addFreshEntity(bellows);
            }
        }
    }

    // ---- Zurückspulen ----

    static final class Rewind extends StrataAbility<Stratolith> {
        Rewind(Stratolith mob) {
            super(mob, "rewind", 40, 1, 300);
        }

        @Override
        protected void onWarn() {
            mob.getNavigation().stop();
            mob.playAction("rewind");
            mob.setGlow(GLOW_CORE, 1.0f);
            mob.sound(SoundEvents.BELL_RESONATE, 2.0f, 0.5f);
            for (ServerPlayer p : mob.bossBar.getPlayers()) {
                Mc.actionbar(p, Component.translatable("message.strata_epochs.stratolith.rewind").withStyle(net.minecraft.ChatFormatting.GOLD));
            }
        }

        @Override
        protected void onWarnTick() {
            double a = mob.tickCount * 0.4;
            Vec3 core = mob.corePosition();
            mob.server().sendParticles(MobCompat.dust(GOLD, 1.2f), core.x + Math.cos(a) * 0.6, core.y + Math.sin(a) * 0.6,
                    core.z, 1, 0, 0, 0, 0);
        }

        @Override
        protected void onStart() {
            float target = mob.healthBefore();
            if (target > mob.getHealth()) {
                mob.setHealth(Math.min(mob.getMaxHealth() / 3, target));   // nie zurück in eine abgebrochene Schicht
                mob.server().sendParticles(MobCompat.dust(GOLD, 1.8f), mob.getX(), mob.getY() + 1.2, mob.getZ(), 40, 0.8, 0.8, 0.8, 0);
                mob.sound(SoundEvents.RESPAWN_ANCHOR_CHARGE, 2.0f, 0.6f);
            }
        }

        @Override
        protected void onEnd() {
            mob.setGlow(GLOW_CORE, 0.6f);
            if ("rewind".equals(mob.currentAction())) mob.stopAction();
        }

        @Override
        protected void onIncomingDamage(LivingIncomingDamageEvent event) {
            if (phase() != Phase.WARNING) return;
            Entity attacker = event.getSource().getEntity();
            if (attacker instanceof LivingEntity living && event.getSource().getDirectEntity() == attacker
                    && living.getMainHandItem().is(ItemTags.PICKAXES) && mob.inFront(attacker)) {
                // Der Kniff: Spitzhacke auf den Kern stoppt das Zurückspulen und betäubt ihn.
                interrupt();
                mob.stunned = (int) value("stun", 40);
                mob.getNavigation().stop();
                mob.server().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                        EpochsContent.PETRIFIED_STONE.get().defaultBlockState()), mob.getX(), mob.getY() + 1.2, mob.getZ(),
                        40, 0.5, 0.6, 0.5, 0.15);
                mob.sound(SoundEvents.STONE_BREAK, 2.0f, 0.5f);
                if (living instanceof ServerPlayer p) {
                    Mc.actionbar(p, Component.translatable("message.strata_epochs.stratolith.broken").withStyle(net.minecraft.ChatFormatting.GREEN));
                }
            }
        }
    }

    Vec3 corePosition() {
        return position().add(getLookAngle().multiply(0.75, 0, 0.75)).add(0, 1.2, 0);
    }

    /** Kern-Treffer zählt nur von vorn: Angreifer höchstens 45° neben der Blickrichtung (Kern sitzt vorn im Sockel). */
    boolean inFront(Entity attacker) {
        Vec3 look = new Vec3(getLookAngle().x, 0, getLookAngle().z).normalize();
        Vec3 to = new Vec3(attacker.getX() - getX(), 0, attacker.getZ() - getZ()).normalize();
        return look.dot(to) >= Math.cos(Math.toRadians(45));
    }

    /** Steht still beim Aufsteigen, Betäubtsein und während Vorwarnungen. */
    private final class HoldStillGoal extends Goal {
        HoldStillGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return busy();
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

    private final class SlamGoal extends Goal {
        /** Bodenkontakt bei 1,0 s: 0,6 s Ausholen und 0,2 s Halten über dem Kopf als Vorwarnung. */
        private static final int IMPACT_TICK = 20;
        private int swing = -1;
        private int cooldown;

        SlamGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private double v(String key, double fallback) {
            return tuning().ability("slam", key, fallback);
        }

        @Override
        public boolean canUse() {
            return getTarget() != null && getTarget().isAlive() && !busy();
        }

        @Override
        public boolean canContinueToUse() {
            return (swing >= 0 || canUse()) && rising == 0 && stunned == 0;
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
                if (swing >= 28) swing = -1;
                return;
            }
            if (target == null) return;
            getLookControl().setLookAt(target, 30f, 30f);
            double reach = v("reach", 3.5);
            if (distanceTo(target) > reach - 0.3) {
                if (tickCount % 10 == 0 || getNavigation().isDone()) getNavigation().moveTo(target, 1.0);
            } else {
                getNavigation().stop();
                if (cooldown == 0 && tuning().abilityEnabled("slam")) {
                    swing = 0;
                    cooldown = (int) v("cooldown", 60);
                    playAction("slam");
                    sound(SoundEvents.RAVAGER_ROAR, 1.2f, 0.6f);
                }
            }
        }

        private void impact(LivingEntity target) {
            ServerLevel level = server();
            Vec3 front = position().add(getLookAngle().multiply(2.0, 0, 2.0));
            var ground = level.getBlockState(BlockPos.containing(front.x, getY() - 0.5, front.z));
            if (!ground.isAir()) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), front.x, getY() + 0.1, front.z,
                        40, 1.0, 0.1, 1.0, 0.15);
            }
            level.sendParticles(ParticleTypes.EXPLOSION, front.x, getY() + 0.3, front.z, 1, 0, 0, 0, 0);
            level.playSound(null, front.x, getY(), front.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.6f, 0.5f);
            if (target != null && distanceTo(target) <= v("reach", 3.5) + 0.6) {
                MobCompat.hurt(target, damageSources().mobAttack(Stratolith.this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
                target.push(0, v("lift", 0.5), 0);
            }
        }
    }
}
