package dev.strata.epochs.mob;

import dev.strata.core.mc.Mc;
import dev.strata.epochs.EpochTravel;
import dev.strata.epochs.EpochsContent;
import dev.strata.epochs.EpochsMobs;
import dev.strata.epochs.Solidification;
import dev.strata.epochs.StrataEpochs;
import dev.strata.epochs.mc.MobCompat;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Resonanzling: lebende Stimmgabel aus Kristallquarz (Kristallzeit, friedlich). Bei ihm ruht die Verfestigung.
 *
 * <ul>
 *   <li><b>Gleichklang</b> ({@code calm}): Solange er gestimmt ist, ruht die Uhr aller Spieler im Umkreis
 *       ({@link Solidification#pause}). Alle paar Sekunden schlägt er an (Ton, Partikelring am Rand).</li>
 *   <li><b>Verstimmung</b> ({@code dissonance}): Beginnt ein Spieler im Umkreis Stein oder Erz anzuschlagen
 *       (Härte ≥ 1) oder trifft dort ein Wesen, wird er <i>unruhig</i> (nur Warnung). Wird der Block wirklich
 *       abgebaut oder kommt in der Unruhe ein zweiter Treffer, schreit er nach der Vorwarnung auf: Der Gleichklang
 *       endet für eine Weile, er weicht zurück und glimmt nur noch. Treffer auf Monster, die einen Spieler
 *       angreifen, zählen nicht.</li>
 *   <li><b>Einstimmen</b> ({@code tune}): Kristallquarz in der Hand lockt ihn; Rechtsklick damit stimmt ihn an
 *       dieser Stelle ein (Heimat, er bleibt und verschwindet nicht mehr).</li>
 * </ul>
 * Er greift nie an. Getötet lässt er Resonanzsplitter fallen (die eigene Uhr ruht kurz).
 * Er schwebt ohne Pfadsuche: Ziel wählen, hinschweben, über dem Boden halten.
 */
public class ResonantShard extends StrataMob {

    public static final StrataMobDef DEF = new StrataMobDef(StrataEpochs.MOD_ID, "resonant_shard",
            List.of("resonate", "dissonance", "tune"), "idle", "fly", List.of("crystals", "eyes"), 0.3f);

    private static final int GLOW_CRYSTALS = 0;
    private static final int GLOW_EYES = 1;

    /** Vorgaben; Datapack-Werte unter {@code abilities.calm}, {@code abilities.dissonance}, {@code abilities.tune}. */
    /** Radius des Gleichklangs, zugleich der Radius, in dem Abbau und Kampf ihn stören (sichtbar als Ring). */
    public static final double CALM_RADIUS = 5.0;
    public static final int PULSE_TICKS = 80;
    public static final int WARNING_TICKS = 12;
    public static final int UNEASY_TICKS = 100;
    public static final int SILENCE_TICKS = 600;
    public static final double FLEE_DISTANCE = 6.0;
    public static final double DRIFT = 3.0;
    public static final double LURE_RANGE = 8.0;
    /** Mindesthärte eines Blocks, dessen Abbau ihn stört (Stein 1,5, Erz 3; Erde, Sand, Pflanzen darunter). */
    public static final float MIN_HARDNESS = 1.0f;
    /** Suchradius für Störungen; ein Datapack-Radius darüber wird gekappt. */
    private static final double SEARCH_RADIUS = 16.0;
    /** Blöcke, deren Abbau ihn nie stört (Werkbank, Truhen, Tische im eigenen Lager). */
    public static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> IGNORED =
            MobCompat.blockTag(StrataEpochs.id("resonance_ignores"));
    /** So lange hält eine Pause an; der Gleichklang erneuert sie zweimal pro Sekunde. */
    private static final int PAUSE_REFRESH = 30;
    private static final double HOVER = 0.6;
    /** Länger flieht er nicht (er hängt sonst an einem Hindernis). */
    private static final int FLEE_TICKS = 60;
    /** Leuchten des Quarzes in Ruhe (Leucht-Ebene ersetzt die Farbe; darunter wäre er dunkler als die Textur). */
    private static final float GLOW_REST = 0.85f;
    /** Ohne Heimat (Long.MIN_VALUE): nicht eingestimmt. */
    public static final long NO_HOME = Long.MIN_VALUE;

    private BlockPos home;
    private boolean tuned;
    private boolean loaded;
    private int uneasyTicks;
    private int shriekIn = -1;
    private int silentTicks;
    private int pulseTicks;
    private int driftTicks;
    private Vec3 driftTarget;
    private Vec3 fleeTarget;
    private boolean following;
    private int fleeTicks;
    private int pulses;
    private Vec3 lastDisturbance;
    private ServerPlayer disturber;

    public ResonantShard(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setGlow(GLOW_CRYSTALS, GLOW_REST);
        setGlow(GLOW_EYES, 1.0f);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return MobCompat.baseAttributes()
                .add(Attributes.MAX_HEALTH, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
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

    // ---- Zustand (auch für den Selbsttest) ----

    /** Wirkt der Gleichklang gerade? */
    public boolean calm() {
        return silentTicks == 0 && shriekIn < 0 && !following && fleeTarget == null && tuning().abilityEnabled("calm");
    }

    /** Radius des Gleichklangs und der Störung. */
    public double radius() {
        return Math.min(SEARCH_RADIUS, value("calm", "radius", CALM_RADIUS));
    }

    public boolean uneasy() {
        return uneasyTicks > 0;
    }

    public boolean silent() {
        return silentTicks > 0;
    }

    public boolean tuned() {
        return tuned;
    }

    public BlockPos home() {
        return home;
    }

    private double value(String ability, String key, double fallback) {
        return tuning().ability(ability, key, fallback);
    }

    // ---- Server-Tick ----

    @Override
    public void tick() {
        if (level() instanceof ServerLevel server) serverTick(server);
        super.tick();
    }

    private void serverTick(ServerLevel level) {
        if (!loaded) {
            loaded = true;
            long saved = getData(EpochsContent.RESONANT_HOME.get());
            tuned = saved != NO_HOME;
            home = tuned ? BlockPos.of(saved) : blockPosition();
        }
        if (uneasyTicks > 0) uneasyTicks--;
        if (shriekIn >= 0) {
            if (tickCount % 2 == 0) setGlow(GLOW_CRYSTALS, random.nextBoolean() ? 1.0f : 0.15f);   // flackert
            if (shriekIn-- == 0) shriek(level);
        } else if (uneasyTicks > 0 && silentTicks == 0) {
            setGlow(GLOW_CRYSTALS, tickCount % 6 < 3 ? 0.9f : 0.3f);
        }
        if (silentTicks > 0 && --silentTicks == 0) {
            if (!tuned) home = blockPosition();
            setGlow(GLOW_CRYSTALS, GLOW_REST);
            setGlow(GLOW_EYES, 1.0f);
        }
        move(level);
        if (calm()) calmTick(level);
    }

    private void calmTick(ServerLevel level) {
        double radius = radius();
        if (tickCount % 10 == 0) {
            double factor = value("calm", "factor", 0.0);   // 0 = die Uhr ruht, sonst läuft sie nur langsamer
            for (ServerPlayer player : playersWithin(level, radius)) {
                if (factor <= 0) Solidification.pause(player, PAUSE_REFRESH);
                else Solidification.slowDown(player, factor, PAUSE_REFRESH);
            }
        }
        if (uneasyTicks == 0) setGlow(GLOW_CRYSTALS, pulseTicks > 0 ? 1.0f : GLOW_REST);
        if (pulseTicks > 0) pulseTicks--;
        int interval = Math.max(20, (int) value("calm", "pulse", PULSE_TICKS));
        if (tickCount % interval == 0) {
            pulseTicks = 20;
            if (uneasyTicks == 0) playAction("resonate");
            if (pulses++ % 2 == 0) sound(EpochsMobs.RESONANT_SHARD_RESONATE.get(), 0.6f, 1.0f);   // Ton nur jeden 2. Puls
            double ground = groundY(level);
            for (int i = 0; i < 32; i++) {                    // Ring am Boden zeigt den Umkreis
                double a = i * Math.PI * 2 / 32;
                level.sendParticles(MobCompat.dust(0xb48cff, 1.0f), getX() + Math.cos(a) * radius, ground + 0.15,
                        getZ() + Math.sin(a) * radius, 1, 0, 0, 0, 0);
            }
        }
    }

    /**
     * Nur für den Selbsttest: Simulierte Spieler sind nicht in der Welt eingetragen und würden von der
     * Entity-Suche nicht gefunden. Im Spiel bleibt die Liste leer.
     */
    public static final List<ServerPlayer> TEST_PLAYERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    private List<ServerPlayer> players(ServerLevel level, double range) {
        List<ServerPlayer> list = new java.util.ArrayList<>(level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(range)));
        for (ServerPlayer p : TEST_PLAYERS) if (p.level() == level && !list.contains(p)) list.add(p);
        list.removeIf(p -> p.isSpectator() || p.position().distanceTo(position()) > range + 0.5);
        return list;
    }

    private List<ServerPlayer> playersWithin(ServerLevel level, double radius) {
        return players(level, radius);
    }

    // ---- Bewegung ----

    private void move(ServerLevel level) {
        Vec3 target;
        double speed;
        Vec3 face = null;
        ServerPlayer lure = silentTicks == 0 && fleeTarget == null ? lure(level) : null;
        following = lure != null;
        if (fleeTarget != null) {
            target = fleeTarget;
            speed = 0.3;
            if (position().distanceTo(fleeTarget) < 0.8 || ++fleeTicks > FLEE_TICKS) {
                fleeTarget = null;
                fleeTicks = 0;
            }
        } else if (lure != null) {
            Vec3 eye = lure.getEyePosition();
            Vec3 away = position().subtract(eye);
            away = new Vec3(away.x, 0, away.z);
            away = away.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : away.normalize();
            Vec3 wanted = eye.add(away.scale(2.0)).add(0, -1.0, 0);
            target = free(level, wanted) ? wanted : position();   // kein freier Platz neben dem Spieler: warten
            speed = 0.15;
            face = eye;
            if (!tuned) home = blockPosition();
        } else {
            if (driftTarget == null || --driftTicks <= 0 || position().distanceTo(driftTarget) < 0.4) {
                driftTarget = pickDrift(level);
                driftTicks = 80 + random.nextInt(80);
            }
            target = driftTarget;
            speed = 0.04;
            ServerPlayer near = level.getNearestPlayer(this, 8.0) instanceof ServerPlayer p && !p.isSpectator() ? p : null;
            if (near != null) face = near.getEyePosition();
        }
        // Höhe: knapp über dem Boden unter dem Ziel, aber nur, wenn dort Platz ist (nie in Fels oder Decke).
        double ground = groundBelow(level, target);
        if (!Double.isNaN(ground)) {
            double hoverY = ground + HOVER;
            double y = lure != null ? Math.max(hoverY, Math.min(target.y, hoverY + 2.0)) : hoverY;
            Vec3 adjusted = new Vec3(target.x, y, target.z);
            if (free(level, adjusted)) target = adjusted;
        }
        Vec3 delta = target.subtract(position());
        double length = delta.length();
        Vec3 wanted = length < 0.05 ? Vec3.ZERO : delta.scale(Math.min(speed, length * 0.2) / length);
        setDeltaMovement(getDeltaMovement().scale(0.5).add(wanted.scale(0.5)));
        Vec3 look = face != null ? face.subtract(position()) : length > 0.3 ? delta : null;
        if (look != null && look.horizontalDistanceSqr() > 1.0e-4) {
            float yaw = (float) (Math.atan2(-look.x, look.z) * 180 / Math.PI);
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
        }
    }

    /** Freier Platz für die Trefferfläche an {@code p} und freie Sicht von hier dorthin (nicht durch Wände). */
    private boolean free(ServerLevel level, Vec3 p) {
        if (!level.noCollision(this, getBoundingBox().move(p.subtract(position())))) return false;
        Vec3 from = position().add(0, 0.4, 0);
        Vec3 to = p.add(0, 0.4, 0);
        return level.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, this)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    /** Ein freier, erreichbarer Punkt in Schwebehöhe um die Heimat; sonst bleibt er, wo er ist. */
    private Vec3 pickDrift(ServerLevel level) {
        double drift = value("tune", "drift", DRIFT);
        Vec3 base = Vec3.atBottomCenterOf(home);
        for (int i = 0; i < 6; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = random.nextDouble() * drift;
            Vec3 at = new Vec3(base.x + Math.cos(a) * r, Math.max(base.y, getY()), base.z + Math.sin(a) * r);
            double ground = groundBelow(level, at);
            if (Double.isNaN(ground)) continue;
            Vec3 p = new Vec3(at.x, ground + HOVER, at.z);
            if (free(level, p)) return p;
        }
        return position();
    }

    /** Nächster Spieler in Reichweite mit Kristallquarz in einer Hand, oder null. */
    private ServerPlayer lure(ServerLevel level) {
        if (!tuning().abilityEnabled("tune")) return null;
        ServerPlayer best = null;
        double bestDistance = LURE_RANGE;
        for (ServerPlayer p : players(level, LURE_RANGE)) {
            if (!(isLure(p.getMainHandItem()) || isLure(p.getOffhandItem()))) continue;
            if (tuned && !p.isShiftKeyDown()) continue;   // einen eingestimmten lockt nur, wer schleicht (kein Versehen)
            double d = p.distanceTo(this);
            if (d < bestDistance) {
                best = p;
                bestDistance = d;
            }
        }
        return best;
    }

    private static boolean isLure(ItemStack stack) {
        return stack.is(EpochsContent.CRYSTAL_QUARTZ.get());
    }

    private double groundY(ServerLevel level) {
        double ground = groundBelow(level, position());
        return Double.isNaN(ground) ? getY() - HOVER : ground;
    }

    /**
     * Oberkante des ersten festen Blocks unter {@code at} (höchstens 8 Blöcke tief), sonst at.y - 1.
     * Liegt {@code at} selbst in einem festen Block, gibt es keinen Boden darunter: NaN (Ziel verwerfen).
     */
    private double groundBelow(ServerLevel level, Vec3 at) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(at.x, at.y + 0.5, at.z).mutable();
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return Double.NaN;
        for (int i = 0; i < 9; i++, pos.move(0, -1, 0)) {
            BlockState state = level.getBlockState(pos);
            var shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty()) return pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
        }
        return at.y - 1.0;
    }

    // ---- Verstimmung ----

    /** Art der Störung: Anschlagen eines Blocks (nur Warnung), Abbau (Aufschrei), Treffer (Warnung, dann Aufschrei). */
    public enum Disturbance { STRIKE, BREAK, HIT }

    /**
     * Eine Störung von {@code player} bei {@code from}. Anschlagen macht ihn nur unruhig; ein Abbau oder ein zweiter
     * Treffer während der Unruhe lässt ihn nach der Vorwarnzeit aufschreien.
     */
    public void disturb(ServerPlayer player, Vec3 from, Disturbance kind) {
        if (!tuning().abilityEnabled("dissonance")) return;
        lastDisturbance = from;
        if (silentTicks > 0) {
            if (kind == Disturbance.HIT && from.distanceTo(position()) < 1.0e-3) flee(player.position());   // direkt getroffen
            return;
        }
        if (shriekIn >= 0) return;
        int warning = Math.max(10, (int) value("dissonance", "warning", WARNING_TICKS));
        boolean shriek = kind == Disturbance.BREAK || kind == Disturbance.HIT && uneasyTicks > 0;
        if (shriek) {
            shriekIn = warning;
            disturber = player;
            playAction("dissonance");
            sound(EpochsMobs.RESONANT_SHARD_DISSONANCE.get(), 1.0f, 1.0f);
        } else {
            boolean fresh = uneasyTicks == 0;
            uneasyTicks = (int) value("dissonance", "uneasy", UNEASY_TICKS);
            if (!fresh) return;
            playActionFor("dissonance", warning);
            sound(EpochsMobs.RESONANT_SHARD_DISSONANCE.get(), 0.7f, 1.25f);
            // Alle im Radius sollen die Warnung lesen, nicht nur der Störer (Mitspieler im Lager).
            java.util.Set<ServerPlayer> told = new java.util.HashSet<>();
            if (player != null) told.add(player);
            if (level() instanceof ServerLevel level) told.addAll(playersWithin(level, radius()));
            for (ServerPlayer p : told) {
                notify(p, Component.translatable("message.strata_epochs.resonant_shard.uneasy").withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    private void shriek(ServerLevel level) {
        int silence = (int) value("dissonance", "silence", SILENCE_TICKS);
        silentTicks = Math.max(1, silence);
        uneasyTicks = 0;
        pulseTicks = 0;
        setGlow(GLOW_CRYSTALS, 0.1f);
        setGlow(GLOW_EYES, 0.25f);
        sound(EpochsMobs.RESONANT_SHARD_SHRIEK.get(), 1.2f, 1.0f);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_CLUSTER.defaultBlockState()),
                getX(), getY() + 0.9, getZ(), 18, 0.3, 0.4, 0.3, 0.15);
        level.sendParticles(MobCompat.dust(0xd04a8a, 1.2f), getX(), getY() + 1.1, getZ(), 12, 0.5, 0.3, 0.5, 0);
        flee(lastDisturbance != null ? lastDisturbance : position());
        if (disturber != null && disturber.isAlive()) {
            notify(disturber, Component.translatable("message.strata_epochs.resonant_shard.silent", silentTicks / 20)
                    .withStyle(ChatFormatting.RED));
        }
        disturber = null;
    }

    /** Weicht vom Störer weg: geradeaus, sonst schräg oder kürzer, immer nur in freien, erreichbaren Raum. */
    private void flee(Vec3 from) {
        driftTarget = null;
        fleeTarget = null;
        fleeTicks = 0;
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 away = position().subtract(from);
        away = new Vec3(away.x, 0, away.z);
        if (away.lengthSqr() < 1.0e-4) away = new Vec3(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
        away = away.normalize();
        double distance = value("dissonance", "flee", FLEE_DISTANCE);
        for (double scale : new double[]{1.0, 0.66, 0.33}) {
            for (int turn : new int[]{0, 45, -45, 90, -90}) {
                double rad = Math.toRadians(turn);
                Vec3 dir = new Vec3(away.x * Math.cos(rad) - away.z * Math.sin(rad), 0, away.x * Math.sin(rad) + away.z * Math.cos(rad));
                Vec3 at = position().add(dir.scale(distance * scale));
                double ground = groundBelow(level, at);
                if (Double.isNaN(ground)) continue;
                Vec3 p = new Vec3(at.x, ground + HOVER, at.z);
                if (free(level, p)) {
                    fleeTarget = p;
                    return;
                }
            }
        }
    }

    /** Alle Resonanzlinge, in deren Radius {@code at} liegt. */
    private static List<ResonantShard> around(ServerLevel level, Vec3 at) {
        return level.getEntitiesOfClass(ResonantShard.class, new AABB(at, at).inflate(SEARCH_RADIUS));
    }

    private static boolean disturbs(ServerLevel level, BlockPos pos, BlockState state) {
        return state.getDestroySpeed(level, pos) >= MIN_HARDNESS && !state.is(IGNORED);
    }

    /**
     * Abbau in der Nähe dieses Resonanzlings: anschlagen ({@code broken = false}) warnt, abbauen verstimmt.
     * Weiche Blöcke, Arbeitsblöcke und alles außerhalb des Radius zählen nicht. Gibt zurück, ob es ihn störte.
     */
    public boolean nearbyBlock(ServerPlayer player, BlockPos pos, BlockState state, boolean broken) {
        Vec3 at = Vec3.atCenterOf(pos);
        if (!(level() instanceof ServerLevel level) || !disturbs(level, pos, state) || position().distanceTo(at) > radius()) return false;
        disturb(player, at, broken ? Disturbance.BREAK : Disturbance.STRIKE);
        return true;
    }

    /** Ein Spieler trifft ein anderes Wesen bei {@code at}: stört nur innerhalb des Radius. */
    public boolean nearbyHit(ServerPlayer player, Vec3 at) {
        if (position().distanceTo(at) > radius()) return false;
        disturb(player, at, Disturbance.HIT);
        return true;
    }

    /** Ein Spieler beginnt einen Block anzuschlagen: Vorwarnung (er kann noch aufhören). */
    public static void onStartBreaking(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START
                || !(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)
                || player.isSpectator()) return;
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        for (ResonantShard shard : around(level, Vec3.atCenterOf(pos))) shard.nearbyBlock(player, pos, state, false);
    }

    /** Ein Block wurde abgebaut (von {@code BreakHook.onBroken}): Aufschrei bei Resonanzlingen im Radius. */
    public static void onBlockBroken(LevelAccessor accessor, BlockPos pos, BlockState state, Player player) {
        if (!(accessor instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer) || player.isSpectator()) return;
        for (ResonantShard shard : around(level, Vec3.atCenterOf(pos))) shard.nearbyBlock(serverPlayer, pos, state, true);
    }

    /** Ein Spieler trifft ihn oder ein Wesen in seinem Radius. Selbstverteidigung gegen Monster zählt nicht. */
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)
                || !(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (event.getEntity() instanceof ResonantShard shard) {
            // Der erste Treffer warnt nur (kein Schaden, ein Stoß zurück); erst in der Unruhe oder verstimmt trifft es.
            boolean warnOnly = !shard.uneasy() && !shard.silent() && shard.tuning().abilityEnabled("dissonance");
            shard.disturb(player, shard.position(), Disturbance.HIT);
            if (warnOnly) {
                event.setCanceled(true);
                Vec3 away = shard.position().subtract(player.position());
                away = new Vec3(away.x, 0, away.z);
                if (away.lengthSqr() > 1.0e-4) shard.setDeltaMovement(shard.getDeltaMovement().add(away.normalize().scale(0.4)));
            }
            return;
        }
        if (selfDefense(event.getEntity())) return;
        Vec3 at = event.getEntity().position();
        for (ResonantShard shard : around(level, at)) shard.nearbyHit(player, at);
    }

    /** Selbstverteidigung: Ein Monster, das gerade einen Spieler angreift, darf man treffen, ohne ihn zu stören. */
    public static boolean selfDefense(net.minecraft.world.entity.Entity victim) {
        return victim instanceof net.minecraft.world.entity.Mob mob && targetsPlayer(mob.getTarget());
    }

    public static boolean targetsPlayer(net.minecraft.world.entity.LivingEntity target) {
        return target instanceof Player;
    }

    // ---- Einstimmen ----

    /** Stimmt ihn an seiner jetzigen Stelle ein: neue Heimat, er bleibt und verschwindet nicht mehr. */
    public void tune() {
        tuned = true;
        home = blockPosition();
        driftTarget = null;
        setData(EpochsContent.RESONANT_HOME.get(), home.asLong());
        setPersistenceRequired();
        playAction("tune");
        sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.4f);
        if (level() instanceof ServerLevel level) {
            level.sendParticles(MobCompat.dust(0xffe08a, 1.0f), getX(), getY() + 0.5, getZ(), 16, 0.5, 0.2, 0.5, 0);
        }
    }

    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof ResonantShard shard) || event.getHand() != InteractionHand.MAIN_HAND) return;
        ItemStack held = event.getItemStack();
        if (!isLure(held)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!shard.tuning().abilityEnabled("tune")) return;
        if (shard.silent()) {
            notify(player, Component.translatable("message.strata_epochs.resonant_shard.still_silent")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!player.isCreative()) held.shrink(1);
        shard.tune();
        notify(player, Component.translatable("message.strata_epochs.resonant_shard.tuned").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** Eigene Meldung in der Statusleiste; die Ruhe-Meldung schweigt solange (3 s), sonst wäre sie sofort überschrieben. */
    private static void notify(ServerPlayer player, Component message) {
        Mc.actionbar(player, message);
        Solidification.holdMessage(player, 60);
    }

    // ---- Resonanzsplitter ----

    public static final int SPLINTER_PAUSE = 600;
    public static final int SPLINTER_COOLDOWN = 6000;
    public static final int SPLINTER_CHARGES = 3;

    /**
     * Rechtsklick mit einem Resonanzsplitter: Die eigene Uhr ruht 30 s (nur in einer Epoche), danach 5 min
     * Abklingzeit. Nach {@link #SPLINTER_CHARGES} Ladungen zerbricht er. Werte unter {@code abilities.splinter}.
     */
    public static void useSplinter(ServerPlayer player, ItemStack stack) {
        if (Mc.isOnCooldown(player, stack)) return;
        if (EpochTravel.epochOf(player.level()).isEmpty()) {
            Mc.actionbar(player, Component.translatable("message.strata_epochs.resonant_splinter.no_epoch").withStyle(ChatFormatting.GRAY));
            return;
        }
        var tuning = dev.strata.core.api.StrataAPI.mobTuning(((ServerLevel) player.level()).getServer(), DEF.id());
        int pause = (int) tuning.ability("splinter", "pause", SPLINTER_PAUSE);
        Solidification.pause(player, pause);
        Mc.addCooldown(player, stack, (int) tuning.ability("splinter", "cooldown", SPLINTER_COOLDOWN));
        if (!player.isCreative()) {
            int used = stack.getDamageValue() + 1;
            if (used >= stack.getMaxDamage()) stack.shrink(1);
            else stack.setDamageValue(used);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), EpochsMobs.RESONANT_SHARD_RESONATE.get(),
                SoundSource.PLAYERS, 1.0f, 1.2f);
        notify(player, Component.translatable("message.strata_epochs.resonant_splinter", pause / 20)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    // ---- Spawn ----

    public static boolean canSpawnHere(net.minecraft.world.level.ServerLevelAccessor level, BlockPos pos) {
        var tuning = dev.strata.core.api.StrataAPI.mobTuning(level.getLevel().getServer(), DEF.id());
        if (tuning.spawn("enabled", 1) == 0) return false;
        if (!level.getBlockState(pos.above()).isAir() || !level.getBlockState(pos.above(2)).isAir()) return false;
        int maxNearby = (int) tuning.spawn("max_nearby", 2);
        return level.getEntitiesOfClass(ResonantShard.class, new AABB(pos).inflate(48)).size() < maxNearby;
    }

    // ---- Sounds ----

    @Override
    protected SoundEvent getAmbientSound() {
        return silentTicks > 0 ? null : EpochsMobs.RESONANT_SHARD_AMBIENT.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 240;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return EpochsMobs.RESONANT_SHARD_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return EpochsMobs.RESONANT_SHARD_DEATH.get();
    }

    private void sound(SoundEvent sound, float volume, float pitch) {
        level().playSound(null, getX(), getY(), getZ(), sound, SoundSource.NEUTRAL, volume, pitch);
    }
}
