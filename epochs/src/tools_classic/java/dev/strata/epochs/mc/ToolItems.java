package dev.strata.epochs.mc;

import java.util.function.Function;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.ToolMaterial;

/** Axt, Schaufel, Hacke: Minecraft 1.21.11 bis 26.2 (eigene Klassen für Entrinden, Wege und Ackerboden). */
final class ToolItems {

    private ToolItems() {}

    static Function<Item.Properties, Item> axe(ToolMaterial m, float damage, float speed) {
        return p -> new AxeItem(m, damage, speed, p);
    }

    static Function<Item.Properties, Item> shovel(ToolMaterial m, float damage, float speed) {
        return p -> new ShovelItem(m, damage, speed, p);
    }

    static Function<Item.Properties, Item> hoe(ToolMaterial m, float damage, float speed) {
        return p -> new HoeItem(m, damage, speed, p);
    }
}
