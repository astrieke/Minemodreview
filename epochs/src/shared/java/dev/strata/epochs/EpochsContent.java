package dev.strata.epochs;

import dev.strata.core.mc.Mc;
import dev.strata.epochs.mc.Reg;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Alle Blöcke, Items und Daten von Strata Epochs. */
public final class EpochsContent {

    private static final Reg REG = new Reg(StrataEpochs.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = Mc.attachmentRegister(StrataEpochs.MOD_ID);

    // Blöcke
    public static final Supplier<Block> CHRONOSTONE = REG.block("chronostone",
            p -> p.mapColor(MapColor.COLOR_PURPLE).strength(2.0f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE));
    public static final Supplier<Block> CRYSTAL_QUARTZ_ORE = REG.block("crystal_quartz_ore",
            p -> p.mapColor(MapColor.COLOR_PURPLE).strength(3.0f, 6.0f).requiresCorrectToolForDrops()
                    .lightLevel(s -> 7).sound(SoundType.AMETHYST));
    public static final Supplier<Block> AMBER_ORE = REG.block("amber_ore",
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(3.0f, 6.0f).requiresCorrectToolForDrops()
                    .lightLevel(s -> 4).sound(SoundType.DEEPSLATE));
    public static final Supplier<Block> PETRIFIED_STONE = REG.block("petrified_stone",
            p -> p.mapColor(MapColor.STONE).strength(1.5f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.TUFF));
    /** Oberfläche der Aschezeit. */
    public static final Supplier<Block> VOLCANIC_ASH = REG.block("volcanic_ash",
            p -> p.mapColor(MapColor.COLOR_GRAY).strength(0.6f).sound(SoundType.SAND));
    public static final Supplier<Block> EMBER_ORE = REG.block("ember_ore",
            p -> p.mapColor(MapColor.COLOR_RED).strength(3.0f, 6.0f).requiresCorrectToolForDrops()
                    .lightLevel(s -> 9).sound(SoundType.DEEPSLATE));
    /** Präparationstisch: hier legt man mit dem Meißel Fossilien frei (siehe {@link FossilPrep}). */
    public static final Supplier<Block> FOSSIL_TABLE = REG.block("fossil_table",
            p -> p.mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD));
    /** Kernanalyse-Tisch (siehe {@link CoreAnalysis}). */
    public static final Supplier<Block> ANALYSIS_TABLE = REG.block("analysis_table", dev.strata.epochs.mc.AnalysisTableBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(2.5f).sound(SoundType.COPPER));
    /** Epochen-Transplantator (siehe {@link Transplanter}). */
    public static final Supplier<Block> TRANSPLANTER = REG.block("transplanter",
            p -> p.mapColor(MapColor.COLOR_PURPLE).strength(5.0f, 1200.0f).requiresCorrectToolForDrops()
                    .lightLevel(s -> 7).sound(SoundType.AMETHYST));
    public static final Supplier<Block> EPOCH_GATE = REG.block("epoch_gate",
            p -> p.mapColor(MapColor.COLOR_CYAN).strength(5.0f, 1200.0f).requiresCorrectToolForDrops()
                    .lightLevel(s -> 12).sound(SoundType.AMETHYST));

    /** Vitrine für das Museum der Erdgeschichte (siehe {@link DisplayCases}). */
    public static final Supplier<Block> DISPLAY_CASE = REG.block("display_case", DisplayCaseBlock::new,
            p -> p.mapColor(MapColor.COLOR_GRAY).strength(1.5f, 6.0f).sound(SoundType.GLASS).noOcclusion());

    /** Harzfalle (siehe {@link dev.strata.epochs.mob.AmberMoth}): leer, mit gefangener Motte, ausgehärtet. */
    public static final Supplier<Block> RESIN_TRAP = REG.block("resin_trap",
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(0.5f).sound(SoundType.HONEY_BLOCK));
    public static final Supplier<Block> RESIN_TRAP_MOTH = REG.block("resin_trap_moth", ResinTrapBlock::new,
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(0.5f).sound(SoundType.HONEY_BLOCK).randomTicks());
    public static final Supplier<Block> RESIN_TRAP_HARDENED = REG.block("resin_trap_hardened",
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(0.8f).sound(SoundType.AMETHYST));

    /** Harzspur der Harzschnecke (bremst wie Honig) und die daraus ausgehärtete Bernsteinplatte (gibt Bernstein). */
    public static final Supplier<Block> RESIN_TRAIL = REG.block("resin_trail", ResinTrailBlock::new,
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(0.1f).sound(SoundType.HONEY_BLOCK).speedFactor(0.4f)
                    .jumpFactor(0.5f).randomTicks().noOcclusion().lightLevel(s -> 4));
    public static final Supplier<Block> AMBER_PLATE = REG.block("amber_plate", net.minecraft.world.level.block.CarpetBlock::new,
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(0.3f).sound(SoundType.AMETHYST).noOcclusion());

    /** Zeitriss in der Oberwelt (siehe {@link TimeRifts}): ohne Kollision, unzerstörbar, nur mit Zeitanker zu versiegeln. */
    public static final Supplier<Block> TIME_RIFT = REG.ghost("time_rift", TimeRiftBlock::new,
            p -> p.mapColor(MapColor.COLOR_PURPLE).lightLevel(s -> 11).sound(SoundType.AMETHYST).randomTicks());

    // Epochen-Pflanzen (siehe {@link EpochCrop}): Samen stecken in Fossilbrocken, reif geben sie Epochen-Rohstoffe.
    public static final Supplier<Block> QUARTZ_THISTLE = REG.crop("quartz_thistle",
            p -> new EpochCrop(p, "crystal_age", EpochsContent.QUARTZ_THISTLE_SEEDS), p -> p.mapColor(MapColor.COLOR_PURPLE));
    public static final Supplier<Block> RESIN_FERN = REG.crop("resin_fern",
            p -> new EpochCrop(p, "spore_age", EpochsContent.RESIN_FERN_SPORES), p -> p.mapColor(MapColor.COLOR_ORANGE));
    public static final Supplier<Block> EMBER_TUBERS = REG.crop("ember_tubers",
            p -> new EpochCrop(p, "ash_age", EpochsContent.EMBER_TUBER), p -> p.mapColor(MapColor.COLOR_RED)
                    .lightLevel(s -> s.getValue(net.minecraft.world.level.block.BeetrootBlock.AGE) == 3 ? 5 : 0));
    public static final Supplier<Item> QUARTZ_THISTLE_SEEDS = REG.seed("quartz_thistle_seeds", QUARTZ_THISTLE);
    public static final Supplier<Item> RESIN_FERN_SPORES = REG.seed("resin_fern_spores", RESIN_FERN);
    public static final Supplier<Item> EMBER_TUBER = REG.seed("ember_tuber", EMBER_TUBERS);

    // Items
    /** Kernbohrer in drei Stufen: Stufe n erreicht Epochen bis zur Tiefe n. */
    public static final Supplier<Item> CORE_DRILL = REG.item("core_drill", p -> p.durability(64));
    public static final Supplier<Item> CRYSTAL_CORE_DRILL = REG.item("crystal_core_drill", p -> p.durability(160));
    public static final Supplier<Item> DEEP_CORE_DRILL = REG.item("deep_core_drill", p -> p.durability(400));
    public static final Supplier<Item> CORE_SAMPLE = REG.item("core_sample", p -> p.stacksTo(16));
    public static final Supplier<Item> CRYSTAL_QUARTZ = REG.item("crystal_quartz", p -> p);
    public static final Supplier<Item> AMBER_SHARD = REG.item("amber_shard", p -> p);
    /** Aus Glutsteinerz der Aschezeit; brennt im Ofen so lange wie eine Lohenrute. */
    public static final Supplier<Item> EMBER_SHARD = REG.item("ember_shard", p -> p);
    public static final Supplier<Item> TIME_ANCHOR = REG.item("time_anchor", p -> p.stacksTo(1));
    /** Drop des Geodenkriechers. */
    public static final Supplier<Item> CALCITE_SCALE = REG.item("calcite_scale", p -> p);
    /** Drop des Sporenbalgs, Zutat für die Keimfrische. */
    public static final Supplier<Item> SPORE_SAC = REG.item("spore_sac", p -> p);
    /** Drop des Steinzeugen: Rechtsklick schaltet den Kodex-Knoten „Echo eines Reisenden“ frei. */
    public static final Supplier<Item> CODEX_FRAGMENT = REG.item("codex_fragment", p -> p.stacksTo(16));
    /**
     * Zeitessenz: verdichtete Zeit aus versiegelten Zeitrissen und vom Steinzeugen. Lässt eine Epochen-Pflanze eine
     * Stufe wachsen (Knochenmehl wirkt bei ihnen nicht, siehe {@link EpochCrop}); Item-Tag
     * {@code strata_epochs:time_essence} für Magie-Mods und Rezepte von Packs.
     */
    public static final Supplier<Item> TIME_ESSENCE = REG.item("time_essence", p -> p.rarity(net.minecraft.world.item.Rarity.UNCOMMON));

    /** Schichtkern: weckt auf Zeitgestein den Stratolithen (siehe {@link StratolithSummon}). */
    public static final Supplier<Item> LAYER_CORE = REG.item("layer_core", p -> p.stacksTo(16).rarity(net.minecraft.world.item.Rarity.RARE));
    /** Herz der Tiefenzeit: Beute des Stratolithen; in der zweiten Hand verstärkt es den Transplantator. */
    public static final Supplier<Item> HEART_OF_DEEP_TIME = REG.item("heart_of_deep_time",
            p -> p.stacksTo(1).fireResistant().rarity(net.minecraft.world.item.Rarity.EPIC));

    /** Keimfrische: dreht die Verfestigungs-Uhr ein Stück zurück (siehe {@link GermTonic}). */
    public static final Supplier<Item> GERM_TONIC = REG.item("germ_tonic", p -> p.stacksTo(16));
    /** Abwurf des Resonanzlings: 3 Ladungen, je eine lässt die eigene Uhr 30 s ruhen. */
    /** Aus der ausgehärteten Harzfalle: zählt beim Transplantator als Fossil der Sporenzeit. */
    public static final Supplier<Item> AMBER_INCLUSION = REG.item("amber_inclusion", p -> p.rarity(net.minecraft.world.item.Rarity.UNCOMMON));
    public static final Supplier<Item> RESONANT_SPLINTER = REG.item("resonant_splinter",
            p -> p.durability(dev.strata.epochs.mob.ResonantShard.SPLINTER_CHARGES));

    /** Ein versteinerter Gegenstand aus dem Inventar; trägt das Original in sich (siehe {@link ItemPetrification}). */
    public static final Supplier<Item> PETRIFIED_ITEM = REG.hiddenItem("petrified_item", ItemPetrification.Lump::new,
            p -> p.stacksTo(1).fireResistant());

    // Fossilien
    /** Roher Fossilbrocken aus einer Epoche; trägt die Epoche und den Präparationsfortschritt. */
    public static final Supplier<Item> FOSSIL_CHUNK = REG.item("fossil_chunk", p -> p.stacksTo(16));
    public static final Supplier<Item> CHISEL = REG.item("chisel", p -> p.durability(128));
    public static final Supplier<Item> AMMONITE_FOSSIL = REG.item("ammonite_fossil", p -> p.stacksTo(16));
    public static final Supplier<Item> FERN_FOSSIL = REG.item("fern_fossil", p -> p.stacksTo(16));
    public static final Supplier<Item> ANCIENT_SKULL = REG.item("ancient_skull", p -> p.stacksTo(16));

    static {
        REG.blockItem(CHRONOSTONE, "chronostone");
        REG.blockItem(CRYSTAL_QUARTZ_ORE, "crystal_quartz_ore");
        REG.blockItem(AMBER_ORE, "amber_ore");
        REG.blockItem(PETRIFIED_STONE, "petrified_stone");
        REG.blockItem(VOLCANIC_ASH, "volcanic_ash");
        REG.blockItem(EMBER_ORE, "ember_ore");
        REG.blockItem(FOSSIL_TABLE, "fossil_table");
        REG.blockItem(TRANSPLANTER, "transplanter");
        REG.blockItem(ANALYSIS_TABLE, "analysis_table");
        REG.blockItem(EPOCH_GATE, "epoch_gate");
        REG.blockItem(DISPLAY_CASE, "display_case");
        REG.blockItem(RESIN_TRAP, "resin_trap");
    }

    /** Auf dem Bohrkern: welche Epoche er zeigt. */
    public static final Supplier<net.minecraft.core.component.DataComponentType<String>> SAMPLE_EPOCH =
            REG.stringComponent("sample_epoch");

    /** Am Bohrkern: 1 = am Kernanalyse-Tisch analysiert. */
    public static final Supplier<net.minecraft.core.component.DataComponentType<Integer>> ANALYZED =
            REG.intComponent("analyzed");

    /** Am versteinerten Gegenstand: UUID des Spielers, bei dem er versteinert ist. */
    public static final Supplier<net.minecraft.core.component.DataComponentType<String>> PETRIFIED_OWNER =
            REG.stringComponent("petrified_owner");

    /** Am Fossilbrocken: wie viele Meißelschläge schon sitzen. */
    public static final Supplier<net.minecraft.core.component.DataComponentType<Integer>> PREP_PROGRESS =
            REG.intComponent("prep_progress");

    /** Am Spieler: Rückkehrpunkt und Zeit in der aktuellen Epoche. */
    public static final Supplier<AttachmentType<EpochVisit>> VISIT =
            Mc.attachment(ATTACHMENTS, "visit", () -> EpochVisit.NONE, EpochVisit.CODEC, false);

    /** Am Spiralit: wem er gehört und ob er wartet. */
    public static final Supplier<AttachmentType<dev.strata.epochs.mob.SpiralitBond>> BOND =
            Mc.attachment(ATTACHMENTS, "spiralit_bond", () -> dev.strata.epochs.mob.SpiralitBond.NONE,
                    dev.strata.epochs.mob.SpiralitBond.CODEC, false);

    /**
     * Am Spieler: die UUID seines aktuellen Spiralits. Nur dieser ist an ihn gebunden; ein anderer Spiralit mit
     * seinem Namen (z. B. aus einer entladenen Gegend) löst die Bindung, sobald er geladen wird.
     */
    public static final Supplier<AttachmentType<String>> SPIRALIT_ID =
            Mc.attachment(ATTACHMENTS, "spiralit_id", () -> "", com.mojang.serialization.Codec.STRING, true);

    /** An der Dimension: laufende Transplantationen (überstehen so einen Neustart). */
    public static final Supplier<AttachmentType<java.util.List<TransplantRecord>>> TRANSPLANTS =
            Mc.attachment(ATTACHMENTS, "transplants", java.util.List::of, TransplantRecord.LIST_CODEC, false);

    /** Am Stratolithen: wie viele Schichten er schon abgeworfen hat (0–2), damit es nach dem Laden so bleibt. */
    /** Heimat eines eingestimmten Resonanzlings ({@link BlockPos#asLong}), sonst {@code ResonantShard.NO_HOME}. */
    /** Eigene Spurstücke einer Harzschnecke (Positionen als {@link BlockPos#asLong}): ihre Grenze gilt pro Schnecke. */
    public static final Supplier<AttachmentType<java.util.List<Long>>> SNAIL_TRAIL =
            Mc.attachment(ATTACHMENTS, "snail_trail", java.util.List::of, com.mojang.serialization.Codec.LONG.listOf(), false);
    public static final Supplier<AttachmentType<Long>> RESONANT_HOME =
            Mc.attachment(ATTACHMENTS, "resonant_home", () -> dev.strata.epochs.mob.ResonantShard.NO_HOME,
                    com.mojang.serialization.Codec.LONG, false);
    public static final Supplier<AttachmentType<Integer>> STRATOLITH_LAYER =
            Mc.attachment(ATTACHMENTS, "stratolith_layer", () -> 0, com.mojang.serialization.Codec.INT, false);

    /** An der Dimension: offene Zeitrisse (überstehen so einen Neustart). */
    public static final Supplier<AttachmentType<java.util.List<RiftRecord>>> RIFTS =
            Mc.attachment(ATTACHMENTS, "rifts", java.util.List::of, RiftRecord.LIST_CODEC, false);

    private EpochsContent() {}

    /** Stufe eines Kernbohrers (1 bis 3), 0 für alles andere. */
    public static int drillTier(net.minecraft.world.item.ItemStack stack) {
        if (stack.is(CORE_DRILL.get())) return 1;
        if (stack.is(CRYSTAL_CORE_DRILL.get())) return 2;
        if (stack.is(DEEP_CORE_DRILL.get())) return 3;
        return 0;
    }

    static void register(IEventBus modBus) {
        REG.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(REG::addToCreativeTabs);
        dev.strata.epochs.mc.TablePort.register(modBus, ANALYSIS_TABLE);
    }
}
