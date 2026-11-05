package dev.strata.epochs;

import dev.strata.core.Landscape;
import dev.strata.epochs.mc.MobCompat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Eine laufende Transplantation: verwandelt eine Scheibe um den Transplantator in die Landschaft einer Epoche.
 *
 * <p>Läuft in Phasen und mit festem Budget pro Tick (Config {@code transplanter.blocks_per_tick}):
 * <ol>
 *   <li><b>Schichten:</b> Spalte für Spalte von innen nach außen. Oberfläche, Unterschicht und Gestein werden ersetzt,
 *       aber nur Blöcke aus dem Tag {@code strata_epochs:transplantable} (Bauten bleiben unberührt).
 *       Ein Teil der Oberflächenblöcke steigt sichtbar als Block-Display aus dem Boden und klappt nach oben.</li>
 *   <li><b>Biom:</b> per {@code /fillbiome} in 4 × 4-Säulen, damit Nebel, Farben und Mob-Spawns stimmen.</li>
 *   <li><b>Wachsen:</b> die Features der Epoche (Riesenpilze, Geoden, Basaltsäulen) erscheinen nacheinander.</li>
 * </ol>
 * Es wird nur in geladenen Chunks gearbeitet; ist eine Spalte nicht geladen, wartet sie, bis sie es ist.
 * Laufende Transplantationen werden nicht gespeichert: Ein Neustart bricht sie ab, das Gebiet bleibt halb fertig.
 */
public final class TransplantJob {

    public enum Phase { LAYERS, BIOME, GROW, DONE }

    private static final TagKey<Block> TRANSPLANTABLE = MobCompat.blockTag(StrataEpochs.id("transplantable"));
    private static final int UNDER_LAYERS = 3;
    private static final int SEARCH_DOWN = 8;
    private static final int RISE_TICKS = 8;
    private static final int SETTLE_TICKS = 6;
    private static final int MAX_RISING = 96;
    private static final int GROW_INTERVAL = 10;
    /** Die Welle läuft so viele Blöcke pro Tick nach außen (0,08 = 1,6 Blöcke pro Sekunde). */
    private static final double WAVE_SPEED = 0.08;
    private static final int WAVE_START = 20;
    private static final int RUMBLE_INTERVAL = 12;

    /** Ein aufsteigender Block: erst über den Boden gewölbt, dann gesetzt. */
    private static final class Rising {
        final Display.BlockDisplay display;
        final BlockPos pos;
        final BlockState state;
        final int settleTick, doneTick;
        boolean settling;

        Rising(Display.BlockDisplay display, BlockPos pos, BlockState state, int settleTick, int doneTick) {
            this.display = display;
            this.pos = pos;
            this.state = state;
            this.settleTick = settleTick;
            this.doneTick = doneTick;
        }
    }

    final ServerLevel level;
    final BlockPos center;
    final int radius;
    final String epochId;
    private final Landscape landscape;
    private final Consumer<TransplantJob> onDone;
    private final BlockState top, under, filler;
    private final Random random;
    private final Deque<int[]> columns = new ArrayDeque<>();
    private final List<int[]> waiting = new ArrayList<>();
    private final Deque<int[]> biomeColumns = new ArrayDeque<>();
    private final Deque<BlockPos> growSpots = new ArrayDeque<>();
    private final List<Rising> rising = new ArrayList<>();
    /** Blöcke eigener Wuchsformen, die nacheinander erscheinen: {x, y, z, Tick} und der Zustand. */
    private final java.util.PriorityQueue<Object[]> growing =
            new java.util.PriorityQueue<>((a, b) -> Integer.compare((int) a[1], (int) b[1]));
    private Phase phase = Phase.LAYERS;
    private int ticks;
    private int minTop = Integer.MAX_VALUE, maxTop = Integer.MIN_VALUE;
    private int changed;
    private boolean finished;
    /** Wird bei jedem Phasenwechsel aufgerufen (zum Speichern). */
    Runnable onPhaseChange = () -> { };
    /** UUID des Spielers, der sie gestartet hat (leer = Befehl/Test). */
    String owner = "";
    /** Nur für den Selbsttest: ohne Block-Displays, alles sofort. */
    boolean instant;

    public TransplantJob(ServerLevel level, BlockPos center, int radius, String epochId, Landscape landscape,
                         Consumer<TransplantJob> onDone) {
        this.level = level;
        this.center = center.immutable();
        this.radius = radius;
        this.epochId = epochId;
        this.landscape = landscape;
        this.onDone = onDone;
        this.top = MobCompat.block(landscape.top()).defaultBlockState();
        this.under = MobCompat.block(landscape.under()).defaultBlockState();
        this.filler = MobCompat.block(landscape.filler()).defaultBlockState();
        this.random = new Random(center.asLong() ^ epochId.hashCode());

        // Spalten nach Abstand sortiert: Die Verwandlung läuft als Welle nach außen.
        List<int[]> all = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > radius + 0.5) continue;
                // ausgefranster Rand statt Zirkelkreis
                if (d > radius - 1.5 && ((dx * 31 + dz * 17) & 1) == 0) continue;
                all.add(new int[] {center.getX() + dx, center.getZ() + dz, (int) Math.round(d * 100)});
            }
        }
        all.sort((a, b) -> Integer.compare(a[2], b[2]));
        columns.addAll(all);
        for (int qx = Math.floorDiv(center.getX() - radius, 4); qx <= Math.floorDiv(center.getX() + radius, 4); qx++) {
            for (int qz = Math.floorDiv(center.getZ() - radius, 4); qz <= Math.floorDiv(center.getZ() + radius, 4); qz++) {
                double cx = qx * 4 + 2 - center.getX(), cz = qz * 4 + 2 - center.getZ();
                if (cx * cx + cz * cz <= radius * radius) biomeColumns.add(new int[] {qx * 4, qz * 4});
            }
        }
    }

    public Phase phase() {
        return phase;
    }

    private void setPhase(Phase next) {
        phase = next;
        onPhaseChange.run();
    }

    /**
     * Nach einem Neustart in einer gespeicherten Phase weitermachen. Übrig gebliebene Block-Displays
     * (mit dem Tag {@code strata_rise}) werden entfernt; die Schichten-Phase läuft einfach noch einmal,
     * fertige Spalten bleiben dabei unverändert.
     */
    void resumeAt(Phase saved) {
        Transplanter.command(level, String.format(Locale.ROOT,
                "execute positioned %d %d %d run kill @e[type=minecraft:block_display,tag=strata_rise,distance=..%d]",
                center.getX(), center.getY(), center.getZ(), radius + 16));
        if (saved == Phase.LAYERS || saved == Phase.DONE) return;
        columns.clear();
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, center.getX(), center.getZ()) - 1;
        minTop = surface - 8;
        maxTop = surface + 8;
        phase = saved;
        if (saved == Phase.GROW) {
            biomeColumns.clear();
            planGrowth();
        }
    }

    /** Wie viele Blöcke bisher verändert wurden. */
    public int changed() {
        return changed;
    }

    /** Ein Tick. Liefert true, solange die Transplantation noch läuft. */
    public boolean tick() {
        if (finished) return false;
        if (!level.hasChunkAt(center)) return true;            // Transplantator nicht geladen: pausieren
        ticks++;
        finishRising(false);
        switch (phase) {
            case LAYERS -> tickLayers();
            case BIOME -> tickBiome();
            case GROW -> tickGrow();
            case DONE -> { }
        }
        if (phase == Phase.DONE && rising.isEmpty()) {
            finished = true;
            onDone.accept(this);
            return false;
        }
        return true;
    }

    /** Für den Selbsttest: alles ohne Pausen ausführen. */
    void runToEnd() {
        instant = true;
        for (int guard = 0; guard < 100_000 && tick(); guard++) { }
    }

    // ---- Phase 1: Schichten ----

    private void tickLayers() {
        int budget = instant ? Integer.MAX_VALUE : EpochsConfig.TRANSPLANT_BLOCKS_PER_TICK.get();
        int used = 0;
        if (ticks % 40 == 0 && !waiting.isEmpty()) {           // nicht geladene Spalten erneut versuchen
            columns.addAll(waiting);
            waiting.clear();
        }
        // Die Welle bestimmt das Tempo (sichtbar), das Budget nur die Obergrenze (Performance).
        double front = instant ? Double.MAX_VALUE : 1.5 + Math.max(0, ticks - WAVE_START) * WAVE_SPEED;
        while (used < budget && !columns.isEmpty() && columns.peek()[2] <= front * 100) {
            int[] c = columns.poll();
            BlockPos column = new BlockPos(c[0], center.getY(), c[1]);
            if (!level.hasChunkAt(column)) {
                waiting.add(c);
                continue;
            }
            used += transformColumn(c[0], c[1]);
        }
        if (!instant && ticks % RUMBLE_INTERVAL == 0) rumble();
        if (columns.isEmpty() && waiting.isEmpty()) setPhase(Phase.BIOME);
    }

    private int transformColumn(int x, int z) {
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, surface, z);
        // Bis zu 8 Blöcke nach unten den ersten umwandelbaren Block suchen (unter Baumstämmen, Schnee usw.).
        int y = surface;
        while (y > surface - SEARCH_DOWN && !level.getBlockState(pos.setY(y)).is(TRANSPLANTABLE)) y--;
        if (!level.getBlockState(pos.setY(y)).is(TRANSPLANTABLE)) return 1;
        minTop = Math.min(minTop, y);
        maxTop = Math.max(maxTop, y);

        int count = 0;
        // Pflanzen und Schnee auf der Oberfläche verschwinden.
        BlockState above = level.getBlockState(pos.setY(y + 1));
        if (!above.isAir() && above.canBeReplaced() && level.getFluidState(pos).isEmpty()) {
            level.setBlock(pos.immutable(), Blocks.AIR.defaultBlockState(), 2);
            count++;
        }
        for (int depth = 0; depth < landscape.depth(); depth++) {
            pos.setY(y - depth);
            BlockState old = level.getBlockState(pos);
            if (!old.is(TRANSPLANTABLE)) continue;
            BlockState state = depth == 0 ? top : depth <= UNDER_LAYERS ? under : filler;
            if (old == state) continue;
            if (depth == 0 && !instant && rising.size() < MAX_RISING && random.nextInt(2) == 0
                    && rise(pos.immutable(), state)) {
                count++;
                continue;
            }
            level.setBlock(pos.immutable(), state, 2);
            count++;
        }
        changed += count;
        if (!instant && random.nextInt(4) == 0) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, top), x + 0.5, y + 1.1, z + 0.5,
                    4, 0.3, 0.1, 0.3, 0.05);
        }
        return Math.max(1, count);
    }

    /** Oberflächenblock steigt gekippt und flach aus dem Boden und klappt in 8 Ticks an seinen Platz. */
    private boolean rise(BlockPos pos, BlockState state) {
        String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        float tilt = (random.nextBoolean() ? 1 : -1) * (0.17f + random.nextFloat() * 0.12f);   // sin(Winkel/2)
        float w = (float) Math.sqrt(1 - tilt * tilt);
        String cmd = String.format(Locale.ROOT, "summon minecraft:block_display %d %d %d {Tags:[\"strata_rise\"],block_state:{Name:\"%s\"},"
                        + "transformation:{left_rotation:[%.3ff,0f,0f,%.3ff],right_rotation:[0f,0f,0f,1f],"
                        + "translation:[0f,-0.9f,0f],scale:[1f,0.3f,1f]}}",
                pos.getX(), pos.getY(), pos.getZ(), name, tilt, w);
        Transplanter.command(level, cmd);
        List<Display.BlockDisplay> found = level.getEntitiesOfClass(Display.BlockDisplay.class, new AABB(pos).inflate(0.1),
                d -> d.blockPosition().equals(pos) && rising.stream().noneMatch(r -> r.display == d));
        if (found.isEmpty()) return false;                      // Befehl nicht verfügbar: Block einfach setzen
        Display.BlockDisplay display = found.get(0);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        // Erst über den Boden heben (die Schicht wölbt sich auf), dann setzen.
        float lift = 0.35f + random.nextFloat() * 0.4f;
        transform(display, RISE_TICKS, String.format(Locale.ROOT, "%.2ff", lift));
        rising.add(new Rising(display, pos, state, ticks + RISE_TICKS + 1, ticks + RISE_TICKS + SETTLE_TICKS + 2));
        return true;
    }

    private void transform(Display.BlockDisplay display, int duration, String y) {
        Transplanter.command(level, "data merge entity " + display.getStringUUID()
                + " {start_interpolation:0,interpolation_duration:" + duration + ",transformation:{left_rotation:[0f,0f,0f,1f],"
                + "right_rotation:[0f,0f,0f,1f],translation:[0f," + y + ",0f],scale:[1f,1f,1f]}}");
    }

    private void finishRising(boolean all) {
        rising.removeIf(r -> {
            if (!all && !r.settling && ticks >= r.settleTick) {
                r.settling = true;
                transform(r.display, SETTLE_TICKS, "0f");
            }
            if (!all && ticks < r.doneTick) return false;
            r.display.discard();
            if (level.getBlockState(r.pos).isAir()) level.setBlock(r.pos, r.state, 3);
            return true;
        });
    }

    private void rumble() {
        double x = center.getX() + 0.5, y = center.getY(), z = center.getZ() + 0.5;
        level.playSound(null, x, y, z, SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, 2.0f, 0.5f);
        level.playSound(null, x, y, z, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.5f, 0.6f);
    }

    // ---- Phase 2: Biom ----

    private void tickBiome() {
        if (minTop == Integer.MAX_VALUE) {                       // nichts umgewandelt (alles verbaut)
            phase = Phase.DONE;
            return;
        }
        int perTick = instant ? Integer.MAX_VALUE : 6;
        // Innerhalb der Weltgrenzen bleiben, sonst lehnt /fillbiome den ganzen Bereich ab.
        int y1 = Math.max(dev.strata.core.mc.Mc.minY(level), minTop - 8);
        int y2 = Math.min(dev.strata.core.mc.Mc.maxY(level) - 1, maxTop + 24);
        for (int i = 0; i < perTick && !biomeColumns.isEmpty(); i++) {
            int[] q = biomeColumns.poll();
            Transplanter.command(level, String.format(Locale.ROOT, "fillbiome %d %d %d %d %d %d %s",
                    q[0], y1, q[1], q[0] + 3, y2, q[1] + 3, landscape.biome()));
        }
        if (biomeColumns.isEmpty()) {
            setPhase(Phase.GROW);
            planGrowth();
        }
    }

    // ---- Phase 3: Wachsen ----

    private void planGrowth() {
        if (landscape.features().isEmpty()) return;
        int count = (int) Math.round(landscape.featureCount() * (radius * radius) / 144.0);
        for (int i = 0; i < count * 4 && growSpots.size() < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = Math.sqrt(random.nextDouble()) * (radius - 2);
            int x = center.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * dist);
            if (Math.abs(x - center.getX()) <= 1 && Math.abs(z - center.getZ()) <= 1) continue;   // nicht auf den Tisch
            if (!level.hasChunkAt(new BlockPos(x, center.getY(), z))) continue;
            // Höhe über den ersten Nicht-Luft-Block (die Bewegungs-Höhenkarte zählt in 26.x nicht jeden Block gleich).
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            if (!level.getBlockState(new BlockPos(x, y - 1, z)).is(top.getBlock())) continue;
            BlockPos spot = new BlockPos(x, y, z);
            if (growSpots.stream().anyMatch(p -> p.distSqr(spot) < 16)) continue;
            growSpots.add(spot);
        }
    }

    private void tickGrow() {
        while (!growing.isEmpty() && (instant || (int) growing.peek()[1] <= ticks)) {
            Object[] g = growing.poll();
            BlockPos pos = (BlockPos) g[0];
            if (level.getBlockState(pos).canBeReplaced()) level.setBlock(pos, (BlockState) g[2], 3);
        }
        if (!instant && ticks % GROW_INTERVAL != 0) return;
        BlockPos spot = growSpots.poll();
        if (spot != null) {
            String feature = landscape.features().get(random.nextInt(landscape.features().size()));
            if (!growBuiltIn(feature, spot)) {
                Transplanter.command(level, String.format(Locale.ROOT, "place feature %s %d %d %d",
                        feature, spot.getX(), spot.getY(), spot.getZ()));
            }
            if (!instant) {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, spot.getX() + 0.5, spot.getY() + 1, spot.getZ() + 0.5,
                        12, 0.8, 1.0, 0.8, 0.0);
                level.playSound(null, spot, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1.0f, 0.7f);
            }
        }
        if (growSpots.isEmpty() && growing.isEmpty()) {
            finishRising(true);
            phase = Phase.DONE;
        }
    }

    // ---- eigene Wuchsformen ----

    /**
     * Eigene Wuchsformen, die überall funktionieren (Vanilla-Geoden und Basaltsäulen brauchen Höhlen bzw. den Nether):
     * {@code strata_epochs:crystal_spire} (Amethystspitze mit Kristallquarzerz) und
     * {@code strata_epochs:basalt_cluster} (Basaltsäulen mit Glutsteinerz). Sie wachsen Block für Block.
     */
    private boolean growBuiltIn(String feature, BlockPos spot) {
        if (feature.equals(StrataEpochs.id("crystal_spire"))) {
            BlockState body = MobCompat.block("minecraft:amethyst_block").defaultBlockState();
            BlockState ore = EpochsContent.CRYSTAL_QUARTZ_ORE.get().defaultBlockState();
            int height = 3 + random.nextInt(4);
            column(spot, height, body, ore, 0.25, MobCompat.block("minecraft:amethyst_cluster").defaultBlockState(), 0);
            for (int i = 0; i < 2; i++) {
                BlockPos side = spot.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
                if (!side.equals(spot)) column(side, 1 + random.nextInt(2), body, ore, 0.4,
                        MobCompat.block("minecraft:medium_amethyst_bud").defaultBlockState(), 6);
            }
            return true;
        }
        if (feature.equals(StrataEpochs.id("basalt_cluster"))) {
            BlockState body = MobCompat.block("minecraft:basalt").defaultBlockState();
            BlockState ore = EpochsContent.EMBER_ORE.get().defaultBlockState();
            int count = 3 + random.nextInt(3);
            for (int i = 0; i < count; i++) {
                BlockPos at = i == 0 ? spot : spot.offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2);
                column(at, 1 + random.nextInt(i == 0 ? 6 : 4), body, ore, 0.15, null, i * 3);
            }
            return true;
        }
        return false;
    }

    /** Eine Säule ab {@code base} (auf dem Boden), Block für Block alle 3 Ticks, mit Erz-Anteil und optionaler Spitze. */
    private void column(BlockPos base, int height, BlockState body, BlockState ore, double oreChance, BlockState tip, int delay) {
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, base.getX(), base.getZ());
        for (int i = 0; i < height; i++) {
            BlockState state = random.nextDouble() < oreChance ? ore : body;
            growing.add(new Object[] {new BlockPos(base.getX(), y + i, base.getZ()), ticks + delay + i * 3, state});
        }
        if (tip != null) growing.add(new Object[] {new BlockPos(base.getX(), y + height, base.getZ()), ticks + delay + height * 3, tip});
    }

    /** Server stoppt: schwebende Displays entfernen und ihre Blöcke setzen; die Phase bleibt für den Neustart. */
    void parkDisplays() {
        finishRising(true);
    }

    /** Abbruch: schwebende Displays aufräumen, Blöcke setzen. */
    void abort() {
        finishRising(true);
        phase = Phase.DONE;
    }
}
