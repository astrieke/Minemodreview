package dev.strata.epochs.mc;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registrierung von Blöcken, Items und Komponenten für Minecraft 1.21.11 bis 26.3. */
public final class Reg {

    private final DeferredRegister.Blocks blocks;
    private final DeferredRegister.Items items;
    private final DeferredRegister.DataComponents components;
    private final List<Supplier<? extends Item>> tabItems = new ArrayList<>();

    public Reg(String modId) {
        blocks = DeferredRegister.createBlocks(modId);
        items = DeferredRegister.createItems(modId);
        components = DeferredRegister.createDataComponents(net.minecraft.core.registries.Registries.DATA_COMPONENT_TYPE, modId);
    }

    public Supplier<Block> block(String name, UnaryOperator<BlockBehaviour.Properties> props) {
        // Ab 1.21.2 braucht jeder Block seine ID schon in den Properties; registerSimpleBlock setzt sie.
        return blocks.registerSimpleBlock(name, props);
    }

    public Supplier<Item> item(String name, UnaryOperator<Item.Properties> props) {
        Supplier<Item> item = items.registerSimpleItem(name, props);
        tabItems.add(item);
        return item;
    }

    public void blockItem(Supplier<Block> block, String name) {
        tabItems.add(items.registerSimpleBlockItem(name, block));
    }

    public Supplier<DataComponentType<String>> stringComponent(String name) {
        return components.registerComponentType(name,
                b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    }

    public void register(IEventBus modBus) {
        blocks.register(modBus);
        items.register(modBus);
        components.register(modBus);
    }

    public void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            tabItems.forEach(i -> event.accept(i.get()));
        }
    }
}
