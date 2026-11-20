package dev.strata.epochs;

import com.mojang.authlib.GameProfile;
import dev.strata.core.api.StrataAPI;
import dev.strata.core.mc.Mc;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Leistungsmessung ({@code -Dstrata.perf=true}, siehe {@code scripts/perf.sh}): Wie lange braucht ein Server-Tick, wenn
 * Strata-Inhalte laufen? Gemessen wird die Dauer jedes Ticks (Pre bis Post) im Schnelllauf, in vier Szenen in einer
 * abgeschlossenen Arena in der Kristallzeit, mit einem Spieler in der Mitte (den die Mobs angreifen dürfen):
 * <ol>
 *   <li>leer (Grundlast des Servers),</li>
 *   <li>80 Zombies (Vanilla-Vergleich),</li>
 *   <li>80 Strata-Mobs, 10 von jeder Art außer dem Boss,</li>
 *   <li>zwei Transplantationen in der Oberwelt, die immer wieder neu starten.</li>
 * </ol>
 * Danach jede Strata-Art einzeln (40 Tiere), damit ein teurer Mob im Mittel nicht untergeht.
 */
final class Perf {

    private static final int WARMUP = 300;
    private static final int MEASURE = 2400;
    private static final String TAG = "strata_perf";
    private static final String[] STRATA_MOBS = {"geode_crawler", "spore_bellows", "petrified_witness", "ember_burrower",
            "resin_snail", "amber_moth", "resonant_shard", "spiralit"};
    private static final List<String> SCENES = new ArrayList<>(List.of("leer", "80 Zombies", "80 Strata-Mobs", "2 Transplantationen"));
    static {
        for (String m : STRATA_MOBS) SCENES.add("40 " + m);   // jede Art einzeln, damit ein teurer Mob auffällt
    }

    private final MinecraftServer server;
    private final ServerLevel level;
    private final BlockPos center;
    private dev.strata.epochs.mc.Combatant player;
    private int scene = -1;
    private int tick;
    private long started;
    private final List<Long> times = new ArrayList<>();
    private final List<String> results = new ArrayList<>();
    private final List<Double> means = new ArrayList<>();
    private boolean done;

    private Perf(MinecraftServer server, ServerLevel level) {
        this.server = server;
        this.level = level;
        this.center = new BlockPos(9000, Math.max(Mc.minY(level) + 20, 40), 9000);
    }

    static void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        if (!Boolean.getBoolean("strata.perf")) return;
        MinecraftServer server = event.getServer();
        ServerLevel level = Mc.level(server, StrataEpochs.id("crystal_age"));
        if (level == null) return;
        Perf run = new Perf(server, level);
        run.build();
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre e) -> run.started = System.nanoTime());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> run.tick());
        Transplanter.command(server.overworld(), "tick sprint " + (SCENES.size() * (WARMUP + MEASURE) + 2000));
    }

    /** Arena 33 × 33, 6 hoch, aus Zeitgestein; Spieler in der Mitte. */
    private void build() {
        Transplanter.command(level, "forceload add " + (center.getX() - 32) + " " + (center.getZ() - 32) + " "
                + (center.getX() + 32) + " " + (center.getZ() + 32));
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-17, -1, -17), center.offset(17, 6, 17))) {
            boolean shell = Math.abs(p.getX() - center.getX()) == 17 || Math.abs(p.getZ() - center.getZ()) == 17
                    || p.getY() == center.getY() - 1 || p.getY() == center.getY() + 6;
            level.setBlock(p, shell ? EpochsContent.CHRONOSTONE.get().defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
        level.setBlock(center.offset(0, 5, 0), Blocks.GLOWSTONE.defaultBlockState(), 2);   // Licht für Motten
        player = new dev.strata.epochs.mc.Combatant(level, new GameProfile(java.util.UUID.nameUUIDFromBytes(TAG.getBytes()), TAG));
        if (player.connection.getConnection().channel() == null) new io.netty.channel.embedded.EmbeddedChannel(player.connection.getConnection());
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.setPos(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
        level.addNewPlayer(player);
        player.dropSpawnProtection();
    }

    private void tick() {
        if (done) return;
        long took = System.nanoTime() - started;
        player.tickCombat();
        player.setHealth(player.getMaxHealth());   // Angriffe zählen als Last, der Spieler bleibt am Leben
        tick++;
        if (scene < 0) {                            // Selbsttest und Durchspieltest abwarten
            if (tick >= 1200) nextScene();
            return;
        }
        if (tick > WARMUP) times.add(took);
        if (scene == 3 && tick % 20 == 0 && !Transplanter.busyNear(server.overworld(), transplantSite(0))) startTransplants();
        if (tick >= WARMUP + MEASURE) {
            report();
            nextScene();
        }
    }

    private void nextScene() {
        // Arena direkt räumen (ein kill-Befehl trifft frisch gerufene Mobs je nach Version nicht alle).
        for (Entity e : level.getAllEntities()) {
            if (e instanceof net.minecraft.world.entity.Mob && inArena(e)) e.discard();
        }
        scene++;
        tick = 0;
        times.clear();
        if (scene >= SCENES.size()) {
            finish();
            return;
        }
        switch (scene) {
            case 0 -> { }
            case 1 -> { for (int i = 0; i < 80; i++) summon("minecraft:zombie", i); }
            case 2 -> { for (int i = 0; i < 80; i++) summon("strata_epochs:" + STRATA_MOBS[i % STRATA_MOBS.length], i); }
            case 3 -> startTransplants();
            default -> { for (int i = 0; i < 40; i++) summon("strata_epochs:" + STRATA_MOBS[scene - 4], i * 2); }
        }
    }

    private boolean inArena(Entity e) {
        return Math.abs(e.getX() - center.getX()) < 17 && Math.abs(e.getZ() - center.getZ()) < 17;
    }

    private void summon(String type, int i) {
        int x = center.getX() - 14 + (i % 10) * 3;
        int z = center.getZ() - 14 + (i / 10) * 3;
        Transplanter.command(level, "summon " + type + " " + x + " " + (center.getY() + 1) + " " + z
                + " {PersistenceRequired:1b,Tags:[\"" + TAG + "\"]}");
    }

    private BlockPos transplantSite(int i) {
        return new BlockPos(9000 + i * 80, 0, -9000);
    }

    /** Zwei Transplantationen (Kristallzeit) in der Oberwelt, mit dem Radius aus der Konfiguration. */
    private void startTransplants() {
        ServerLevel overworld = server.overworld();
        var landscape = StrataAPI.epochs(server).get(StrataEpochs.id("crystal_age")).landscape().orElseThrow();
        for (int i = 0; i < 2; i++) {
            BlockPos site = transplantSite(i);
            overworld.getChunk(site);
            int y = overworld.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, site.getX(), site.getZ());
            Transplanter.start(overworld, new BlockPos(site.getX(), y, site.getZ()), StrataEpochs.id("crystal_age"), landscape,
                    EpochsConfig.TRANSPLANT_RADIUS.get(), null);
        }
    }

    private void report() {
        List<Long> sorted = new ArrayList<>(times);
        sorted.sort(null);
        double mean = sorted.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6;
        double p95 = sorted.isEmpty() ? 0 : sorted.get((int) (sorted.size() * 0.95)) / 1e6;
        long count = 0;
        // Mobs in der Arena zählen (nicht über Tags: der Getter heißt ab 26.3 anders)
        for (Entity e : level.getAllEntities()) {
            if (e instanceof net.minecraft.world.entity.Mob && inArena(e)) count++;
        }
        means.add(mean);
        double perMob = count == 0 ? 0 : (mean - means.get(0)) / count;
        String extra = scene == 0 ? "" : String.format(" (+%.2f ms gegenüber leer%s)", mean - means.get(0),
                count == 0 ? "" : String.format(", %.4f ms je Mob", perMob));
        results.add(String.format("%-20s: %.2f ms je Tick, 95 %% unter %.2f ms, %d Mobs%s", SCENES.get(scene), mean, p95, count, extra));
        StrataEpochs.LOG.info("[Strata-Leistung] {}", results.get(results.size() - 1));
    }

    private void finish() {
        done = true;
        if (means.size() >= 4) {
            StrataEpochs.LOG.info("[Strata-Leistung] je Mob: Zombie {} ms, Strata-Mob {} ms (Mittel über alle 8 Arten); "
                            + "ein Tick hat 50 ms",
                    String.format("%.4f", (means.get(1) - means.get(0)) / 80), String.format("%.4f", (means.get(2) - means.get(0)) / 80));
        }
        StrataEpochs.LOG.info("[Strata-Leistung] Fertig");
        Transplanter.command(server.overworld(), "tick sprint stop");
        level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        server.halt(false);
    }
}
