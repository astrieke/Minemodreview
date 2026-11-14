package dev.strata.epochs.mob;

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
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Harzschnecke: friedliche Schnecke der Sporenzeit mit Bernstein-Gehäuse. Erneuerbare Bernstein-Quelle.
 *
 * <ul>
 *   <li><b>Harzspur</b> ({@code trail}): Beim Kriechen legt sie alle paar Ticks eine klebrige Harzspur
 *       ({@link dev.strata.epochs.ResinTrailBlock}), die zur Bernsteinplatte aushärtet (Abbau: 1 Bernstein).
 *       Pro Schnecke höchstens {@link #TRAIL_CAP} Stück im Umkreis, sonst legt sie keine neue.</li>
 *   <li><b>Gehäuse</b> ({@code shell}): Nimmt sie Schaden, zieht sie sich 5 s ins Gehäuse zurück und nimmt nur
 *       30 % Schaden.</li>
 *   <li><b>Füttern</b> ({@code feed}): Harzfarn-Sporen locken sie; gefüttert ist sie 2 min satt und legt doppelt
 *       so oft Spur.</li>
 * </ul>
 */
public class ResinSnail extends StrataMob {

    public static final StrataMobDef DEF = new StrataMobDef(StrataEpochs.MOD_ID, "resin_snail",
            List.of("retreat", "emerge", "eat"), "idle", "walk", List.of("shell"), 0.3f);

    public static final int TRAIL_INTERVAL = 30;
    public static final int TRAIL_CAP = 16;
    public static final int TRAIL_CAP_FED = 24;
    public static final int TRAIL_RADIUS = 8;
    public static final int SHELL_TICKS = 100;
    public static final int SHELL_COOLDOWN = 200;
    public static final double SHELL_FACTOR = 0.3;
    public static final int FED_TICKS = 2400;

    private int shellTicks;
    private int shellCooldown;
    private int fedTicks;
    private int trailTicks;

    public ResinSnail(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setGlow(0, 0.3f);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return MobCompat.baseAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.12)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.FOLLOW_RANGE, 10.0);
    }

    @Override
    public StrataMobDef def() {
        return DEF;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new TemptGoal(this, 1.2, ResinSnail::isFood, false));
        goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 6.0f));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    public static boolean isFood(ItemStack stack) {
        return stack.is(EpochsContent.RESIN_FERN_SPORES.get());
    }

    private double value(String ability, String key, double fallback) {
        return tuning().ability(ability, key, fallback);
    }

    public boolean inShell() {
        return shellTicks > 0;
    }

    public boolean fed() {
        return fedTicks > 0;
    }

    // ---- Server-Tick ----

    @Override
    public void tick() {
        if (level() instanceof ServerLevel server) serverTick(server);
        super.tick();
    }

    private void serverTick(ServerLevel level) {
        if (shellCooldown > 0) shellCooldown--;
        if (fedTicks > 0) fedTicks--;
        if (shellTicks > 0) {
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
            if (--shellTicks == 0) {
                playAction("emerge");
                setGlow(0, 0.3f);
            }
            return;
        }
        if (!tuning().abilityEnabled("trail")) return;
        boolean moving = getDeltaMovement().horizontalDistanceSqr() > 1.0e-5;
        int interval = (int) value("trail", "interval", TRAIL_INTERVAL);
        if (fedTicks > 0) interval = Math.max(5, interval / 2);
        if (moving && ++trailTicks >= interval) {
            trailTicks = 0;
            layTrail(level);
        }
    }

    /** Legt ein Stück Harzspur an ihrer Stelle, wenn dort Platz ist und die Grenze nicht erreicht ist. */
    public boolean layTrail(ServerLevel level) {
        BlockPos pos = blockPosition();
        if (!level.getBlockState(pos).isAir()) return false;
        BlockState below = level.getBlockState(pos.below());
        if (!below.isFaceSturdy(level, pos.below(), Direction.UP)) return false;
        int radius = (int) value("trail", "radius", TRAIL_RADIUS);
        int cap = (int) (fedTicks > 0 ? value("trail", "cap_fed", TRAIL_CAP_FED) : value("trail", "cap", TRAIL_CAP));
        if (countTrail(level, pos, radius) >= cap) return false;
        level.setBlockAndUpdate(pos, EpochsContent.RESIN_TRAIL.get().defaultBlockState());
        level.sendParticles(MobCompat.dust(0xe08a26, 0.6f), getX(), getY() + 0.1, getZ(), 2, 0.15, 0.02, 0.15, 0);
        return true;
    }

    /** Spurstücke und Bernsteinplatten im Umkreis (Würfel, 3 Blöcke hoch). */
    public static int countTrail(ServerLevel level, BlockPos center, int radius) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, -1, -radius), center.offset(radius, 1, radius))) {
            BlockState s = level.getBlockState(p);
            if (s.is(EpochsContent.RESIN_TRAIL.get()) || s.is(EpochsContent.AMBER_PLATE.get())) n++;
        }
        return n;
    }

    // ---- Gehäuse ----

    @Override
    protected void onIncomingDamage(LivingIncomingDamageEvent event) {
        super.onIncomingDamage(event);
        if (!tuning().abilityEnabled("shell")) return;
        if (shellTicks > 0) {
            event.setAmount(event.getAmount() * (float) value("shell", "damage_factor", SHELL_FACTOR));
        } else if (shellCooldown == 0) {
            retreat();
        }
    }

    /** Zieht sich ins Gehäuse zurück (der auslösende Treffer zählt noch voll). */
    public void retreat() {
        shellTicks = (int) value("shell", "duration", SHELL_TICKS);
        shellCooldown = shellTicks + (int) value("shell", "cooldown", SHELL_COOLDOWN);
        holdAction("retreat");
        setGlow(0, 0.15f);
        getNavigation().stop();
        level().playSound(null, getX(), getY(), getZ(), EpochsMobs.RESIN_SNAIL_RETREAT.get(), SoundSource.NEUTRAL, 1.0f, 1.0f);
    }

    // ---- Füttern ----

    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof ResinSnail snail) || event.getHand() != InteractionHand.MAIN_HAND) return;
        ItemStack held = event.getItemStack();
        if (!isFood(held)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player) || snail.inShell() || !snail.tuning().abilityEnabled("feed")) return;
        if (!player.isCreative()) held.shrink(1);
        snail.feed();
    }

    public void feed() {
        fedTicks = (int) value("feed", "duration", FED_TICKS);
        playAction("eat");
        heal(2.0f);
        if (level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HEART, getX(), getY() + 0.6, getZ(), 3, 0.2, 0.2, 0.2, 0);
        }
        level().playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.6f, 1.4f);
    }

    // ---- Spawn ----

    public static boolean canSpawnHere(net.minecraft.world.level.ServerLevelAccessor level, BlockPos pos) {
        var tuning = dev.strata.core.api.StrataAPI.mobTuning(level.getLevel().getServer(), DEF.id());
        if (tuning.spawn("enabled", 1) == 0) return false;
        int maxNearby = (int) tuning.spawn("max_nearby", 4);
        return level.getEntitiesOfClass(ResinSnail.class, new AABB(pos).inflate(32)).size() < maxNearby;
    }

    // ---- Sounds ----

    @Override
    protected SoundEvent getAmbientSound() {
        return shellTicks > 0 ? null : EpochsMobs.RESIN_SNAIL_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return EpochsMobs.RESIN_SNAIL_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return EpochsMobs.RESIN_SNAIL_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 0.6f;
    }
}
