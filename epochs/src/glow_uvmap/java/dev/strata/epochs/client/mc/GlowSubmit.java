package dev.strata.epochs.client.mc;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Leucht-Ebene einreichen: ab Minecraft 26.3 (ohne Bruch-Overlay-Argument). */
final class GlowSubmit {

    private GlowSubmit() {}

    static void submit(SubmitNodeCollector collector, StrataMobModel model, StrataMobRenderState state,
                       PoseStack poseStack, RenderType renderType, int color) {
        collector.order(1).submitModel(model, state, poseStack, renderType, 0xF000F0, OverlayTexture.NO_OVERLAY,
                color, null, state.outlineColor);
    }
}
