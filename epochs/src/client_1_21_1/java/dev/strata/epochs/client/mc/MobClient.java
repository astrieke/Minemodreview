package dev.strata.epochs.client.mc;

import dev.strata.epochs.EpochsMobs;
import dev.strata.epochs.client.MobModelBuilder;
import dev.strata.epochs.mob.GeodeCrawler;
import dev.strata.epochs.mob.PetrifiedWitness;
import dev.strata.epochs.mob.Spiralit;
import dev.strata.epochs.mob.SporeBellows;
import java.util.List;
import dev.strata.epochs.mob.StrataMobDef;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Renderer und Modell-Ebenen der Strata-Mobs anmelden: Minecraft 1.21.1. */
public final class MobClient {

    private MobClient() {}

    public static ModelLayerLocation layer(StrataMobDef def) {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(def.modId(), def.name()), "main");
    }

    public static ResourceLocation texture(StrataMobDef def, String suffix) {
        return ResourceLocation.fromNamespaceAndPath(def.modId(), "textures/entity/" + def.name() + suffix + ".png");
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterLayerDefinitions event) -> {
            for (StrataMobDef def : List.of(GeodeCrawler.DEF, SporeBellows.DEF, PetrifiedWitness.DEF, Spiralit.DEF)) {
                event.registerLayerDefinition(layer(def), () -> MobModelBuilder.build(def.model()));
            }
        });
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
            event.registerEntityRenderer(EpochsMobs.GEODE_CRAWLER.get(), ctx -> new StrataMobRenderer<>(ctx, GeodeCrawler.DEF));
            event.registerEntityRenderer(EpochsMobs.SPORE_BELLOWS.get(), ctx -> new StrataMobRenderer<>(ctx, SporeBellows.DEF));
            event.registerEntityRenderer(EpochsMobs.PETRIFIED_WITNESS.get(), ctx -> new StrataMobRenderer<>(ctx, PetrifiedWitness.DEF));
            event.registerEntityRenderer(EpochsMobs.SPIRALIT.get(), ctx -> new StrataMobRenderer<>(ctx, Spiralit.DEF));
        });
    }
}
