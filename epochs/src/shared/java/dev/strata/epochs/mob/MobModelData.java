package dev.strata.epochs.mob;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ein Mob-Modell aus Blockbench, als neutrale Daten ({@code mobs/<id>/model.json}).
 *
 * <p>Koordinaten sind Blockbench-Pixel: y nach oben, Füße bei y = 0, der Mob schaut nach −z.
 * Server und Client lesen dieselbe Datei. Der Client baut daraus das Modell,
 * der Server prüft im Selbsttest, ob alle Knochen der Animationen existieren.
 */
public record MobModelData(String id, int textureWidth, int textureHeight,
                           List<Bone> bones, List<Cube> cubes, Map<String, MobAnimation> animations) {

    public record Bone(String name, String parent, float[] origin, float[] rotation) {}

    public record Cube(String name, String bone, float[] from, float[] to, int u, int v,
                       float[] rotation, float[] origin, boolean mirror) {}

    private static final Map<String, MobModelData> CACHE = new ConcurrentHashMap<>();

    /** Lädt {@code assets/<modId>/strata_mobs/<id>.json} aus dem Mod-Jar (einmal, danach aus dem Cache). */
    public static MobModelData load(String modId, String id) {
        return CACHE.computeIfAbsent(modId + ":" + id, key -> {
            String path = "/assets/" + modId + "/strata_mobs/" + id + ".json";
            try (InputStream in = MobModelData.class.getResourceAsStream(path)) {
                if (in == null) throw new IllegalStateException("Mob-Modell fehlt: " + path);
                return parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public Bone bone(String name) {
        for (Bone b : bones) if (b.name.equals(name)) return b;
        return null;
    }

    /** Animation nach Kurzname, z. B. {@code "walk"} für {@code animation.geode_crawler.walk}. */
    public MobAnimation animation(String shortName) {
        return animations.get(shortName);
    }

    static MobModelData parse(JsonObject json) {
        JsonArray size = json.getAsJsonArray("texture_size");
        List<Bone> bones = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray("bones")) {
            JsonObject b = e.getAsJsonObject();
            bones.add(new Bone(b.get("name").getAsString(),
                    b.has("parent") ? b.get("parent").getAsString() : null,
                    vec(b, "origin", 0), vec(b, "rotation", 0)));
        }
        List<Cube> cubes = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray("cubes")) {
            JsonObject c = e.getAsJsonObject();
            JsonArray uv = c.getAsJsonArray("uv_offset");
            cubes.add(new Cube(c.get("name").getAsString(), c.get("bone").getAsString(),
                    vec(c, "from", 0), vec(c, "to", 0), uv.get(0).getAsInt(), uv.get(1).getAsInt(),
                    c.has("rotation") ? vec(c, "rotation", 0) : null,
                    c.has("origin") ? vec(c, "origin", 0) : null,
                    c.has("mirror_uv") && c.get("mirror_uv").getAsBoolean()));
        }
        Map<String, MobAnimation> animations = new LinkedHashMap<>();
        if (json.has("animations")) {
            for (JsonElement e : json.getAsJsonArray("animations")) {
                MobAnimation anim = MobAnimation.parse(e.getAsJsonObject());
                animations.put(anim.shortName(), anim);
            }
        }
        return new MobModelData(json.get("id").getAsString(), size.get(0).getAsInt(), size.get(1).getAsInt(),
                List.copyOf(bones), List.copyOf(cubes), Map.copyOf(animations));
    }

    static float[] vec(JsonObject obj, String key, float fallback) {
        if (!obj.has(key)) return new float[]{fallback, fallback, fallback};
        JsonArray a = obj.getAsJsonArray(key);
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }
}
