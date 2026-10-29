package dev.strata.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.HashSet;
import java.util.Set;

/** Welche Kodex-Knoten ein Spieler freigeschaltet hat. Wird am Spieler gespeichert. */
public record CodexProgress(Set<String> unlocked) {

    public static final CodexProgress EMPTY = new CodexProgress(Set.of());

    public static final Codec<CodexProgress> CODEC = Codec.STRING.listOf()
            .xmap(list -> new CodexProgress(Set.copyOf(list)), p -> p.unlocked().stream().sorted().toList());

    /** Neuere NeoForge-Versionen speichern Attachments als Map. */
    public static final MapCodec<CodexProgress> MAP_CODEC = CODEC.fieldOf("unlocked");

    public CodexProgress {
        unlocked = Set.copyOf(unlocked);
    }

    public CodexProgress with(String node) {
        Set<String> next = new HashSet<>(unlocked);
        next.add(node);
        return new CodexProgress(next);
    }
}
