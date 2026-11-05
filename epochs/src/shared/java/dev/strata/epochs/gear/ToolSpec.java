package dev.strata.epochs.gear;

/**
 * Werkzeugmaterial ohne Bezug zur Minecraft-Version (1.21.1: {@code Tier}, ab 1.21.2: {@code ToolMaterial}).
 *
 * @param incorrectBlocksTag Blöcke, die es nicht abbauen kann, z. B. {@code minecraft:incorrect_for_iron_tool}
 * @param repairTag          Item-Tag der Reparatur-Zutat, z. B. {@code strata_epochs:repairs_crystal_quartz_tools}
 */
public record ToolSpec(String incorrectBlocksTag, int durability, float speed, float attackDamageBonus,
                       int enchantmentValue, String repairTag) {}
