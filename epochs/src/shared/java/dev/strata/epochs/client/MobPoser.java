package dev.strata.epochs.client;

import dev.strata.epochs.mob.MobAnimation;
import dev.strata.epochs.mob.MobModelData;
import dev.strata.epochs.mob.StrataMobDef;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Setzt die Pose eines Strata-Mobs aus seinen Blockbench-Animationen, für alle Minecraft-Versionen gleich.
 *
 * <p>Reihenfolge pro Bild: Ruhepose, dann additiv Idle (immer), Laufen (eingeblendet nach Tempo),
 * die laufende Aktion und zuletzt die Kopfdrehung zum Blickziel (nur ohne Aktion).
 */
public final class MobPoser {

    private static final float DEG = (float) (Math.PI / 180.0);
    /** Vanilla-Vierbeiner: Beinschwung cos(limbSwing · 0,6662), eine Periode ≈ 9,43 Einheiten. */
    private static final float WALK_PERIOD = (float) (2 * Math.PI / 0.6662);

    /** Was der Renderer pro Bild übergibt (aus Entity oder Render-State). */
    public record Input(float ageInTicks, float walkPos, float walkSpeed, float headYaw, float headPitch,
                        String action, float actionSeconds) {}

    private final StrataMobDef def;
    private final MobModelData model;
    private final ModelPart root;
    private final Map<String, ModelPart> bones = new HashMap<>();

    public MobPoser(StrataMobDef def, ModelPart root) {
        this.def = def;
        this.model = def.model();
        this.root = root;
        for (MobModelData.Bone bone : model.bones()) bones.put(bone.name(), find(bone));
    }

    private ModelPart find(MobModelData.Bone bone) {
        if (bone.parent() == null) return root.getChild(bone.name());
        return find(model.bone(bone.parent())).getChild(bone.name());
    }

    public ModelPart bone(String name) {
        return bones.get(name);
    }

    public void pose(Input in) {
        for (ModelPart part : bones.values()) part.resetPose();

        if (def.idle() != null) apply(model.animation(def.idle()), in.ageInTicks() / 20f, 1f);

        MobAnimation walk = def.walk() == null ? null : model.animation(def.walk());
        float walkWeight = Math.min(1f, in.walkSpeed() * 2f);
        if (walk != null && walkWeight > 0.01f) {
            apply(walk, in.walkPos() / WALK_PERIOD * walk.length(), walkWeight);
        }

        MobAnimation action = in.action() == null ? null : model.animation(in.action());
        if (action != null) {
            apply(action, in.actionSeconds(), 1f);
        } else {
            ModelPart head = bones.get("head");
            if (head != null) {
                head.yRot += clamp(in.headYaw(), -35f, 35f) * DEG;
                head.xRot += clamp(in.headPitch(), -25f, 25f) * DEG;
            }
        }
    }

    /** Addiert eine Animation zur Zeit {@code seconds} mit Gewicht {@code w} auf die aktuelle Pose. */
    private void apply(MobAnimation anim, float seconds, float w) {
        if (anim == null) return;
        for (String boneName : anim.tracks().keySet()) {
            ModelPart part = bones.get(boneName);
            if (part == null) continue;
            float[] rot = anim.sample(boneName, MobAnimation.Channel.ROTATION, seconds);
            if (rot != null) {
                part.xRot += -rot[0] * DEG * w;
                part.yRot += -rot[1] * DEG * w;
                part.zRot += rot[2] * DEG * w;
            }
            float[] pos = anim.sample(boneName, MobAnimation.Channel.POSITION, seconds);
            if (pos != null) {
                part.x += -pos[0] * w;
                part.y += -pos[1] * w;
                part.z += pos[2] * w;
            }
            float[] scale = anim.sample(boneName, MobAnimation.Channel.SCALE, seconds);
            if (scale != null) {
                part.xScale *= 1 + (scale[0] - 1) * w;
                part.yScale *= 1 + (scale[1] - 1) * w;
                part.zScale *= 1 + (scale[2] - 1) * w;
            }
        }
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
