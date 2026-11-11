package dev.strata.epochs.mc;

import net.minecraft.world.entity.Entity;

/** Befehls-Tags einer Entity ({@code /tag}); die Abfrage heißt ab 26.1 {@code entityTags()} statt {@code getTags()}. */
public final class EntityTags {

    private EntityTags() {}

    public static boolean has(Entity entity, String tag) {
        return entity.getTags().contains(tag);
    }
}
