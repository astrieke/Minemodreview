package dev.strata.core.mc;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.NewDatapackRegistryEvent;

/** Datapack-Registries anmelden: ab Minecraft 26.3 ({@code NewDatapackRegistryEvent}). */
public final class McDatapack {

    private McDatapack() {}

    public static <T> void register(IEventBus modBus, ResourceKey<Registry<T>> key, Codec<T> codec) {
        modBus.addListener((NewDatapackRegistryEvent event) -> event.worldRegistry(key, codec, codec));
    }
}
