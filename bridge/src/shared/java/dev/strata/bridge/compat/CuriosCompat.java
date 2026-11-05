package dev.strata.bridge.compat;

import dev.strata.epochs.EpochsContent;
import dev.strata.epochs.Solidification;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Curios: Der Zeitanker passt in den Talisman-Slot („Stabilisator-Amulett“ aus dem Konzept) und wirkt dort
 * genauso wie im Inventar. Der Slot wird per Datapack für Spieler freigeschaltet
 * ({@code data/strata_bridge/curios/entities/time_anchor.json}), das Item liegt im Tag {@code curios:charm}.
 * Wird nur geladen, wenn Curios installiert ist.
 */
public final class CuriosCompat {

    private CuriosCompat() {}

    public static void register() {
        Solidification.ANCHOR_SOURCES.add(player -> CuriosApi.getCuriosInventory(player)
                .map(inv -> inv.isEquipped(EpochsContent.TIME_ANCHOR.get()))
                .orElse(false));
    }

    /**
     * Für den Selbsttest: Zeitanker in den ersten Talisman-Slot legen.
     * @return false, wenn der Spieler (noch) keinen Talisman-Slot hat – ab Curios 14 bekommen Spieler
     *         ihre Slots erst beim Betreten der Welt, simulierte Test-Spieler also nie
     */
    public static boolean equipForTest(net.minecraft.server.level.ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player).map(inv -> {
            if (!inv.getCurios().containsKey("charm")) return false;
            inv.setEquippedCurio("charm", 0, EpochsContent.TIME_ANCHOR.get().getDefaultInstance());
            return true;
        }).orElse(false);
    }
}
