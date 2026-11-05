package dev.strata.epochs.mc;

import dev.strata.epochs.gear.ArmorSpec;
import dev.strata.epochs.gear.ToolSpec;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Werkzeuge und Rüstung registrieren: Minecraft 1.21.11 bis 26.3 ({@code ToolMaterial}, {@code ArmorMaterial} als Wert,
 * Rüstungstextur über {@code assets/<mod>/equipment/<name>.json}).
 */
public final class GearReg {

    private final String modId;
    private final DeferredRegister.Items items;
    private final List<Supplier<? extends Item>> tools = new ArrayList<>();
    private final List<Supplier<? extends Item>> combat = new ArrayList<>();

    public GearReg(String modId) {
        this.modId = modId;
        items = DeferredRegister.createItems(modId);
    }

    public Object toolMaterial(ToolSpec spec) {
        return new ToolMaterial(TagKey.create(Registries.BLOCK, Identifier.parse(spec.incorrectBlocksTag())),
                spec.durability(), spec.speed(), spec.attackDamageBonus(), spec.enchantmentValue(),
                TagKey.create(Registries.ITEM, Identifier.parse(spec.repairTag())));
    }

    public Supplier<Item> sword(String name, Object material, float damage, float speed) {
        return add(combat, items.registerItem(name, Item::new, p -> p.sword((ToolMaterial) material, damage, speed)));
    }

    public Supplier<Item> pickaxe(String name, Object material, float damage, float speed) {
        return add(tools, items.registerItem(name, Item::new, p -> p.pickaxe((ToolMaterial) material, damage, speed)));
    }

    public Supplier<Item> axe(String name, Object material, float damage, float speed) {
        return add(tools, items.registerItem(name, ToolItems.axe((ToolMaterial) material, damage, speed), p -> p));
    }

    public Supplier<Item> shovel(String name, Object material, float damage, float speed) {
        return add(tools, items.registerItem(name, ToolItems.shovel((ToolMaterial) material, damage, speed), p -> p));
    }

    public Supplier<Item> hoe(String name, Object material, float damage, float speed) {
        return add(tools, items.registerItem(name, ToolItems.hoe((ToolMaterial) material, damage, speed), p -> p));
    }

    public Object armorMaterial(ArmorSpec spec) {
        EnumMap<ArmorType, Integer> defense = new EnumMap<>(ArmorType.class);
        defense.put(ArmorType.HELMET, spec.helmet());
        defense.put(ArmorType.CHESTPLATE, spec.chestplate());
        defense.put(ArmorType.LEGGINGS, spec.leggings());
        defense.put(ArmorType.BOOTS, spec.boots());
        defense.put(ArmorType.BODY, spec.chestplate());
        return new ArmorMaterial(spec.durabilityMultiplier(), defense, spec.enchantmentValue(), SoundEvents.ARMOR_EQUIP_IRON,
                spec.toughness(), spec.knockbackResistance(), TagKey.create(Registries.ITEM, Identifier.parse(spec.repairTag())),
                ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(modId, spec.name())));
    }

    /** slot: "helmet", "chestplate", "leggings" oder "boots". */
    public Supplier<Item> armor(String name, Object material, ArmorSpec spec, String slot) {
        ArmorType type = ArmorType.valueOf(slot.toUpperCase(java.util.Locale.ROOT));
        return add(combat, items.registerItem(name, Item::new, p -> p.humanoidArmor((ArmorMaterial) material, type)));
    }

    private static Supplier<Item> add(List<Supplier<? extends Item>> list, Supplier<Item> item) {
        list.add(item);
        return item;
    }

    public void register(IEventBus modBus) {
        items.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) tools.forEach(i -> event.accept(i.get()));
            if (event.getTabKey() == CreativeModeTabs.COMBAT) combat.forEach(i -> event.accept(i.get()));
        });
    }
}
