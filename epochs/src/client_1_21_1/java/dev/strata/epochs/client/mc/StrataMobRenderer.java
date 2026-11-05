package dev.strata.epochs.client.mc;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.strata.epochs.mob.StrataMob;
import dev.strata.epochs.mob.StrataMobDef;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Generischer Renderer aller Strata-Mobs mit Leucht-Ebenen: Minecraft 1.21.1. */
public final class StrataMobRenderer<T extends StrataMob> extends MobRenderer<T, StrataMobModel<T>> {

    private final ResourceLocation texture;

    public StrataMobRenderer(EntityRendererProvider.Context ctx, StrataMobDef def) {
        super(ctx, new StrataMobModel<>(ctx.bakeLayer(MobClient.layer(def)), def), def.shadow());
        this.texture = MobClient.texture(def, "");
        for (String glow : def.glowLayers()) addLayer(new GlowLayer(MobClient.texture(def, "_glow_" + glow)));
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return texture;
    }

    /** Leuchtet unabhängig vom Umgebungslicht; die Stärke kommt vom Mob (0..1). */
    private final class GlowLayer extends RenderLayer<T, StrataMobModel<T>> {
        private final RenderType renderType;

        GlowLayer(ResourceLocation texture) {
            super(StrataMobRenderer.this);
            this.renderType = RenderType.eyes(texture);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int light, T entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            float glow = entity.glow();
            if (glow <= 0.01f || entity.isInvisible()) return;
            int c = Math.round(glow * 255);
            int color = 0xFF000000 | c << 16 | c << 8 | c;
            getParentModel().renderToBuffer(poseStack, buffers.getBuffer(renderType), 0xF000F0, OverlayTexture.NO_OVERLAY, color);
        }
    }
}
