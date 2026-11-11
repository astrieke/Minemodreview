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

    /** Block mit eigener Klasse, z. B. {@link AnalysisTableBlock}. */
    public Supplier<Block> block(String name, java.util.function.Function<BlockBehaviour.Properties, ? extends Block> factory,
                                 UnaryOperator<BlockBehaviour.Properties> props) {
        return blocks.registerBlock(name, factory, props);
    }

    /** Pflanze: ohne Kollision, Zufallsticks, sofort abbaubar; Kolben wie bei Roter Bete (die Konstante heißt ab 26.3 anders). */
    public Supplier<Block> crop(String name, java.util.function.Function<BlockBehaviour.Properties, ? extends Block> factory,
                                UnaryOperator<BlockBehaviour.Properties> props) {
        return blocks.registerBlock(name, factory, p -> props.apply(p.noCollision().randomTicks().instabreak()
                .sound(net.minecraft.world.level.block.SoundType.CROP)
                .pushReaction(net.minecraft.world.level.block.Blocks.BEETROOTS.defaultBlockState().getPistonPushReaction())));
    }

    /** Item, das nicht im Kreativ-Tab steht (entsteht nur im Spiel, z. B. ein versteinerter Gegenstand). */
    public Supplier<Item> hiddenItem(String name, UnaryOperator<Item.Properties> props) {
        return items.registerSimpleItem(name, props);
    }

    /** Wie {@link #hiddenItem(String, UnaryOperator)}, mit eigener Item-Klasse. */
    public Supplier<Item> hiddenItem(String name, java.util.function.Function<Item.Properties, ? extends Item> factory,
                                     UnaryOperator<Item.Properties> props) {
        return items.registerItem(name, factory, props);
    }

    /** Saatgut: pflanzt den Block, heißt aber wie das Item ({@code item.<mod>.<name>}). */
    public Supplier<Item> seed(String name, Supplier<Block> crop) {
        Supplier<Item> item = items.registerItem(name, p -> new net.minecraft.world.item.BlockItem(crop.get(), p));
        tabItems.add(item);
        return item;
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

    public Supplier<DataComponentType<Integer>> intComponent(String name) {
        return components.registerComponentType(name,
                b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
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
