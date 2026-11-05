package dev.strata.bridge.compat;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.stack.EmiStack;
import dev.strata.bridge.BridgeTexts;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * EMI (nur Minecraft 1.21.1): dieselben Infoseiten wie im JEI-Plugin, als EMI-Info-Rezepte.
 * Wird von EMI über {@link EmiEntrypoint} gefunden und nur geladen, wenn EMI installiert ist.
 */
@EmiEntrypoint
public final class StrataEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        BridgeTexts.jeiInfo().forEach((item, key) -> {
            String path = BuiltInRegistries.ITEM.getKey(item.get()).getPath();
            registry.addRecipe(new EmiInfoRecipe(List.of(EmiStack.of(item.get())), List.of(Component.translatable(key)),
                    ResourceLocation.fromNamespaceAndPath("strata_bridge", "/info/" + path)));
        });
    }
}
