package dev.strata.epochs.mob;

import dev.strata.core.MobTuning;
import dev.strata.core.api.StrataAPI;
import dev.strata.core.mc.Mc;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Basis aller Strata-Mobs: Animations-Uhr, Leucht-Ebenen, Fähigkeiten und Datapack-Werte.
 *
 * <p>Der Server wählt die laufende Aktion ({@link #playAction}), der Client spielt die passende
 * Blockbench-Animation ab. Übertragen wird nur eine Zahl, die Zeit misst jede Seite selbst.
 * Das funktioniert in allen Minecraft-Versionen gleich.
 */
public abstract class StrataMob extends PathfinderMob {

    /** Laufende Aktion: (Zähler << 8) | (Index in {@link StrataMobDef#actions()} + 1), 0 = keine. */
    private static final EntityDataAccessor<Integer> DATA_ACTION =
            SynchedEntityData.defineId(StrataMob.class, EntityDataSerializers.INT);
    /** Stärke der Leucht-Ebenen, je 8 Bit (0..255) für bis zu 4 Ebenen, vom Server gesetzt. */
    private static final EntityDataAccessor<Integer> DATA_GLOW =
            SynchedEntityData.defineId(StrataMob.class, EntityDataSerializers.INT);
    /** Phase (z. B. abgeworfene Schichten des Stratolithen): Knochen {@code shedN_…} sind ab Phase N ausgeblendet. */
    private static final EntityDataAccessor<Integer> DATA_STAGE =
            SynchedEntityData.defineId(StrataMob.class, EntityDataSerializers.INT);

    private static final int TUNING_REFRESH_TICKS = 200;
    private static final int ENCOUNTER_INTERVAL = 40;
    private static final double ENCOUNTER_RADIUS = 8.0;

    protected final List<StrataAbility<?>> abilities = new ArrayList<>();
    private int actionSerial;
    private int serverActionEnd = -1;
    private MobTuning tuning = MobTuning.EMPTY;
    private boolean tuningApplied;

    // Nur auf dem Client: wann die aktuelle Aktion begonnen hat.
    private int clientActionStart;

    protected StrataMob(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    /** Beschreibung des Mobs (Modell, Animationen, Leucht-Ebenen, Vorgaben). */
    public abstract StrataMobDef def();

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ACTION, 0);
        builder.define(DATA_GLOW, 0);
        builder.define(DATA_STAGE, 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_ACTION.equals(accessor)) clientActionStart = tickCount;
        if (DATA_STAGE.equals(accessor)) onStageChanged();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel)) return;
        if (!tuningApplied || tickCount % TUNING_REFRESH_TICKS == 0) refreshTuning();
        if (serverActionEnd >= 0 && tickCount >= serverActionEnd) stopAction();
        for (StrataAbility<?> ability : abilities) ability.tick();
        if (tickCount % ENCOUNTER_INTERVAL == 0) awardEncounter();
    }

    /**
     * Advancement „Begegnung“ für Spieler in der Nähe, die den Mob sehen können.
     * Die Datei ({@code <mod>:strata_codex/met_<name>}) liefert Strata Bridge; fehlt sie, passiert nichts.
     */
    private void awardEncounter() {
        String id = def().modId() + ":strata_codex/met_" + def().name();
        for (ServerPlayer player : level().getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(ENCOUNTER_RADIUS))) {
            if (!player.isSpectator() && hasLineOfSight(player)) Mc.awardAdvancement(player, id);
        }
    }

    // ---- Aktionen (Animationen mit Keyframes) ----

    /** Startet eine Aktion, z. B. {@code "scratch"}. Läuft sie schon, beginnt sie von vorn. */
    public void playAction(String name) {
        int index = def().actions().indexOf(name);
        if (index < 0) throw new IllegalArgumentException(def().id() + ": unbekannte Aktion " + name);
        actionSerial = (actionSerial + 1) & 0xFFFFFF;
        entityData.set(DATA_ACTION, (actionSerial << 8) | (index + 1));
        MobAnimation anim = def().model().animation(name);
        serverActionEnd = anim != null && anim.loop() == MobAnimation.Loop.ONCE
                ? tickCount + Math.round(anim.length() * 20) : -1;
    }

    /** Startet eine Aktion für eine feste Zeit (z. B. einen Sprung aus einer Schleifen-Animation). */
    public void playActionFor(String name, int ticks) {
        playAction(name);
        serverActionEnd = tickCount + ticks;
    }

    /** Hält eine Aktion so lange, bis {@link #stopAction} kommt (für Haltephasen wie das Einrollen). */
    public void holdAction(String name) {
        playAction(name);
        serverActionEnd = -1;
    }

    public void stopAction() {
        serverActionEnd = -1;
        if (entityData.get(DATA_ACTION) != 0) entityData.set(DATA_ACTION, 0);
    }

    /** Name der laufenden Aktion oder null. Auf beiden Seiten nutzbar. */
    public String currentAction() {
        int index = (entityData.get(DATA_ACTION) & 0xFF) - 1;
        return index < 0 || index >= def().actions().size() ? null : def().actions().get(index);
    }

    /** Sekunden seit Beginn der Aktion (Client). */
    public float actionSeconds(float partialTick) {
        return (tickCount - clientActionStart + partialTick) / 20f;
    }

    // ---- Leucht-Ebene ----

    /** Stärke der ersten Leucht-Ebene (0..1). */
    public void setGlow(float strength) {
        setGlow(0, strength);
    }

    /** Stärke der Leucht-Ebene {@code layer} (Reihenfolge wie {@link StrataMobDef#glowLayers()}), 0..1. */
    public void setGlow(int layer, float strength) {
        int value = Math.round(Math.max(0f, Math.min(1f, strength)) * 255);
        int shift = layer * 8;
        int packed = entityData.get(DATA_GLOW);
        int updated = (packed & ~(0xFF << shift)) | (value << shift);
        if (updated != packed) entityData.set(DATA_GLOW, updated);
    }

    public float glow() {
        return glow(0);
    }

    public float glow(int layer) {
        return ((entityData.get(DATA_GLOW) >>> (layer * 8)) & 0xFF) / 255f;
    }

    // ---- Phase ----

    public int stage() {
        return entityData.get(DATA_STAGE);
    }

    protected void setStage(int stage) {
        entityData.set(DATA_STAGE, stage);
    }

    /** Die Phase hat sich geändert (Server und Client), z. B. um die Trefferfläche anzupassen. */
    protected void onStageChanged() {}

    // ---- Datapack-Werte ----

    /** Die Datapack-Werte dieses Mobs (Server). Ohne Datapack-Eintrag leer, dann gelten die Vorgaben. */
    public MobTuning tuning() {
        return tuning;
    }

    private void refreshTuning() {
        if (!(level() instanceof ServerLevel server)) return;
        tuning = StrataAPI.mobTuning(server.getServer(), def().id());
        if (!tuningApplied) {
            tuningApplied = true;
            boolean fullHealth = getHealth() >= getMaxHealth();
            apply(Attributes.MAX_HEALTH, "max_health");
            apply(Attributes.ATTACK_DAMAGE, "attack_damage");
            apply(Attributes.ARMOR, "armor");
            apply(Attributes.MOVEMENT_SPEED, "movement_speed");
            if (fullHealth) setHealth(getMaxHealth());
        }
    }

    private void apply(Holder<Attribute> attribute, String name) {
        AttributeInstance instance = getAttribute(attribute);
        Double value = tuning.attributes().get(name);
        if (instance != null && value != null) instance.setBaseValue(value);
    }

    // ---- Schaden ----

    /** Für Fähigkeiten wie den Kristallpanzer: eingehenden Schaden ändern. Standard: nichts. */
    protected void onIncomingDamage(LivingIncomingDamageEvent event) {
        for (StrataAbility<?> ability : abilities) ability.onIncomingDamage(event);
    }

    /** Wird einmal beim Modstart auf dem NeoForge-Bus angemeldet. */
    public static void handleIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof StrataMob mob && mob.level() instanceof ServerLevel) {
            mob.onIncomingDamage(event);
        }
    }
}
