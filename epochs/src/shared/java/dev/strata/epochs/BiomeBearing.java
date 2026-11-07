package dev.strata.epochs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Peilung im Epochentor: In welchem Biom der Epoche steht man, und in welcher Richtung und Entfernung liegen
 * die anderen? Damit man in der knappen Zeit gezielt zu einem Nebenbiom (mehr Erz) gehen kann.
 *
 * <p>Gesucht wird direkt in der Biom-Quelle der Dimension (ohne Chunks zu laden), auf einem Raster von 16 Blöcken
 * bis {@link #RANGE} Blöcke weit.
 */
public final class BiomeBearing {

    /** Wie weit die Peilung reicht (Nebenbiome liegen laut Selbsttest höchstens 300 Blöcke vom Ankunftsort). */
    public static final int RANGE = 384;
    private static final int STEP = 16;
    private static final String[] WINDS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

    private BiomeBearing() {}

    /** Ein anderes Biom: Name (Biom-ID), Entfernung in Blöcken, Himmelsrichtung (n, ne, … nw). */
    public record Target(String biome, int distance, String wind) {}

    /** Biom-ID an einer Stelle (aus der Biom-Quelle, ohne Chunk zu laden). */
    public static String biomeAt(ServerLevel level, BlockPos pos) {
        return level.getUncachedNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2).getRegisteredName();
    }

    /** Die nächste Stelle jedes anderen Bioms der Dimension, nach Entfernung sortiert. */
    public static List<Target> scan(ServerLevel level, BlockPos from) {
        String here = biomeAt(level, from);
        Map<String, int[]> best = new HashMap<>();   // Biom -> {dx, dz, Abstand²}
        for (int dx = -RANGE; dx <= RANGE; dx += STEP) {
            for (int dz = -RANGE; dz <= RANGE; dz += STEP) {
                int d2 = dx * dx + dz * dz;
                if (d2 > RANGE * RANGE) continue;
                String id = biomeAt(level, from.offset(dx, 0, dz));
                if (id.equals(here)) continue;
                int[] b = best.get(id);
                if (b == null || d2 < b[2]) best.put(id, new int[] {dx, dz, d2});
            }
        }
        List<Target> result = new ArrayList<>();
        best.forEach((id, b) -> result.add(new Target(id, (int) Math.round(Math.sqrt(b[2])), wind(b[0], b[1]))));
        result.sort((a, b) -> Integer.compare(a.distance(), b.distance()));
        return result;
    }

    /** Himmelsrichtung eines Versatzes: Norden ist −Z, Osten +X. */
    public static String wind(int dx, int dz) {
        double angle = Math.toDegrees(Math.atan2(dx, -dz));   // 0 = Norden, 90 = Osten
        int index = (int) Math.floorMod(Math.round(angle / 45.0), 8);
        return WINDS[index];
    }

    /** Schickt dem Spieler die Peilung als Chatzeilen (bleiben lesbar, anders als die Aktionsleiste). */
    public static void tell(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        BlockPos at = player.blockPosition();
        player.sendSystemMessage(Component.translatable("message.strata_epochs.bearing.here", biomeName(biomeAt(level, at)))
                .withStyle(ChatFormatting.GOLD));
        List<Target> targets = scan(level, at);
        if (targets.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.strata_epochs.bearing.none").withStyle(ChatFormatting.GRAY));
            return;
        }
        for (Target t : targets) {
            player.sendSystemMessage(Component.translatable("message.strata_epochs.bearing.target", biomeName(t.biome()),
                    t.distance(), Component.translatable("message.strata_epochs.bearing.wind." + t.wind()))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static Component biomeName(String id) {
        int colon = id.indexOf(':');
        return Component.translatable("biome." + id.substring(0, colon) + "." + id.substring(colon + 1));
    }
}
