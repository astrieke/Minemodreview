package dev.strata.epochs.mc;

import dev.strata.epochs.gear.ArmorSpec;
import dev.strata.epochs.gear.ToolSpec;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Werkzeuge und Rüstung registrieren: Minecraft 1.21.1 ({@code Tier}, Rüstungsmaterial als Registry-Eintrag). */
public final class GearReg {

    private final String modId;
    private final DeferredRegister.Items items;
    private final DeferredRegister<ArmorMaterial> materials;
    private final List<Supplier<? extends Item>> tools = new ArrayList<>();
    private final List<Supplier<? extends Item>> combat = new ArrayList<>();

    public GearReg(String modId) {
        this.modId = modId;
        items = DeferredRegister.createItems(modId);
        materials = DeferredRegister.create(Registries.ARMOR_MATERIAL, modId);
    }

    private static TagKey<Item> itemTag(String id) {
        return TagKey.create(Registries.ITEM, ResourceLocation.parse(id));
    }

    public Object toolMaterial(ToolSpec spec) {
        TagKey<Block> incorrect = TagKey.create(Registries.BLOCK, ResourceLocation.parse(spec.incorrectBlocksTag()));
        TagKey<Item> repair = itemTag(spec.repairTag());
        return new Tier() {
            public int getUses() { return spec.durability(); }
            public float getSpeed() { return spec.speed(); }
            public float getAttackDamageBonus() { return spec.attackDamageBonus(); }
            public TagKey<Block> getIncorrectBlocksForDrops() { return incorrect; }
            public int getEnchantmentValue() { return spec.enchantmentValue(); }
            public Ingredient getRepairIngredient() { return Ingredient.of(repair); }
        };
    }

    public Supplier<Item> sword(String name, Object material, float damage, float speed) {
        Tier tier = (Tier) material;
        return add(combat, items.register(name, () -> new SwordItem(tier,
                new Item.Properties().attributes(SwordItem.createAttributes(tier, damage, speed)))));
    }

    public Supplier<Item> pickaxe(String name, Object material, float damage, float speed) {
        Tier tier = (Tier) material;
        return add(tools, items.register(name, () -> new PickaxeItem(tier,
                new Item.Properties().attributes(DiggerItem.createAttributes(tier, damage, speed)))));
    }

    public Supplier<Item> axe(String name, Object material, float damage, float speed) {
        Tier tier = (Tier) material;
        return add(tools, items.register(name, () -> new AxeItem(tier,
                new Item.Properties().attributes(DiggerItem.createAttributes(tier, damage, speed)))));
    }

    public Supplier<Item> shovel(String name, Object material, float damage, float speed) {
        Tier tier = (Tier) material;
        return add(tools, items.register(name, () -> new ShovelItem(tier,
                new Item.Properties().attributes(DiggerItem.createAttributes(tier, damage, speed)))));
    }

    public Supplier<Item> hoe(String name, Object material, float damage, float speed) {
        Tier tier = (Tier) material;
        return add(tools, items.register(name, () -> new HoeItem(tier,
                new Item.Properties().attributes(DiggerItem.createAttributes(tier, damage, speed)))));
    }

    public Object armorMaterial(ArmorSpec spec) {
        TagKey<Item> repair = itemTag(spec.repairTag());
        return materials.register(spec.name(), () -> {
            EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
            defense.put(ArmorItem.Type.HELMET, spec.helmet());
            defense.put(ArmorItem.Type.CHESTPLATE, spec.chestplate());
            defense.put(ArmorItem.Type.LEGGINGS, spec.leggings());
            defense.put(ArmorItem.Type.BOOTS, spec.boots());
            defense.put(ArmorItem.Type.BODY, spec.chestplate());
            return new ArmorMaterial(defense, spec.enchantmentValue(), SoundEvents.ARMOR_EQUIP_IRON, () -> Ingredient.of(repair),
                    List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(modId, spec.name()))),
                    spec.toughness(), spec.knockbackResistance());
        });
    }

    /** slot: "helmet", "chestplate", "leggings" oder "boots". */
    @SuppressWarnings("unchecked")
    public Supplier<Item> armor(String name, Object material, ArmorSpec spec, String slot) {
        Holder<ArmorMaterial> holder = (Holder<ArmorMaterial>) material;
        ArmorItem.Type type = ArmorItem.Type.valueOf(slot.toUpperCase(java.util.Locale.ROOT));
        return add(combat, items.register(name, () -> new ArmorItem(holder, type,
                new Item.Properties().durability(type.getDurability(spec.durabilityMultiplier())))));
    }

    private static Supplier<Item> add(List<Supplier<? extends Item>> list, Supplier<Item> item) {
        list.add(item);
        return item;
    }

    public void register(IEventBus modBus) {
        materials.register(modBus);
        items.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) tools.forEach(i -> event.accept(i.get()));
            if (event.getTabKey() == CreativeModeTabs.COMBAT) combat.forEach(i -> event.accept(i.get()));
        });
    }
}
