package dev.strata.epochs.mc;

import java.util.function.Function;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

/** Axt, Schaufel, Hacke: ab Minecraft 26.3 (die Wirkung steckt in den Item-Eigenschaften, keine eigenen Klassen mehr). */
final class ToolItems {

    private ToolItems() {}

    static Function<Item.Properties, Item> axe(ToolMaterial m, float damage, float speed) {
        return p -> new Item(p.axe(m, damage, speed));
    }

    static Function<Item.Properties, Item> shovel(ToolMaterial m, float damage, float speed) {
        return p -> new Item(p.shovel(m, damage, speed));
    }

    static Function<Item.Properties, Item> hoe(ToolMaterial m, float damage, float speed) {
        return p -> new Item(p.hoe(m, damage, speed));
    }
}
