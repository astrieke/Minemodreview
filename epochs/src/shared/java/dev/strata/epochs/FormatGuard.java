package dev.strata.epochs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.MinecraftServer;

/**
 * Format-Wächter: Liest jede Datendatei der Strata-Mods (und des Demo-Datapacks) mit dem Codec der laufenden Minecraft-Version ein, schreibt sie
 * wieder heraus und meldet jedes Feld, das dabei verloren geht.
 *
 * <p>Minecraft übergeht unbekannte Felder stillschweigend. Ändert Mojang ein Format (z. B. 26.3: Loot-„conditions“ wurde
 * „condition“), lädt die Datei weiter ohne Fehler, nur fehlt dann die Bedingung. Genau das findet dieser Test.
 */
final class FormatGuard {

    /**
     * Felder, die beim Zurückschreiben fehlen dürfen: Sie stehen auf dem Standardwert, den der Codec weglässt. Selbst wenn
     * eine Version so ein Feld nicht mehr kennt, ändert sich nichts. Schlüssel ist das Pfadende, Wert der Standard als JSON.
     */
    private static final Map<String, String> DEFAULTS = Map.of(
            ".display.frame", "\"task\"",
            ".display.show_toast", "true",
            ".display.announce_to_chat", "true",
            ".result.count", "1",
            ".category", "\"misc\"",                 // Rezepte (ab 26.2 optional)
            ".solidify_seconds", "600",               // Strata-Epoche (ab 26.3 als Datapack-Registry geprüft)
            ".depth", "1",
            ".landscape.depth", "10",
            ".landscape.feature_count", "6");

    private FormatGuard() {}

    static int run(MinecraftServer server) {
        DynamicOps<JsonElement> ops = dev.strata.epochs.mc.MobCompat.dataOps(server);
        Map<String, Codec<?>> codecs = new LinkedHashMap<>();
        codecs.put("loot_table", net.minecraft.world.level.storage.loot.LootTable.DIRECT_CODEC);
        codecs.put("recipe", net.minecraft.world.item.crafting.Recipe.CODEC);
        codecs.put("advancement", net.minecraft.advancements.Advancement.CODEC);
        List<RegistryDataLoader.RegistryData<?>> registries = datapackRegistries();
        for (RegistryDataLoader.RegistryData<?> data : registries) {
            codecs.put(net.minecraft.core.registries.Registries.elementsDirPath(data.key()), data.elementCodec());
        }

        int files = 0;
        List<String> lost = new ArrayList<>();
        List<String> broken = new ArrayList<>();
        for (Map.Entry<String, Codec<?>> type : codecs.entrySet()) {
            var found = server.getResourceManager().listResources(type.getKey(), id -> id.getPath().endsWith(".json"));
            for (var entry : found.entrySet()) {
                if (!entry.getKey().getNamespace().startsWith("strata")) continue;   // Strata-Mods und Demo-Datapack
                String name = entry.getKey().getPath();
                if (name.substring(type.getKey().length() + 1).contains("/") && !type.getKey().equals("loot_table")
                        && !type.getKey().equals("advancement")) {
                    continue;   // Unterordner gehören zu einem anderen Typ (z. B. worldgen/biome unter worldgen)
                }
                files++;
                JsonElement source;
                try (Reader in = entry.getValue().openAsReader()) {
                    source = JsonParser.parseReader(in);
                } catch (Exception e) {
                    broken.add(name + ": " + e.getMessage());
                    continue;
                }
                String problem = roundTrip(type.getValue(), ops, source, name, lost);
                if (problem != null) broken.add(name + ": " + problem);
            }
        }
        boolean ok = files > 0 && lost.isEmpty() && broken.isEmpty();
        for (String l : lost) StrataEpochs.LOG.error("[Strata-Test] Datenformat: Feld geht verloren: {}", l);
        for (String b : broken) StrataEpochs.LOG.error("[Strata-Test] Datenformat: lässt sich nicht lesen: {}", b);
        StrataEpochs.LOG.info("[Strata-Test] Datenformat: {} Dateien, {} verlorene Felder, {} unlesbar -> {}",
                files, lost.size(), broken.size(), ok ? "OK" : "FEHLER");
        return ok ? 0 : 1;
    }

    /**
     * Alle Datapack-Registries samt Dimensionen, mit denen von NeoForge und Mods (Biom-Modifier, Strata-Epochen).
     * Bis 26.2 liefert sie NeoForge über {@code DataPackRegistriesHooks}; ab 26.3 stehen sie in Minecrafts Liste
     * {@code WORLD_REGISTRIES}. Reflexion, weil es die Methoden nicht in jeder Version gibt (Laufzeit: Mojang-Namen).
     */
    @SuppressWarnings("unchecked")
    private static List<RegistryDataLoader.RegistryData<?>> datapackRegistries() {
        try {
            var hook = Class.forName("net.neoforged.neoforge.registries.DataPackRegistriesHooks")
                    .getMethod("getDataPackRegistriesWithDimensions");
            return new ArrayList<>(((java.util.stream.Stream<RegistryDataLoader.RegistryData<?>>) hook.invoke(null)).toList());
        } catch (ReflectiveOperationException ignored) {
            // 26.3: kein Hook mehr, die Liste enthält alles
        }
        try {
            List<RegistryDataLoader.RegistryData<?>> list = new ArrayList<>(
                    (List<RegistryDataLoader.RegistryData<?>>) RegistryDataLoader.class.getField("WORLD_REGISTRIES").get(null));
            list.addAll(RegistryDataLoader.DIMENSION_REGISTRIES);
            return list;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Keine Liste der Datapack-Registries gefunden", e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String roundTrip(Codec codec, DynamicOps<JsonElement> ops, JsonElement source, String name, List<String> lost) {
        var decoded = codec.parse(ops, source);
        if (decoded.error().isPresent()) return "Lesen: " + ((com.mojang.serialization.DataResult.Error<?>) decoded.error().get()).message();
        var encoded = codec.encodeStart(ops, decoded.result().get());
        if (encoded.error().isPresent()) return "Schreiben: " + ((com.mojang.serialization.DataResult.Error<?>) encoded.error().get()).message();
        compare(source, (JsonElement) encoded.result().get(), name, lost);
        return null;
    }

    private static boolean isDefault(String path, JsonElement value) {
        // Längstes passendes Pfadende gewinnt (".landscape.depth" vor ".depth").
        return DEFAULTS.entrySet().stream().filter(d -> path.endsWith(d.getKey()))
                .max(java.util.Comparator.comparingInt(d -> d.getKey().length()))
                .map(d -> d.getValue().equals(value.toString())).orElse(false);
    }

    /** Jeder Schlüssel aus der Quelle muss nach dem Zurückschreiben noch da sein (rekursiv). */
    private static void compare(JsonElement source, JsonElement written, String path, List<String> lost) {
        if (source.isJsonObject() && written.isJsonObject()) {
            JsonObject w = written.getAsJsonObject();
            for (var e : source.getAsJsonObject().entrySet()) {
                String p = path + "." + e.getKey();
                if (!w.has(e.getKey())) {
                    if (!isDefault(p, e.getValue())) lost.add(p + " = " + e.getValue());
                } else {
                    compare(e.getValue(), w.get(e.getKey()), p, lost);
                }
            }
        } else if (source.isJsonArray() && written.isJsonArray()) {
            var s = source.getAsJsonArray();
            var w = written.getAsJsonArray();
            for (int i = 0; i < Math.min(s.size(), w.size()); i++) compare(s.get(i), w.get(i), path + "[" + i + "]", lost);
        } else if (source.isJsonObject() && written.isJsonArray() && written.getAsJsonArray().size() == 1) {
            compare(source, written.getAsJsonArray().get(0), path, lost);   // Einzelwert, als Liste geschrieben
        } else if (source.isJsonArray() && source.getAsJsonArray().size() == 1 && written.isJsonObject()) {
            compare(source.getAsJsonArray().get(0), written, path, lost);   // Liste mit einem Eintrag, als Einzelwert
        }
    }
}
