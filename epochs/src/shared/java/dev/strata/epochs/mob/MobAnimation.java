package dev.strata.epochs.mob;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Eine Blockbench-Animation und ihr Abspieler, ohne Bezug zur Minecraft-Version.
 *
 * <p>Regeln wie in Blockbench: Werte werden auf die Ruhepose addiert, Winkel komponentenweise
 * interpoliert (kein Quaternion, keine Normalisierung auf ±180°). Ist einer der beiden
 * Nachbar-Keyframes {@code catmullrom}, läuft der Abschnitt als Catmull-Rom-Spline,
 * bei {@code step} springt der Wert, sonst linear.
 */
public record MobAnimation(String name, String shortName, float length, Loop loop,
                           Map<String, Map<Channel, List<Keyframe>>> tracks) {

    public enum Channel { ROTATION, POSITION, SCALE }

    public enum Loop { LOOP, ONCE, HOLD }

    public record Keyframe(float time, float[] value, String interpolation) {}

    /** Wert eines Kanals zur Zeit {@code seconds}, oder null, wenn der Knochen diesen Kanal nicht nutzt. */
    public float[] sample(String bone, Channel channel, float seconds) {
        Map<Channel, List<Keyframe>> byChannel = tracks.get(bone);
        if (byChannel == null) return null;
        List<Keyframe> keys = byChannel.get(channel);
        if (keys == null || keys.isEmpty()) return null;
        return interpolate(keys, localTime(seconds));
    }

    /** Zeit innerhalb der Animation: bei Schleifen modulo Länge, sonst am Ende festgehalten. */
    public float localTime(float seconds) {
        if (length <= 0) return 0;
        if (loop == Loop.LOOP) {
            float t = seconds % length;
            return t < 0 ? t + length : t;
        }
        return Math.max(0, Math.min(length, seconds));
    }

    public boolean finished(float seconds) {
        return loop == Loop.ONCE && seconds >= length;
    }

    static float[] interpolate(List<Keyframe> keys, float t) {
        if (t <= keys.get(0).time) return keys.get(0).value.clone();
        Keyframe last = keys.get(keys.size() - 1);
        if (t >= last.time) return last.value.clone();
        int i = 0;
        while (i < keys.size() - 2 && keys.get(i + 1).time <= t) i++;
        Keyframe before = keys.get(i);
        Keyframe after = keys.get(i + 1);
        float span = after.time - before.time;
        float a = span <= 0 ? 1 : (t - before.time) / span;
        float[] out = new float[3];
        if ("catmullrom".equals(before.interpolation) || "catmullrom".equals(after.interpolation)) {
            float[] p0 = keys.get(Math.max(0, i - 1)).value;
            float[] p3 = keys.get(Math.min(keys.size() - 1, i + 2)).value;
            for (int c = 0; c < 3; c++) out[c] = catmullRom(a, p0[c], before.value[c], after.value[c], p3[c]);
        } else if ("step".equals(before.interpolation)) {
            return before.value.clone();
        } else {
            for (int c = 0; c < 3; c++) out[c] = before.value[c] + (after.value[c] - before.value[c]) * a;
        }
        return out;
    }

    static float catmullRom(float t, float p0, float p1, float p2, float p3) {
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5f * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    static MobAnimation parse(JsonObject json) {
        String name = json.get("name").getAsString();
        String shortName = name.substring(name.lastIndexOf('.') + 1);
        Loop loop = switch (json.has("loop") ? json.get("loop").getAsString() : "once") {
            case "loop" -> Loop.LOOP;
            case "hold_on_last_frame" -> Loop.HOLD;
            default -> Loop.ONCE;
        };
        Map<String, Map<Channel, List<Keyframe>>> tracks = new LinkedHashMap<>();
        for (JsonElement e : json.getAsJsonArray("keyframes")) {
            JsonObject k = e.getAsJsonObject();
            Channel channel = Channel.valueOf(k.get("channel").getAsString().toUpperCase(java.util.Locale.ROOT));
            JsonArray v = k.getAsJsonArray("value");
            float[] value = {v.get(0).getAsFloat(), v.get(1).getAsFloat(), v.get(2).getAsFloat()};
            String interp = k.has("interpolation") ? k.get("interpolation").getAsString() : "linear";
            tracks.computeIfAbsent(k.get("bone").getAsString(), b -> new LinkedHashMap<>())
                    .computeIfAbsent(channel, c -> new ArrayList<>())
                    .add(new Keyframe(k.get("time").getAsFloat(), value, interp));
        }
        tracks.values().forEach(m -> m.values().forEach(l -> l.sort(Comparator.comparingDouble(Keyframe::time))));
        return new MobAnimation(name, shortName, json.get("length").getAsFloat(), loop, tracks);
    }
}
