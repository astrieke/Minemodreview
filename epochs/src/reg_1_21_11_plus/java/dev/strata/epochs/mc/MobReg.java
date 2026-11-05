package dev.strata.epochs.mc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registrierung von Mobs, Spawn-Eiern und Sounds für Minecraft 1.21.11 bis 26.3. */
public final class MobReg {

    private final String modId;
    private final DeferredRegister<EntityType<?>> entities;
    private final DeferredRegister.Items items;
    private final DeferredRegister<SoundEvent> sounds;
    private final List<Supplier<? extends Item>> eggs = new ArrayList<>();

    public MobReg(String modId) {
        this.modId = modId;
        entities = DeferredRegister.create(Registries.ENTITY_TYPE, modId);
        items = DeferredRegister.createItems(modId);
        sounds = DeferredRegister.create(Registries.SOUND_EVENT, modId);
    }

    public <T extends Mob> Supplier<EntityType<T>> entity(String name, EntityType.EntityFactory<T> factory,
                                                          MobCategory category, float width, float height, int trackingRange) {
        return entities.register(name, id -> EntityType.Builder.of(factory, category)
                .sized(width, height).clientTrackingRange(trackingRange)
                .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
    }

    /** Spawn-Ei: ab 1.21.5 mit eigener Textur ({@code textures/item/<name>.png}), die Farben entfallen. */
    public Supplier<Item> spawnEgg(String name, Supplier<? extends EntityType<? extends Mob>> type, int background, int highlight) {
        Supplier<Item> egg = items.registerItem(name, SpawnEggItem::new, p -> p.spawnEgg(type.get()));
        eggs.add(egg);
        return egg;
    }

    public Supplier<SoundEvent> sound(String name) {
        return sounds.register(name, () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(modId, name)));
    }

    public void register(IEventBus modBus) {
        entities.register(modBus);
        items.register(modBus);
        sounds.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) eggs.forEach(e -> event.accept(e.get()));
        });
    }
}
