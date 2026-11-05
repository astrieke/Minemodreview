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
        failures += testPreparation(server);

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
        StrataEpochs.LOG.info("[Strata-Test] Fertig, {} Fehler", failures);
    }

    /** Prüft einen Mob: Modell und Animationen, Loot, Spawn-Modifier, Datapack-Werte und ein echtes Exemplar. */
    private static int testMob(MinecraftServer server, StrataMobDef def,
                               java.util.function.Function<ServerLevel, StrataMob> factory, String epochName) {
        // Der Steinzeuge spawnt nicht natürlich, er erwacht aus versteinertem Stein.
        boolean naturalSpawn = !def.name().equals("petrified_witness");
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
