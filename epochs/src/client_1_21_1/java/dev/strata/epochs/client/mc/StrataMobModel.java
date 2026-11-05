package dev.strata.epochs.client.mc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.strata.epochs.client.MobPoser;
import dev.strata.epochs.mob.StrataMob;
import dev.strata.epochs.mob.StrataMobDef;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;

/** Generisches Modell aller Strata-Mobs: Minecraft 1.21.1 (ohne Render States). */
public final class StrataMobModel<T extends StrataMob> extends EntityModel<T> {

    private final ModelPart root;
    private final MobPoser poser;

    public StrataMobModel(ModelPart root, StrataMobDef def) {
        this.root = root;
        this.poser = new MobPoser(def, root);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float partial = ageInTicks - entity.tickCount;
        poser.pose(new MobPoser.Input(ageInTicks, limbSwing, limbSwingAmount, netHeadYaw, headPitch,
                entity.currentAction(), entity.actionSeconds(partial)));
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color) {
        root.render(poseStack, buffer, light, overlay, color);
    }
}
