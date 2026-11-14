package dev.strata.epochs;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Einstellungen von Strata Epochs ({@code config/strata_epochs-common.toml}, ab 26.3 {@code -local.toml}). */
public final class EpochsConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue GEODE_CRAWLER;
    public static final ModConfigSpec.BooleanValue SPORE_BELLOWS;
    public static final ModConfigSpec.BooleanValue PETRIFIED_WITNESS;
    public static final ModConfigSpec.BooleanValue EMBER_BURROWER;
    public static final ModConfigSpec.BooleanValue RESONANT_SHARD;
    public static final ModConfigSpec.BooleanValue AMBER_MOTH;
    public static final ModConfigSpec.BooleanValue RESIN_SNAIL;
    public static final ModConfigSpec.IntValue MAX_MOBS_PER_EPOCH;
    public static final ModConfigSpec.BooleanValue TRANSPLANTER;
    public static final ModConfigSpec.IntValue TRANSPLANT_RADIUS;
    public static final ModConfigSpec.IntValue TRANSPLANT_BLOCKS_PER_TICK;
    public static final ModConfigSpec.IntValue TRANSPLANT_MAX_JOBS;
    public static final ModConfigSpec.BooleanValue RIFTS;
    public static final ModConfigSpec.BooleanValue RIFT_GRIEFING;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> RIFT_DIMENSIONS;
    public static final ModConfigSpec.IntValue RIFT_INTERVAL;
    public static final ModConfigSpec.DoubleValue RIFT_CHANCE;
    public static final ModConfigSpec.IntValue RIFT_LIFETIME;
    public static final ModConfigSpec.IntValue RIFT_MAX_ACTIVE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Mobs der Epochen").push("mobs");
        GEODE_CRAWLER = b.comment("Geodenkriecher spawnen natürlich (Kristallzeit)").define("geode_crawler", true);
        SPORE_BELLOWS = b.comment("Sporenbälge spawnen natürlich (Sporenzeit)").define("spore_bellows", true);
        PETRIFIED_WITNESS = b.comment("Steinzeugen erwachen aus versteinertem Stein bei Spielern, die verfestigen")
                .define("petrified_witness", true);
        EMBER_BURROWER = b.comment("Glutwühler spawnen natürlich (Aschezeit)").define("ember_burrower", true);
        RESONANT_SHARD = b.comment("Resonanzlinge spawnen natürlich (Kristallzeit)").define("resonant_shard", true);
        AMBER_MOTH = b.comment("Bernsteinmotten spawnen natürlich (Sporenzeit)").define("amber_moth", true);
        RESIN_SNAIL = b.comment("Harzschnecken spawnen natürlich (Sporenzeit)").define("resin_snail", true);
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
        b.comment("Zeitrisse (Verwerfungen): bei Spielern, die schon in einer Epoche waren, reißt ab und zu ein Riss auf")
                .push("rifts");
        RIFTS = b.comment("Zeitrisse an").define("enabled", true);
        RIFT_GRIEFING = b.comment("Offene Risse verwandeln natürlichen Boden (Tag strata_epochs:transplantable) in Epochen-Boden")
                .define("griefing", true);
        RIFT_DIMENSIONS = b.comment("Dimensionen, in denen Risse entstehen (alle anderen sind ausgenommen)")
                .defineListAllowEmpty("dimensions", java.util.List.of("minecraft:overworld"), () -> "minecraft:overworld",
                        o -> o instanceof String);
        RIFT_INTERVAL = b.comment("Alle so viele Minuten wird pro Spieler gewürfelt").defineInRange("interval_minutes", 20, 1, 1440);
        RIFT_CHANCE = b.comment("Chance pro Wurf, dass sich ein Riss öffnet").defineInRange("chance", 0.35, 0.0, 1.0);
        RIFT_LIFETIME = b.comment("So viele Sekunden bleibt ein Riss offen, wenn niemand ihn versiegelt")
                .defineInRange("lifetime_seconds", 300, 30, 3600);
        RIFT_MAX_ACTIVE = b.comment("Höchstens so viele offene Risse pro Dimension").defineInRange("max_active", 4, 1, 64);
        b.pop();
        SPEC = b.build();
    }

    private EpochsConfig() {}
}
