package dev.strata.epochs;

import dev.strata.common.SolidificationClock;
import dev.strata.core.Epoch;
import dev.strata.core.api.StrataAPI;
import dev.strata.core.mc.Mc;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Wer zu lange in einer Epoche bleibt, verfestigt: erst Warnung, dann Verlangsamung
 * und versteinernde Blöcke in der Nähe, schließlich versteinern Gegenstände im Inventar
 * ({@link ItemPetrification}). Ein Zeitanker im Inventar verdoppelt die Zeit.
 *
 * <p>Mobs greifen über {@link #speedUp} (Zeitfaktor, z. B. Sporenwolke) und {@link #advance}
 * (Zeit abziehen, z. B. Sanduhr-Puls) in die Uhr ein; {@link #pause} hält sie an (Resonanzling).
 */
public final class Solidification {

    /** Zeitfaktor pro Spieler: Faktor und Spielzeit, bis zu der er gilt. Nur im Speicher, nicht gespeichert. */
    private record Factor(double factor, long until) {}

    private static final java.util.Map<java.util.UUID, Factor> FACTORS = new java.util.concurrent.ConcurrentHashMap<>();
    /** Spieler -> Spielzeit, bis zu der seine Uhr ruht (Resonanzling, Resonanzsplitter). Nur im Speicher. */
    private static final java.util.Map<java.util.UUID, Long> PAUSED_UNTIL = new java.util.concurrent.ConcurrentHashMap<>();
    /** Spieler -> Spielzeit, bis zu der die Ruhe-Meldung schweigt (eine eigene Meldung, z. B. „wird unruhig“, steht). */
    private static final java.util.Map<java.util.UUID, Long> MESSAGE_HOLD = new java.util.concurrent.ConcurrentHashMap<>();
    /** Bremse aus dem Datapack (Gleichklang mit Faktor > 0): Spieler -> Faktor und Ende. Nur im Speicher. */
    private static final java.util.Map<java.util.UUID, Factor> BRAKES = new java.util.concurrent.ConcurrentHashMap<>();

    private static final int CHECK_INTERVAL = 20;
    private static final int PETRIFY_INTERVAL = 100;
    private static final int PETRIFY_RADIUS = 3;

    private Solidification() {}

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % CHECK_INTERVAL != 0) return;
        if (player.isCreative() || player.isSpectator()) {
            ItemPetrification.thaw(player, false);
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        Optional<String> epochId = EpochTravel.epochOf(level);
        EpochVisit visit = player.getData(EpochsContent.VISIT.get());
        if (epochId.isEmpty()) {
            ItemPetrification.thaw(player, false);   // draußen löst sich alles, auch fremde Fundstücke
            if (visit.ticks() != 0 || visit.bonus() != 0) {
                player.setData(EpochsContent.VISIT.get(), new EpochVisit(visit.returnDimension(), visit.x(), visit.y(), visit.z(), 0, 0));
            }
            return;
        }

        if (isPaused(player)) {
            // Die Uhr ruht: nichts zählt weiter, und auch eine schon laufende Verfestigung wirkt hier nicht.
            if (!messageHeld(player)) {
                Mc.actionbar(player, Component.translatable("message.strata_epochs.solidify.paused")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            return;
        }
        long before = visit.ticks();
        long ticks = before + Math.round(CHECK_INTERVAL * factor(player));
        player.setData(EpochsContent.VISIT.get(), visit.withTicks(ticks));

        Epoch epoch = StrataAPI.epochs(level.getServer()).get(epochId.get());
        double bonus = timeBonus(player);
        long allowed = SolidificationClock.allowedTicks(epoch.solidifySeconds() * 20L, bonus);

        SolidificationClock.Stage stage = SolidificationClock.stage(ticks, allowed);
        if (SolidificationClock.thawsItems(stage)) ItemPetrification.thaw(player, true);
        switch (stage) {
            case STABLE -> { }
            case WARNING -> Mc.actionbar(player, Component.translatable("message.strata_epochs.solidify.warning",
                    (allowed - ticks) / 20).withStyle(ChatFormatting.GOLD));
            case SOLIDIFYING -> {
                // Zweite Stufe: nach den Blöcken versteinern Gegenstände im Inventar (siehe ItemPetrification).
                net.minecraft.world.item.ItemStack lost = SolidificationClock.petrifiesItem(before, ticks, allowed,
                        epoch.petrifyItemsSeconds() * 20L) ? ItemPetrification.petrifyOne(player, level.getRandom())
                        : net.minecraft.world.item.ItemStack.EMPTY;
                Mc.actionbar(player, lost.isEmpty() ? Component.translatable("message.strata_epochs.solidify.active")
                        .withStyle(ChatFormatting.RED) : Component.translatable("message.strata_epochs.solidify.item",
                        ItemPetrification.describe(lost)).withStyle(ChatFormatting.RED));
                player.addEffect(new MobEffectInstance(Mc.slowness(), 60, 1));
                player.addEffect(new MobEffectInstance(Mc.miningFatigue(), 60, 1));
                if (player.tickCount % PETRIFY_INTERVAL == 0) {   // nach Spielzeit, der Zeitfaktor verschiebt die Uhr
                    petrifyNear(level, player.blockPosition());
                    dev.strata.epochs.mob.WitnessAwakening.tryAwaken(level, player);
                }
            }
        }
    }

    /**
     * Die Uhr läuft für {@code durationTicks} um {@code factor} schneller (2,5 = zweieinhalbfach).
     * Ein stärkerer Faktor ersetzt einen schwächeren, ein gleich starker verlängert nur die Dauer.
     */
    public static void speedUp(ServerPlayer player, double factor, int durationTicks) {
        long now = player.level().getGameTime();
        Factor neu = new Factor(factor, now + durationTicks);
        FACTORS.merge(player.getUUID(), neu, (old, n) -> {
            if (old.until <= now || old.factor < n.factor) return n;
            if (old.factor > n.factor) return old;
            return new Factor(n.factor, Math.max(old.until, n.until));
        });
    }

    /**
     * Die Uhr des Spielers ruht für {@code ticks} (keine Zeit läuft ab, auch keine laufende Verfestigung wirkt).
     * Eine längere Pause bleibt bestehen; Zeitabzüge über {@link #advance} wirken trotzdem.
     */
    public static void pause(ServerPlayer player, int ticks) {
        long until = player.level().getGameTime() + ticks;
        PAUSED_UNTIL.merge(player.getUUID(), until, Math::max);
    }

    /** Die Uhr läuft für {@code ticks} höchstens um {@code factor} (0..1) langsamer; die stärkste Bremse gilt. */
    public static void slowDown(ServerPlayer player, double factor, int ticks) {
        long now = player.level().getGameTime();
        BRAKES.merge(player.getUUID(), new Factor(factor, now + ticks), (old, n) -> {
            if (old.until <= now || n.factor < old.factor) return n;
            if (n.factor > old.factor) return old;
            return new Factor(n.factor, Math.max(old.until, n.until));
        });
    }

    private static double brake(ServerPlayer player) {
        Factor f = BRAKES.get(player.getUUID());
        if (f == null) return 1.0;
        if (f.until <= player.level().getGameTime()) {
            BRAKES.remove(player.getUUID());
            return 1.0;
        }
        return f.factor;
    }

    /** Die Ruhe-Meldung schweigt {@code ticks} lang, damit eine andere Meldung in der Statusleiste lesbar bleibt. */
    public static void holdMessage(ServerPlayer player, int ticks) {
        MESSAGE_HOLD.put(player.getUUID(), player.level().getGameTime() + ticks);
    }

    public static boolean messageHeld(ServerPlayer player) {
        Long until = MESSAGE_HOLD.get(player.getUUID());
        if (until == null) return false;
        if (until <= player.level().getGameTime()) {
            MESSAGE_HOLD.remove(player.getUUID());
            return false;
        }
        return true;
    }

    /** Ruht die Uhr des Spielers gerade? */
    public static boolean isPaused(ServerPlayer player) {
        Long until = PAUSED_UNTIL.get(player.getUUID());
        if (until == null) return false;
        if (until <= player.level().getGameTime()) {
            PAUSED_UNTIL.remove(player.getUUID());
            return false;
        }
        return true;
    }

    /**
     * Weitere Orte, an denen ein Zeitanker zählt (z. B. ein Curios-Slot). Strata Bridge trägt sich hier ein,
     * Strata Epochs selbst kennt keine Fremd-Mods.
     */
    public static final java.util.List<java.util.function.Predicate<ServerPlayer>> ANCHOR_SOURCES =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Trägt der Spieler einen Zeitanker (Inventar oder ein angemeldeter Ort wie ein Curios-Slot)? */
    public static boolean hasTimeAnchor(ServerPlayer player) {
        if (player.getInventory().contains(EpochsContent.TIME_ANCHOR.get().getDefaultInstance())) return true;
        for (var source : ANCHOR_SOURCES) if (source.test(player)) return true;
        return false;
    }

    /** Zusatzzeit als Anteil: Zeitanker +100 %, analysierter Bohrkern beim Betreten +25 %, vollständiges Museum +10 %. */
    public static double timeBonus(ServerPlayer player) {
        double anchor = hasTimeAnchor(player) ? 1.0 : 0.0;
        double museum = StrataAPI.hasNode(player, StrataEpochs.id("museum")) ? DisplayCases.MUSEUM_BONUS : 0.0;
        return anchor + museum + player.getData(EpochsContent.VISIT.get()).bonus();
    }

    /** Stand der Uhr: verbleibender Anteil der erlaubten Zeit (0..1) und Phase. */
    public record Clock(double remaining, SolidificationClock.Stage stage) {}

    /** Die Uhr des Spielers in seiner aktuellen Epoche; leer außerhalb einer Epoche. */
    public static Optional<Clock> clock(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Optional<String> epochId = EpochTravel.epochOf(level);
        if (epochId.isEmpty()) return Optional.empty();
        Epoch epoch = StrataAPI.epochs(level.getServer()).get(epochId.get());
        if (epoch == null || epoch.solidifySeconds() <= 0) return Optional.empty();
        long ticks = player.getData(EpochsContent.VISIT.get()).ticks();
        double bonus = timeBonus(player);
        long allowed = SolidificationClock.allowedTicks(epoch.solidifySeconds() * 20L, bonus);
        double remaining = allowed <= 0 ? 0 : Math.max(0, Math.min(1, 1 - ticks / (double) allowed));
        return Optional.of(new Clock(remaining, SolidificationClock.stage(ticks, allowed)));
    }

    /** Aktueller Zeitfaktor des Spielers (1 = normal): Sporenwolken beschleunigen, Geodenrüstung, Spiralit und
     * ein Resonanzling mit Datapack-Faktor bremsen (die stärkste Bremse gilt); in der Ruhe 0. */
    public static double factor(ServerPlayer player) {
        if (isPaused(player)) return 0.0;
        double armor = EpochsGear.hasFullGeodeSet(player) ? EpochsGear.GEODE_SET_FACTOR : 1.0;
        // Rüstung und Spiralit stapeln sich nicht: Es gilt die stärkere Bremse.
        armor = Math.min(armor, Math.min(dev.strata.epochs.mob.Spiralit.factorFor(player), brake(player)));
        Factor f = FACTORS.get(player.getUUID());
        if (f == null) return armor;
        if (f.until <= player.level().getGameTime()) {
            FACTORS.remove(player.getUUID());
            return armor;
        }
        return f.factor * armor;
    }

    /**
     * Zieht erlaubte Zeit ab: Die Uhr springt um {@code ticks} vor. Negative Werte drehen sie zurück
     * (nie unter 0), z. B. für die Keimfrische. Nur in einer Epoche wirksam.
     */
    public static void advance(ServerPlayer player, long ticks) {
        if (EpochTravel.epochOf((ServerLevel) player.level()).isEmpty()) return;
        EpochVisit visit = player.getData(EpochsContent.VISIT.get());
        player.setData(EpochsContent.VISIT.get(), visit.withTicks(Math.max(0, visit.ticks() + ticks)));
    }

    /** Verwandelt einen zufälligen festen Block in der Nähe in versteinerten Stein. */
    static void petrifyNear(ServerLevel level, BlockPos center) {
        var random = level.getRandom();
        for (int attempt = 0; attempt < 8; attempt++) {
            BlockPos pos = center.offset(
                    random.nextInt(PETRIFY_RADIUS * 2 + 1) - PETRIFY_RADIUS,
                    random.nextInt(PETRIFY_RADIUS * 2 + 1) - PETRIFY_RADIUS,
                    random.nextInt(PETRIFY_RADIUS * 2 + 1) - PETRIFY_RADIUS);
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getDestroySpeed(level, pos) < 0 || level.getBlockEntity(pos) != null
                    || state.is(EpochsContent.EPOCH_GATE.get()) || state.is(EpochsContent.PETRIFIED_STONE.get())
                    || !level.getFluidState(pos).isEmpty()) {
                continue;
            }
            level.setBlockAndUpdate(pos, EpochsContent.PETRIFIED_STONE.get().defaultBlockState());
            return;
        }
    }
}
