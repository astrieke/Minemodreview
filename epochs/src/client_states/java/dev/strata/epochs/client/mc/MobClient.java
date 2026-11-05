package dev.strata.epochs.client.mc;

import dev.strata.epochs.EpochsMobs;
import dev.strata.epochs.client.MobModelBuilder;
import dev.strata.epochs.mob.GeodeCrawler;
import dev.strata.epochs.mob.StrataMobDef;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Renderer und Modell-Ebenen der Strata-Mobs anmelden: Minecraft 1.21.11 bis 26.3. */
public final class MobClient {

    private MobClient() {}

    public static ModelLayerLocation layer(StrataMobDef def) {
        return new ModelLayerLocation(Identifier.fromNamespaceAndPath(def.modId(), def.name()), "main");
    }

    public static Identifier texture(StrataMobDef def, String suffix) {
        return Identifier.fromNamespaceAndPath(def.modId(), "textures/entity/" + def.name() + suffix + ".png");
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterLayerDefinitions event) ->
                event.registerLayerDefinition(layer(GeodeCrawler.DEF), () -> MobModelBuilder.build(GeodeCrawler.DEF.model())));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
                event.registerEntityRenderer(EpochsMobs.GEODE_CRAWLER.get(), ctx -> new StrataMobRenderer<>(ctx, GeodeCrawler.DEF)));
    }
}
