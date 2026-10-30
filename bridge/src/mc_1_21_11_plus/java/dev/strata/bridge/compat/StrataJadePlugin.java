package dev.strata.bridge.compat;

import dev.strata.bridge.BridgeTexts;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/** Jade: Hinweise beim Anschauen von Epochentor, Erzen und versteinertem Stein. Nur geladen, wenn Jade installiert ist. */
@WailaPlugin
public final class StrataJadePlugin implements IWailaPlugin {

    static final Identifier UID = Identifier.fromNamespaceAndPath("strata_bridge", "blocks");

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(Provider.INSTANCE, Block.class);
    }

    enum Provider implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            String key = BridgeTexts.jadeLine(accessor.getBlock(), accessor.getLevel());
            if (key != null) tooltip.add(Component.translatable(key).withStyle(ChatFormatting.AQUA));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
