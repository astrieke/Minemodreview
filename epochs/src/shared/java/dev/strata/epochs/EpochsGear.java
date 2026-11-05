package dev.strata.epochs;

import dev.strata.core.mc.Mc;
import dev.strata.epochs.gear.ArmorSpec;
import dev.strata.epochs.gear.ToolSpec;
import dev.strata.epochs.mc.GearReg;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Ausrüstung aus den Epochen.
 * <ul>
 *   <li><b>Kristallquarz-Werkzeuge</b>: zwischen Eisen und Diamant, Reparatur mit Kristallquarz.</li>
 *   <li><b>Geodenrüstung</b> aus Calcitschuppen: etwas stärker als Eisen. Als volles Set läuft die Verfestigung
 *       um ein Viertel langsamer.</li>
 *   <li><b>Bernsteinklinge</b>: Treffer verlangsamen das Ziel kurz („im Harz gefangen“).</li>
 * </ul>
 */
public final class EpochsGear {

    private static final GearReg REG = new GearReg(StrataEpochs.MOD_ID);

    public static final ToolSpec CRYSTAL_QUARTZ = new ToolSpec("minecraft:incorrect_for_iron_tool",
            700, 7.0f, 2.5f, 16, "strata_epochs:crystal_quartz_tool_materials");
    public static final ToolSpec AMBER = new ToolSpec("minecraft:incorrect_for_iron_tool",
            450, 6.0f, 2.0f, 20, "strata_epochs:amber_tool_materials");
    public static final ArmorSpec GEODE = new ArmorSpec("geode", 22, 2, 6, 5, 2, 14, 1.0f, 0.0f,
            "strata_epochs:repairs_geode_armor");

    /** Faktor der Verfestigungs-Uhr mit voller Geodenrüstung. */
    public static final double GEODE_SET_FACTOR = 0.75;

    private static final Object QUARTZ_TOOLS = REG.toolMaterial(CRYSTAL_QUARTZ);
    private static final Object AMBER_TOOLS = REG.toolMaterial(AMBER);
    private static final Object GEODE_ARMOR = REG.armorMaterial(GEODE);

    public static final Supplier<Item> CRYSTAL_QUARTZ_SWORD = REG.sword("crystal_quartz_sword", QUARTZ_TOOLS, 3.0f, -2.4f);
    public static final Supplier<Item> CRYSTAL_QUARTZ_PICKAXE = REG.pickaxe("crystal_quartz_pickaxe", QUARTZ_TOOLS, 1.0f, -2.8f);
    public static final Supplier<Item> CRYSTAL_QUARTZ_AXE = REG.axe("crystal_quartz_axe", QUARTZ_TOOLS, 5.5f, -3.05f);
    public static final Supplier<Item> CRYSTAL_QUARTZ_SHOVEL = REG.shovel("crystal_quartz_shovel", QUARTZ_TOOLS, 1.5f, -3.0f);
    public static final Supplier<Item> CRYSTAL_QUARTZ_HOE = REG.hoe("crystal_quartz_hoe", QUARTZ_TOOLS, -2.5f, -0.5f);
    public static final Supplier<Item> AMBER_BLADE = REG.sword("amber_blade", AMBER_TOOLS, 3.0f, -2.2f);

    public static final Supplier<Item> GEODE_HELMET = REG.armor("geode_helmet", GEODE_ARMOR, GEODE, "helmet");
    public static final Supplier<Item> GEODE_CHESTPLATE = REG.armor("geode_chestplate", GEODE_ARMOR, GEODE, "chestplate");
    public static final Supplier<Item> GEODE_LEGGINGS = REG.armor("geode_leggings", GEODE_ARMOR, GEODE, "leggings");
    public static final Supplier<Item> GEODE_BOOTS = REG.armor("geode_boots", GEODE_ARMOR, GEODE, "boots");

    private EpochsGear() {}

    static void register(IEventBus modBus) {
        REG.register(modBus);
        NeoForge.EVENT_BUS.addListener(EpochsGear::onIncomingDamage);
    }

    /** Trägt der Spieler alle vier Teile der Geodenrüstung? */
    public static boolean hasFullGeodeSet(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(GEODE_HELMET.get())
                && player.getItemBySlot(EquipmentSlot.CHEST).is(GEODE_CHESTPLATE.get())
                && player.getItemBySlot(EquipmentSlot.LEGS).is(GEODE_LEGGINGS.get())
                && player.getItemBySlot(EquipmentSlot.FEET).is(GEODE_BOOTS.get());
    }

    /** Bernsteinklinge: Nahkampftreffer geben 2 s Langsamkeit I. */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        if (event.getSource().getDirectEntity() != attacker || !attacker.getMainHandItem().is(AMBER_BLADE.get())) return;
        event.getEntity().addEffect(new MobEffectInstance(Mc.slowness(), 40, 0));
    }

    /** Zusatzzeile im Tooltip (Client), damit man die Sonderwirkung im Spiel findet. */
    public static Component tooltip(ItemStack stack) {
        if (stack.is(AMBER_BLADE.get())) {
            return Component.translatable("tooltip.strata_epochs.amber_blade").withStyle(ChatFormatting.GOLD);
        }
        if (stack.is(GEODE_HELMET.get()) || stack.is(GEODE_CHESTPLATE.get()) || stack.is(GEODE_LEGGINGS.get())
                || stack.is(GEODE_BOOTS.get())) {
            return Component.translatable("tooltip.strata_epochs.geode_set").withStyle(ChatFormatting.LIGHT_PURPLE);
        }
        return null;
    }
}
