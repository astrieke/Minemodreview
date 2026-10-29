package dev.strata.core.mc;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/** Datapack-Registries anmelden: Minecraft 1.21.1 bis 26.2 ({@code DataPackRegistryEvent}). */
public final class McDatapack {

    private McDatapack() {}

    public static <T> void register(IEventBus modBus, ResourceKey<Registry<T>> key, Codec<T> codec) {
        modBus.addListener((DataPackRegistryEvent.NewRegistry event) -> event.dataPackRegistry(key, codec, codec));
    }
}
