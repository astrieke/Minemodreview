package dev.strata.epochs;

import dev.strata.core.Epoch;
import dev.strata.core.api.StrataAPI;
import dev.strata.core.mc.Mc;
import dev.strata.core.MobTuning;
import dev.strata.epochs.mob.GeodeCrawler;
import dev.strata.epochs.mob.PetrifiedWitness;
import dev.strata.epochs.mob.SporeBellows;
import dev.strata.epochs.mob.MobAnimation;
import dev.strata.epochs.mob.MobModelData;
import dev.strata.epochs.mob.StrataMob;
import dev.strata.epochs.mob.StrataMobDef;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Selbsttest für Entwicklungsläufe ({@code -Dstrata.selftest=true}, im Gradle-Server-Run aktiv).
 *
 * <p>Generiert in jeder Epochen-Dimension einen Chunk, zählt Grundblock und Erze,
 * sucht einen Ankunftsplatz und prüft Bohrkerne. Das Ergebnis steht im Log und wird
 * von {@code scripts/smoke-test.sh} ausgewertet.
 */
final class SelfTest {

    private SelfTest() {}

    static void onServerStarted(ServerStartedEvent event) {
        if (!Boolean.getBoolean("strata.selftest")) return;
        MinecraftServer server = event.getServer();
        int failures = 0;

        String sampleEpoch = StrataEpochs.id("crystal_age");
        if (!CoreSamples.epochOf(CoreSamples.create(sampleEpoch)).orElse("").equals(sampleEpoch)) {
            StrataEpochs.LOG.error("[Strata-Test] Bohrkern speichert die Epoche nicht");
            failures++;
        }

        failures += testFossils();
        failures += testAnalysis(server);
        failures += testPreparation(server);
        failures += testTransplant(server);
        failures += testBurrower(server);
        for (String epoch : List.of("crystal_age", "spore_age", "ash_age")) failures += testGrowth(server, epoch);

        for (Map.Entry<String, Epoch> entry : StrataAPI.epochs(server).entrySet()) {
            if (entry.getValue().dimension().isEmpty()) continue;
            String dim = entry.getValue().dimension().get();
            ServerLevel level = Mc.level(server, dim);
            if (level == null) {
                StrataEpochs.LOG.error("[Strata-Test] {}: Dimension {} fehlt", entry.getKey(), dim);
                failures++;
                continue;
            }
            ChunkAccess chunk = level.getChunk(0, 0);
            int base = count(level, chunk, EpochsContent.CHRONOSTONE.get());
            int ores = count(level, chunk, EpochsContent.CRYSTAL_QUARTZ_ORE.get())
                    + count(level, chunk, EpochsContent.AMBER_ORE.get())
                    + count(level, chunk, EpochsContent.EMBER_ORE.get());
            BlockPos arrival = EpochTravel.prepareArrival(level, 8, 8);
            boolean gate = level.getBlockState(arrival.east(2)).is(EpochsContent.EPOCH_GATE.get());
            boolean ok = base > 1000 && ores > 0 && gate;
            if (!ok) failures++;
            StrataEpochs.LOG.info("[Strata-Test] {}: Zeitgestein={}, Erze={}, Ankunft={}, Tor={} -> {}",
                    entry.getKey(), base, ores, arrival.toShortString(), gate, ok ? "OK" : "FEHLER");
        }
        failures += testMob(server, GeodeCrawler.DEF, l -> new GeodeCrawler(EpochsMobs.GEODE_CRAWLER.get(), l), "crystal_age");
        failures += testMob(server, SporeBellows.DEF, l -> new SporeBellows(EpochsMobs.SPORE_BELLOWS.get(), l), "spore_age");
        failures += testMob(server, PetrifiedWitness.DEF, l -> new PetrifiedWitness(EpochsMobs.PETRIFIED_WITNESS.get(), l), "crystal_age");
        failures += testMob(server, dev.strata.epochs.mob.EmberBurrower.DEF,
                l -> new dev.strata.epochs.mob.EmberBurrower(EpochsMobs.EMBER_BURROWER.get(), l), "ash_age");
        failures += testMob(server, dev.strata.epochs.mob.Spiralit.DEF,
                l -> new dev.strata.epochs.mob.Spiralit(EpochsMobs.SPIRALIT.get(), l), "ash_age");
        StrataEpochs.LOG.info("[Strata-Test] Fertig, {} Fehler", failures);
        persistTest(server);
    }

    /** Prüft einen Mob: Modell und Animationen, Loot, Spawn-Modifier, Datapack-Werte und ein echtes Exemplar. */
    private static int testMob(MinecraftServer server, StrataMobDef def,
                               java.util.function.Function<ServerLevel, StrataMob> factory, String epochName) {
        // Der Steinzeuge spawnt nicht natürlich, er erwacht aus versteinertem Stein.
        boolean naturalSpawn = !def.name().equals("petrified_witness") && !def.name().equals("spiralit");
        List<String> problems = new ArrayList<>();
        MobModelData model;
        try {
            model = def.model();
        } catch (RuntimeException e) {
            StrataEpochs.LOG.error("[Strata-Test] {}: Modell lässt sich nicht laden: {}", def.name(), e.toString());
            return 1;
        }
        Set<String> bones = new HashSet<>();
        model.bones().forEach(b -> bones.add(b.name()));
        model.cubes().forEach(c -> { if (!bones.contains(c.bone())) problems.add("Würfel " + c.name() + " ohne Knochen"); });
        model.bones().forEach(b -> { if (b.parent() != null && !bones.contains(b.parent())) problems.add("Knochen " + b.name() + " ohne Eltern"); });
        model.animations().values().forEach(a -> a.tracks().keySet().forEach(bone -> {
            if (!bones.contains(bone)) problems.add("Animation " + a.shortName() + " nutzt fehlenden Knochen " + bone);
        }));
        List<String> needed = new ArrayList<>(def.actions());
        if (def.idle() != null) needed.add(def.idle());
        if (def.walk() != null) needed.add(def.walk());
        needed.forEach(n -> { if (model.animation(n) == null) problems.add("Animation " + n + " fehlt"); });
        for (String glow : def.glowLayers()) {
            requireResource(problems, "/assets/" + def.modId() + "/textures/entity/" + def.name() + "_glow_" + glow + ".png");
        }
        requireResource(problems, "/assets/" + def.modId() + "/textures/entity/" + def.name() + ".png");
        requireResource(problems, "/data/" + def.modId() + "/loot_table/entities/" + def.name() + ".json");
        if (naturalSpawn) {
            requireResource(problems, "/data/" + def.modId() + "/neoforge/biome_modifier/" + def.name() + "_spawns.json");
        }

        // Abspieler: Ein Wert mitten in der Idle-Animation muss zwischen den Keyframes liegen.
        MobAnimation idle = def.idle() == null ? null : model.animation(def.idle());
        if (idle != null && !idle.tracks().isEmpty()) {
            String bone = idle.tracks().keySet().iterator().next();
            boolean any = false;
            for (MobAnimation.Channel ch : MobAnimation.Channel.values()) {
                any |= idle.sample(bone, ch, idle.length() / 4) != null;
            }
            if (!any) {
                problems.add("Idle-Animation liefert keine Werte");
            }
        }

        // Ein echtes Exemplar in der Epoche: Datapack-Werte müssen ankommen.
        MobTuning tuning = StrataAPI.mobTuning(server, def.id());
        if (tuning == MobTuning.EMPTY) problems.add("keine Datapack-Werte (strata_core:mob_tuning)");
        String epoch = StrataEpochs.id(epochName);
        ServerLevel level = StrataAPI.epochs(server).get(epoch).dimension().map(d -> Mc.level(server, d)).orElse(null);
        float health = -1;
        if (level != null) {
            StrataMob mob = factory.apply(level);
            if (mob == null) {
                problems.add("Entity lässt sich nicht erzeugen");
            } else {
                BlockPos at = EpochTravel.prepareArrival(level, 8, 8);
                mob.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                level.addFreshEntity(mob);
                mob.tick();
                health = mob.getMaxHealth();
                double expected = tuning.attribute("max_health", health);
                if (Math.abs(health - expected) > 0.01) problems.add("Leben " + health + " statt " + expected);
                mob.playAction(def.actions().get(0));
                if (!def.actions().get(0).equals(mob.currentAction())) problems.add("Aktion wird nicht gesetzt");
                mob.discard();
            }
        }

        problems.forEach(p -> StrataEpochs.LOG.error("[Strata-Test] {}: {}", def.name(), p));
        StrataEpochs.LOG.info("[Strata-Test] Mob {}: {} Knochen, {} Würfel, {} Animationen, Leben {} -> {}",
                def.name(), model.bones().size(), model.cubes().size(), model.animations().size(), health,
                problems.isEmpty() ? "OK" : "FEHLER");
        return problems.isEmpty() ? 0 : 1;
    }

    /** Fossilbrocken: Epoche im Item, passendes Fossil, Loot-Pools für jede Epoche. */
    private static int testFossils() {
        List<String> problems = new ArrayList<>();
        String spore = StrataEpochs.id("spore_age");
        var chunk = FossilPrep.chunk(spore);
        if (!spore.equals(chunk.get(EpochsContent.SAMPLE_EPOCH.get()))) problems.add("Brocken speichert die Epoche nicht");
        if (FossilPrep.fossilFor(spore) != EpochsContent.FERN_FOSSIL.get()) problems.add("falsches Fossil für die Sporenzeit");
        if (!FossilPrep.epochOfFossil(new net.minecraft.world.item.ItemStack(EpochsContent.ANCIENT_SKULL.get()))
                .orElse("").equals(StrataEpochs.id("ash_age"))) problems.add("Schädel gehört nicht zur Aschezeit");
        for (String block : List.of("chronostone", "petrified_stone")) {
            String path = "/data/" + StrataEpochs.MOD_ID + "/loot_table/blocks/" + block + ".json";
            try (var in = SelfTest.class.getResourceAsStream(path)) {
                String text = in == null ? "" : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                for (String epoch : List.of("crystal_age", "spore_age", "ash_age")) {
                    if (!text.contains("fossil_chunk") || !text.contains(StrataEpochs.id(epoch))) {
                        problems.add(block + ": kein Fossil-Pool für " + epoch);
                    }
                }
            } catch (java.io.IOException e) {
                problems.add(block + ": " + e);
            }
        }
        StrataEpochs.LOG.info("[Strata-Test] Fossilien: {}", problems.isEmpty() ? "OK" : "FEHLER " + problems);
        return problems.isEmpty() ? 0 : 1;
    }

    /** Präparation mit einem simulierten Spieler: fünf ruhige Schläge legen frei, ein Fehlschlag zerbricht. */
    private static int testPreparation(MinecraftServer server) {
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(server.overworld());
        var main = net.minecraft.world.InteractionHand.MAIN_HAND;
        var off = net.minecraft.world.InteractionHand.OFF_HAND;
        BlockPos table = BlockPos.ZERO;
        player.setItemInHand(main, new net.minecraft.world.item.ItemStack(EpochsContent.CHISEL.get()));
        player.setItemInHand(off, FossilPrep.chunk(StrataEpochs.id("ash_age")).copyWithCount(3));
        for (int i = 0; i < FossilPrep.STEPS; i++) {
            FossilPrep.stroke(player, player.getMainHandItem(), table, FossilPrep.CALM_TICKS, () -> 0.99);
        }
        boolean done = player.getOffhandItem().is(EpochsContent.ANCIENT_SKULL.get());
        int restCount = player.getInventory().countItem(EpochsContent.FOSSIL_CHUNK.get());
        boolean rest = restCount == 2;
        boolean worn = player.getMainHandItem().getDamageValue() == FossilPrep.STEPS;

        player.setItemInHand(off, FossilPrep.chunk(StrataEpochs.id("ash_age")));
        FossilPrep.stroke(player, player.getMainHandItem(), table, 0, () -> 0.2);   // hastig: 30 % Risiko
        boolean broken = player.getOffhandItem().isEmpty();
        boolean risks = FossilPrep.risk(0) > 0.29 && FossilPrep.risk(FossilPrep.CALM_TICKS) < 0.04;
        player.getInventory().clearContent();
        boolean ok = done && rest && worn && broken && risks;
        StrataEpochs.LOG.info("[Strata-Test] Präparation: Fossil={}, Rest={}, Meißel={}, Bruch={}, Risiko={} -> {}",
                done, rest, worn, broken, risks, ok ? "OK" : "FEHLER");
        return ok ? 0 : 1;
    }

    /** Transplantation im Testgebiet: Oberfläche, Unterschicht, Biom; ein „Bau“-Block bleibt unberührt. */
    private static int testTransplant(MinecraftServer server) {
        ServerLevel level = server.overworld();
        String epochId = StrataEpochs.id("crystal_age");
        var landscape = StrataAPI.epochs(server).get(epochId).landscape();
        if (landscape.isEmpty()) {
            StrataEpochs.LOG.error("[Strata-Test] Transplantation: Kristallzeit hat keine Landschaft");
            return 1;
        }
        int cx = 4000, cz = 4000;
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) level.getChunk((cx >> 4) + x, (cz >> 4) + z);
        int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz) - 1;
        BlockPos build = new BlockPos(cx + 2, ground, cz);
        level.setBlock(build, net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState(), 2);
        BlockPos table = new BlockPos(cx, ground + 1, cz);
        TransplantJob job = Transplanter.start(level, table, epochId, landscape.get(), 5, null);
        job.runToEnd();
        BlockPos probe = new BlockPos(cx - 2, ground, cz + 1);
        String biome = level.getBiome(probe.above()).getRegisteredName();
        boolean top = level.getBlockState(probe).is(net.minecraft.world.level.block.Blocks.CALCITE);
        boolean under = level.getBlockState(probe.below()).is(net.minecraft.world.level.block.Blocks.SMOOTH_BASALT);
        boolean kept = level.getBlockState(build).is(net.minecraft.world.level.block.Blocks.OAK_PLANKS);
        boolean biomeOk = biome.equals(StrataEpochs.id("crystal_hollows"));
        // Block-Display wie beim Aufsteigen beschwören und über den Tag wieder entfernen (Aufräumen nach Neustart).
        BlockPos d = new BlockPos(cx, ground + 4, cz);
        Transplanter.command(level, "summon minecraft:block_display " + d.getX() + " " + d.getY() + " " + d.getZ()
                + " {Tags:[\"strata_rise\"],block_state:{Name:\"minecraft:calcite\"}}");
        var displays = level.getEntitiesOfClass(net.minecraft.world.entity.Display.BlockDisplay.class,
                new net.minecraft.world.phys.AABB(d).inflate(0.5));
        Transplanter.command(level, "execute positioned " + d.getX() + " " + d.getY() + " " + d.getZ()
                + " run kill @e[type=minecraft:block_display,tag=strata_rise,distance=..3]");
        boolean display = displays.size() == 1 && displays.get(0).isRemoved();
        boolean ok = top && under && kept && biomeOk && display && job.phase() == TransplantJob.Phase.DONE;
        if (!ok) StrataEpochs.LOG.info("[Strata-Test] Transplantation: Boden y={}, Probe {} = {}, darunter {}", ground,
                probe.toShortString(), level.getBlockState(probe), level.getBlockState(probe.below()));
        StrataEpochs.LOG.info("[Strata-Test] Transplantation: Blöcke={}, Oberfläche={}, Unterschicht={}, Bau erhalten={}, Biom={}, "
                + "Display={} -> {}", job.changed(), top, under, kept, biome, display, ok ? "OK" : "FEHLER");
        return ok ? 0 : 1;
    }

    /** Wachsen die Features einer Landschaft (Geoden, Riesenpilze, Basaltsäulen)? Zählt Blöcke über dem Boden. */
    private static int testGrowth(MinecraftServer server, String name) {
        ServerLevel level = server.overworld();
        String epochId = StrataEpochs.id(name);
        var landscape = StrataAPI.epochs(server).get(epochId).landscape().orElseThrow();
        int cx = 5000 + 200 * List.of("crystal_age", "spore_age", "ash_age").indexOf(name), cz = 5000;
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) level.getChunk((cx >> 4) + x, (cz >> 4) + z);
        int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz) - 1;
        TransplantJob job = Transplanter.start(level, new BlockPos(cx, ground + 1, cz), epochId, landscape, 10, null);
        job.runToEnd();
        int grown = 0;
        for (BlockPos pos : BlockPos.betweenClosed(cx - 10, ground + 1, cz - 10, cx + 10, ground + 12, cz + 10)) {
            if (!level.getBlockState(pos).isAir()) grown++;
        }
        boolean ok = grown > 0;
        StrataEpochs.LOG.info("[Strata-Test] Wachsen {}: {} Blöcke über dem Boden ({}) -> {}", name, grown,
                landscape.features(), ok ? "OK" : "FEHLER");
        return ok ? 0 : 1;
    }

    /**
     * Neustart-Test für Transplantationen ({@code -Dstrata.persisttest=start}): startet eine langsame Transplantation
     * und fährt den Server sauber herunter. Beim nächsten Start muss sie fortgesetzt werden (Log „fortgesetzt“).
     */
    private static void persistTest(MinecraftServer server) {
        if (!"start".equals(System.getProperty("strata.persisttest"))) return;
        ServerLevel level = server.overworld();
        int cx = 6000, cz = 6000;
        level.getChunk(cx >> 4, cz >> 4);
        Transplanter.command(level, "forceload add " + (cx - 16) + " " + (cz - 16) + " " + (cx + 16) + " " + (cz + 16));
        int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, cx, cz);
        String epochId = StrataEpochs.id("spore_age");
        Transplanter.start(level, new BlockPos(cx, ground, cz), epochId,
                StrataAPI.epochs(server).get(epochId).landscape().orElseThrow(), 8, null);
        StrataEpochs.LOG.info("[Strata-Test] Neustart-Test: Transplantation gestartet, Server fährt herunter");
        server.halt(false);
    }

    /** Kernanalyse: Kern wird markiert, Notiz hat Epoche, Bonus, Tiefe, Fundstücke und Kodex-Zeile. */
    private static int testAnalysis(MinecraftServer server) {
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(server.overworld());
        var sample = CoreSamples.create(StrataEpochs.id("ash_age"));
        CoreSamples.analyze(sample, player);
        var lore = sample.get(net.minecraft.core.component.DataComponents.LORE);
        boolean ok = CoreSamples.isAnalyzed(sample) && lore != null && lore.lines().size() == 5;
        StrataEpochs.LOG.info("[Strata-Test] Kernanalyse: analysiert={}, Zeilen={} -> {}", CoreSamples.isAnalyzed(sample),
                lore == null ? 0 : lore.lines().size(), ok ? "OK" : "FEHLER");
        return ok ? 0 : 1;
    }

    /** Glutwühler: taucht bei Ziel in 6 Blöcken ab, grollt nah am Ziel, bricht hervor (Schaden + Wurf). */
    private static int testBurrower(MinecraftServer server) {
        ServerLevel level = server.overworld();
        int cx = 7000, cz = 7000;
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) level.getChunk((cx >> 4) + x, (cz >> 4) + z);
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, cx, cz);
        var mob = new dev.strata.epochs.mob.EmberBurrower(EpochsMobs.EMBER_BURROWER.get(), level);
        mob.setPos(cx + 0.5, y, cz + 0.5);
        level.addFreshEntity(mob);
        var pig = new dev.strata.epochs.mob.GeodeCrawler(EpochsMobs.GEODE_CRAWLER.get(), level);   // Ziel, in allen Versionen gleich
        pig.setPos(cx + 6.5, y, cz + 0.5);
        level.addFreshEntity(pig);
        float health = pig.getHealth();
        java.util.Set<dev.strata.epochs.mob.EmberBurrower.State> seen = new java.util.LinkedHashSet<>();
        boolean moved = false;
        for (int t = 0; t < 400 && !seen.contains(dev.strata.epochs.mob.EmberBurrower.State.EXPOSED); t++) {
            mob.setTarget(pig);
            if (!moved) pig.setPos(mob.getX() + 6, mob.getY(), mob.getZ());   // bis zum Abtauchen auf Abstand
            mob.tick();
            seen.add(mob.state());
            if (!moved && mob.state() == dev.strata.epochs.mob.EmberBurrower.State.BURROWED) {
                pig.setPos(mob.getX() + 1.5, mob.getY(), mob.getZ());       // Ziel kommt in Reichweite
                moved = true;
            }
        }
        boolean launched = pig.getDeltaMovement().y > 0.3 || pig.getHealth() < health;
        boolean hurt = pig.getHealth() < health;
        mob.discard();
        pig.discard();
        boolean ok = seen.containsAll(java.util.List.of(dev.strata.epochs.mob.EmberBurrower.State.DIGGING,
                dev.strata.epochs.mob.EmberBurrower.State.BURROWED, dev.strata.epochs.mob.EmberBurrower.State.TREMOR,
                dev.strata.epochs.mob.EmberBurrower.State.EMERGING, dev.strata.epochs.mob.EmberBurrower.State.EXPOSED))
                && hurt && launched;
        StrataEpochs.LOG.info("[Strata-Test] Glutwühler: Zustände={}, Schaden={}, Wurf={} -> {}", seen, hurt, launched,
                ok ? "OK" : "FEHLER");
        return ok ? 0 : 1;
    }

    private static void requireResource(List<String> problems, String path) {
        if (SelfTest.class.getResource(path) == null) problems.add("Datei fehlt: " + path);
    }

    private static int count(ServerLevel level, ChunkAccess chunk, Block block) {
        int n = 0;
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = Mc.minY(level); y < Mc.maxY(level); y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (chunk.getBlockState(pos.set(minX + x, y, minZ + z)).is(block)) n++;
                }
            }
        }
        return n;
    }
}
