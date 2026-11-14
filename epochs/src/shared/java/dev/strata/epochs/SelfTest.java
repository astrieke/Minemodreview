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
        failures += testBiomes(server);
        failures += testSpiralitBond(server);
        for (String epoch : List.of("crystal_age", "spore_age", "ash_age")) failures += testGrowth(server, epoch);
        failures += testCrops(server);
        failures += testItemPetrification(server);
        failures += testRifts(server);
        failures += testMuseum(server);
        failures += testStratolith(server);
        failures += testResonantShard(server);
        failures += testAmberMoth(server);
        failures += testResinSnail(server);

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
        failures += testMob(server, dev.strata.epochs.mob.ResinSnail.DEF,
                l -> new dev.strata.epochs.mob.ResinSnail(EpochsMobs.RESIN_SNAIL.get(), l), "spore_age");
        failures += testMob(server, dev.strata.epochs.mob.AmberMoth.DEF,
                l -> new dev.strata.epochs.mob.AmberMoth(EpochsMobs.AMBER_MOTH.get(), l), "spore_age");
        failures += testMob(server, dev.strata.epochs.mob.ResonantShard.DEF,
                l -> new dev.strata.epochs.mob.ResonantShard(EpochsMobs.RESONANT_SHARD.get(), l), "crystal_age");
        StrataEpochs.LOG.info("[Strata-Test] Fertig, {} Fehler", failures);
        persistTest(server);
    }

    /** Sprachschlüssel von Strata Epochs (de_de, en_us). */
    private static final Map<String, java.util.Set<String>> LANG = Map.of("de_de", langKeys("de_de"), "en_us", langKeys("en_us"));

    private static java.util.Set<String> langKeys(String lang) {
        try (var in = SelfTest.class.getResourceAsStream("/assets/" + StrataEpochs.MOD_ID + "/lang/" + lang + ".json")) {
            if (in == null) return java.util.Set.of();
            return com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().keySet();
        } catch (java.io.IOException | RuntimeException e) {
            return java.util.Set.of();
        }
    }

    /** Ereignisse aus sounds.json mit ihren Dateien (Format "ns:pfad", ohne .ogg). */
    private static final Map<String, List<String>> SOUNDS = readSounds();

    private static Map<String, List<String>> readSounds() {
        Map<String, List<String>> result = new java.util.HashMap<>();
        try (var in = SelfTest.class.getResourceAsStream("/assets/" + StrataEpochs.MOD_ID + "/sounds.json")) {
            if (in == null) return result;
            var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : json.entrySet()) {
                List<String> files = new ArrayList<>();
                for (var sound : e.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
                    if (sound.isJsonPrimitive()) files.add(sound.getAsString());
                    else if (!sound.getAsJsonObject().has("type")) files.add(sound.getAsJsonObject().get("name").getAsString());
                }
                result.put(e.getKey(), files);
            }
        } catch (java.io.IOException | RuntimeException e) {
            StrataEpochs.LOG.error("[Strata-Test] sounds.json nicht lesbar", e);
        }
        return result;
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
        // Eigene Sounds: Ruf, Schmerz und Tod sind in sounds.json eingetragen, und jede genannte Datei existiert.
        for (String kind : List.of("ambient", "hurt", "death")) {
            if (!SOUNDS.containsKey("entity." + def.name() + "." + kind)) problems.add("Sound fehlt: entity." + def.name() + "." + kind);
        }
        SOUNDS.forEach((event, files) -> {
            if (!event.startsWith("entity." + def.name() + ".")) return;
            for (String file : files) {
                String ns = file.substring(0, file.indexOf(':'));
                requireResource(problems, "/assets/" + ns + "/sounds/" + file.substring(file.indexOf(':') + 1) + ".ogg");
            }
        });

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
        boolean seed = player.getInventory().countItem(EpochsContent.EMBER_TUBER.get()) == 1;

        player.setItemInHand(off, FossilPrep.chunk(StrataEpochs.id("ash_age")));
        FossilPrep.stroke(player, player.getMainHandItem(), table, 0, () -> 0.2);   // hastig: 30 % Risiko
        boolean broken = player.getOffhandItem().isEmpty();
        boolean risks = FossilPrep.risk(0) > 0.29 && FossilPrep.risk(FossilPrep.CALM_TICKS) < 0.04;
        player.getInventory().clearContent();
        boolean ok = done && rest && worn && broken && risks && seed;
        StrataEpochs.LOG.info("[Strata-Test] Präparation: Fossil={}, Rest={}, Meißel={}, Bruch={}, Risiko={}, Samen={} -> {}",
                done, rest, worn, broken, risks, seed, ok ? "OK" : "FEHLER");
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
        // Freie Fläche suchen: In der flachen Testwelt stehen manchmal Dörfer (in 26.3 lag eines genau hier).
        int cx = 4000, cz = 4000;
        for (int k = 0; k < 12 && !cleanSite(level, cx, cz, 7); k++) cx += 160;
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

    /**
     * Ein gebundener Spiralit pro Spieler, auch wenn der alte gerade nicht geladen ist: Der Spieler merkt sich die
     * UUID seines aktuellen Spiralits; ein anderer mit seinem Namen löst sich beim nächsten Laden.
     */
    private static int testSpiralitBond(MinecraftServer server) {
        ServerLevel level = server.overworld();
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        player.setData(EpochsContent.SPIRALIT_ID.get(), "");
        var at = new net.minecraft.world.phys.Vec3(7000.5, 120, 7000.5);
        level.getChunk(7000 >> 4, 7000 >> 4);
        var first = dev.strata.epochs.mob.Spiralit.hatch(level, at, player);
        boolean remembered = player.getData(EpochsContent.SPIRALIT_ID.get()).equals(first.getStringUUID());
        boolean blocks = dev.strata.epochs.mob.Spiralit.hasSpiralit(player);
        // Der erste „wandert in eine entladene Gegend“: Er ist nicht mehr da, ein neuer darf schlüpfen.
        var bondOfFirst = first.bond();
        first.discard();
        boolean freeAgain = !dev.strata.epochs.mob.Spiralit.hasSpiralit(player) && dev.strata.epochs.mob.Spiralit.replacesUnloaded(player);
        var second = dev.strata.epochs.mob.Spiralit.hatch(level, at, player);
        // Der alte wird wieder geladen (neue Instanz mit seiner Bindung): Er muss sich lösen, der neue bleibt.
        var returned = new dev.strata.epochs.mob.Spiralit(EpochsMobs.SPIRALIT.get(), level);
        returned.setData(EpochsContent.BOND.get(), bondOfFirst);
        boolean oldReleased = !dev.strata.epochs.mob.Spiralit.checkBond(returned, player) && returned.bond().owner().isEmpty();
        boolean newKept = dev.strata.epochs.mob.Spiralit.checkBond(second, player);
        // Spiralit von vor dem Merker: Der Spieler hat noch keine ID, der Spiralit wird übernommen.
        player.setData(EpochsContent.SPIRALIT_ID.get(), "");
        boolean adopted = dev.strata.epochs.mob.Spiralit.checkBond(second, player)
                && player.getData(EpochsContent.SPIRALIT_ID.get()).equals(second.getStringUUID());
        // Tod: Der Merker wird geleert, beim nächsten Schlüpfen gibt es keinen „löst sich“-Hinweis.
        dev.strata.epochs.mob.Spiralit.forget(second, player);
        boolean forgotten = player.getData(EpochsContent.SPIRALIT_ID.get()).isEmpty()
                && !dev.strata.epochs.mob.Spiralit.replacesUnloaded(player);
        // Herrenlose verschwinden mit der Zeit, gebundene und benannte nicht.
        boolean despawn = !returned.isPersistenceRequired() && second.isPersistenceRequired();
        // Ersetzen eines nicht geladenen: erst der zweite Klick nach einer Pause bestätigt, gehalten zählt nicht.
        boolean confirm = !FossilPrep.confirmed(player, 1000) && !FossilPrep.confirmed(player, 1004)
                && FossilPrep.confirmed(player, 1030);
        second.discard();
        player.setData(EpochsContent.SPIRALIT_ID.get(), "");
        boolean ok = remembered && blocks && freeAgain && oldReleased && newKept && adopted && forgotten && despawn && confirm;
        StrataEpochs.LOG.info("[Strata-Test] Spiralit-Bindung: gemerkt={}, blockiert={}, frei nach Entladen={}, alter gelöst={}, "
                + "neuer bleibt={}, alter Stand übernommen={}, Tod vergessen={}, herrenlos verschwindet={}, Bestätigung={} -> {}",
                remembered, blocks, freeAgain, oldReleased, newKept, adopted, forgotten, despawn, confirm, ok ? "OK" : "FEHLER");
        return ok ? 0 : 1;
    }

    /** Ebene ohne Bauwerke: überall dieselbe Höhe und Gras obenauf (lädt die Chunks dafür). */
    private static boolean cleanSite(ServerLevel level, int cx, int cz, int r) {
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) level.getChunk((cx >> 4) + x, (cz >> 4) + z);
        var type = net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES;
        int h = level.getHeight(type, cx, cz);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (level.getHeight(type, cx + dx, cz + dz) != h) return false;
                if (!level.getBlockState(new BlockPos(cx + dx, h - 1, cz + dz)).is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)) return false;
            }
        }
        return true;
    }

    /** Wachsen die Features einer Landschaft (Geoden, Riesenpilze, Basaltsäulen)? Zählt Blöcke über dem Boden. */
    private static int testGrowth(MinecraftServer server, String name) {
        ServerLevel level = server.overworld();
        String epochId = StrataEpochs.id(name);
        var landscape = StrataAPI.epochs(server).get(epochId).landscape().orElseThrow();
        int cx = 5000 + 200 * List.of("crystal_age", "spore_age", "ash_age").indexOf(name), cz = 5000;
        for (int k = 0; k < 12 && !cleanSite(level, cx, cz, 12); k++) cz += 160;
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
        return (ok ? 0 : 1) + testAnalysisPort(server);
    }

    /**
     * Kernanalyse-Tisch mit Trichter: Trichter über dem Tisch, Truhe darunter. Simulieren bewegt nichts,
     * analysierte Kerne und fremde Items lehnt der Tisch ab, ohne Inventar darunter nimmt er nichts.
     */
    private static int testAnalysisPort(MinecraftServer server) {
        ServerLevel level = server.overworld();
        int x = 7000, z = 7000;
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) + 1;
        BlockPos chest = new BlockPos(x, y, z), table = chest.above(), hopper = table.above(), lone = chest.east(3);
        level.setBlockAndUpdate(chest, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(table, EpochsContent.ANALYSIS_TABLE.get().defaultBlockState());
        level.setBlockAndUpdate(hopper, net.minecraft.world.level.block.Blocks.HOPPER.defaultBlockState());
        level.setBlockAndUpdate(lone, EpochsContent.ANALYSIS_TABLE.get().defaultBlockState());
        var cores = CoreSamples.create(StrataEpochs.id("crystal_age")).copyWithCount(3);
        var container = (net.minecraft.world.Container) level.getBlockEntity(chest);

        int simulated = dev.strata.epochs.mc.TablePort.insert(level, table, cores, true);
        boolean simOk = simulated == 3 && container.isEmpty();
        int moved = dev.strata.epochs.mc.TablePort.insert(level, table, cores, false);
        var out = container.getItem(0);
        var lore = out.get(net.minecraft.core.component.DataComponents.LORE);
        boolean movedOk = moved == 3 && out.getCount() == 3 && CoreSamples.isAnalyzed(out)
                && lore != null && lore.lines().size() == 4;
        boolean rejects = dev.strata.epochs.mc.TablePort.insert(level, table, out.copy(), true) == 0
                && dev.strata.epochs.mc.TablePort.insert(level, table, new net.minecraft.world.item.ItemStack(EpochsContent.CHISEL.get()), true) == 0
                && dev.strata.epochs.mc.TablePort.insert(level, lone, cores, true) == 0;

        var hopperBe = (net.minecraft.world.level.block.entity.HopperBlockEntity) level.getBlockEntity(hopper);
        hopperBe.setItem(0, cores.copyWithCount(2));
        net.minecraft.world.level.block.entity.HopperBlockEntity.pushItemsTick(level, hopper, level.getBlockState(hopper), hopperBe);
        int analyzed = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (CoreSamples.isAnalyzed(container.getItem(i))) analyzed += container.getItem(i).getCount();
        }
        boolean hopperOk = analyzed == 4 && hopperBe.getItem(0).getCount() == 1;

        // Rohre merken sich die Schnittstelle: Abbau muss den Cache leeren, Neusetzen ihn wieder füllen.
        var probe = dev.strata.epochs.mc.TablePort.probe(level, table);
        boolean before = probe.reachable().getAsBoolean();
        level.setBlockAndUpdate(table, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        int afterRemove = probe.invalidations()[0];
        boolean gone = !probe.reachable().getAsBoolean();
        level.setBlockAndUpdate(table, EpochsContent.ANALYSIS_TABLE.get().defaultBlockState());
        boolean cacheOk = before && afterRemove > 0 && gone && probe.invalidations()[0] > afterRemove
                && probe.reachable().getAsBoolean();
        boolean sides = dev.strata.epochs.mc.TablePort.offersFrom(level, table, net.minecraft.core.Direction.NORTH)
                && !dev.strata.epochs.mc.TablePort.offersFrom(level, table, net.minecraft.core.Direction.DOWN);
        probe.close().run();

        hopperBe.clearContent();
        container.clearContent();
        for (BlockPos pos : List.of(hopper, table, chest, lone)) {
            level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        boolean ok = simOk && movedOk && rejects && hopperOk && cacheOk && sides;
        StrataEpochs.LOG.info("[Strata-Test] Analyse-Tisch mit Trichter: Simulation={}, Durchreichen={} ({}), Ablehnen={}, "
                + "Trichter={}, Rohr-Cache={}, Seiten={} -> {}",
                simOk, movedOk, moved, rejects, hopperOk, cacheOk, sides, ok ? "OK" : "FEHLER");
        return ok ? 0 : 1;
    }

    /**
     * Epochen-Pflanzen: wachsen nur auf ihrem Boden in einem Biom ihrer Epoche (auch auf transplantiertem Boden in
     * der Oberwelt), brauchen kein Licht, werden reif und geben dann Rohstoff und Samen.
     */
    private static int testCrops(MinecraftServer server) {
        int failures = 0;
        record Case(java.util.function.Supplier<net.minecraft.world.level.block.Block> crop, String epoch,
                    net.minecraft.world.level.block.Block soil, net.minecraft.world.item.Item product) {}
        var cases = List.of(
                new Case(EpochsContent.QUARTZ_THISTLE, "crystal_age", net.minecraft.world.level.block.Blocks.CALCITE,
                        EpochsContent.CRYSTAL_QUARTZ.get()),
                new Case(EpochsContent.RESIN_FERN, "spore_age", net.minecraft.world.level.block.Blocks.MYCELIUM,
                        EpochsContent.AMBER_SHARD.get()),
                new Case(EpochsContent.EMBER_TUBERS, "ash_age", EpochsContent.VOLCANIC_ASH.get(),
                        EpochsContent.EMBER_SHARD.get()));
        ServerLevel overworld = server.overworld();
        int ox = 7100;
        for (Case c : cases) {
            EpochCrop crop = (EpochCrop) c.crop().get();
            ServerLevel epoch = Mc.level(server, StrataEpochs.id(c.epoch()));
            // In der Epoche: über dem Ankunftsort, in der Luft (dort ist es dunkel genug für die Lichtfrage).
            BlockPos ground = EpochTravel.prepareArrival(epoch, 8, 8).offset(4, 6, 4);
            BlockPos at = ground.above();
            epoch.setBlockAndUpdate(ground, c.soil().defaultBlockState());
            epoch.setBlockAndUpdate(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            boolean onSoil = crop.defaultBlockState().canSurvive(epoch, at);
            epoch.setBlockAndUpdate(ground, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            boolean onStone = crop.defaultBlockState().canSurvive(epoch, at);
            epoch.setBlockAndUpdate(ground, c.soil().defaultBlockState());

            // Wachsen ohne Licht: genug Zufallsticks bringen sie zur Reife.
            epoch.setBlockAndUpdate(at, crop.defaultBlockState());
            var random = net.minecraft.util.RandomSource.create(7);
            int ticks = 0;
            while (!crop.isMaxAge(epoch.getBlockState(at)) && ticks < 400) {
                epoch.getBlockState(at).randomTick(epoch, at, random);
                ticks++;
            }
            boolean ripe = epoch.getBlockState(at).is(crop) && crop.isMaxAge(epoch.getBlockState(at));
            var drops = net.minecraft.world.level.block.Block.getDrops(epoch.getBlockState(at), epoch, at, null);
            int seeds = drops.stream().filter(d -> d.is(EpochCrop.seedFor(crop.epoch()))).mapToInt(d -> d.getCount()).sum();
            boolean harvest = drops.stream().anyMatch(d -> d.is(c.product())) && seeds >= 1 && seeds <= 2;
            boolean noBonemeal = !dev.strata.epochs.mc.CropBase.acceptsBonemeal(crop, epoch, at);
            var young = net.minecraft.world.level.block.Block.getDrops(crop.defaultBlockState(), epoch, at, null);
            boolean seedBack = young.size() == 1 && young.get(0).is(EpochCrop.seedFor(crop.epoch()))
                    && young.stream().noneMatch(d -> d.is(c.product()));
            // Zeitessenz: alle Epochen-Pflanzen im 3x3-Feld eine Stufe für eine Essenz; reif = wirkungslos, nicht verbraucht.
            for (BlockPos p : BlockPos.betweenClosed(ground.offset(-1, 0, -1), ground.offset(1, 0, 1))) {
                epoch.setBlockAndUpdate(p, c.soil().defaultBlockState());
                epoch.setBlockAndUpdate(p.above(), crop.defaultBlockState());
            }
            var essence = new net.minecraft.world.item.ItemStack(EpochsContent.TIME_ESSENCE.get(), 5);
            int grown = EpochCrop.applyEssence(epoch, at, essence, true);
            boolean field = grown == 9 && essence.getCount() == 4
                    && epoch.getBlockState(at.offset(1, 0, 1)).getValue(net.minecraft.world.level.block.BeetrootBlock.AGE) == 1;
            EpochCrop.applyEssence(epoch, at, essence, true);
            EpochCrop.applyEssence(epoch, at, essence, true);
            boolean essenceOk = field && crop.isMaxAge(epoch.getBlockState(at)) && essence.getCount() == 2
                    && EpochCrop.applyEssence(epoch, at, essence, true) == 0 && essence.getCount() == 2;
            for (BlockPos p : BlockPos.betweenClosed(ground.offset(-1, 0, -1), ground.offset(1, 1, 1))) {
                epoch.setBlockAndUpdate(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
            harvest &= essenceOk;
            epoch.setBlockAndUpdate(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            epoch.setBlockAndUpdate(ground, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());

            // Oberwelt: gleicher Boden, aber falsches Biom; nach /fillbiome (wie beim Transplantieren) geht es.
            overworld.getChunk(ox >> 4, 7100 >> 4);
            BlockPos owGround = new BlockPos(ox, overworld.getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, ox, 7100) + 2, 7100);
            BlockPos owAt = owGround.above();
            overworld.setBlockAndUpdate(owGround, c.soil().defaultBlockState());
            boolean wild = crop.defaultBlockState().canSurvive(overworld, owAt);
            String biome = StrataAPI.epochs(server).get(crop.epoch()).landscape().orElseThrow().biome();
            Transplanter.command(overworld, "fillbiome " + (ox - 2) + " " + (owAt.getY() - 2) + " 7098 "
                    + (ox + 2) + " " + (owAt.getY() + 2) + " 7102 " + biome);
            boolean transplanted = crop.defaultBlockState().canSurvive(overworld, owAt);
            overworld.setBlockAndUpdate(owGround, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            ox += 16;

            String key = "message.strata_epochs.crop.wrong_place." + net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(crop).getPath();
            boolean texts = LANG.get("de_de").contains(key) && LANG.get("en_us").contains(key);
            boolean ok = onSoil && !onStone && ripe && harvest && noBonemeal && seedBack && !wild && transplanted && texts;
            if (!ok) failures++;
            StrataEpochs.LOG.info("[Strata-Test] Pflanze {}: Boden={}, Stein={}, reif nach {} Ticks={}, Ernte={} ({}), "
                    + "Knochenmehl={}, jung nur Samen={}, Oberwelt={}, transplantiert={}, Texte={} -> {}", crop.epoch(), onSoil,
                    onStone, ticks, ripe, harvest, drops, !noBonemeal, seedBack, wild, transplanted, texts, ok ? "OK" : "FEHLER");
        }
        return failures;
    }

    /**
     * Inventar-Verfestigung: Ein Stapel wird zum versteinerten Gegenstand und kommt beim Lösen unverändert zurück
     * (Anzahl, Abnutzung). Reihenfolge: Rucksack, dann Schnellleiste, nie die Haupthand; Zeitanker, Keimfrische,
     * Rüstung und zweite Hand nie. Fremde Brocken lösen sich in der Epoche nicht, beim Tod löst sich alles,
     * und im echten Spielertick draußen auch. Brocken passen nicht in Behälter-Items und verbrennen nicht.
     */
    private static int testItemPetrification(MinecraftServer server) {
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(server.overworld());
        var inventory = player.getInventory();
        inventory.clearContent();
        var pick = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE);
        pick.setDamageValue(37);
        var sword = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD);
        var quartz = new net.minecraft.world.item.ItemStack(EpochsContent.CRYSTAL_QUARTZ.get(), 12);
        var dirt = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT, 40);
        inventory.setItem(0, pick.copy());   // Haupthand (Platz 0 ist ausgewählt)
        inventory.setItem(1, new net.minecraft.world.item.ItemStack(EpochsContent.TIME_ANCHOR.get()));
        inventory.setItem(2, new net.minecraft.world.item.ItemStack(EpochsContent.GERM_TONIC.get(), 3));
        inventory.setItem(3, sword.copy());
        inventory.setItem(20, quartz.copy());
        inventory.setItem(25, dirt.copy());
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD));
        var random = net.minecraft.util.RandomSource.create(3);

        var p1 = ItemPetrification.petrifyOne(player, random);
        var p2 = ItemPetrification.petrifyOne(player, random);
        boolean backpackFirst = ItemPetrification.isPetrified(inventory.getItem(20)) && ItemPetrification.isPetrified(inventory.getItem(25))
                && !ItemPetrification.isPetrified(inventory.getItem(3)) && !p1.isEmpty() && !p2.isEmpty();
        var p3 = ItemPetrification.petrifyOne(player, random);
        var p4 = ItemPetrification.petrifyOne(player, random);
        boolean order = backpackFirst && p3.is(net.minecraft.world.item.Items.IRON_SWORD) && p4.isEmpty()
                && net.minecraft.world.item.ItemStack.matches(inventory.getItem(0), pick);
        boolean keeps = net.minecraft.world.item.ItemStack.matches(ItemPetrification.content(inventory.getItem(20)), quartz)
                && net.minecraft.world.item.ItemStack.matches(ItemPetrification.content(inventory.getItem(25)), dirt);
        boolean immune = inventory.getItem(1).is(EpochsContent.TIME_ANCHOR.get())
                && inventory.getItem(2).is(EpochsContent.GERM_TONIC.get())
                && player.getOffhandItem().is(net.minecraft.world.item.Items.SHIELD);
        boolean named = inventory.getItem(20).getHoverName().getString().contains("12");

        // Brocken taugt nicht als Behälter-Inhalt und verbrennt nicht.
        var lump = inventory.getItem(20);
        boolean noNesting = !lump.getItem().canFitInsideContainerItems();
        boolean fireproof = new net.minecraft.world.entity.item.ItemEntity(server.overworld(), 0, 300, 0, lump.copy()).fireImmune();

        // Fremder Brocken: in der Epoche bleibt er zu, eigene lösen sich.
        var foreign = lump.copy();
        foreign.set(EpochsContent.PETRIFIED_OWNER.get(), "00000000-0000-0000-0000-000000000001");
        inventory.setItem(30, foreign);
        int ownInEpoch = ItemPetrification.thaw(player, true);
        boolean coop = ownInEpoch == 3 && ItemPetrification.isPetrified(inventory.getItem(30))
                && net.minecraft.world.item.ItemStack.matches(inventory.getItem(20), quartz);

        // Tod: vorher löst sich alles (auch der fremde Brocken), damit die Originale herausfallen.
        // Über den Event-Bus, damit auch Registrierung und Priorität geprüft sind.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(
                player, player.damageSources().lava()));
        boolean death = !ItemPetrification.isPetrified(inventory.getItem(30))
                && net.minecraft.world.item.ItemStack.matches(inventory.getItem(30), quartz);

        // Echter Spielertick in der Oberwelt: Brocken (auch in der zweiten Hand) löst sich.
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemPetrification.petrify(sword, player.getStringUUID()));
        player.tickCount = 0;
        Solidification.onPlayerTick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
        boolean tick = net.minecraft.world.item.ItemStack.matches(player.getOffhandItem(), sword);
        inventory.clearContent();

        boolean data = StrataAPI.epochs(server).get(StrataEpochs.id("crystal_age")).petrifyItemsSeconds() == 20;
        List<String> missing = new ArrayList<>();
        for (String key : List.of("item.strata_epochs.petrified_item", "item.strata_epochs.petrified_item.of",
                "tooltip.strata_epochs.petrified_item", "message.strata_epochs.solidify.item",
                "message.strata_epochs.solidify.thaw_one", "message.strata_epochs.solidify.thaw")) {
            for (String lang : List.of("de_de", "en_us")) if (!LANG.get(lang).contains(key)) missing.add(lang + ":" + key);
        }
        boolean ok = order && keeps && immune && named && noNesting && fireproof && coop && death && tick && data && missing.isEmpty();
        StrataEpochs.LOG.info("[Strata-Test] Inventar-Verfestigung: Reihenfolge={}, Inhalt={}, immun={}, Name={}, "
                + "nicht in Behältern={}, feuerfest={}, fremde bleiben={}, Tod={}, Tick={}, Datapack={}, Texte fehlen={} -> {}",
                order, keeps, immune, named, noNesting, fireproof, coop, death, tick, data, missing, ok ? "OK" : "FEHLER");
        return ok ? 0 : 1;
    }

    /**
     * Zeitrisse: öffnen, natürlichen Boden zur Epoche machen (Bauten bleiben), nur mit Zeitanker beim Schleichen
     * versiegeln (Fossil + Rohstoff), nach der Lebensdauer von selbst schließen, Abstand zu anderen Rissen,
     * nur in erlaubten Dimensionen, nur Epochen, die der Spieler besucht hat.
     */
    private static int testRifts(MinecraftServer server) {
        ServerLevel level = server.overworld();
        int cx = 8500, cz = 8500;
        for (int k = 0; k < 12 && !cleanSite(level, cx, cz, 8); k++) cz += 160;
        var hm = net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES;
        int ground = level.getHeight(hm, cx, cz) - 1;
        BlockPos pos = new BlockPos(cx, ground + 1, cz);
        BlockPos build = new BlockPos(cx + 1, ground, cz);
        level.setBlockAndUpdate(build, net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState());
        String epoch = StrataEpochs.id("crystal_age");
        long lifetime = 6000;

        TimeRifts.open(level, pos, epoch);
        var record = level.getData(EpochsContent.RIFTS.get()).stream().filter(r -> r.pos() == pos.asLong()).findFirst().orElse(null);
        boolean opened = level.getBlockState(pos).is(EpochsContent.TIME_RIFT.get()) && record != null
                && level.getBlockState(pos.above()).is(EpochsContent.TIME_RIFT.get());   // zwei Blöcke hoch

        var random = net.minecraft.util.RandomSource.create(11);
        int converted = 0;
        for (int i = 0; i < 80 && record != null; i++) {
            if (TimeRifts.convertOne(level, record, record.openedAt() + lifetime, lifetime, random) != null) converted++;
        }
        int calcite = 0;
        for (BlockPos p : BlockPos.betweenClosed(cx - 7, ground, cz - 7, cx + 7, ground, cz + 7)) {
            if (level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.CALCITE)) calcite++;
        }
        boolean spreads = converted >= 8 && calcite == converted
                && level.getBlockState(build).is(net.minecraft.world.level.block.Blocks.OAK_PLANKS);

        boolean spaced = TimeRifts.findSpot(level, pos, random) == null;   // ein Riss liegt schon in der Nähe
        TimeRifts.tickAll(level, record == null ? 0 : record.openedAt() + 40, false, lifetime);
        boolean stillOpen = level.getBlockState(pos).is(EpochsContent.TIME_RIFT.get());

        // Eigener Test-Spieler: der Kodex des Standard-Testspielers bleibt für andere Tests unberührt.
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("strata_rift_test".getBytes()), "strata_rift_test"));
        player.setPos(cx + 2.5, ground + 1, cz + 0.5);
        player.setShiftKeyDown(true);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(EpochsContent.CHISEL.get()));
        boolean refused = !TimeRifts.seal(player, level, pos) && level.getBlockState(pos).is(EpochsContent.TIME_RIFT.get());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(EpochsContent.TIME_ANCHOR.get()));
        boolean sealed = TimeRifts.seal(player, level, pos) && level.getBlockState(pos).isAir()
                && level.getData(EpochsContent.RIFTS.get()).stream().noneMatch(r -> r.pos() == pos.asLong());
        var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(2));
        boolean reward = drops.stream().anyMatch(e -> e.getItem().is(EpochsContent.FOSSIL_CHUNK.get()))
                && drops.stream().anyMatch(e -> e.getItem().is(EpochsContent.CRYSTAL_QUARTZ.get()))
                && drops.stream().anyMatch(e -> e.getItem().is(EpochsContent.TIME_ESSENCE.get()));
        // Steinzeuge lässt Zeitessenz fallen (50 %): steht in seiner Beutetabelle.
        try (var in = SelfTest.class.getResourceAsStream("/data/strata_epochs/loot_table/entities/petrified_witness.json")) {
            reward &= in != null && new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).contains("strata_epochs:time_essence");
        } catch (java.io.IOException e) {
            reward = false;
        }
        drops.forEach(net.minecraft.world.entity.Entity::discard);
        player.setShiftKeyDown(false);
        player.getInventory().clearContent();

        // Ohne Versiegeln: nach der Lebensdauer zu, ohne Belohnung.
        BlockPos pos2 = pos.offset(3, 0, 3);
        TimeRifts.open(level, pos2, StrataEpochs.id("ash_age"));
        long opened2 = level.getGameTime();
        TimeRifts.tickAll(level, opened2 + lifetime, false, lifetime);
        boolean expired = level.getBlockState(pos2).isAir() && level.getBlockState(pos2.above()).isAir()
                && level.getData(EpochsContent.RIFTS.get()).stream().noneMatch(r -> r.pos() == pos2.asLong())
                && level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(pos2).inflate(2)).isEmpty();

        boolean dims = TimeRifts.allowedIn(level) && !TimeRifts.allowedIn(Mc.level(server, epoch));
        boolean surface = TimeRifts.atSurface(level, pos) && !TimeRifts.atSurface(level, pos.below(20));

        // Abgelaufen in entladener Gegend: zählt nicht mehr zur Grenze, der Datensatz fällt weg.
        long now = level.getGameTime();
        BlockPos far = new BlockPos(29_000_000, 70, 29_000_000);
        var stale = new java.util.ArrayList<>(level.getData(EpochsContent.RIFTS.get()));
        stale.add(new RiftRecord(far.asLong(), epoch, now - lifetime - 100));
        level.setData(EpochsContent.RIFTS.get(), stale);
        long active = TimeRifts.activeCount(level, now, lifetime);
        TimeRifts.tickAll(level, now, false, lifetime);
        boolean pruned = active == 0 && level.getData(EpochsContent.RIFTS.get()).stream().noneMatch(r -> r.pos() == far.asLong());
        // Verwaister Rissblock (ohne Datensatz) verschwindet beim Zufallstick.
        BlockPos orphan = pos.offset(-3, 0, 3);
        level.setBlockAndUpdate(orphan, EpochsContent.TIME_RIFT.get().defaultBlockState());
        level.getBlockState(orphan).randomTick(level, orphan, random);
        pruned &= level.getBlockState(orphan).isAir();

        // Friedlich: keine Mobs aus dem Riss.
        var difficulty = level.getDifficulty();
        server.setDifficulty(net.minecraft.world.Difficulty.PEACEFUL, true);
        boolean peaceful = !TimeRifts.mayReleaseMob(level, pos);
        server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
        peaceful &= TimeRifts.mayReleaseMob(level, pos);
        server.setDifficulty(difficulty, true);
        boolean moss = TimeRifts.surfaceFor(level, StrataEpochs.id("spore_age")).is(net.minecraft.world.level.block.Blocks.MOSS_BLOCK);
        // Wasser spült den Riss nicht weg (sonst ließe er sich mit einem Eimer ohne Zeitanker schließen).
        var riftState = EpochsContent.TIME_RIFT.get().defaultBlockState();
        boolean dry = riftState.getBlock() instanceof net.minecraft.world.level.block.LiquidBlockContainer c
                && !c.placeLiquid(level, orphan, riftState, net.minecraft.world.level.material.Fluids.WATER.getSource(false));
        boolean before = TimeRifts.epochsFor(player).contains(epoch);
        StrataAPI.unlock(player, StrataEpochs.id("first_core"));
        StrataAPI.unlock(player, StrataEpochs.id("visited_crystal_age"));
        boolean eligible = !before && TimeRifts.epochsFor(player).contains(epoch);
        level.setBlockAndUpdate(build, net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState());

        List<String> missing = new ArrayList<>();
        for (String key : List.of("block.strata_epochs.time_rift", "item.strata_epochs.time_essence", "message.strata_epochs.rift.opened",
                "message.strata_epochs.rift.how", "message.strata_epochs.rift.sealed")) {
            for (String lang : List.of("de_de", "en_us")) if (!LANG.get(lang).contains(key)) missing.add(lang + ":" + key);
        }
        for (String lang : List.of("de_de", "en_us")) {
            if (!LANG.get(lang).contains("message.strata_epochs.essence.ripe")) missing.add(lang + ":message.strata_epochs.essence.ripe");
        }
        boolean ok = opened && spreads && spaced && stillOpen && refused && sealed && reward && expired && dims
                && eligible && surface && pruned && peaceful && moss && dry && missing.isEmpty();
        StrataEpochs.LOG.info("[Strata-Test] Zeitrisse: offen={}, Boden={} ({} Blöcke, Bau bleibt), Abstand={}, läuft={}, "
                + "ohne Anker zu={}, versiegelt={}, Belohnung={}, abgelaufen={}, Dimensionen={}, nur besuchte={}, "
                + "Oberfläche={}, Verwaiste weg={}, Friedlich={}, Moos={}, wasserfest={}, Texte fehlen={} -> {}",
                opened, spreads, converted, spaced, stillOpen, refused, sealed, reward, expired, dims, eligible,
                surface, pruned, peaceful, moss, dry, missing, ok ? "OK" : "FEHLER");
        return ok ? 0 : 1;
    }

    /**
     * Museum der Erdgeschichte: Vitrine stellt einen Gegenstand aus (Display trägt das Item), lehnt einen zweiten
     * ab, gibt ihn heraus, lässt ihn beim Entfernen fallen; mit Bohrkern und Fossil jeder Epoche in der Nähe
     * schaltet sich der Kodex-Knoten {@code museum} frei.
     */
    private static int testMuseum(MinecraftServer server) {
        ServerLevel level = server.overworld();
        int cx = 8800, cz = 8800;
        for (int k = 0; k < 12 && !cleanSite(level, cx, cz, 9); k++) cz += 160;
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("strata_museum_test".getBytes()), "strata_museum_test"));
        StrataAPI.unlock(player, StrataEpochs.id("first_core"));
        var requirements = DisplayCases.requirements(server);

        List<net.minecraft.world.item.ItemStack> exhibits = new ArrayList<>();
        for (String e : List.of("crystal_age", "spore_age", "ash_age")) {
            exhibits.add(CoreSamples.create(StrataEpochs.id(e)));
            exhibits.add(new net.minecraft.world.item.ItemStack(FossilPrep.fossilFor(StrataEpochs.id(e)), 2));
        }
        for (var item : List.of(EpochsContent.CRYSTAL_QUARTZ, EpochsContent.QUARTZ_THISTLE_SEEDS, EpochsContent.AMBER_SHARD,
                EpochsContent.RESIN_FERN_SPORES, EpochsContent.EMBER_SHARD, EpochsContent.EMBER_TUBER,
                EpochsContent.CALCITE_SCALE, EpochsContent.SPORE_SAC, EpochsContent.CODEX_FRAGMENT)) {
            exhibits.add(new net.minecraft.world.item.ItemStack(item.get()));
        }
        List<BlockPos> cases = new ArrayList<>();
        boolean placed = true;
        for (int i = 0; i < exhibits.size(); i++) {
            BlockPos pos = new BlockPos(cx - 4 + 2 * (i % 5), y, cz - 2 + 2 * (i / 5));   // 5 x 3, alles in Reichweite
            level.setBlockAndUpdate(pos, EpochsContent.DISPLAY_CASE.get().defaultBlockState());
            cases.add(pos);
            var held = exhibits.get(i).copy();
            int before = held.getCount();
            placed &= DisplayCases.put(level, pos, held, i == 3 ? player.getStringUUID() : "") && held.getCount() == before - 1
                    && net.minecraft.world.item.ItemStack.isSameItemSameComponents(DisplayCases.exhibit(level, pos), exhibits.get(i));
            if (i < exhibits.size() - 1) {
                placed &= DisplayCases.present(level, cases.get(0)).size() == i + 1;
            }
        }
        boolean occupied = !DisplayCases.put(level, cases.get(0), new net.minecraft.world.item.ItemStack(EpochsContent.CRYSTAL_QUARTZ.get()), "");
        boolean complete = requirements.size() == 15 && DisplayCases.present(level, cases.get(0)).size() == 15;
        // Besucher ohne eigenes Exponat bekommt den Knoten nicht.
        var visitor = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("strata_museum_visitor".getBytes()), "strata_museum_visitor"));
        StrataAPI.unlock(visitor, StrataEpochs.id("first_core"));
        DisplayCases.checkMuseum(visitor, level, cases.get(0));
        boolean visitorOut = !StrataAPI.hasNode(visitor, StrataEpochs.id("museum"));
        double bonusBefore = Solidification.timeBonus(player);
        DisplayCases.checkMuseum(player, level, cases.get(0));
        boolean codex = StrataAPI.hasNode(player, StrataEpochs.id("museum"))
                && Math.abs(Solidification.timeBonus(player) - bonusBefore - DisplayCases.MUSEUM_BONUS) < 1e-9 && visitorOut;

        // Fremdes Item-Display (ohne Tag) in einer leeren Vitrine: wird weder gefunden noch entfernt.
        BlockPos spare = new BlockPos(cx + 9, y, cz);
        level.setBlockAndUpdate(spare, EpochsContent.DISPLAY_CASE.get().defaultBlockState());
        Transplanter.command(level, "summon minecraft:item_display " + (spare.getX() + 0.5) + " " + (spare.getY() + 0.6) + " "
                + (spare.getZ() + 0.5) + " {Tags:[\"strata_foreign_test\"]}");
        boolean foreign = DisplayCases.find(level, spare) == null && DisplayCases.take(level, spare).isEmpty()
                && level.getEntitiesOfClass(net.minecraft.world.entity.Display.ItemDisplay.class,
                        new net.minecraft.world.phys.AABB(spare)).size() == 1;
        Transplanter.command(level, "kill @e[type=minecraft:item_display,tag=strata_foreign_test]");
        level.setBlockAndUpdate(spare, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());

        var taken = DisplayCases.take(level, cases.get(0));
        boolean takeOut = CoreSamples.epochOf(taken).isPresent() && DisplayCases.find(level, cases.get(0)) == null
                && DisplayCases.present(level, cases.get(1)).size() == 14;
        // Abbau: Exponat fällt heraus, das Display verschwindet.
        BlockPos last = cases.get(cases.size() - 1);
        level.setBlockAndUpdate(last, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(last).inflate(1.5));
        boolean dropped = drops.stream().anyMatch(d -> d.getItem().is(EpochsContent.CODEX_FRAGMENT.get()))
                && DisplayCases.find(level, last) == null;
        drops.forEach(net.minecraft.world.entity.Entity::discard);
        for (BlockPos pos : cases) {
            DisplayCases.take(level, pos);
            level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(cases.get(0)).inflate(20)).forEach(net.minecraft.world.entity.Entity::discard);

        List<String> missing = new ArrayList<>();
        for (String key : List.of("block.strata_epochs.display_case", "message.strata_epochs.museum.core",
                "message.strata_epochs.museum.progress", "message.strata_epochs.museum.done",
                "message.strata_epochs.museum.complete", "message.strata_epochs.museum.occupied",
                "message.strata_epochs.museum.contribute")) {
            for (String lang : List.of("de_de", "en_us")) if (!LANG.get(lang).contains(key)) missing.add(lang + ":" + key);
        }
        boolean ok = placed && occupied && complete && codex && foreign && takeOut && dropped && missing.isEmpty();
        StrataEpochs.LOG.info("[Strata-Test] Museum: ausgestellt={}, belegt={}, vollständig={} ({} Exponate), Kodex+Bonus={}, "
                + "fremdes Display bleibt={}, herausgenommen={}, fällt heraus={}, Texte fehlen={} -> {}", placed, occupied,
                complete, requirements.size(), codex, foreign, takeOut, dropped, missing, ok ? "OK" : "FEHLER");
        return ok ? 0 : 1;
    }

    /**
     * Stratolith: Beschwörung nur mit Meisterschaft und Platz; unverwundbar beim Aufsteigen; Schichtwechsel bei 2/3
     * und 1/3 Leben (Beute, kleinere Trefferfläche, Phase gespeichert); Zurückspulen holt verlorenes Leben zurück,
     * ein Spitzhackentreffer von vorn stoppt es (von hinten nicht); Herz der Tiefenzeit verstärkt den Transplantator;
     * Kodex beim Sieg.
     */
    // Hinweis: tick() direkt aufgerufen zählt tickCount nicht hoch (das macht sonst der Server), daher tickCount++.
    private static int testStratolith(MinecraftServer server) {
        ServerLevel level = server.overworld();
        int cx = 9200, cz = 9200;
        for (int k = 0; k < 12 && !cleanSite(level, cx, cz, 6); k++) cz += 160;
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        BlockPos stone = new BlockPos(cx, y - 1, cz);
        level.setBlockAndUpdate(stone, EpochsContent.CHRONOSTONE.get().defaultBlockState());
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("strata_boss_test".getBytes()), "strata_boss_test"));
        player.setPos(cx + 0.5, y, cz + 4.5);
        var difficulty = level.getDifficulty();
        server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);

        boolean needsMastery = "need_mastery".equals(StratolithSummon.problem(player, level, stone.above()));
        StrataAPI.unlock(player, StrataEpochs.id("first_core"));
        for (String e : StratolithSummon.REQUIRED) {
            StrataAPI.unlock(player, StrataEpochs.id("visited_" + e));
            StrataAPI.unlock(player, StrataEpochs.id("fossil_" + e));
            StrataAPI.unlock(player, StrataEpochs.id("mastered_" + e));
        }
        level.setBlockAndUpdate(stone.above(2), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        boolean needsSpace = "no_space".equals(StratolithSummon.problem(player, level, stone.above()));
        level.setBlockAndUpdate(stone.above(2), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        boolean summonOk = needsMastery && needsSpace && StratolithSummon.problem(player, level, stone.above()) == null;

        var boss = StratolithSummon.summon(level, stone.above());
        boss.setYRot(0);
        boss.setYHeadRot(0);
        boss.yBodyRot = 0;   // schaut nach Süden (+z): der Testspieler steht vorn
        float full = boss.getHealth();
        boss.hurt(level.damageSources().generic(), 20);
        boolean risingSafe = boss.isRising() && boss.getHealth() == full && boss.bossBar() != null;
        for (int i = 0; i < dev.strata.epochs.mob.Stratolith.RISE_TICKS + 2; i++) { boss.tickCount++; boss.tick(); }

        // Phase 1 -> 2 -> 3: Schicht fällt nach dem Abwurf (20 Ticks), Höhe schrumpft.
        float h0 = boss.getBbHeight();
        boss.setHealth(full * 0.6f);
        for (int i = 0; i < 25; i++) { boss.tickCount++; boss.tick(); }
        float h1 = boss.getBbHeight();
        boolean crystalDrop = !level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, boss.getBoundingBox().inflate(4),
                e -> e.getItem().is(EpochsContent.CRYSTAL_QUARTZ.get())).isEmpty();
        boss.setHealth(full * 0.3f);
        for (int i = 0; i < 25; i++) { boss.tickCount++; boss.tick(); }
        float h2 = boss.getBbHeight();
        boolean layers = boss.layer() == 2 && boss.getData(EpochsContent.STRATOLITH_LAYER.get()) == 2 && crystalDrop
                && h0 > h1 && h1 > h2 && Math.abs(h2 - 1.5f) < 0.01f
                && !level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, boss.getBoundingBox().inflate(4),
                        e -> e.getItem().is(EpochsContent.AMBER_SHARD.get())).isEmpty();

        // Zurückspulen: 9 s bei 70 Leben, dann Treffer auf 50 -> nach der Vorwarnung wieder 70.
        boss.setHealth(70);
        for (int i = 0; i < 200; i++) { boss.tickCount++; boss.tick(); }
        boss.setHealth(50);
        boolean started = boss.triggerRewind();
        for (int i = 0; i < 45; i++) { boss.tickCount++; boss.tick(); }
        boolean rewound = started && boss.getHealth() >= 69.9f;

        // Konter: zweiter Stratolith in Phase 3, Spitzhacke von hinten wirkungslos, von vorn bricht es ab.
        var second = StratolithSummon.summon(level, stone.above().offset(0, 0, 0));
        second.setYRot(0);
        second.setYHeadRot(0);
        second.yBodyRot = 0;
        for (int i = 0; i < dev.strata.epochs.mob.Stratolith.RISE_TICKS + 2; i++) { second.tickCount++; second.tick(); }
        second.setHealth(70);
        for (int i = 0; i < 200; i++) { second.tickCount++; second.tick(); }
        second.setHealth(50);
        second.triggerRewind();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
        // Die KI (Umherstreifen, Umschauen) dreht ihn in den Vorwärts-Ticks zufällig: Blickrichtung vor
        // jedem Treffer fest setzen und den Spieler relativ zu ihr stellen, sonst entscheidet der Zufall über inFront.
        faceSouth(second);
        player.setPos(second.getX(), second.getY(), second.getZ() - 3);   // hinten
        second.hurt(player.damageSources().playerAttack(player), 1);
        boolean backNoEffect = second.rewindPhase() == dev.strata.epochs.mob.StrataAbility.Phase.WARNING;
        for (int i = 0; i < 11; i++) { second.tickCount++; second.tick(); }   // Unverwundbarkeit nach dem Treffer abwarten (Feld ab 26.3 privat)
        faceSouth(second);
        player.setPos(second.getX(), second.getY(), second.getZ() + 3);   // vorn
        second.hurt(player.damageSources().playerAttack(player), 1);
        boolean countered = backNoEffect && second.rewindPhase() != dev.strata.epochs.mob.StrataAbility.Phase.WARNING
                && second.isStunned();
        for (int i = 0; i < 45; i++) { second.tickCount++; second.tick(); }
        countered &= second.getHealth() < 55;
        second.discard();

        // Herz der Tiefenzeit in der zweiten Hand: größerer Radius, Transplantator ohne Fossil.
        int base = StratolithSummon.transplantRadius(player);
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
                new net.minecraft.world.item.ItemStack(EpochsContent.HEART_OF_DEEP_TIME.get()));
        boolean heart = StratolithSummon.hasHeart(player) && StratolithSummon.transplantRadius(player) > base;
        player.getInventory().clearContent();
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, net.minecraft.world.item.ItemStack.EMPTY);

        // Sieg: Kodex-Knoten für Spieler in der Nähe.
        player.setPos(boss.getX() + 2, boss.getY(), boss.getZ());
        for (int i = 0; i < 11; i++) { boss.tickCount++; boss.tick(); }
        boss.hurt(player.damageSources().playerAttack(player), 10_000);
        boolean codex = !boss.isAlive() && StrataAPI.hasNode(player, StrataEpochs.id("stratolith"));
        level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(stone).inflate(12))
                .forEach(net.minecraft.world.entity.Entity::discard);
        level.getEntitiesOfClass(dev.strata.epochs.mob.StrataMob.class, new net.minecraft.world.phys.AABB(stone).inflate(20))
                .forEach(net.minecraft.world.entity.Entity::discard);
        level.setBlockAndUpdate(stone, net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState());
        server.setDifficulty(difficulty, true);

        boolean loot;
        try (var in = SelfTest.class.getResourceAsStream("/data/strata_epochs/loot_table/entities/stratolith.json")) {
            String text = in == null ? "" : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            loot = text.contains("strata_epochs:heart_of_deep_time") && text.contains("minecraft:killed_by_player");
        } catch (java.io.IOException e) {
            loot = false;
        }
        boolean sounds = true;   // eigene Boss-Klänge: in sounds.json eingetragen (Dateien prüft testMob)
        for (String kind : List.of("ambient", "hurt", "death", "rise", "rewind", "slam")) {
            if (!SOUNDS.containsKey("entity.stratolith." + kind)) sounds = false;
        }
        List<String> missing = new ArrayList<>();
        for (String key : List.of("entity.strata_epochs.stratolith", "item.strata_epochs.layer_core",
                "item.strata_epochs.heart_of_deep_time", "message.strata_epochs.stratolith.shed.1",
                "message.strata_epochs.stratolith.shed.2", "message.strata_epochs.stratolith.rewind",
                "message.strata_epochs.stratolith.broken", "message.strata_epochs.stratolith.need_mastery",
                "message.strata_epochs.stratolith.no_space", "message.strata_epochs.stratolith.peaceful",
                "message.strata_epochs.stratolith.top",
                "subtitles.strata_epochs.stratolith.ambient", "subtitles.strata_epochs.stratolith.hurt",
                "subtitles.strata_epochs.stratolith.death", "subtitles.strata_epochs.stratolith.rise",
                "subtitles.strata_epochs.stratolith.rewind", "subtitles.strata_epochs.stratolith.slam")) {
            for (String lang : List.of("de_de", "en_us")) if (!LANG.get(lang).contains(key)) missing.add(lang + ":" + key);
        }
        boolean ok = summonOk && risingSafe && layers && rewound && countered && heart && codex && loot && sounds && missing.isEmpty();
        StrataEpochs.LOG.info("[Strata-Test] Stratolith: Beschwörung={}, Aufsteigen geschützt={}, Schichten={} (Höhe {}/{}/{}), "
                + "Zurückspulen={}, Konter von vorn={}, Herz={}, Kodex={}, Beute={}, Klänge={}, Texte fehlen={} -> {}",
                summonOk, risingSafe, layers, h0, h1, h2, rewound, countered, heart, codex, loot, sounds, missing, ok ? "OK" : "FEHLER");
        return ok ? 0 : 1;
    }

    /**
     * Resonanzling: Gleichklang hält die Uhr an (auch eine laufende), nicht beim Folgen; weiche Blöcke stören nicht,
     * Anschlagen nur warnt, Abbau verstimmt; erster Treffer warnt, zweiter verstimmt; Selbstverteidigung zählt nicht;
     * Einstimmen speichert die Heimat; der Splitter lässt die Uhr ruhen und verbraucht eine von 3 Ladungen.
     */
    private static int testResonantShard(MinecraftServer server) {
        String epoch = StrataEpochs.id("crystal_age");
        ServerLevel level = StrataAPI.epochs(server).get(epoch).dimension().map(d -> Mc.level(server, d)).orElse(null);
        if (level == null) {
            StrataEpochs.LOG.error("[Strata-Test] Resonanzling: Kristallzeit fehlt");
            return 1;
        }
        BlockPos ground = EpochTravel.prepareArrival(level, 640, 640);
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("strata_resonance_test".getBytes()), "strata_resonance_test"));
        player.setPos(ground.getX() + 0.5, ground.getY(), ground.getZ() + 2.5);
        var shards = dev.strata.epochs.mob.ResonantShard.TEST_PLAYERS;
        shards.add(player);
        java.util.function.Function<BlockPos, dev.strata.epochs.mob.ResonantShard> spawn = at -> {
            var s = new dev.strata.epochs.mob.ResonantShard(EpochsMobs.RESONANT_SHARD.get(), level);
            s.setPos(at.getX() + 0.5, at.getY() + 0.6, at.getZ() + 0.5);
            level.addFreshEntity(s);
            return s;
        };
        java.util.function.BiConsumer<net.minecraft.world.entity.Mob, Integer> run = (m, n) -> {
            for (int i = 0; i < n; i++) { m.tickCount++; m.tick(); }
        };
        var a = spawn.apply(ground);
        run.accept(a, 25);
        boolean paused = Solidification.isPaused(player) && Solidification.factor(player) == 0.0;
        player.setData(EpochsContent.VISIT.get(), player.getData(EpochsContent.VISIT.get()).withTicks(100_000));
        player.tickCount = 0;
        Solidification.onPlayerTick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
        boolean clockStill = player.getData(EpochsContent.VISIT.get()).ticks() == 100_000;   // auch tief in der Verfestigung

        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(EpochsContent.CRYSTAL_QUARTZ.get()));
        run.accept(a, 3);
        boolean noCalmWhileFollowing = !a.calm();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
        run.accept(a, 3);

        BlockPos near = a.blockPosition().offset(1, -1, 0);   // relativ zur jetzigen Stelle: er driftet bis zu 3 Blöcke
        var stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        // Die Suche nach Resonanzlingen ist Vanilla; frisch erzeugte Testmobs findet sie je nach Version erst
        // später (Entities werden asynchron geladen). Geprüft wird daher die Regel pro Resonanzling.
        boolean softIgnored = !a.nearbyBlock(player, near, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), true)
                && !a.nearbyBlock(player, near, net.minecraft.world.level.block.Blocks.CRAFTING_TABLE.defaultBlockState(), true)
                && !a.uneasy() && a.calm();
        boolean farIgnored = !a.nearbyBlock(player, a.blockPosition().offset(8, -1, 0), stone, true) && !a.uneasy();
        a.nearbyBlock(player, near, stone, false);
        boolean warned = a.uneasy() && a.calm() && !a.silent() && Solidification.messageHeld(player);   // Warnung bleibt lesbar
        a.nearbyBlock(player, near, stone, true);
        run.accept(a, dev.strata.epochs.mob.ResonantShard.WARNING_TICKS + 2);
        boolean silenced = a.silent() && !a.calm();

        var b = spawn.apply(ground.offset(0, 0, 24));
        run.accept(b, 2);
        boolean farHitIgnored = !b.nearbyHit(player, b.position().add(8, 0, 0)) && !b.uneasy();
        b.disturb(player, b.position(), dev.strata.epochs.mob.ResonantShard.Disturbance.HIT);
        run.accept(b, dev.strata.epochs.mob.ResonantShard.WARNING_TICKS + 2);
        boolean firstHitWarns = b.uneasy() && !b.silent();
        b.disturb(player, b.position(), dev.strata.epochs.mob.ResonantShard.Disturbance.HIT);
        run.accept(b, dev.strata.epochs.mob.ResonantShard.WARNING_TICKS + 2);
        boolean secondHitSilences = b.silent();

        // Echter Schadensweg (NeoForge-Ereignis): der erste Treffer kostet kein Leben, erst der zweite.
        var hit = spawn.apply(ground.offset(-24, 0, 0));
        run.accept(hit, 2);
        float full = hit.getHealth();
        dev.strata.epochs.mc.MobCompat.hurt(hit, level.damageSources().playerAttack(player), 3);
        boolean firstHitHarmless = hit.getHealth() == full && hit.uneasy();
        dev.strata.epochs.mc.MobCompat.hurt(hit, level.damageSources().playerAttack(player), 3);
        boolean secondHitHurts = hit.getHealth() < full;
        hit.discard();

        // setTarget verwirft ab 26.1 unverwundbare Ziele (simulierte Spieler), daher die Regel direkt.
        var attacker = new GeodeCrawler(EpochsMobs.GEODE_CRAWLER.get(), level);
        boolean selfDefense = !dev.strata.epochs.mob.ResonantShard.selfDefense(attacker)
                && dev.strata.epochs.mob.ResonantShard.targetsPlayer(player)
                && !dev.strata.epochs.mob.ResonantShard.targetsPlayer(attacker);

        var c = spawn.apply(ground.offset(24, 0, 0));
        run.accept(c, 2);
        c.tune();
        boolean tuned = c.tuned() && c.home().equals(c.blockPosition()) && c.isPersistenceRequired()
                && c.getData(EpochsContent.RESONANT_HOME.get()) == c.home().asLong();

        var other = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("strata_splinter_test".getBytes()), "strata_splinter_test"));
        other.setPos(ground.getX() + 40.5, ground.getY(), ground.getZ() + 40.5);
        var splinter = new net.minecraft.world.item.ItemStack(EpochsContent.RESONANT_SPLINTER.get());
        boolean before = Solidification.isPaused(other);
        dev.strata.epochs.mob.ResonantShard.useSplinter(other, splinter);
        boolean splinterOk = !before && Solidification.isPaused(other) && splinter.getMaxDamage() == 3
                && splinter.getDamageValue() == 1 && Mc.isOnCooldown(other, splinter);

        shards.remove(player);
        a.discard();
        b.discard();
        c.discard();

        boolean loot;
        try (var in = SelfTest.class.getResourceAsStream("/data/strata_epochs/loot_table/entities/resonant_shard.json")) {
            String text = in == null ? "" : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            loot = text.contains("strata_epochs:resonant_splinter") && text.contains("minecraft:killed_by_player");
        } catch (java.io.IOException e) {
            loot = false;
        }
        boolean sounds = true;
        for (String kind : List.of("ambient", "hurt", "death", "resonate", "dissonance", "shriek")) {
            if (!SOUNDS.containsKey("entity.resonant_shard." + kind)) sounds = false;
        }
        List<String> missing = new ArrayList<>();
        for (String key : List.of("entity.strata_epochs.resonant_shard", "item.strata_epochs.resonant_splinter",
                "item.strata_epochs.resonant_shard_spawn_egg", "message.strata_epochs.solidify.paused",
                "message.strata_epochs.resonant_shard.uneasy", "message.strata_epochs.resonant_shard.silent",
                "message.strata_epochs.resonant_shard.still_silent", "message.strata_epochs.resonant_shard.tuned",
                "message.strata_epochs.resonant_splinter", "message.strata_epochs.resonant_splinter.no_epoch", "subtitles.strata_epochs.resonant_shard.ambient",
                "subtitles.strata_epochs.resonant_shard.hurt", "subtitles.strata_epochs.resonant_shard.death",
                "subtitles.strata_epochs.resonant_shard.resonate", "subtitles.strata_epochs.resonant_shard.dissonance",
                "subtitles.strata_epochs.resonant_shard.shriek")) {
            for (String lang : List.of("de_de", "en_us")) if (!LANG.get(lang).contains(key)) missing.add(lang + ":" + key);
        }
        boolean ok = paused && clockStill && noCalmWhileFollowing && softIgnored && farIgnored && warned && silenced
                && firstHitWarns && secondHitSilences && farHitIgnored && firstHitHarmless && secondHitHurts && selfDefense && tuned && splinterOk && loot && sounds && missing.isEmpty();
        StrataEpochs.LOG.info("[Strata-Test] Resonanzling: Ruhe={}, Uhr steht={}, folgt ohne Ruhe={}, weich egal={}, weit egal={}, "
                + "Anschlagen warnt={}, Abbau verstimmt={}, 1. Treffer warnt={}, 2. verstimmt={}, Treffer weit egal={}, "
                + "1. Treffer ohne Schaden={}, 2. mit Schaden={}, Selbstverteidigung={}, "
                + "eingestimmt={}, Splitter={}, Beute={}, Klänge={}, Texte fehlen={} -> {}",
                paused, clockStill, noCalmWhileFollowing, softIgnored, farIgnored, warned, silenced, firstHitWarns,
                secondHitSilences, farHitIgnored, firstHitHarmless, secondHitHurts, selfDefense, tuned, splinterOk, loot, sounds, missing, ok ? "OK" : "FEHLER");
        return ok ? 0 : 1;
    }

    /**
     * Bernsteinmotte und Harzfalle: Falle nur mit Lichtquelle daneben; eine Motte fliegt zur beleuchteten Falle, landet
     * und wird eingeschlossen; die Falle härtet im Zufallstick aus und gibt Bernstein mit Einschluss, der beim
     * Transplantator als Fossil der Sporenzeit zählt. Sprinten erschreckt sie (Staub blendet, sie flieht), Schleichen nie.
     */
    private static int testAmberMoth(MinecraftServer server) {
        String epoch = StrataEpochs.id("spore_age");
        ServerLevel level = StrataAPI.epochs(server).get(epoch).dimension().map(d -> Mc.level(server, d)).orElse(null);
        if (level == null) {
            StrataEpochs.LOG.error("[Strata-Test] Bernsteinmotte: Sporenzeit fehlt");
            return 1;
        }
        BlockPos ground = EpochTravel.prepareArrival(level, -640, 640);
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("strata_moth_test".getBytes()), "strata_moth_test"));
        player.setPos(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
        var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        var glow = net.minecraft.world.level.block.Blocks.GLOWSTONE.defaultBlockState();
        java.util.function.Function<net.minecraft.world.phys.Vec3, dev.strata.epochs.mob.AmberMoth> spawn = at -> {
            var m = new dev.strata.epochs.mob.AmberMoth(EpochsMobs.AMBER_MOTH.get(), level);
            m.setPos(at.x, at.y, at.z);
            level.addFreshEntity(m);
            return m;
        };
        java.util.function.BiConsumer<net.minecraft.world.entity.Mob, Integer> run = (m, n) -> {
            for (int i = 0; i < n && !m.isRemoved(); i++) { m.tickCount++; m.tick(); }
        };
        // Freier Raum für die Versuche (3 hoch), damit Höhlenwände nicht stören.
        for (BlockPos p : BlockPos.betweenClosed(ground.offset(-1, 0, -4), ground.offset(9, 3, 4))) level.setBlockAndUpdate(p, air);
        for (BlockPos p : BlockPos.betweenClosed(ground.offset(-1, -1, -4), ground.offset(9, -1, 4))) {
            level.setBlockAndUpdate(p, net.minecraft.world.level.block.Blocks.MUD.defaultBlockState());
        }

        // Falle direkt angeflogen: ohne Licht keine Falle, mit Leuchtstein daneben schon.
        BlockPos trapA = ground.offset(2, 0, -2);
        level.setBlockAndUpdate(trapA, EpochsContent.RESIN_TRAP.get().defaultBlockState());
        boolean unlit = !dev.strata.epochs.mob.AmberMoth.lit(level, trapA);
        level.setBlockAndUpdate(trapA.west(), glow);
        boolean lit = unlit && dev.strata.epochs.mob.AmberMoth.lit(level, trapA);
        var a = spawn.apply(net.minecraft.world.phys.Vec3.atCenterOf(trapA.above()));
        a.landOn(trapA);
        run.accept(a, dev.strata.epochs.mob.AmberMoth.LAND_TICKS + 2);
        boolean caught = a.isRemoved() && level.getBlockState(trapA).is(EpochsContent.RESIN_TRAP_MOTH.get());

        // Echter Anflug: Motte 3 Blöcke neben einer beleuchteten Falle findet sie selbst.
        BlockPos trapB = ground.offset(7, 0, 2);
        level.setBlockAndUpdate(trapB, EpochsContent.RESIN_TRAP.get().defaultBlockState());
        level.setBlockAndUpdate(trapB.east(), glow);
        var b = spawn.apply(net.minecraft.world.phys.Vec3.atCenterOf(trapB.above()).add(-3, 0.5, 0));
        run.accept(b, 200);
        boolean flewIn = b.isRemoved() && level.getBlockState(trapB).is(EpochsContent.RESIN_TRAP_MOTH.get());

        // Aushärten im Zufallstick, dann Ernte.
        var block = level.getBlockState(trapB);
        for (int i = 0; i < 200 && block.is(EpochsContent.RESIN_TRAP_MOTH.get()); i++) {
            block.randomTick(level, trapB, level.getRandom());
            block = level.getBlockState(trapB);
        }
        boolean hardened = block.is(EpochsContent.RESIN_TRAP_HARDENED.get());
        // In der Oberwelt härtet sie nie aus (keine AFK-Farm in transplantierten Gebieten).
        ServerLevel overworld = server.overworld();
        BlockPos outside = new BlockPos(-9100, Mc.minY(overworld) + 2, 9100);
        overworld.getChunk(outside);
        overworld.setBlockAndUpdate(outside, EpochsContent.RESIN_TRAP_MOTH.get().defaultBlockState());
        for (int i = 0; i < 200; i++) overworld.getBlockState(outside).randomTick(overworld, outside, overworld.getRandom());
        boolean onlyInEpoch = overworld.getBlockState(outside).is(EpochsContent.RESIN_TRAP_MOTH.get());
        overworld.setBlockAndUpdate(outside, air);
        dev.strata.epochs.mob.AmberMoth.harvest(level, trapB, player);
        boolean harvested = level.getBlockState(trapB).isAir()
                && player.getInventory().contains(new net.minecraft.world.item.ItemStack(EpochsContent.AMBER_INCLUSION.get()));
        player.getInventory().clearContent();

        var inclusion = new net.minecraft.world.item.ItemStack(EpochsContent.AMBER_INCLUSION.get());
        boolean fossil = Transplanter.acceptsFossil(inclusion, epoch, FossilPrep.fossilFor(epoch))
                && Transplanter.acceptsFossil(new net.minecraft.world.item.ItemStack(EpochsContent.FERN_FOSSIL.get()), epoch, FossilPrep.fossilFor(epoch))
                && !Transplanter.acceptsFossil(inclusion, StrataEpochs.id("crystal_age"), FossilPrep.fossilFor(StrataEpochs.id("crystal_age")));

        // Erschrecken: Sprinten ja, Schleichen nie; Staub blendet Spieler in der Nähe, danach flieht sie.
        player.setSprinting(true);
        boolean sprintScares = dev.strata.epochs.mob.AmberMoth.scares(player);
        player.setShiftKeyDown(true);
        boolean sneakSafe = sprintScares && !dev.strata.epochs.mob.AmberMoth.scares(player);
        player.setShiftKeyDown(false);
        player.setSprinting(false);
        dev.strata.epochs.mob.AmberMoth.TEST_PLAYERS.add(player);
        var c = spawn.apply(player.position().add(1.2, 0.8, 0));
        c.scare(player.position());
        boolean warned = c.warning() && !player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS);
        run.accept(c, dev.strata.epochs.mob.AmberMoth.WARNING_TICKS + 2);
        boolean dusted = warned && player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS) && c.fleeing();
        dev.strata.epochs.mob.AmberMoth.TEST_PLAYERS.remove(player);
        player.removeAllEffects();
        c.discard();
        if (!b.isRemoved()) b.discard();
        level.setBlockAndUpdate(trapA, air);

        boolean loot;
        try (var in = SelfTest.class.getResourceAsStream("/data/strata_epochs/loot_table/blocks/resin_trap_hardened.json");
             var recipe = SelfTest.class.getResourceAsStream("/data/strata_epochs/recipe/resin_trap.json")) {
            String text = in == null ? "" : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            loot = text.contains("strata_epochs:amber_inclusion") && recipe != null;
        } catch (java.io.IOException e) {
            loot = false;
        }
        boolean sounds = true;
        for (String kind : List.of("ambient", "hurt", "death", "dust")) {
            if (!SOUNDS.containsKey("entity.amber_moth." + kind)) sounds = false;
        }
        List<String> missing = new ArrayList<>();
        for (String key : List.of("entity.strata_epochs.amber_moth", "item.strata_epochs.amber_moth_spawn_egg",
                "block.strata_epochs.resin_trap", "block.strata_epochs.resin_trap_moth", "block.strata_epochs.resin_trap_hardened",
                "item.strata_epochs.amber_inclusion", "subtitles.strata_epochs.amber_moth.ambient",
                "subtitles.strata_epochs.amber_moth.hurt", "subtitles.strata_epochs.amber_moth.death",
                "subtitles.strata_epochs.amber_moth.dust")) {
            for (String lang : List.of("de_de", "en_us")) if (!LANG.get(lang).contains(key)) missing.add(lang + ":" + key);
        }
        boolean ok = lit && caught && flewIn && hardened && onlyInEpoch && harvested && fossil && sneakSafe && dusted && loot && sounds && missing.isEmpty();
        StrataEpochs.LOG.info("[Strata-Test] Bernsteinmotte: Licht an der Falle={}, gefangen={}, Anflug={}, ausgehärtet={}, nur in Epoche={}, Ernte={}, "
                + "Fossil-Tag={}, Schleichen sicher={}, Staub+Flucht={}, Beute+Rezept={}, Klänge={}, Texte fehlen={} -> {}",
                lit, caught, flewIn, hardened, onlyInEpoch, harvested, fossil, sneakSafe, dusted, loot, sounds, missing, ok ? "OK" : "FEHLER");
        return ok ? 0 : 1;
    }

    /**
     * Harzschnecke: legt Harzspur nur auf freiem Boden und bis zu ihrer eigenen Grenze (gefüttert höher); die Spur bremst,
     * härtet in einer Epoche zur Bernsteinplatte aus (außerhalb löst sie sich auf), die Bernstein gibt; ein Treffer schickt sie ins Gehäuse, wo sie nur noch wenig Schaden nimmt.
     */
    private static int testResinSnail(MinecraftServer server) {
        String epoch = StrataEpochs.id("spore_age");
        ServerLevel level = StrataAPI.epochs(server).get(epoch).dimension().map(d -> Mc.level(server, d)).orElse(null);
        if (level == null) {
            StrataEpochs.LOG.error("[Strata-Test] Harzschnecke: Sporenzeit fehlt");
            return 1;
        }
        BlockPos ground = EpochTravel.prepareArrival(level, 640, -640);
        var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        for (BlockPos p : BlockPos.betweenClosed(ground.offset(-12, 0, -12), ground.offset(12, 2, 12))) level.setBlockAndUpdate(p, air);
        for (BlockPos p : BlockPos.betweenClosed(ground.offset(-12, -1, -12), ground.offset(12, -1, 12))) {
            level.setBlockAndUpdate(p, net.minecraft.world.level.block.Blocks.MUD.defaultBlockState());
        }
        var snail = new dev.strata.epochs.mob.ResinSnail(EpochsMobs.RESIN_SNAIL.get(), level);
        snail.setPos(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
        level.addFreshEntity(snail);
        snail.tickCount++;
        snail.tick();
        var trail = EpochsContent.RESIN_TRAIL.get();
        boolean laid = snail.layTrail(level) && level.getBlockState(ground).is(trail) && !snail.layTrail(level);
        boolean sticky = trail.getSpeedFactor() < 1.0f && trail.getJumpFactor() < 1.0f;

        // Grenze pro Schnecke: 4 eigene Stücke, dann keine neue; gefüttert 6; ein abgebautes Stück macht Platz.
        java.util.function.IntFunction<Boolean> layAt = dx -> {
            snail.setPos(ground.getX() + 0.5 + dx, ground.getY(), ground.getZ() + 0.5);
            return snail.layTrail(level);
        };
        boolean four = layAt.apply(2) && layAt.apply(4) && layAt.apply(6);   // mit dem ersten Stück: 4
        boolean capped = four && !layAt.apply(8) && snail.ownTrail(level).size() == dev.strata.epochs.mob.ResinSnail.TRAIL_CAP;
        snail.feed();
        boolean fedMore = snail.fed() && layAt.apply(8) && layAt.apply(10) && !layAt.apply(-2)
                && dev.strata.epochs.mob.ResinSnail.isFood(new net.minecraft.world.item.ItemStack(EpochsContent.RESIN_FERN_SPORES.get()));
        level.setBlockAndUpdate(ground.offset(10, 0, 0), air);                 // Spieler baut ein Stück ab
        boolean freed = fedMore && layAt.apply(-2);
        snail.setPos(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);

        // Außerhalb einer Epoche löst sich die Spur auf statt auszuhärten.
        ServerLevel overworld = server.overworld();
        BlockPos outside = new BlockPos(9300, Mc.minY(overworld) + 3, -9300);
        overworld.getChunk(outside);
        overworld.setBlockAndUpdate(outside.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        overworld.setBlockAndUpdate(outside, trail.defaultBlockState());
        for (int i = 0; i < 200 && overworld.getBlockState(outside).is(trail); i++) {
            overworld.getBlockState(outside).randomTick(overworld, outside, overworld.getRandom());
        }
        boolean dissolves = overworld.getBlockState(outside).isAir();
        overworld.setBlockAndUpdate(outside.below(), air);

        // Aushärten im Zufallstick zur Bernsteinplatte.
        var state = level.getBlockState(ground);
        for (int i = 0; i < 200 && state.is(trail); i++) {
            state.randomTick(level, ground, level.getRandom());
            state = level.getBlockState(ground);
        }
        boolean hardened = state.is(EpochsContent.AMBER_PLATE.get());

        // Gehäuse: der erste Treffer schickt sie hinein, danach nimmt sie nur noch wenig Schaden; nach 5 s kommt sie heraus.
        float full = snail.getHealth();
        dev.strata.epochs.mc.MobCompat.hurt(snail, level.damageSources().generic(), 3);
        boolean retreated = snail.inShell();
        // Im Gehäuse sperrt ein Ziel jede Bewegung: auch ein gesetzter Weg (etwa vom Locken) wird verworfen.
        var shellAt = snail.position();
        snail.getNavigation().moveTo(snail.getX() + 5, snail.getY(), snail.getZ(), 1.0);
        for (int i = 0; i < 11; i++) { snail.tickCount++; snail.tick(); }
        double moved = Math.hypot(snail.getX() - shellAt.x, snail.getZ() - shellAt.z);   // waagerecht (die Spur hebt sie um 1/16)
        boolean stays = moved < 0.05 && snail.getNavigation().isDone() && snail.shellLocked();
        float before = snail.getHealth();
        dev.strata.epochs.mc.MobCompat.hurt(snail, level.damageSources().generic(), 4);
        boolean shielded = retreated && before < full && before - snail.getHealth() < 2.0f && before - snail.getHealth() > 0f;
        for (int i = 0; i < dev.strata.epochs.mob.ResinSnail.SHELL_TICKS; i++) { snail.tickCount++; snail.tick(); }
        boolean emerged = !snail.inShell();
        snail.discard();
        for (BlockPos p : BlockPos.betweenClosed(ground.offset(-12, 0, -12), ground.offset(12, 0, 12))) level.setBlockAndUpdate(p, air);

        boolean loot;
        try (var in = SelfTest.class.getResourceAsStream("/data/strata_epochs/loot_table/blocks/amber_plate.json")) {
            String text = in == null ? "" : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            loot = text.contains("strata_epochs:amber_shard");
        } catch (java.io.IOException e) {
            loot = false;
        }
        boolean sounds = true;
        for (String kind : List.of("ambient", "hurt", "death", "retreat")) {
            if (!SOUNDS.containsKey("entity.resin_snail." + kind)) sounds = false;
        }
        List<String> missing = new ArrayList<>();
        for (String key : List.of("entity.strata_epochs.resin_snail", "item.strata_epochs.resin_snail_spawn_egg",
                "block.strata_epochs.resin_trail", "block.strata_epochs.amber_plate",
                "subtitles.strata_epochs.resin_snail.ambient", "subtitles.strata_epochs.resin_snail.hurt",
                "subtitles.strata_epochs.resin_snail.death", "subtitles.strata_epochs.resin_snail.retreat")) {
            for (String lang : List.of("de_de", "en_us")) if (!LANG.get(lang).contains(key)) missing.add(lang + ":" + key);
        }
        boolean ok = laid && sticky && capped && freed && dissolves && hardened && shielded && stays && emerged && loot && sounds && missing.isEmpty();
        StrataEpochs.LOG.info("[Strata-Test] Harzschnecke: Spur={}, klebrig={}, Grenze={}, gefüttert mehr und Abbau macht Platz={}, außerhalb aufgelöst={}, ausgehärtet={}, "
                + "Gehäuse schützt={}, bleibt im Gehäuse stehen={}, kommt heraus={}, Beute={}, Klänge={}, Texte fehlen={} -> {}",
                laid, sticky, capped, freed, dissolves, hardened, shielded, stays, emerged, loot, sounds, missing, ok ? "OK" : "FEHLER");
        return ok ? 0 : 1;
    }

    /** Blick, Kopf und Körper nach Süden (+z), damit {@code inFront} im Test nicht von der KI-Drehung abhängt. */
    private static void faceSouth(net.minecraft.world.entity.Mob mob) {
        mob.setYRot(0);
        mob.setXRot(0);
        mob.setYHeadRot(0);
        mob.yBodyRot = 0;
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
        double maxLift = 0;
        boolean moved = false;
        boolean backTurned = false;
        for (int t = 0; t < 400 && !seen.contains(dev.strata.epochs.mob.EmberBurrower.State.EXPOSED); t++) {
            mob.setTarget(pig);
            if (!moved) pig.setPos(mob.getX() + 6, mob.getY(), mob.getZ());   // bis zum Abtauchen auf Abstand
            mob.tick();
            maxLift = Math.max(maxLift, pig.getDeltaMovement().y);
            seen.add(mob.state());
            if (mob.state() == dev.strata.epochs.mob.EmberBurrower.State.EMERGING && !backTurned) {
                double yaw = Math.toRadians(mob.getYRot());
                double fx = -Math.sin(yaw), fz = Math.cos(yaw);
                double tx = pig.getX() - mob.getX(), tz = pig.getZ() - mob.getZ(), len = Math.sqrt(tx * tx + tz * tz);
                backTurned = len > 0 && (fx * tx + fz * tz) / len < -0.35;     // Ziel liegt hinter ihm
            }
            if (!moved && mob.state() == dev.strata.epochs.mob.EmberBurrower.State.BURROWED) {
                pig.setPos(mob.getX() + 1.5, mob.getY(), mob.getZ());       // Ziel kommt in Reichweite
                moved = true;
            }
        }
        boolean launched = maxLift > 0.3;
        boolean hurt = pig.getHealth() < health;
        mob.discard();
        pig.discard();
        boolean ok = seen.containsAll(java.util.List.of(dev.strata.epochs.mob.EmberBurrower.State.DIGGING,
                dev.strata.epochs.mob.EmberBurrower.State.BURROWED, dev.strata.epochs.mob.EmberBurrower.State.TREMOR,
                dev.strata.epochs.mob.EmberBurrower.State.EMERGING, dev.strata.epochs.mob.EmberBurrower.State.EXPOSED))
                && hurt && launched && backTurned;
        StrataEpochs.LOG.info("[Strata-Test] Glutwühler: Zustände={}, Schaden={}, Wurf={}, Rücken zum Ziel={} -> {}",
                seen, hurt, launched, backTurned, ok ? "OK" : "FEHLER");
        return ok ? 0 : 1;
    }

    /**
     * Böden, an denen man jedes Biom erkennt (siehe epochs/worldgen/epochs.json). Ein zweiter Eintrag ist
     * der Fleckenblock (Magma zwischen Schwarzstein, Moos auf Schlamm).
     */
    private static final Map<String, List<String>> BIOME_FLOORS = Map.of(
            "crystal_hollows", List.of("minecraft:calcite"), "geode_vaults", List.of("minecraft:amethyst_block"),
            "rime_grotto", List.of("minecraft:packed_ice"), "spore_grotto", List.of("minecraft:mycelium"),
            "moss_marsh", List.of("minecraft:mud", "minecraft:moss_block", "minecraft:clay"), "glow_thicket", List.of("minecraft:warped_nylium", "minecraft:warped_wart_block"),
            "ash_wastes", List.of("strata_epochs:volcanic_ash"), "magma_rifts", List.of("minecraft:blackstone", "minecraft:magma_block"),
            "bone_hollow", List.of("minecraft:soul_soil"));

    /** Erlaubter Anteil des Fleckenblocks am Boden. */
    // Gemessen wird nur ein kleines Gebiet; je nach Welt lag der Magma-Anteil bisher zwischen 7 und 51 %.
    // Geprüft wird daher nur, dass es Flecken gibt und das Biom nicht ganz aus Magma besteht.
    private static final Map<String, double[]> PATCH_RANGE = Map.of(
            "magma_rifts", new double[] {0.03, 0.65}, "moss_marsh", new double[] {0.25, 0.85});

    /**
     * Mehrere Biome pro Epoche: Jedes Biom der Dimension braucht einen spürbaren Anteil, muss höchstens
     * 300 Blöcke vom Ankunftsort liegen, an seinen Böden die eigene Oberfläche haben, und eine Ankunft
     * dort steht auf sicherem Boden (kein Magma, keine Lava).
     */
    private static int testBiomes(MinecraftServer server) {
        int failures = 0;
        final int step = 32, range = 1536, maxDistance = 300;
        for (Map.Entry<String, Epoch> entry : StrataAPI.epochs(server).entrySet()) {
            if (entry.getValue().dimension().isEmpty()) continue;
            ServerLevel level = Mc.level(server, entry.getValue().dimension().get());
            if (level == null) continue;
            BlockPos origin = EpochTravel.prepareArrival(level, 8, 8);
            Map<String, Integer> counts = new java.util.TreeMap<>();
            for (var holder : level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes()) {
                counts.put(holder.getRegisteredName(), 0);
            }
            int cells = 2 * range / step;
            String[][] grid = new String[cells][cells];
            for (int i = 0; i < cells; i++) {
                for (int j = 0; j < cells; j++) {
                    String id = level.getUncachedNoiseBiome((-range + i * step) >> 2, 0, (-range + j * step) >> 2).getRegisteredName();
                    grid[i][j] = id;
                    counts.merge(id, 1, Integer::sum);
                }
            }
            // Pro Biom: der nächste Fundort (Abstand) und der nächste Ort mitten im Biom (für Boden und Ankunft).
            Map<String, BlockPos> nearest = new java.util.HashMap<>();
            Map<String, BlockPos> core = new java.util.HashMap<>();
            Map<String, List<BlockPos>> cores = new java.util.HashMap<>();
            for (int i = 1; i < cells - 1; i++) {
                for (int j = 1; j < cells - 1; j++) {
                    String id = grid[i][j];
                    BlockPos pos = new BlockPos(-range + i * step, origin.getY(), -range + j * step);
                    BlockPos best = nearest.get(id);
                    if (best == null || pos.distSqr(origin) < best.distSqr(origin)) nearest.put(id, pos);
                    boolean inside = id.equals(grid[i - 1][j]) && id.equals(grid[i + 1][j]) && id.equals(grid[i][j - 1]) && id.equals(grid[i][j + 1]);
                    if (inside) cores.computeIfAbsent(id, k -> new ArrayList<>()).add(pos);
                    BlockPos bestCore = core.get(id);
                    if (inside && (bestCore == null || pos.distSqr(origin) < bestCore.distSqr(origin))) core.put(id, pos);
                }
            }
            int samples = cells * cells;
            List<String> parts = new ArrayList<>();
            boolean ok = counts.size() >= 3;
            for (Map.Entry<String, Integer> c : counts.entrySet()) {
                String id = c.getKey();
                String name = id.substring(id.indexOf(':') + 1);
                double share = c.getValue() / (double) samples;
                BlockPos at = nearest.get(id);
                int dist = at == null ? -1 : (int) Math.sqrt(at.distSqr(origin));
                BlockPos mid = core.getOrDefault(id, at);
                // Böden aus den nächsten Kernpunkten sammeln, bis genug zusammenkommen (manche Chunks sind fast nur Fels).
                List<BlockPos> spots = new ArrayList<>(cores.getOrDefault(id, at == null ? List.of() : List.of(at)));
                spots.sort(java.util.Comparator.comparingDouble(q -> q.distSqr(origin)));
                // Am Ursprung bauen andere Selbsttests Bühnen aus Zeitgestein: diese Chunks nicht als Boden werten.
                List<BlockPos> away = spots.stream().filter(q -> Math.abs(q.getX()) > 48 || Math.abs(q.getZ()) > 48).toList();
                if (!away.isEmpty()) spots = new ArrayList<>(away);
                int[] floor = new int[3];
                Map<String, Integer> found = new java.util.HashMap<>();
                for (int k = 0; k < Math.min(16, spots.size()) && floor[0] < 600; k++) {
                    int[] f = floors(level, spots.get(k), id, BIOME_FLOORS.getOrDefault(name, List.of()), found);
                    for (int n = 0; n < 3; n++) floor[n] += f[n];
                }
                String foreign = found.keySet().stream().filter(k -> k.startsWith("@") && k.length() > 1).findFirst().orElse("");
                found.keySet().removeIf(k -> k.startsWith("@"));
                String common = found.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(3)
                        .map(f -> f.getKey().substring(f.getKey().lastIndexOf('.') + 1) + " " + f.getValue()).collect(java.util.stream.Collectors.joining("/"));
                double match = floor[0] == 0 ? 0 : (floor[1] + floor[2]) / (double) floor[0];
                boolean safe = mid != null && safeArrival(level, EpochTravel.prepareArrival(level, mid.getX(), mid.getZ()));
                // Ohne Flecken muss der Hauptboden die Hälfte ausmachen; mit Flecken zählt beides zusammen (≥ 60 %),
                // und der Fleckenanteil liegt im vorgesehenen Bereich (Magma nur in Rissen, Moos gut sichtbar).
                double patchShare = floor[0] == 0 ? 0 : floor[2] / (double) floor[0];
                double[] patchRange = PATCH_RANGE.get(name);
                boolean surfaceOk = patchRange == null ? match >= 0.5
                        : match >= 0.6 && patchShare >= patchRange[0] && patchShare <= patchRange[1];
                boolean biomeOk = share >= 0.08 && at != null && dist <= maxDistance && floor[0] >= 20 && surfaceOk && safe;
                if (!biomeOk) ok = false;
                if (!biomeOk && !foreign.isEmpty()) StrataEpochs.LOG.info("[Strata-Test] {}: fremder Boden {}", name, foreign.substring(1));
                String patch = floor[2] > 0 ? String.format(java.util.Locale.ROOT, " (Flecken %.0f%%)", 100.0 * floor[2] / floor[0]) : "";
                parts.add(String.format(java.util.Locale.ROOT, "%s %.0f%% ab %d Blöcken (bei %d %d), Boden %.0f%% von %d%s, meist %s, Ankunft %s%s",
                        name, share * 100, dist, at == null ? 0 : at.getX(), at == null ? 0 : at.getZ(),
                        match * 100, floor[0], patch, common, safe ? "sicher" : "UNSICHER", biomeOk ? "" : " (!)"));
            }
            // Peilung im Tor: findet vom Ankunftsort aus jedes andere Biom, Entfernung passt zum Raster oben.
            String here = BiomeBearing.biomeAt(level, origin);
            var targets = BiomeBearing.scan(level, origin);
            boolean winds = BiomeBearing.wind(0, -10).equals("n") && BiomeBearing.wind(10, 0).equals("e")
                    && BiomeBearing.wind(-10, 10).equals("sw") && BiomeBearing.wind(7, -7).equals("ne")
                    // Relativ zur Blickrichtung: Yaw 180 = nach Norden, Yaw 0 = nach Süden.
                    && BiomeBearing.side(0, -10, 180).equals("ahead") && BiomeBearing.side(10, 0, 180).equals("right")
                    && BiomeBearing.side(10, 0, 0).equals("left") && BiomeBearing.side(-7, -7, 270).equals("behind_left");
            // Gecachte Peilung: enthält alle Biome (auch das eigene des Feldes), damit tell() an Grenzen richtig filtert.
            winds &= BiomeBearing.scanCached(level, origin).size() == counts.size();
            long t0 = System.nanoTime();
            for (int r = 0; r < 5; r++) BiomeBearing.scan(level, origin.offset(1000 + 512 * r, 0, 1000));
            long scanUs = (System.nanoTime() - t0) / 5_000;   // Mittel über 5 volle Suchen, in Mikrosekunden
            long scanMs = scanUs / 1000;
            List<String> missingKeys = new ArrayList<>();
            for (String key : BiomeBearing.translationKeys(new ArrayList<>(counts.keySet()))) {
                for (String lang : List.of("de_de", "en_us")) if (!LANG.get(lang).contains(key)) missingKeys.add(lang + ":" + key);
            }
            winds &= missingKeys.isEmpty() && scanMs < 200;
            boolean bearing = winds && targets.size() == counts.size() - 1;
            for (var t : targets) {
                // Die Peilung tastet feiner (16er-Schritte) als dieses Raster (32er): näher ist erlaubt, wenn dort
                // wirklich das Biom liegt, weiter weg als das Raster aber nicht.
                BlockPos n = nearest.get(t.biome());
                boolean there = BiomeBearing.biomeAt(level, origin.offset(t.dx(), 0, t.dz())).equals(t.biome());
                if (n == null || !there || t.distance() > Math.sqrt(n.distSqr(origin)) + 48) bearing = false;
            }
            if (!bearing) ok = false;
            parts.add("Peilung " + targets.stream().map(t -> t.biome().substring(t.biome().indexOf(':') + 1) + " " + t.distance() + " " + t.wind())
                    .collect(java.util.stream.Collectors.joining("/")) + " (hier " + here.substring(here.indexOf(':') + 1) + ", " + scanUs + " µs je Suche"
                    + (missingKeys.isEmpty() ? "" : ", Texte fehlen " + missingKeys) + ")" + (bearing ? "" : " (!)"));
            // Knochensenke: Netherfossilien (Biom-Tag has_structure/nether_fossil) müssen in der Nähe liegen.
            BlockPos hollow = core.getOrDefault(StrataEpochs.id("bone_hollow"), nearest.get(StrataEpochs.id("bone_hollow")));
            if (hollow != null) {
                boolean[] found = {false};
                var source = server.createCommandSourceStack().withSuppressedOutput().withLevel(level)
                        .withPosition(net.minecraft.world.phys.Vec3.atCenterOf(hollow))
                        .withCallback((success, result) -> found[0] = success);
                server.getCommands().performPrefixedCommand(source, "locate structure minecraft:nether_fossil");
                if (!found[0]) ok = false;
                parts.add("Netherfossilien " + (found[0] ? "gefunden" : "FEHLEN (!)"));
            }
            if (!ok) failures++;
            StrataEpochs.LOG.info("[Strata-Test] Biome {}: {} -> {}", entry.getKey(), String.join(", ", parts), ok ? "OK" : "FEHLER");
        }
        return failures;
    }

    /**
     * Böden im Chunk um {@code at}, die im Biom {@code biome} liegen: {gesamt, erwartete Blöcke außer dem Fleckenblock, Fleckenblock (zweiter Eintrag)}.
     * Boden = fester Block, darüber nichts Festes und keine Flüssigkeit.
     */
    private static int[] floors(ServerLevel level, BlockPos at, String biome, List<String> expected, Map<String, Integer> found) {
        ChunkAccess chunk = level.getChunk(at.getX() >> 4, at.getZ() >> 4);
        List<Block> blocks = expected.stream().map(dev.strata.epochs.mc.MobCompat::block).toList();
        Block second = blocks.size() < 2 ? null : blocks.get(1);
        int[] result = new int[3];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                net.minecraft.world.level.block.state.BlockState above = chunk.getBlockState(pos.set(minX + x, Mc.maxY(level) - 1, minZ + z));
                for (int y = Mc.maxY(level) - 2; y > Mc.minY(level); y--) {
                    net.minecraft.world.level.block.state.BlockState state = chunk.getBlockState(pos.set(minX + x, y, minZ + z));
                    // Boden: kein Luft-/Flüssigkeits-/Pflanzenblock (Schlamm zählt, obwohl er nicht ganz voll ist),
                    // darüber Luft oder etwas Ersetzbares (Gras, Teppich), keine Flüssigkeit.
                    boolean ground = !state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced();
                    boolean open = (above.isAir() || above.canBeReplaced()) && above.getFluidState().isEmpty();
                    // Zeitgestein ist das Grundgestein der Epoche und liegt an einzelnen Stellen offen: neutral, zählt nicht mit.
                    if (ground && open && !state.is(net.minecraft.world.level.block.Blocks.BEDROCK)
                            && !state.is(EpochsContent.CHRONOSTONE.get())
                            && level.getBiome(pos.above()).getRegisteredName().equals(biome)) {
                        result[0]++;
                        found.merge(state.getBlock().getDescriptionId(), 1, Integer::sum);
                        if (second != null && state.is(second)) result[2]++;
                        else if (blocks.stream().anyMatch(state::is)) result[1]++;
                        else if (!found.containsKey("@")) {
                            // Erste fremde Bodenstelle merken (für die Fehlersuche): Block, darüber, zwei darüber.
                            found.put("@" + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + state.getBlock().getDescriptionId()
                                    + " < " + above.getBlock().getDescriptionId() + " < "
                                    + chunk.getBlockState(pos.above().above()).getBlock().getDescriptionId(), 0);
                            found.put("@", 0);
                        }
                    }
                    above = state;
                }
            }
        }
        return result;
    }

    /** Ankunft: kein Magma unter Spieler und Tor, keine Lava in der Nähe. */
    private static boolean safeArrival(ServerLevel level, BlockPos feet) {
        for (int dx = -2; dx <= 4; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos p = feet.offset(dx, dy, dz);
                    if (level.getFluidState(p).is(net.minecraft.tags.FluidTags.LAVA)) return false;
                    if (dy == -1 && dx >= -1 && dx <= 3 && Math.abs(dz) <= 1 && EpochTravel.harmful(level.getBlockState(p))) return false;
                }
            }
        }
        return true;
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
