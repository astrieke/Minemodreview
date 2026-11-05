package dev.strata.epochs;

import dev.strata.epochs.client.mc.MobClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** Client-Teil von Strata Epochs: Renderer und Modelle der Mobs. Wird nur auf dem Client geladen. */
@Mod(value = StrataEpochs.MOD_ID, dist = Dist.CLIENT)
public final class StrataEpochsClient {

    public StrataEpochsClient(IEventBus modBus) {
        MobClient.register(modBus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) -> {
                    var line = EpochsGear.tooltip(event.getItemStack());
                    if (line != null) event.getToolTip().add(line);
                });
    }
}
