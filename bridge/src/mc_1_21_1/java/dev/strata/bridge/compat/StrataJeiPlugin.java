package dev.strata.bridge.compat;

import dev.strata.bridge.BridgeTexts;
import dev.strata.bridge.StrataBridge;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** JEI: Infoseiten für die Strata-Items. Wird nur geladen, wenn JEI installiert ist. */
@JeiPlugin
public final class StrataJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath("strata_bridge", "jei");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var info = BridgeTexts.jeiInfo();
        info.forEach((item, key) ->
                registration.addItemStackInfo(new ItemStack(item.get()), Component.translatable(key)));
        StrataBridge.LOG.info("[Strata-Bridge] JEI: {} Infoseiten registriert", info.size());
    }
}
