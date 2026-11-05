package dev.strata.epochs;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Einstellungen von Strata Epochs ({@code config/strata_epochs-common.toml}, ab 26.3 {@code -local.toml}). */
public final class EpochsConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue GEODE_CRAWLER;
    public static final ModConfigSpec.IntValue MAX_MOBS_PER_EPOCH;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Mobs der Epochen").push("mobs");
        GEODE_CRAWLER = b.comment("Geodenkriecher spawnen natürlich (Kristallzeit)").define("geode_crawler", true);
        MAX_MOBS_PER_EPOCH = b.comment("Höchstzahl aller Strata-Mobs pro Epochen-Dimension").defineInRange("max_per_epoch", 60, 0, 10_000);
        b.pop();
        SPEC = b.build();
    }

    private EpochsConfig() {}
}
