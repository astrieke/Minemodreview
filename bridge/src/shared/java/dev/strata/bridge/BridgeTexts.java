package dev.strata.bridge;

import dev.strata.core.api.StrataAPI;
import dev.strata.core.mc.Mc;
import dev.strata.epochs.EpochsContent;
import dev.strata.epochs.EpochsGear;
import dev.strata.epochs.mob.StrataMob;
import net.minecraft.world.entity.Entity;
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
        info.put(EpochsContent.CRYSTAL_CORE_DRILL, "jei.strata_bridge.info.core_drill");
        info.put(EpochsContent.DEEP_CORE_DRILL, "jei.strata_bridge.info.core_drill");
        info.put(EpochsContent.CORE_SAMPLE, "jei.strata_bridge.info.core_sample");
        info.put(() -> EpochsContent.EPOCH_GATE.get().asItem(), "jei.strata_bridge.info.epoch_gate");
        info.put(EpochsContent.TIME_ANCHOR, "jei.strata_bridge.info.time_anchor");
        info.put(() -> EpochsContent.CRYSTAL_QUARTZ_ORE.get().asItem(), "jei.strata_bridge.info.crystal_quartz_ore");
        info.put(() -> EpochsContent.AMBER_ORE.get().asItem(), "jei.strata_bridge.info.amber_ore");
        info.put(() -> EpochsContent.EMBER_ORE.get().asItem(), "jei.strata_bridge.info.ember_ore");
        info.put(EpochsContent.EMBER_SHARD, "jei.strata_bridge.info.ember_shard");
        info.put(() -> EpochsContent.PETRIFIED_STONE.get().asItem(), "jei.strata_bridge.info.petrified_stone");
        info.put(EpochsContent.FOSSIL_CHUNK, "jei.strata_bridge.info.fossil_chunk");
        info.put(EpochsContent.CHISEL, "jei.strata_bridge.info.fossil_chunk");
        info.put(() -> EpochsContent.FOSSIL_TABLE.get().asItem(), "jei.strata_bridge.info.fossil_chunk");
        info.put(EpochsContent.AMMONITE_FOSSIL, "jei.strata_bridge.info.ammonite_fossil");
        info.put(EpochsContent.FERN_FOSSIL, "jei.strata_bridge.info.fossil");
        info.put(EpochsContent.ANCIENT_SKULL, "jei.strata_bridge.info.fossil");
        info.put(() -> EpochsContent.TRANSPLANTER.get().asItem(), "jei.strata_bridge.info.transplanter");
        info.put(() -> EpochsContent.ANALYSIS_TABLE.get().asItem(), "jei.strata_bridge.info.analysis_table");
        info.put(EpochsContent.PETRIFIED_ITEM::get, "jei.strata_bridge.info.petrified_item");
        info.put(EpochsContent.TIME_ESSENCE::get, "jei.strata_bridge.info.time_essence");
        info.put(EpochsContent.LAYER_CORE::get, "jei.strata_bridge.info.layer_core");
        info.put(EpochsContent.HEART_OF_DEEP_TIME::get, "jei.strata_bridge.info.heart_of_deep_time");
        info.put(EpochsContent.RESONANT_SPLINTER::get, "jei.strata_bridge.info.resonant_splinter");
        info.put(() -> EpochsContent.DISPLAY_CASE.get().asItem(), "jei.strata_bridge.info.display_case");
        info.put(EpochsContent.QUARTZ_THISTLE_SEEDS::get, "jei.strata_bridge.info.quartz_thistle_seeds");
        info.put(EpochsContent.RESIN_FERN_SPORES::get, "jei.strata_bridge.info.resin_fern_spores");
        info.put(EpochsContent.EMBER_TUBER::get, "jei.strata_bridge.info.ember_tuber");
        info.put(EpochsContent.CALCITE_SCALE, "jei.strata_bridge.info.calcite_scale");
        info.put(EpochsContent.SPORE_SAC, "jei.strata_bridge.info.spore_sac");
        info.put(EpochsContent.GERM_TONIC, "jei.strata_bridge.info.germ_tonic");
        info.put(EpochsContent.CODEX_FRAGMENT, "jei.strata_bridge.info.codex_fragment");
        info.put(EpochsGear.AMBER_BLADE, "jei.strata_bridge.info.amber_blade");
        for (Supplier<Item> piece : java.util.List.of(EpochsGear.GEODE_HELMET, EpochsGear.GEODE_CHESTPLATE,
                EpochsGear.GEODE_LEGGINGS, EpochsGear.GEODE_BOOTS)) {
            info.put(piece, "jei.strata_bridge.info.geode_armor");
        }
        return info;
    }

    /** Jade-Zeile für einen Strata-Mob (Epoche und Rolle), oder null. */
    public static String jadeMobLine(Entity entity) {
        if (entity instanceof StrataMob mob) return "jade.strata_bridge.mob." + mob.def().name();
        return null;
    }

    /** Jade-Zeile für einen Block, oder null, wenn Strata zu diesem Block nichts sagt. */
    public static String jadeLine(Block block, Level level) {
        if (block == EpochsContent.EPOCH_GATE.get()) {
            boolean inEpoch = StrataAPI.epochOfDimension(level.registryAccess(), Mc.levelId(level)).isPresent();
            return inEpoch ? "jade.strata_bridge.gate.leave" : "jade.strata_bridge.gate.enter";
        }
        if (block == EpochsContent.CRYSTAL_QUARTZ_ORE.get()) return "jade.strata_bridge.found_in.crystal_age";
        if (block == EpochsContent.AMBER_ORE.get()) return "jade.strata_bridge.found_in.spore_age";
        if (block == EpochsContent.EMBER_ORE.get() || block == EpochsContent.VOLCANIC_ASH.get()) {
            return "jade.strata_bridge.found_in.ash_age";
        }
        if (block == EpochsContent.PETRIFIED_STONE.get()) return "jade.strata_bridge.petrified";
        return null;
    }
}
