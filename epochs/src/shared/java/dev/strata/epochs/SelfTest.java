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
    private static final Map<String, double[]> PATCH_RANGE = Map.of(
            "magma_rifts", new double[] {0.10, 0.40}, "moss_marsh", new double[] {0.25, 0.85});

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
                BlockPos n = nearest.get(t.biome());
                if (n == null || Math.abs(t.distance() - Math.sqrt(n.distSqr(origin))) > 48) bearing = false;
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
