package dev.strata.epochs.client.mc;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.strata.epochs.mob.StrataMob;
import dev.strata.epochs.mob.StrataMobDef;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** Generischer Renderer aller Strata-Mobs mit Leucht-Ebenen: Minecraft 1.21.11 bis 26.3. */
public final class StrataMobRenderer<T extends StrataMob> extends MobRenderer<T, StrataMobRenderState, StrataMobModel> {

    private final Identifier texture;

    public StrataMobRenderer(EntityRendererProvider.Context ctx, StrataMobDef def) {
        super(ctx, new StrataMobModel(ctx.bakeLayer(MobClient.layer(def)), def), def.shadow());
        this.texture = MobClient.texture(def, "");
        for (int i = 0; i < def.glowLayers().size(); i++) {
            addLayer(new GlowLayer(MobClient.texture(def, "_glow_" + def.glowLayers().get(i)), i));
        }
    }

    @Override
    public StrataMobRenderState createRenderState() {
        return new StrataMobRenderState();
    }

    @Override
    public void extractRenderState(T entity, StrataMobRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.action = entity.currentAction();
        state.actionSeconds = entity.actionSeconds(partialTick);
        state.stage = entity.stage();
        for (int i = 0; i < state.glow.length; i++) state.glow[i] = entity.isInvisible() ? 0f : entity.glow(i);
    }

    @Override
    public Identifier getTextureLocation(StrataMobRenderState state) {
        return texture;
    }

    /** Leuchtet unabhängig vom Umgebungslicht; die Stärke kommt vom Mob (0..1). */
    private final class GlowLayer extends RenderLayer<StrataMobRenderState, StrataMobModel> {
        private final RenderType renderType;
        private final int index;

        GlowLayer(Identifier texture, int index) {
            super(StrataMobRenderer.this);
            this.renderType = RenderTypes.eyes(texture);
            this.index = index;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, StrataMobRenderState state,
                           float yRot, float xRot) {
            float glow = state.glow[index];
            if (glow <= 0.01f) return;
            int c = Math.round(glow * 255);
            GlowSubmit.submit(collector, getParentModel(), state, poseStack, renderType, 0xFF000000 | c << 16 | c << 8 | c);
        }
    }
}
