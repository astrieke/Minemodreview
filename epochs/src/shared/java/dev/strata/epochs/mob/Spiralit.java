package dev.strata.epochs.mob;

import dev.strata.common.SolidificationClock;
import dev.strata.core.mc.Mc;
import dev.strata.epochs.EpochsContent;
import dev.strata.epochs.EpochsMobs;
import dev.strata.epochs.Solidification;
import dev.strata.epochs.StrataEpochs;
import dev.strata.epochs.mc.MobCompat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Spiralit: wiedererweckter Ammonit, Begleiter des Spielers. Schwebt schulterhoch hinter ihm.
 *
 * <ul>
 *   <li><b>Uhr:</b> Die Spiralrille leuchtet umso heller, je mehr Zeit in der Epoche bleibt.
 *       In der Warnphase pulsiert sie, und er läutet einmal.</li>
 *   <li><b>Ruhe:</b> In seiner Nähe läuft die Verfestigung um {@link #SOLIDIFY_FACTOR} langsamer.</li>
 *   <li><b>Erzspür:</b> Alle 5 s sucht er Erze um den Spieler und zeigt auf das nächste neue.</li>
 * </ul>
 * Er folgt ohne Pfadsuche (schwebt einfach hin) und reist mit durch Epochentore.
 * Schleichen + Rechtsklick: warten/folgen. Rechtsklick mit Kristallquarz heilt ihn.
 */
public class Spiralit extends StrataMob {

    public static final StrataMobDef DEF = new StrataMobDef(StrataEpochs.MOD_ID, "spiralit",
            List.of("chime", "point", "hatch"), "idle", "swim",
            List.of("spiral_1", "spiral_2", "spiral_3", "spiral_4"), 0.25f);

    /** Vorgaben; Datapack-Werte unter {@code abilities.calm} und {@code abilities.ore_sense}. */
    public static final double BONUS_RANGE = 16.0;
    public static final double SOLIDIFY_FACTOR = 0.8;
    /** Block-Tag der Erze, die er spürt (nur Epochen-Erze, damit er Vanilla-Erzsuche nicht ersetzt). */
    public static final String SENSE_TAG = StrataEpochs.id("spiralit_senses");
    private static final double TELEPORT_DISTANCE = 24.0;
    private static final int CLOCK_INTERVAL = 10;
    private static final int ORE_INTERVAL = 300;
    private static final int ORE_RADIUS = 6;
    private static final int ORE_SHOW_TICKS = 60;
    private static final int STUCK_TICKS = 60;
    private static final int HATCH_GLOW_TICKS = 32;
    private static final float HEAL = 4.0f;
    private static final int SEGMENTS = 4;

    /** Besitzer -> wann sein Spiralit zuletzt in Reichweite war und mit welchem Faktor (für den Bonus). */
    private record Near(long time, double factor) {}

    private static final Map<UUID, Near> NEAR_OWNER = new ConcurrentHashMap<>();
    /** Besitzer -> sein Spiralit (höchstens einer pro Spieler; für Dimensionswechsel und Erzspür). */
    private static final Map<UUID, Spiralit> BY_OWNER = new ConcurrentHashMap<>();

    private BlockPos oreTarget;
    private int oreTicks;
    private final Set<BlockPos> shownOres = new LinkedHashSet<>();
    private boolean warned;
    private int stuckTicks;
    private int hatchTicks = -1;

    public Spiralit(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setPersistenceRequired();
        showSegments(SEGMENTS, 0.6f);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return MobCompat.baseAttributes()
                .add(Attributes.MAX_HEALTH, 12.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    public StrataMobDef def() {
        return DEF;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    public SpiralitBond bond() {
        return getData(EpochsContent.BOND.get());
    }

    public void setOwner(Player player) {
        setData(EpochsContent.BOND.get(), new SpiralitBond(player.getUUID().toString(), false));
    }

    public Optional<ServerPlayer> owner() {
        if (!(level() instanceof ServerLevel server) || bond().owner().isEmpty()) return Optional.empty();
        try {
            return Optional.ofNullable(server.getServer().getPlayerList().getPlayer(UUID.fromString(bond().owner())));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private boolean isOwner(Player player) {
        return player.getUUID().toString().equals(bond().owner());
    }

    // ---- Bewegung, Uhr und Erzspür (Server) ----

    @Override
    public void tick() {
        if (level() instanceof ServerLevel server) serverTick(server);
        super.tick();
    }

    private void serverTick(ServerLevel level) {
        ServerPlayer owner = owner().filter(p -> p.level() == level && p.isAlive()).orElse(null);
        if (owner != null) {
            Spiralit known = BY_OWNER.get(owner.getUUID());
            if (known == null || !known.isAlive()) BY_OWNER.put(owner.getUUID(), this);
        }
        boolean main = owner != null && BY_OWNER.get(owner.getUUID()) == this;
        Vec3 target = null;
        Vec3 face = null;
        if (owner != null && !bond().staying()) {
            double distance = distanceTo(owner);
            if (distance > TELEPORT_DISTANCE || stuckTicks > STUCK_TICKS) {
                Vec3 to = shoulder(owner);
                setPos(to.x, to.y, to.z);
                setDeltaMovement(Vec3.ZERO);
                stuckTicks = 0;
                oreTicks = 0;
                distance = 0;
            }
            if (main && tuning().abilityEnabled("calm") && distance <= tuning().ability("calm", "range", BONUS_RANGE)) {
                NEAR_OWNER.put(owner.getUUID(), new Near(level.getGameTime(), tuning().ability("calm", "factor", SOLIDIFY_FACTOR)));
            }
            if (oreTicks > 0 && oreTarget != null) {
                oreTicks--;
                Vec3 ore = Vec3.atCenterOf(oreTarget);
                Vec3 toOwner = owner.getEyePosition().subtract(ore);
                target = ore.add(toOwner.normalize().scale(Math.min(1.6, toOwner.length())));
                face = ore;
            } else {
                target = shoulder(owner);
                int interval = Math.max(20, (int) tuning().ability("ore_sense", "interval", ORE_INTERVAL));
                if (main && tickCount % interval == 0 && tuning().abilityEnabled("ore_sense")
                        && dev.strata.epochs.EpochTravel.epochOf(level).isPresent()) {
                    senseOre(level, owner);
                }
            }
        }
        if (target != null) {
            Vec3 delta = target.subtract(position());
            double length = delta.length();
            Vec3 wanted = length < 0.1 ? Vec3.ZERO : delta.scale(Math.min(0.45, length * 0.15) / length);
            Vec3 before = position();
            setDeltaMovement(getDeltaMovement().scale(0.5).add(wanted.scale(0.5)));
            // Festgehangen (Wand zwischen ihm und dem Ziel)? Nach 3 s springt er zum Besitzer.
            stuckTicks = length > 2.0 && before.distanceToSqr(xo, yo, zo) < 0.0004 ? stuckTicks + 1 : 0;
            Vec3 look = face != null ? face.subtract(position()) : length > 0.6 ? delta : null;
            float yaw = look != null ? (float) (Math.atan2(-look.x, look.z) * 180 / Math.PI) : owner.getYRot();
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
        } else {
            setDeltaMovement(getDeltaMovement().scale(0.7));
        }
        if (hatchTicks >= 0) {
            // Nach dem Schlüpfen gehen die Spiral-Segmente von innen nach außen an.
            showSegments(Math.min(SEGMENTS, hatchTicks * SEGMENTS / HATCH_GLOW_TICKS + 1), 1.0f);
            if (++hatchTicks > HATCH_GLOW_TICKS) hatchTicks = -1;
        } else if (tickCount % CLOCK_INTERVAL == 0) {
            updateClock(owner);
        }
    }

    /** Schulterhoch, rechts vorn neben dem Spieler: so bleibt er am Bildrand sichtbar. */
    private static Vec3 shoulder(Player owner) {
        double yaw = Math.toRadians(owner.getYRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);       // Blickrichtung
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);      // rechte Hand
        return owner.position().add(rx * 0.8 + fx * 0.9, 1.0, rz * 0.8 + fz * 0.9);
    }

    /** Die inneren {@code lit} Segmente leuchten mit {@code strength}, die äußeren sind aus. */
    private void showSegments(int lit, float strength) {
        for (int i = 0; i < SEGMENTS; i++) {               // Ebene 0 = außen, 3 = innen
            setGlow(i, i >= SEGMENTS - lit ? strength : 0f);
        }
    }

    /**
     * Spiralrille als Uhr in vier Segmenten: Je ein Viertel der Zeit erlischt ein Segment, von außen nach innen.
     * In der Warnphase blinkt nur noch das innerste. Außerhalb einer Epoche leuchten alle gedämpft.
     */
    private void updateClock(ServerPlayer owner) {
        Optional<Solidification.Clock> clock = owner == null ? Optional.empty() : Solidification.clock(owner);
        if (clock.isEmpty()) {
            warned = false;
            showSegments(SEGMENTS, 0.6f);
            return;
        }
        Solidification.Clock c = clock.get();
        if (c.stage() == SolidificationClock.Stage.STABLE) {
            warned = false;
            showSegments(Math.max(1, (int) Math.ceil(c.remaining() * SEGMENTS)), 1.0f);
            return;
        }
        showSegments(1, (tickCount / CLOCK_INTERVAL) % 2 == 0 ? 1.0f : 0.1f);
        if (!warned) {
            warned = true;
            playAction("chime");
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.0f, 0.7f);
        }
    }

    private static boolean senses(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(Tags.Blocks.ORES) && state.getTags().anyMatch(t -> t.location().toString().equals(SENSE_TAG));
    }

    private void senseOre(ServerLevel level, ServerPlayer owner) {
        BlockPos center = owner.blockPosition();
        int radius = Math.max(1, Math.min(12, (int) tuning().ability("ore_sense", "radius", ORE_RADIUS)));
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            if (shownOres.contains(pos) || !senses(level.getBlockState(pos))) continue;
            double d = pos.distSqr(center);
            if (d < bestDistance) {
                bestDistance = d;
                best = pos.immutable();
            }
        }
        if (best == null) return;
        if (shownOres.size() >= 32) shownOres.remove(shownOres.iterator().next());
        shownOres.add(best);
        oreTarget = best;
        oreTicks = ORE_SHOW_TICKS;
        playAction("point");
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0f, 1.5f);
        level.sendParticles(ParticleTypes.END_ROD, best.getX() + 0.5, best.getY() + 0.5, best.getZ() + 0.5,
                8, 0.4, 0.4, 0.4, 0.01);
    }

    // ---- Schaden ----

    @Override
    protected void onIncomingDamage(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        boolean fromOwner = source.getEntity() instanceof Player p && isOwner(p);
        if (fromOwner || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.FALL) || source.is(DamageTypes.CRAMMING)) {
            event.setCanceled(true);
            return;
        }
        super.onIncomingDamage(event);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return EpochsMobs.SPIRALIT_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return EpochsMobs.SPIRALIT_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return EpochsMobs.SPIRALIT_DEATH.get();
    }

    // ---- statische Helfer und Ereignisse ----

    /** Zeitfaktor für die Verfestigung: {@link #SOLIDIFY_FACTOR}, solange der eigene Spiralit nah ist. */
    public static double factorFor(ServerPlayer player) {
        Near near = NEAR_OWNER.get(player.getUUID());
        return near != null && player.level().getGameTime() - near.time() <= 40 ? near.factor() : 1.0;
    }

    /** Schlüpft über dem Präparationstisch und gehört ab sofort dem Spieler. */
    public static Spiralit hatch(ServerLevel level, Vec3 at, ServerPlayer owner) {
        Spiralit spiralit = new Spiralit(EpochsMobs.SPIRALIT.get(), level);
        spiralit.setPos(at.x, at.y, at.z);
        spiralit.setYRot(owner.getYRot() + 180);
        spiralit.setOwner(owner);
        spiralit.playAction("hatch");
        spiralit.showSegments(0, 0f);
        spiralit.hatchTicks = 0;
        level.addFreshEntity(spiralit);
        BY_OWNER.put(owner.getUUID(), spiralit);
        return spiralit;
    }

    /** Rechtsklick: Schleichen schaltet warten/folgen um, Kristallquarz heilt, Herrenlose schließen sich an. */
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Spiralit spiralit) || event.getHand() != InteractionHand.MAIN_HAND) return;
        Player player = event.getEntity();
        if (!spiralit.bond().owner().isEmpty() && !spiralit.isOwner(player)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (spiralit.bond().owner().isEmpty()) {
            spiralit.setOwner(player);
            Mc.actionbar(serverPlayer, Component.translatable("message.strata_epochs.spiralit.bonded").withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }
        ItemStack held = event.getItemStack();
        if (held.is(EpochsContent.CRYSTAL_QUARTZ.get()) && spiralit.getHealth() < spiralit.getMaxHealth()) {
            spiralit.heal(HEAL);
            if (!player.isCreative()) held.shrink(1);
            ((ServerLevel) spiralit.level()).sendParticles(ParticleTypes.HEART, spiralit.getX(), spiralit.getY() + 0.6,
                    spiralit.getZ(), 3, 0.2, 0.2, 0.2, 0);
            return;
        }
        if (player.isShiftKeyDown()) {
            boolean staying = !spiralit.bond().staying();
            spiralit.setData(EpochsContent.BOND.get(), spiralit.bond().withStaying(staying));
            Mc.actionbar(serverPlayer, Component.translatable(staying ? "message.strata_epochs.spiralit.stay"
                    : "message.strata_epochs.spiralit.follow").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    /** Durchs Epochentor (oder jeden anderen Dimensionswechsel): Der folgende Spiralit reist mit. */
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Spiralit old = BY_OWNER.get(player.getUUID());
        if (old == null || !old.isAlive() || old.bond().staying() || old.level() == player.level()) return;
        Near near = NEAR_OWNER.get(player.getUUID());       // war er in der Nähe, als der Spieler ging?
        if (near == null || player.level().getGameTime() - near.time() > 40) return;
        ServerLevel level = (ServerLevel) player.level();
        Spiralit copy = new Spiralit(EpochsMobs.SPIRALIT.get(), level);
        copy.setPos(player.getX(), player.getY() + 1.6, player.getZ());
        copy.setData(EpochsContent.BOND.get(), old.bond());
        copy.setHealth(old.getHealth());
        copy.setCustomName(old.getCustomName());
        level.addFreshEntity(copy);
        old.discard();
        BY_OWNER.put(player.getUUID(), copy);
    }

    /** Hat der Spieler schon einen lebenden Spiralit (in einer geladenen Gegend)? */
    public static boolean hasSpiralit(ServerPlayer player) {
        Spiralit known = BY_OWNER.get(player.getUUID());
        return known != null && known.isAlive();
    }

    /** Beim Herunterfahren (oder Weltwechsel im Einzelspieler) alles vergessen. */
    public static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        NEAR_OWNER.clear();
        BY_OWNER.clear();
    }
}
