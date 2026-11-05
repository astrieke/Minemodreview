package dev.strata.epochs.gear;

/**
 * Rüstungsmaterial ohne Bezug zur Minecraft-Version.
 *
 * @param name                 Name der Textur ({@code geode} → 1.21.1 {@code textures/models/armor/geode_layer_1.png},
 *                             ab 1.21.2 {@code equipment/geode.json})
 * @param durabilityMultiplier wie bei Vanilla (Eisen 15, Diamant 33)
 */
public record ArmorSpec(String name, int durabilityMultiplier, int helmet, int chestplate, int leggings, int boots,
                        int enchantmentValue, float toughness, float knockbackResistance, String repairTag) {}
