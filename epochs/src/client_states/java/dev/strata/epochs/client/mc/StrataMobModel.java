package dev.strata.epochs.client.mc;

import dev.strata.epochs.client.MobPoser;
import dev.strata.epochs.mob.StrataMobDef;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;

/** Generisches Modell aller Strata-Mobs: Minecraft 1.21.11 bis 26.3 (mit Render States). */
public final class StrataMobModel extends EntityModel<StrataMobRenderState> {

    private final MobPoser poser;

    public StrataMobModel(ModelPart root, StrataMobDef def) {
        super(root);
        this.poser = new MobPoser(def, root);
    }

    @Override
    public void setupAnim(StrataMobRenderState state) {
        super.setupAnim(state);
        poser.pose(new MobPoser.Input(state.ageInTicks, state.walkAnimationPos, state.walkAnimationSpeed,
                state.yRot, state.xRot, state.action, state.actionSeconds, state.stage));
    }
}
