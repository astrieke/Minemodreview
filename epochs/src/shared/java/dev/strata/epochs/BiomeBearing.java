package dev.strata.epochs;

import dev.strata.core.mc.Mc;
import dev.strata.epochs.mc.MobCompat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Peilung im Epochentor: In welchem Biom der Epoche steht man, und wo liegen die anderen? Unter Tage gibt es
 * keine Sonne und keinen Kompass, deshalb nennt die Peilung die Richtung relativ zur Blickrichtung („rechts vor
 * dir“) und zeigt sie 5 s lang als farbige Partikelspur vom Spieler aus. Die Himmelsrichtung steht in Klammern.
 *
 * <p>Gesucht wird in der Biom-Quelle der Dimension (ohne Chunks zu laden) auf einem Raster von 16 Blöcken bis
 * {@link #RANGE} Blöcke weit. Das Ergebnis wird pro 16er-Feld zwischengespeichert, und jeder Spieler kann höchstens
 * alle {@link #COOLDOWN_TICKS} Ticks peilen.
 */
public final class BiomeBearing {

    /** Wie weit die Peilung reicht (Nebenbiome liegen laut Selbsttest höchstens 300 Blöcke vom Ankunftsort). */
    public static final int RANGE = 384;
    public static final int COOLDOWN_TICKS = 40;
    private static final int STEP = 16;
    private static final int TRAIL_TICKS = 100;
    private static final int NEAR_RANGE = 48;
    private static final int NEAR_STEP = 4;
    private static final String[] WINDS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
    private static final String[] SIDES = {"ahead", "ahead_right", "right", "behind_right", "behind", "behind_left", "left", "ahead_left"};

    /** Farbe der Spur und des Namens: die Farbe des Bodens, an dem man das Biom erkennt. */
    private static final Map<String, Integer> COLORS = Map.of(
            "crystal_hollows", 0xE8E4F0, "geode_vaults", 0xA65CE0, "rime_grotto", 0x9CC8FF,
            "spore_grotto", 0xB49AB4, "moss_marsh", 0x6FA832, "glow_thicket", 0x2FCFB4,
            "ash_wastes", 0xB0A49A, "magma_rifts", 0xFF6A1A, "bone_hollow", 0xE8E0C8);

    private static final Map<String, List<Target>> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<Target>> eldest) {
            return size() > 256;
        }
    };
    private static final Map<UUID, Long> LAST_USE = new HashMap<>();
    private static final List<Trail> TRAILS = new ArrayList<>();

    private BiomeBearing() {}

    /** Ein anderes Biom: ID, Versatz zur nächsten Stelle (Rand des Bioms) und Entfernung in Blöcken. */
    public record Target(String biome, int dx, int dz, int distance) {
        public String wind() {
            return BiomeBearing.wind(dx, dz);
        }
    }

    private record Trail(ServerLevel level, Vec3 from, Vec3 dir, int color, long until) {}

    /** Biom-ID an einer Stelle (aus der Biom-Quelle, ohne Chunk zu laden). */
    public static String biomeAt(ServerLevel level, BlockPos pos) {
        return level.getUncachedNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2).getRegisteredName();
    }

    /** Die nächste Stelle jedes anderen Bioms der Dimension, nach Entfernung sortiert (ungecacht). */
    public static List<Target> scan(ServerLevel level, BlockPos from) {
        return scan(level, from, biomeAt(level, from));
    }

    /** Wie {@link #scan(ServerLevel, BlockPos)}; {@code here} wird ausgelassen (null: alle Biome, auch das eigene). */
    private static List<Target> scan(ServerLevel level, BlockPos from, String here) {
        return scan(level, from, here, RANGE, STEP);
    }

    private static List<Target> scan(ServerLevel level, BlockPos from, String here, int range, int step) {
        Map<String, int[]> best = new HashMap<>();   // Biom -> {dx, dz, Abstand²}
        for (int dx = -range; dx <= range; dx += step) {
            for (int dz = -range; dz <= range; dz += step) {
                int d2 = dx * dx + dz * dz;
                if (d2 > range * range) continue;
                String id = biomeAt(level, from.offset(dx, 0, dz));
                if (id.equals(here)) continue;   // here == null: alle Biome behalten
                int[] b = best.get(id);
                if (b == null || d2 < b[2]) best.put(id, new int[] {dx, dz, d2});
            }
        }
        List<Target> result = new ArrayList<>();
        best.forEach((id, b) -> result.add(new Target(id, b[0], b[1], (int) Math.round(Math.sqrt(b[2])))));
        result.sort((a, b) -> Integer.compare(a.distance(), b.distance()));
        return result;
    }

    /**
     * Nächste Stelle JEDES Bioms (auch des eigenen) vom Mittelpunkt des 16er-Feldes aus, zwischengespeichert pro
     * Dimension und Feld (das Ergebnis ändert sich nie). Das eigene Biom des Spielers filtert {@link #tell} heraus,
     * denn an Biomgrenzen kann der Feldmittelpunkt in einem anderen Biom liegen als der Spieler.
     */
    public static List<Target> scanCached(ServerLevel level, BlockPos from) {
        BlockPos cell = new BlockPos(from.getX() & ~15 | 8, from.getY(), from.getZ() & ~15 | 8);
        String key = Mc.levelId(level) + "@" + cell.getX() + "," + cell.getZ();
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(key, k -> scan(level, cell, null));
        }
    }

    /** Himmelsrichtung eines Versatzes: Norden ist −Z, Osten +X. */
    public static String wind(int dx, int dz) {
        return WINDS[sector(Math.toDegrees(Math.atan2(dx, -dz)))];
    }

    /**
     * Richtung relativ zur Blickrichtung: ahead, ahead_right, right, … ahead_left.
     * Minecraft-Yaw: 0 = Süden, 90 = Westen, 180 = Norden, 270 = Osten.
     */
    public static String side(int dx, int dz, float yaw) {
        double target = Math.toDegrees(Math.atan2(dx, -dz));   // 0 = Norden, 90 = Osten
        double facing = yaw + 180.0;                            // gleiche Skala für die Blickrichtung
        return SIDES[sector(target - facing)];
    }

    private static int sector(double degrees) {
        return (int) Math.floorMod(Math.round(degrees / 45.0), 8);
    }

    /** Alle Sprachschlüssel, die eine Peilung braucht (für den Selbsttest). */
    public static List<String> translationKeys(List<String> biomes) {
        List<String> keys = new ArrayList<>(List.of("message.strata_epochs.bearing.here", "message.strata_epochs.bearing.target",
                "message.strata_epochs.bearing.none", "message.strata_epochs.bearing.wait"));
        for (String w : WINDS) keys.add("message.strata_epochs.bearing.wind." + w);
        for (String s : SIDES) keys.add("message.strata_epochs.bearing.side." + s);
        for (String b : biomes) keys.add(biomeKey(b));
        return keys;
    }

    /**
     * Peilung für einen Spieler: Chatzeilen plus Partikelspuren. Gibt false zurück, wenn der Spieler gerade
     * erst gepeilt hat (Cooldown).
     */
    public static boolean tell(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        long now = level.getGameTime();
        Long last = LAST_USE.get(player.getUUID());
        if (last != null && now - last < COOLDOWN_TICKS) {
            Mc.actionbar(player, Component.translatable("message.strata_epochs.bearing.wait").withStyle(ChatFormatting.GRAY));
            return false;
        }
        LAST_USE.put(player.getUUID(), now);
        BlockPos at = player.blockPosition();
        String here = biomeAt(level, at);
        player.sendSystemMessage(Component.translatable("message.strata_epochs.bearing.here", biomeName(here))
                .withStyle(ChatFormatting.GOLD));
        List<Target> targets = scanCached(level, at).stream().filter(t -> !t.biome().equals(here)).toList();
        if (targets.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.strata_epochs.bearing.none").withStyle(ChatFormatting.GRAY));
            return true;
        }
        // Nahe Grenzen genauer: im Umkreis von 48 Blöcken auf einem Raster von 4 vom Spieler aus suchen.
        Map<String, Target> near = new HashMap<>();
        for (Target t : scan(level, at, here, NEAR_RANGE, NEAR_STEP)) near.put(t.biome(), t);
        Vec3 eye = player.position().add(0, 1.2, 0);
        BlockPos cell = new BlockPos(at.getX() & ~15 | 8, at.getY(), at.getZ() & ~15 | 8);
        for (Target far : targets) {
            Target local = near.get(far.biome());
            // Versatz vom Spieler aus: nah aus der feinen Suche, sonst vom Feldmittelpunkt umgerechnet.
            int dx = local != null ? local.dx() : cell.getX() + far.dx() - at.getX();
            int dz = local != null ? local.dz() : cell.getZ() + far.dz() - at.getZ();
            Target t = far;
            int distance = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
            player.sendSystemMessage(Component.translatable("message.strata_epochs.bearing.target", biomeName(t.biome()),
                    Component.translatable("message.strata_epochs.bearing.side." + side(dx, dz, player.getYRot())), distance,
                    Component.translatable("message.strata_epochs.bearing.wind." + wind(dx, dz)))
                    .withStyle(ChatFormatting.GRAY));
            double len = Math.max(1, Math.sqrt((double) dx * dx + (double) dz * dz));
            synchronized (TRAILS) {
                TRAILS.add(new Trail(level, eye, new Vec3(dx / len, 0, dz / len), color(t.biome()), now + TRAIL_TICKS));
            }
        }
        return true;
    }

    /** Partikelspuren: 8 Blöcke lang, wandernde Punkte vom Spieler weg in Richtung des Bioms. */
    static void onLevelTick(LevelTickEvent.Post event) {
        if (TRAILS.isEmpty() || !(event.getLevel() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        synchronized (TRAILS) {
            for (Iterator<Trail> it = TRAILS.iterator(); it.hasNext(); ) {
                Trail t = it.next();
                if (t.level() != level) continue;
                if (now > t.until()) {
                    it.remove();
                    continue;
                }
                if (now % 2 != 0) continue;
                var dust = MobCompat.dust(t.color(), 1.2f);
                double phase = (now % 20) / 20.0;
                for (int i = 0; i < 8; i++) {
                    Vec3 p = t.from().add(t.dir().scale(2.5 + i + phase));
                    level.sendParticles(dust, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0);
                }
            }
        }
    }

    private static int color(String biome) {
        return COLORS.getOrDefault(biome.substring(biome.indexOf(':') + 1), 0xFFFFFF);
    }

    private static String biomeKey(String id) {
        int colon = id.indexOf(':');
        return "biome." + id.substring(0, colon) + "." + id.substring(colon + 1);
    }

    private static Component biomeName(String id) {
        int rgb = color(id);
        return Component.translatable(biomeKey(id)).withStyle(style -> style.withColor(rgb));
    }
}
