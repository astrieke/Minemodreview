package dev.strata.epochs.client;

import dev.strata.epochs.mob.MobModelData;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Baut aus einem Blockbench-Modell ({@link MobModelData}) die {@link LayerDefinition} für Minecraft.
 *
 * <p>Umrechnung wie der Java-Export von Blockbench (Minecraft dreht x und y um, Füße bei y = 24):
 * <ul>
 *   <li>Wurzelknochen: {@code offset(-ox, 24 - oy, oz)}, Kindknochen relativ zum Elternknochen mit
 *       umgedrehtem x und y.</li>
 *   <li>Würfel: {@code addBox(-(to.x - ox), -(to.y - oy), from.z - oz, Breite, Höhe, Tiefe)}.</li>
 *   <li>Drehung: {@code xRot = -rx}, {@code yRot = -ry}, {@code zRot = rz}.</li>
 *   <li>Gedrehte Würfel bekommen ein eigenes Kind-Teil {@code <würfel>_r1} am Drehpunkt.</li>
 * </ul>
 * Die Bausteine (MeshDefinition, PartDefinition, CubeListBuilder) sind seit 1.17 gleich,
 * deshalb gilt diese Klasse für alle Minecraft-Versionen.
 */
public final class MobModelBuilder {

    private static final float DEG = (float) (Math.PI / 180.0);

    private MobModelBuilder() {}

    public static LayerDefinition build(MobModelData model) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        Map<String, PartDefinition> parts = new HashMap<>();
        Map<String, MobModelData.Bone> pending = new java.util.LinkedHashMap<>();
        model.bones().forEach(b -> pending.put(b.name(), b));

        // Eltern vor Kindern anlegen, egal in welcher Reihenfolge die Datei sie nennt.
        while (!pending.isEmpty()) {
            boolean progress = false;
            for (var it = pending.values().iterator(); it.hasNext(); ) {
                MobModelData.Bone bone = it.next();
                if (bone.parent() != null && !parts.containsKey(bone.parent())) continue;
                parts.put(bone.name(), addBone(model, bone, bone.parent() == null ? root : parts.get(bone.parent())));
                it.remove();
                progress = true;
            }
            if (!progress) throw new IllegalStateException(model.id() + ": Knochen ohne gültigen Elternknochen: " + pending.keySet());
        }
        return LayerDefinition.create(mesh, model.textureWidth(), model.textureHeight());
    }

    private static PartDefinition addBone(MobModelData model, MobModelData.Bone bone, PartDefinition parentPart) {
        float[] o = bone.origin();
        PartPose pose;
        float rx = -bone.rotation()[0] * DEG, ry = -bone.rotation()[1] * DEG, rz = bone.rotation()[2] * DEG;
        if (bone.parent() == null) {
            pose = PartPose.offsetAndRotation(-o[0], 24 - o[1], o[2], rx, ry, rz);
        } else {
            float[] p = model.bone(bone.parent()).origin();
            pose = PartPose.offsetAndRotation(-(o[0] - p[0]), -(o[1] - p[1]), o[2] - p[2], rx, ry, rz);
        }

        CubeListBuilder cubes = CubeListBuilder.create();
        for (MobModelData.Cube c : model.cubes()) {
            if (c.bone().equals(bone.name()) && !rotated(c)) box(cubes, c, o);
        }
        PartDefinition part = parentPart.addOrReplaceChild(bone.name(), cubes, pose);

        for (MobModelData.Cube c : model.cubes()) {
            if (!c.bone().equals(bone.name()) || !rotated(c)) continue;
            float[] co = c.origin() != null ? c.origin() : o;
            float[] r = c.rotation();
            CubeListBuilder single = CubeListBuilder.create();
            box(single, c, co);
            part.addOrReplaceChild(c.name() + "_r1", single, PartPose.offsetAndRotation(
                    -(co[0] - o[0]), -(co[1] - o[1]), co[2] - o[2], -r[0] * DEG, -r[1] * DEG, r[2] * DEG));
        }
        return part;
    }

    private static boolean rotated(MobModelData.Cube c) {
        float[] r = c.rotation();
        return r != null && (r[0] != 0 || r[1] != 0 || r[2] != 0);
    }

    /** Ein Würfel relativ zum Punkt {@code o} (Knochen- oder Würfel-Drehpunkt, in Blockbench-Koordinaten). */
    private static void box(CubeListBuilder builder, MobModelData.Cube c, float[] o) {
        float w = c.to()[0] - c.from()[0];
        float h = c.to()[1] - c.from()[1];
        float d = c.to()[2] - c.from()[2];
        builder.texOffs(c.u(), c.v()).mirror(c.mirror())
                .addBox(-(c.to()[0] - o[0]), -(c.to()[1] - o[1]), c.from()[2] - o[2], w, h, d);
        builder.mirror(false);
    }
}
