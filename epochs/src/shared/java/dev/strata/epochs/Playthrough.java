package dev.strata.epochs;

import dev.strata.core.api.StrataAPI;
import dev.strata.core.mc.Mc;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Durchspieltest: Ein simulierter Spieler geht den Kern-Ablauf über echte Server-Ticks so durch, wie ein Mensch klickt.
 * Rechtsklicks laufen über {@code ServerPlayerGameMode.useItemOn}, Abbau über {@code destroyBlock}, also über dieselben
 * Wege und Ereignisse wie im Spiel. Schritte: Bohrkern ziehen → analysieren → durchs Tor → Verfestigung → Fossilbrocken
 * abbauen → präparieren → Kodex → zurück durchs Tor → transplantieren (bis fertig) → auf dem neuen Boden anbauen.
 *
 * <p>Was sonst die Netzwerkverbindung oder {@code ServerPlayer.tick} erledigt (Teleport-Bestätigung, Abklingzeiten),
 * übernimmt der Test für den simulierten Spieler selbst; alles andere läuft über den Code, den echte Spieler auslösen.
 *
 * <p>Nur mit {@code -Dstrata.selftest=true}. Ergebnis im Log: {@code [Strata-Durchspiel] … -> OK} bzw. {@code FEHLER}.
 */
final class Playthrough {

    private static final String EPOCH = StrataEpochs.id("crystal_age");
    private static final int TIMEOUT_TICKS = 4000;
    /** Pause zwischen Meißelschlägen (über der Ruhezeit der Präparation: nur 3 % Bruchrisiko). */
    private static final int CHISEL_PAUSE = 25;
    private static final int MAX_CHUNK_TRIES = 6;

    private final MinecraftServer server;
    private final ServerLevel overworld;
    private ServerLevel epoch;
    private ServerPlayer player;
    private BlockPos site;
    private BlockPos arrival;
    private BlockPos transplanter;
    private final Map<String, Boolean> results = new LinkedHashMap<>();
    private final List<String> notes = new ArrayList<>();
    private int step;
    private int wait;
    private int ticks;
    private int tries;
    private int chunks;
    private boolean finished;

    private Playthrough(MinecraftServer server) {
        this.server = server;
        this.overworld = server.overworld();
    }

    /** Startet den Durchspieltest; er meldet sich nach getaner Arbeit im Log ab. */
    static void start(MinecraftServer server) {
        Playthrough run = new Playthrough(server);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> run.tick());
    }

    private void tick() {
        if (finished) return;
        ticks++;
        if (ticks > TIMEOUT_TICKS) {
            fail("Zeit überschritten in Schritt " + step);
            return;
        }
        if (player != null) player.getCooldowns().tick();   // macht sonst ServerPlayer.tick, das ein FakePlayer auslässt
        if (wait > 0) {
            wait--;
            return;
        }
        try {
            runStep();
        } catch (RuntimeException e) {
            StrataEpochs.LOG.error("[Strata-Durchspiel] Ausnahme in Schritt {}", step, e);
            fail("Ausnahme in Schritt " + step + ": " + e);
        }
    }

    private void next(int pause) {
        step++;
        wait = pause;
    }

    private void check(String name, boolean ok, String why) {
        results.put(name, ok);
        if (!ok) notes.add(name + ": " + why);
    }

    private void fail(String why) {
        notes.add(why);
        finish();
    }

    private void finish() {
        if (finished) return;
        finished = true;
        // Der simulierte Spieler darf nach Reisen nicht in einer Spielerliste hängen bleiben.
        if (player != null && player.level() instanceof ServerLevel level && level.players().contains(player)) {
            level.removePlayerImmediately(player, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }
        boolean ok = notes.isEmpty() && !results.isEmpty() && results.values().stream().allMatch(b -> b);
        StringBuilder line = new StringBuilder();
        results.forEach((k, v) -> line.append(line.length() == 0 ? "" : ", ").append(k).append('=').append(v));
        StrataEpochs.LOG.info("[Strata-Durchspiel] {} in {} Ticks{} -> {}", line, ticks,
                notes.isEmpty() ? "" : " (" + String.join("; ", notes) + ")", ok ? "OK" : "FEHLER");
    }

    // ------------------------------------------------------------------ Hilfen wie ein Spieler

    /** Rechtsklick auf die Oberseite eines Blocks mit dem Gegenstand in der Haupthand. */
    private void click(BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
        player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    /** Gegenstand in die Haupthand nehmen; was dort war, wandert zurück ins Inventar. */
    private void hold(ItemStack stack) {
        ItemStack old = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        if (!old.isEmpty() && !old.is(stack.getItem())) player.getInventory().add(old);
    }

    private ItemStack take(net.minecraft.world.item.Item item) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) {
                inv.setItem(i, ItemStack.EMPTY);
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    private int count(net.minecraft.world.item.Item item) {
        var inv = player.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) n += inv.getItem(i).getCount();
        return n;
    }

    private boolean node(String name) {
        return StrataAPI.hasNode(player, StrataEpochs.id(name));
    }

    private void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlockAndUpdate(pos, state);
    }

    // ------------------------------------------------------------------ Ablauf

    private void runStep() {
        switch (step) {
            case 0 -> setup();
            case 1 -> drill(true);
            case 2 -> drill(false);
            case 3 -> analyze();
            case 4 -> enterGate();
            case 5 -> solidification();
            case 6 -> mineChunk();
            case 7 -> prepare();
            case 8 -> leaveFirstClick();
            case 9 -> leaveSecondClick();
            case 10 -> startTransplant();
            case 11 -> awaitTransplant();
            case 12 -> plant();
            default -> finish();
        }
    }

    /** Ort in der Oberwelt suchen, unter dem die Kristallzeit liegt, und eine kleine Steinbühne bauen. */
    private void setup() {
        long seed = Mc.seed(overworld);
        BlockPos found = null;
        for (int r = 0; r < 64 && found == null; r++) {
            int cx = 1500 + r * 8, cz = -1500;
            if (StrataAPI.epochAt(server, seed, cx, cz).filter(EPOCH::equals).isPresent()) found = new BlockPos(cx * 16 + 8, 0, cz * 16 + 8);
        }
        if (found == null) {
            fail("kein Ort mit Kristallzeit gefunden");
            return;
        }
        overworld.getChunk(found);
        int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, found.getX(), found.getZ());
        site = new BlockPos(found.getX(), y, found.getZ());
        for (BlockPos p : BlockPos.betweenClosed(site.offset(-4, 0, -4), site.offset(4, 4, 4))) set(overworld, p, Blocks.AIR.defaultBlockState());
        for (BlockPos p : BlockPos.betweenClosed(site.offset(-4, -1, -4), site.offset(4, -1, 4))) set(overworld, p, Blocks.STONE.defaultBlockState());
        player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(overworld,
                new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("strata_durchspiel".getBytes()), "strata_durchspiel"));
        // Ein FakePlayer hat keinen Netzwerkkanal; für den Dimensionswechsel braucht er einen (Pakete verwirft er ohnehin).
        if (player.connection.getConnection().channel() == null) new io.netty.channel.embedded.EmbeddedChannel(player.connection.getConnection());
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.setPos(site.getX() + 0.5, site.getY(), site.getZ() + 0.5);
        hold(new ItemStack(EpochsContent.CORE_DRILL.get()));
        check("Ort", true, "");
        next(1);
    }

    /** Kernbohrer auf den Boden: ein Bohrkern der Kristallzeit (zweimal, wegen der Abklingzeit mit Pause). */
    private void drill(boolean first) {
        click(site.below());
        int samples = count(EpochsContent.CORE_SAMPLE.get());
        if (first) {
            check("Bohrkern", samples == 1 && node("first_core"), "Kerne=" + samples);
            next(EpochInteractions.DRILL_COOLDOWN_TICKS + 5);
        } else {
            check("zweiter Bohrkern", samples == 2, "Kerne=" + samples);
            next(1);
        }
    }

    /** Kernanalyse-Tisch: Rechtsklick mit dem Stapel. */
    private void analyze() {
        BlockPos table = site.offset(2, 0, 0);
        set(overworld, table, EpochsContent.ANALYSIS_TABLE.get().defaultBlockState());
        hold(take(EpochsContent.CORE_SAMPLE.get()));
        click(table);
        ItemStack held = player.getMainHandItem();
        check("Analyse", CoreSamples.isAnalyzed(held) && held.getCount() == 2, "analysiert=" + CoreSamples.isAnalyzed(held));
        next(5);
    }

    /** Epochentor: Rechtsklick mit dem Bohrkern bringt ihn in die Kristallzeit. */
    private void enterGate() {
        BlockPos gate = site.offset(-2, 0, 0);
        set(overworld, gate, EpochsContent.EPOCH_GATE.get().defaultBlockState());
        click(gate);
        Optional<String> dim = StrataAPI.epochs(server).get(EPOCH).dimension();
        epoch = dim.map(d -> Mc.level(server, d)).orElse(null);
        boolean there = epoch != null && player.level() == epoch;
        EpochVisit visit = player.getData(EpochsContent.VISIT.get());
        check("Epoche", there && node("visited_crystal_age") && Math.abs(visit.bonus() - CoreSamples.ANALYSIS_BONUS) < 1e-6
                && count(EpochsContent.CORE_SAMPLE.get()) == 1,
                "Dimension=" + Mc.levelId(player.level()) + ", Bonus=" + visit.bonus());
        if (!there) {
            fail("nicht in der Epoche angekommen");
            return;
        }
        // Die Zielposition setzt sonst die Netzwerkverbindung (Teleport-Bestätigung des Clients); beim simulierten
        // Spieler übernehmen wir das: Ankunftsplatz ist zwei Blöcke westlich des neuen Rückweg-Tors.
        BlockPos gate2 = null;
        for (int dx = 2; dx <= 10 && gate2 == null; dx++) {   // der Platz kann bis zu 8 Blöcke östlich liegen
            for (int y = Mc.minY(epoch); y < Mc.minY(epoch) + epoch.getHeight() && gate2 == null; y++) {
                BlockPos p = new BlockPos(player.getBlockX() + dx, y, player.getBlockZ());
                if (epoch.getBlockState(p).is(EpochsContent.EPOCH_GATE.get())) gate2 = p;
            }
        }
        if (gate2 == null) {
            fail("kein Rückweg-Tor in der Epoche aufgestellt");
            return;
        }
        arrival = gate2.west(2);
        player.setPos(arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5);
        next(5);
    }

    /** Die Uhr läuft: kurz vor Schluss Warnung, danach Verfestigung mit Langsamkeit. Dann zurück auf Anfang. */
    private void solidification() {
        boolean stable = Solidification.clock(player).map(c -> c.stage() == dev.strata.common.SolidificationClock.Stage.STABLE).orElse(false);
        long allowed = dev.strata.common.SolidificationClock.allowedTicks(
                StrataAPI.epochs(server).get(EPOCH).solidifySeconds() * 20L, Solidification.timeBonus(player));
        tickClock(Math.round(allowed * 0.8));
        boolean warning = Solidification.clock(player).map(c -> c.stage() == dev.strata.common.SolidificationClock.Stage.WARNING).orElse(false);
        tickClock(allowed + 40);
        boolean solid = player.hasEffect(Mc.slowness());
        check("Verfestigung", stable && warning && solid, "stabil=" + stable + ", Warnung=" + warning + ", Langsamkeit=" + solid);
        player.setData(EpochsContent.VISIT.get(), player.getData(EpochsContent.VISIT.get()).withTicks(0));
        player.removeAllEffects();
        hold(new ItemStack(Items.IRON_PICKAXE));
        next(1);
    }

    /** Uhr auf {@code ticks} stellen und einen Prüftakt der Verfestigung laufen lassen. */
    private void tickClock(long ticks) {
        player.setData(EpochsContent.VISIT.get(), player.getData(EpochsContent.VISIT.get()).withTicks(ticks));
        player.tickCount = 0;
        Solidification.onPlayerTick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
    }

    /** Versteinerten Stein abbauen, bis ein Fossilbrocken fällt (15 %; ein Versuch pro Tick). */
    private void mineChunk() {
        BlockPos rock = arrival.offset(0, 0, 2);
        set(epoch, rock.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        set(epoch, rock, EpochsContent.PETRIFIED_STONE.get().defaultBlockState());
        if (player.getMainHandItem().isEmpty()) hold(new ItemStack(Items.IRON_PICKAXE));
        boolean broken = player.gameMode.destroyBlock(rock);
        ItemStack chunk = ItemStack.EMPTY;
        for (ItemEntity drop : epoch.getEntitiesOfClass(ItemEntity.class, new AABB(rock).inflate(2))) {
            if (drop.getItem().is(EpochsContent.FOSSIL_CHUNK.get()) && chunk.isEmpty()) chunk = drop.getItem().copy();
            drop.discard();
        }
        tries++;
        if (!chunk.isEmpty()) {
            boolean right = EPOCH.equals(chunk.get(EpochsContent.SAMPLE_EPOCH.get()));
            check("Fossilbrocken", broken && right, "Epoche im Brocken=" + chunk.get(EpochsContent.SAMPLE_EPOCH.get())
                    + ", abgebaut=" + broken + ", Brocken=" + chunk.getComponents() + ", Versuch " + tries);
            player.setItemInHand(InteractionHand.OFF_HAND, chunk.copyWithCount(1));
            hold(new ItemStack(EpochsContent.CHISEL.get()));
            set(epoch, arrival.offset(2, 0, 2), EpochsContent.FOSSIL_TABLE.get().defaultBlockState());
            chunks++;
            tries = 0;
            next(5);
        } else if (tries > 150) {
            fail("kein Fossilbrocken nach 150 Steinen");
        }
    }

    /** Meißel in der Haupthand, Brocken in der zweiten: fünf ruhige Schläge auf den Präparationstisch. */
    private void prepare() {
        click(arrival.offset(2, 0, 2));
        net.minecraft.world.item.Item fossil = FossilPrep.fossilFor(EPOCH);
        if (count(fossil) > 0 || player.getOffhandItem().is(fossil)) {
            check("Präparation", true, "");
            check("Kodex", node("fossil_crystal_age") && node("mastered_crystal_age"), "fossil/mastered fehlt");
            next(5);
        } else if (!player.getOffhandItem().is(EpochsContent.FOSSIL_CHUNK.get())) {
            // Gebrochen (3 % je ruhigem Schlag): neuen Brocken holen.
            if (chunks >= MAX_CHUNK_TRIES) {
                fail("alle Brocken gebrochen");
                return;
            }
            StrataEpochs.LOG.info("[Strata-Durchspiel] Brocken beim Präparieren gebrochen, neuer Versuch");
            step = 6;
            hold(new ItemStack(Items.IRON_PICKAXE));
            wait = 1;
        } else {
            wait = CHISEL_PAUSE;
        }
    }

    /** Rückweg: zweimal auf das Tor neben dem Ankunftsplatz, mit Pause dazwischen. */
    private BlockPos gateNear() {
        for (BlockPos p : BlockPos.betweenClosed(arrival.offset(-4, -2, -4), arrival.offset(4, 3, 4))) {
            if (epoch.getBlockState(p).is(EpochsContent.EPOCH_GATE.get())) return p.immutable();
        }
        return null;
    }

    private void leaveFirstClick() {
        BlockPos gate = gateNear();
        if (gate == null) {
            fail("kein Tor am Ankunftsplatz " + arrival.toShortString() + " (dort " + epoch.getBlockState(arrival.east(2))
                    + ", Spieler bei " + player.blockPosition().toShortString() + ")");
            return;
        }
        hold(ItemStack.EMPTY);
        click(gate);
        next(20);
    }

    private void leaveSecondClick() {
        EpochVisit visit = player.getData(EpochsContent.VISIT.get());
        click(gateNear());
        player.setPos(visit.x(), visit.y(), visit.z());   // wie oben: sonst Aufgabe der Netzwerkverbindung
        boolean home = player.level() == overworld;
        boolean reset = player.getData(EpochsContent.VISIT.get()).ticks() == 0;
        check("Rückweg", home && reset, "Dimension=" + Mc.levelId(player.level()));
        if (!home) {
            fail("nicht zurückgekehrt");
            return;
        }
        next(5);
    }

    /** Transplantator: Rechtsklick mit dem zweiten Bohrkern; das Fossil im Inventar wird verbraucht. */
    private void startTransplant() {
        transplanter = site.offset(0, 0, 3);
        set(overworld, transplanter, EpochsContent.TRANSPLANTER.get().defaultBlockState());
        if (player.getOffhandItem().is(FossilPrep.fossilFor(EPOCH))) {
            ItemStack fossil = player.getOffhandItem().copy();
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.getInventory().add(fossil);
        }
        int fossils = count(FossilPrep.fossilFor(EPOCH));
        hold(take(EpochsContent.CORE_SAMPLE.get()));
        click(transplanter);
        boolean started = Transplanter.busyNear(overworld, transplanter);
        check("Transplantation gestartet", started && count(FossilPrep.fossilFor(EPOCH)) == fossils - 1,
                "läuft=" + started + ", Fossilien vorher=" + fossils);
        if (!started) {
            fail("Transplantation startete nicht");
            return;
        }
        next(20);
    }

    private void awaitTransplant() {
        if (Transplanter.busyNear(overworld, transplanter)) {
            wait = 20;
            return;
        }
        BlockPos probe = transplanter.offset(2, -1, 1);
        String biome = overworld.getBiome(probe.above()).getRegisteredName();
        boolean ground = overworld.getBlockState(probe).is(Blocks.CALCITE);
        check("Transplantation fertig", biome.equals(StrataEpochs.id("crystal_hollows")) && ground,
                "Biom=" + biome + ", Boden=" + overworld.getBlockState(probe));
        next(1);
    }

    /** Auf dem verwandelten Boden eine Quarzdistel säen (wächst nur in einem Biom der Epoche). */
    private void plant() {
        BlockPos soil = transplanter.offset(2, -1, 1);
        set(overworld, soil.above(), Blocks.AIR.defaultBlockState());
        hold(new ItemStack(EpochsContent.QUARTZ_THISTLE_SEEDS.get()));
        click(soil);
        boolean planted = overworld.getBlockState(soil.above()).is(EpochsContent.QUARTZ_THISTLE.get());
        check("Anbau", planted, "Block=" + overworld.getBlockState(soil.above()));
        next(1);
    }
}
