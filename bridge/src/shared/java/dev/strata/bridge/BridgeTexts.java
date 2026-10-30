package dev.strata.bridge;

import dev.strata.core.api.StrataAPI;
import dev.strata.core.mc.Mc;
import dev.strata.epochs.EpochsContent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Gemeinsame Texte und Daten für JEI und Jade (versionsunabhängig). */
public final class BridgeTexts {

    private BridgeTexts() {}

    /** Item → Übersetzungsschlüssel der JEI-Infoseite. */
    public static Map<Supplier<Item>, String> jeiInfo() {
        Map<Supplier<Item>, String> info = new LinkedHashMap<>();
        info.put(EpochsContent.CORE_DRILL, "jei.strata_bridge.info.core_drill");
        info.put(EpochsContent.CORE_SAMPLE, "jei.strata_bridge.info.core_sample");
        info.put(() -> EpochsContent.EPOCH_GATE.get().asItem(), "jei.strata_bridge.info.epoch_gate");
        info.put(EpochsContent.TIME_ANCHOR, "jei.strata_bridge.info.time_anchor");
        info.put(() -> EpochsContent.CRYSTAL_QUARTZ_ORE.get().asItem(), "jei.strata_bridge.info.crystal_quartz_ore");
        info.put(() -> EpochsContent.AMBER_ORE.get().asItem(), "jei.strata_bridge.info.amber_ore");
        info.put(() -> EpochsContent.PETRIFIED_STONE.get().asItem(), "jei.strata_bridge.info.petrified_stone");
        return info;
    }

    /** Jade-Zeile für einen Block, oder null, wenn Strata zu diesem Block nichts sagt. */
    public static String jadeLine(Block block, Level level) {
        if (block == EpochsContent.EPOCH_GATE.get()) {
            boolean inEpoch = StrataAPI.epochOfDimension(level.registryAccess(), Mc.levelId(level)).isPresent();
            return inEpoch ? "jade.strata_bridge.gate.leave" : "jade.strata_bridge.gate.enter";
        }
        if (block == EpochsContent.CRYSTAL_QUARTZ_ORE.get()) return "jade.strata_bridge.found_in.crystal_age";
        if (block == EpochsContent.AMBER_ORE.get()) return "jade.strata_bridge.found_in.spore_age";
        if (block == EpochsContent.PETRIFIED_STONE.get()) return "jade.strata_bridge.petrified";
        return null;
    }
}
