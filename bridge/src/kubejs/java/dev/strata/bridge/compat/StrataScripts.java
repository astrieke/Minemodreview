package dev.strata.bridge.compat;

import dev.strata.core.api.StrataAPI;
import dev.strata.epochs.CoreSamples;
import dev.strata.epochs.EpochTravel;
import dev.strata.epochs.FossilPrep;
import dev.strata.epochs.Solidification;
import dev.strata.epochs.Transplanter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Helfer für KubeJS-Skripte (Binding {@code Strata}). Beispiele:
 * <pre>
 * Strata.coreSample('strata_epochs:spore_age')          // Bohrkern als Belohnung
 * Strata.epochOf(player.level)                           // 'strata_epochs:ash_age' oder ''
 * Strata.timeBonus(player)                               // 1.25 = Zeitanker +100 %, analysierter Kern +25 %
 * Strata.transplant(level, pos, 'strata_epochs:crystal_age', 8)
 * </pre>
 */
public final class StrataScripts {

    private StrataScripts() {}

    public static ItemStack coreSample(String epochId) {
        return CoreSamples.create(epochId);
    }

    public static ItemStack fossilChunk(String epochId) {
        return FossilPrep.chunk(epochId);
    }

    /** Epoche der Dimension oder leerer Text. */
    public static String epochOf(ServerLevel level) {
        return EpochTravel.epochOf(level).orElse("");
    }

    public static double timeBonus(ServerPlayer player) {
        return Solidification.timeBonus(player);
    }

    public static boolean hasTimeAnchor(ServerPlayer player) {
        return Solidification.hasTimeAnchor(player);
    }

    /** Startet eine Transplantation ohne Voraussetzungen (wie {@code /strata_epochs transplant}). */
    public static boolean transplant(ServerLevel level, BlockPos pos, String epochId, int radius) {
        var epoch = StrataAPI.epochs(level.getServer()).get(epochId);
        if (epoch == null || epoch.landscape().isEmpty()) return false;
        Transplanter.start(level, pos, epochId, epoch.landscape().get(), radius, null);
        return true;
    }
}
