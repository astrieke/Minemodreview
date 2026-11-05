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
    public static final Supplier<Block> EPOCH_GATE = REG.block("epoch_gate",
            p -> p.mapColor(MapColor.COLOR_CYAN).strength(5.0f, 1200.0f).requiresCorrectToolForDrops()
                    .lightLevel(s -> 12).sound(SoundType.AMETHYST));

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
    /** Keimfrische: dreht die Verfestigungs-Uhr ein Stück zurück (siehe {@link GermTonic}). */
    public static final Supplier<Item> GERM_TONIC = REG.item("germ_tonic", p -> p.stacksTo(16));

    static {
        REG.blockItem(CHRONOSTONE, "chronostone");
        REG.blockItem(CRYSTAL_QUARTZ_ORE, "crystal_quartz_ore");
        REG.blockItem(AMBER_ORE, "amber_ore");
        REG.blockItem(PETRIFIED_STONE, "petrified_stone");
        REG.blockItem(VOLCANIC_ASH, "volcanic_ash");
        REG.blockItem(EMBER_ORE, "ember_ore");
        REG.blockItem(EPOCH_GATE, "epoch_gate");
    }

    /** Auf dem Bohrkern: welche Epoche er zeigt. */
    public static final Supplier<net.minecraft.core.component.DataComponentType<String>> SAMPLE_EPOCH =
            REG.stringComponent("sample_epoch");

    /** Am Spieler: Rückkehrpunkt und Zeit in der aktuellen Epoche. */
    public static final Supplier<AttachmentType<EpochVisit>> VISIT =
            Mc.attachment(ATTACHMENTS, "visit", () -> EpochVisit.NONE, EpochVisit.CODEC, false);

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
    }
}
