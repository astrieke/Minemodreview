package dev.strata.epochs.mob;

import dev.strata.core.mc.Mc;
import dev.strata.epochs.EpochsContent;
import dev.strata.epochs.EpochsMobs;
import dev.strata.epochs.StrataEpochs;
import dev.strata.epochs.mc.MobCompat;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Bernsteinmotte: kleine, friedliche Motte der Sporenzeit. Erneuerbare Fossil-Quelle über die Harzfalle.
 *
 * <ul>
 *   <li><b>Lichttanz</b> ({@code light}): Alle 2 s prüft sie einige Stellen in der Nähe und fliegt zur hellsten
 *       (Blocklicht ≥ 8), die sie dann umkreist. Ohne Licht flattert sie ziellos.</li>
 *   <li><b>Schuppenstaub</b> ({@code dust}): Ein sprintender Spieler in der Nähe oder ein Treffer erschreckt sie.
 *       Nach 0,5 s Vorwarnung stäubt sie: Spieler im Umkreis sind kurz geblendet, sie flieht. Schleichen erschreckt
 *       sie nie.</li>
 *   <li><b>Harzfalle</b> ({@code trap}): Liegt eine Harzfalle mit einer Lichtquelle direkt daneben in der Nähe,
 *       fliegt sie hin, landet und bleibt kleben: Aus der Falle wird eine Falle mit Motte, die mit der Zeit zu
 *       Bernstein mit Einschluss aushärtet ({@link dev.strata.epochs.ResinTrapBlock}).</li>
 * </ul>
 */
public class AmberMoth extends StrataMob {

    public static final StrataMobDef DEF = new StrataMobDef(StrataEpochs.MOD_ID, "amber_moth",
            List.of("dust", "land"), "idle", "fly", List.of("spots"), 0.2f);

    public static final double LIGHT_RADIUS = 10.0;
    public static final int MIN_LIGHT = 8;
    public static final int SEEK_TICKS = 40;
    public static final double SCARE_RADIUS = 3.0;
    public static final double DUST_RADIUS = 2.5;
    public static final int WARNING_TICKS = 10;
    public static final int BLIND_TICKS = 40;
    /** Die Schuppen haften: Wer bestäubt wurde, leuchtet so lange (für Mitspieler sichtbar). */
    public static final int MARK_TICKS = 80;
    /** Leuchten der Augenkerne und Vorderränder: in Ruhe, in der Vorwarnung flackernd voll. */
    private static final float GLOW_REST = 0.5f;
    public static final int DUST_COOLDOWN = 200;
    public static final double TRAP_RADIUS = 10.0;
    /** Mindest-Lichtstärke einer Lichtquelle direkt neben der Harzfalle (Fackel 14, Leuchtstein 15). */
    public static final int TRAP_LIGHT = 10;
    public static final int LAND_TICKS = 16;
    private static final int FLEE_TICKS = 50;

    private Vec3 target;
    private BlockPos light;
    private int lightLevel;
    private BlockPos trap;
    private int landTicks = -1;
    private int warnTicks = -1;
    private int dustCooldown;
    private int fleeTicks;
    private Vec3 fleeFrom;
    private double orbit;

    public AmberMoth(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setGlow(0, GLOW_REST);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return MobCompat.baseAttributes()
                .add(Attributes.MAX_HEALTH, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 12.0);
    }

    @Override
    public StrataMobDef def() {
        return DEF;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    private double value(String ability, String key, double fallback) {
        return tuning().ability(ability, key, fallback);
    }

    // ---- Zustand (auch für den Selbsttest) ----

    public boolean warning() {
        return warnTicks >= 0;
    }

    public boolean fleeing() {
        return fleeTicks > 0;
    }

    public boolean landing() {
        return landTicks >= 0;
    }

    public BlockPos light() {
        return light;
    }

    // ---- Server-Tick ----

    @Override
    public void tick() {
        if (level() instanceof ServerLevel server) serverTick(server);
        super.tick();
    }

    private void serverTick(ServerLevel level) {
        if (dustCooldown > 0) dustCooldown--;
        if (landTicks >= 0) {
            landTick(level);
            return;
        }
        if (warnTicks >= 0) {
            setDeltaMovement(getDeltaMovement().scale(0.5));
            setGlow(0, warnTicks % 4 < 2 ? 1.0f : 0.4f);         // flackert: im Dunkeln sichtbar
            if (warnTicks % 2 == 0) {
                level.sendParticles(MobCompat.dust(0xffcf6a, 0.7f), getX(), getY() + 0.35, getZ(), 3, 0.35, 0.1, 0.35, 0);
            }
            if (warnTicks-- == 0) dust(level);
            return;
        }
        if (tickCount % 5 == 0 && fleeTicks == 0) {
            for (ServerPlayer p : players(level, value("dust", "trigger_radius", SCARE_RADIUS))) {
                if (scares(p)) {
                    scare(p.position());
                    return;
                }
            }
        }
        if (fleeTicks > 0) fleeTicks--;
        if (fleeTicks == 0 && (trap != null || tickCount % 20 == 0) && tuning().abilityEnabled("trap") && findTrap(level)) return;
        if (tickCount % SEEK_TICKS == 0 || target == null || position().distanceTo(target) < 0.3) chooseTarget(level);
        move(level);
    }

    /** Erschreckt ihn dieser Spieler? Sprinten ja, Schleichen nie. */
    public static boolean scares(Player player) {
        return player.isSprinting() && !player.isShiftKeyDown() && !player.isSpectator() && !player.isCreative();
    }

    // ---- Lichttanz und Flug ----

    private void chooseTarget(ServerLevel level) {
        if (fleeTicks > 0 && fleeFrom != null) {
            Vec3 away = position().subtract(fleeFrom);
            away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0.3, 0) : away.normalize();
            for (int turn : new int[]{0, 40, -40, 80, -80}) {
                double r = Math.toRadians(turn);
                Vec3 dir = new Vec3(away.x * Math.cos(r) - away.z * Math.sin(r), 0.3, away.x * Math.sin(r) + away.z * Math.cos(r));
                Vec3 p = position().add(dir.scale(5));
                if (free(level, p)) {
                    target = p;
                    return;
                }
            }
            target = position();
            return;
        }
        if (tuning().abilityEnabled("light")) seekLight(level);
        if (light != null) {
            orbit += 0.9;
            Vec3 c = Vec3.atCenterOf(light);
            for (int i = 0; i < 4; i++, orbit += 1.2) {
                Vec3 p = c.add(Math.cos(orbit) * 1.6, 0.6 + 0.4 * Math.sin(orbit * 0.7), Math.sin(orbit) * 1.6);
                if (free(level, p)) {
                    target = p;
                    return;
                }
            }
        }
        for (int i = 0; i < 6; i++) {                          // ziellos flattern
            Vec3 p = position().add((random.nextDouble() - 0.5) * 10, (random.nextDouble() - 0.5) * 4, (random.nextDouble() - 0.5) * 10);
            if (free(level, p)) {
                target = p;
                return;
            }
        }
        target = position();
    }

    /** Prüft einige Stellen in der Nähe und merkt sich die hellste (Blocklicht ≥ {@link #MIN_LIGHT}). */
    private void seekLight(ServerLevel level) {
        double radius = value("light", "radius", LIGHT_RADIUS);
        int min = (int) value("light", "min_light", MIN_LIGHT);
        if (light != null) {
            lightLevel = level.getBrightness(LightLayer.BLOCK, light);
            if (lightLevel < min || light.distToCenterSqr(position()) > radius * radius * 2.25) {
                light = null;
                lightLevel = 0;
            }
        }
        for (int i = 0; i < 12; i++) {
            BlockPos p = BlockPos.containing(getX() + (random.nextDouble() * 2 - 1) * radius,
                    getY() + (random.nextDouble() * 2 - 1) * radius * 0.5, getZ() + (random.nextDouble() * 2 - 1) * radius);
            int l = level.getBrightness(LightLayer.BLOCK, p);
            if (l >= min && l > lightLevel + 1 && level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                light = p;
                lightLevel = l;
            }
        }
    }

    private void move(ServerLevel level) {
        if (target == null) return;
        Vec3 delta = target.subtract(position());
        double length = delta.length();
        double speed = fleeTicks > 0 ? 0.22 : light != null ? 0.12 : 0.08;
        Vec3 wanted = length < 0.05 ? Vec3.ZERO : delta.scale(Math.min(speed, length * 0.3) / length);
        // leichtes Flattern auf und ab
        wanted = wanted.add(0, Math.sin(tickCount * 0.6) * 0.02, 0);
        setDeltaMovement(getDeltaMovement().scale(0.6).add(wanted.scale(0.4)));
        if (delta.horizontalDistanceSqr() > 1.0e-3) {
            float yaw = (float) (Math.atan2(-delta.x, delta.z) * 180 / Math.PI);
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
        }
        if (tickCount % 8 == 0 && light != null) {
            level.sendParticles(MobCompat.dust(0xeda23a, 0.5f), getX(), getY() + 0.3, getZ(), 1, 0.1, 0.05, 0.1, 0);
        }
    }

    /** Freier Platz für die Trefferfläche an {@code p} und freie Sicht von hier dorthin. */
    private boolean free(ServerLevel level, Vec3 p) {
        if (!level.noCollision(this, getBoundingBox().move(p.subtract(position())))) return false;
        return level.clip(new net.minecraft.world.level.ClipContext(position().add(0, 0.25, 0), p.add(0, 0.25, 0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this))
                .getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    // ---- Schuppenstaub ----

    /** Erschrecken: Vorwarnung, dann Staub und Flucht (nur außerhalb der Abklingzeit). */
    public void scare(Vec3 from) {
        if (warnTicks >= 0 || landTicks >= 0 || dustCooldown > 0 || !tuning().abilityEnabled("dust")) {
            fleeFrom = from;
            if (warnTicks < 0 && landTicks < 0) fleeTicks = FLEE_TICKS;
            return;
        }
        fleeFrom = from;
        warnTicks = Math.max(10, (int) value("dust", "warning", WARNING_TICKS));
        playAction("dust");
        setGlow(0, 1.0f);
        sound(EpochsMobs.AMBER_MOTH_DUST.get(), 1.4f, 1.0f);   // deutlich hörbar: das Schwirren ist die Warnung
    }

    private void dust(ServerLevel level) {
        double radius = value("dust", "radius", DUST_RADIUS);
        int blind = (int) value("dust", "blind", BLIND_TICKS);
        level.sendParticles(MobCompat.dust(0xeda23a, 1.2f), getX(), getY() + 0.3, getZ(), 30, radius * 0.4, 0.4, radius * 0.4, 0);
        level.sendParticles(ParticleTypes.WHITE_ASH, getX(), getY() + 0.3, getZ(), 20, radius * 0.5, 0.5, radius * 0.5, 0.02);
        for (ServerPlayer p : players(level, radius)) {
            if (p.isCreative()) continue;
            p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, blind, 0));
            p.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) value("dust", "mark", MARK_TICKS), 0));
        }
        dustCooldown = (int) value("dust", "cooldown", DUST_COOLDOWN);
        fleeTicks = FLEE_TICKS;
        target = null;
        setGlow(0, GLOW_REST);
    }

    @Override
    protected void onIncomingDamage(LivingIncomingDamageEvent event) {
        super.onIncomingDamage(event);
        if (event.getSource().getEntity() instanceof Player player) scare(player.position());
    }

    // ---- Harzfalle ----

    /** Eine Harzfalle mit Lichtquelle daneben in Reichweite? Dann hinfliegen und landen. */
    private boolean findTrap(ServerLevel level) {
        int r = (int) Math.ceil(Math.min(16, value("trap", "radius", TRAP_RADIUS)));
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos center = blockPosition();
        // einmal pro Sekunde (solange keine Falle bekannt ist): höchstens (2r+1)² × 9 Blöcke
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -4, -r), center.offset(r, 4, r))) {
            if (!level.getBlockState(p).is(EpochsContent.RESIN_TRAP.get()) || !lit(level, p)) continue;
            double d = p.distToCenterSqr(position());
            if (d < bestDistance && free(level, Vec3.atCenterOf(p.above()).add(0, 0.1, 0))) {
                best = p.immutable();
                bestDistance = d;
            }
        }
        if (best == null) {
            trap = null;
            return false;
        }
        trap = best;
        target = Vec3.atCenterOf(best.above()).add(0, 0.05, 0);
        if (position().distanceTo(target) < 0.6) {
            landTicks = 0;
            playAction("land");
            setDeltaMovement(Vec3.ZERO);
        } else {
            move(level);
        }
        return true;
    }

    /** Hat die Harzfalle eine Lichtquelle direkt daneben (Lichtstärke ≥ {@link #TRAP_LIGHT})? */
    public static boolean lit(ServerLevel level, BlockPos trap) {
        for (Direction d : Direction.values()) {
            BlockPos n = trap.relative(d);
            if (level.getBlockState(n).getLightEmission() >= TRAP_LIGHT) return true;
        }
        return false;
    }

    private void landTick(ServerLevel level) {
        setDeltaMovement(Vec3.ZERO);
        if (trap == null || !level.getBlockState(trap).is(EpochsContent.RESIN_TRAP.get())) {
            landTicks = -1;
            stopAction();
            return;
        }
        if (++landTicks >= LAND_TICKS) {
            level.setBlockAndUpdate(trap, EpochsContent.RESIN_TRAP_MOTH.get().defaultBlockState());
            level.sendParticles(MobCompat.dust(0xd07a1a, 1.0f), trap.getX() + 0.5, trap.getY() + 1.05, trap.getZ() + 0.5,
                    12, 0.3, 0.05, 0.3, 0);
            level.playSound(null, trap, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.BLOCKS, 1.0f, 1.2f);
            discard();
        }
    }

    /** Landet sofort auf dieser Falle (für den Selbsttest). */
    public void landOn(BlockPos trapPos) {
        trap = trapPos;
        landTicks = 0;
        playAction("land");
    }

    // ---- Testspieler (simulierte Spieler stehen nicht in der Welt) ----

    public static final List<ServerPlayer> TEST_PLAYERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    private List<ServerPlayer> players(ServerLevel level, double range) {
        List<ServerPlayer> list = new java.util.ArrayList<>(level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(range)));
        for (ServerPlayer p : TEST_PLAYERS) if (p.level() == level && !list.contains(p)) list.add(p);
        list.removeIf(p -> p.isSpectator() || p.position().distanceTo(position()) > range + 0.5);
        return list;
    }

    // ---- Ernte der ausgehärteten Falle ----

    /** Rechtsklick auf eine ausgehärtete Harzfalle: gibt Bernstein mit Einschluss, die Falle ist verbraucht. */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!event.getLevel().getBlockState(event.getPos()).is(EpochsContent.RESIN_TRAP_HARDENED.get())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof ServerPlayer player) {
            harvest(level, event.getPos(), player);
        }
    }

    public static void harvest(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!level.getBlockState(pos).is(EpochsContent.RESIN_TRAP_HARDENED.get())) return;
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        ItemStack stack = new ItemStack(EpochsContent.AMBER_INCLUSION.get());
        if (!player.getInventory().add(stack)) {
            level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, player.getX(), player.getY(), player.getZ(), stack));
        }
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.3f);
        level.sendParticles(MobCompat.dust(0xeda23a, 1.0f), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 16, 0.3, 0.3, 0.3, 0);
    }

    // ---- Spawn ----

    public static boolean canSpawnHere(net.minecraft.world.level.ServerLevelAccessor level, BlockPos pos) {
        var tuning = dev.strata.core.api.StrataAPI.mobTuning(level.getLevel().getServer(), DEF.id());
        if (tuning.spawn("enabled", 1) == 0) return false;
        if (!level.getBlockState(pos.above()).isAir()) return false;
        if (level.getBrightness(LightLayer.BLOCK, pos) > tuning.spawn("max_light", 7)) return false;   // spawnt im Dunkeln
        int maxNearby = (int) tuning.spawn("max_nearby", 6);
        return level.getEntitiesOfClass(AmberMoth.class, new AABB(pos).inflate(32)).size() < maxNearby;
    }

    // ---- Sounds ----

    @Override
    protected SoundEvent getAmbientSound() {
        return EpochsMobs.AMBER_MOTH_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return EpochsMobs.AMBER_MOTH_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return EpochsMobs.AMBER_MOTH_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 0.6f;
    }

    private void sound(SoundEvent sound, float volume, float pitch) {
        level().playSound(null, getX(), getY(), getZ(), sound, SoundSource.NEUTRAL, volume, pitch);
    }
}
