package dev.strata.epochs.client.mc;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Leucht-Ebene einreichen: Minecraft 1.21.11 bis 26.2 (mit Bruch-Overlay-Argument). */
final class GlowSubmit {

    private GlowSubmit() {}

    static void submit(SubmitNodeCollector collector, StrataMobModel model, StrataMobRenderState state,
                       PoseStack poseStack, RenderType renderType, int color) {
        collector.order(1).submitModel(model, state, poseStack, renderType, 0xF000F0, OverlayTexture.NO_OVERLAY,
                color, null, state.outlineColor, null);
    }
}
