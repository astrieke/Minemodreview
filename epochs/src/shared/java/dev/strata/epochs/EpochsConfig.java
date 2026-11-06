package dev.strata.epochs;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Einstellungen von Strata Epochs ({@code config/strata_epochs-common.toml}, ab 26.3 {@code -local.toml}). */
public final class EpochsConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue GEODE_CRAWLER;
    public static final ModConfigSpec.BooleanValue SPORE_BELLOWS;
    public static final ModConfigSpec.BooleanValue PETRIFIED_WITNESS;
    public static final ModConfigSpec.BooleanValue EMBER_BURROWER;
    public static final ModConfigSpec.IntValue MAX_MOBS_PER_EPOCH;
    public static final ModConfigSpec.BooleanValue TRANSPLANTER;
    public static final ModConfigSpec.IntValue TRANSPLANT_RADIUS;
    public static final ModConfigSpec.IntValue TRANSPLANT_BLOCKS_PER_TICK;
    public static final ModConfigSpec.IntValue TRANSPLANT_MAX_JOBS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Mobs der Epochen").push("mobs");
        GEODE_CRAWLER = b.comment("Geodenkriecher spawnen natürlich (Kristallzeit)").define("geode_crawler", true);
        SPORE_BELLOWS = b.comment("Sporenbälge spawnen natürlich (Sporenzeit)").define("spore_bellows", true);
        PETRIFIED_WITNESS = b.comment("Steinzeugen erwachen aus versteinertem Stein bei Spielern, die verfestigen")
                .define("petrified_witness", true);
        EMBER_BURROWER = b.comment("Glutwühler spawnen natürlich (Aschezeit)").define("ember_burrower", true);
        MAX_MOBS_PER_EPOCH = b.comment("Höchstzahl aller Strata-Mobs pro Epochen-Dimension").defineInRange("max_per_epoch", 60, 0, 10_000);
        b.pop();
        b.comment("Epochen-Transplantator: verwandelt ein Gebiet der Oberwelt dauerhaft in eine Epoche").push("transplanter");
        TRANSPLANTER = b.comment("Transplantator erlaubt (aus = kein Eingriff in die Landschaft)").define("enabled", true);
        TRANSPLANT_RADIUS = b.comment("Radius des Gebiets in Blöcken").defineInRange("radius", 12, 4, 32);
        TRANSPLANT_BLOCKS_PER_TICK = b.comment("Höchstens so viele Blockänderungen pro Tick und Transplantation")
                .defineInRange("blocks_per_tick", 256, 16, 8192);
        TRANSPLANT_MAX_JOBS = b.comment("Höchstens so viele Transplantationen gleichzeitig pro Server")
                .defineInRange("max_jobs", 2, 1, 16);
        b.pop();
        SPEC = b.build();
    }

    private EpochsConfig() {}
}
