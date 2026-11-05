package dev.strata.epochs.mc;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import org.joml.Vector3f;

/** Kleine Unterschiede im Mob-Code: Minecraft 1.21.1. */
public final class MobCompat {

    private MobCompat() {}

    public static void hurt(Entity target, DamageSource source, float amount) {
        target.hurt(source, amount);
    }

    public static ParticleOptions dust(int rgb, float scale) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), scale);
    }

    /** Grundattribute eines Mobs. */
    public static AttributeSupplier.Builder baseAttributes() {
        return Mob.createMobAttributes();
    }

    /** Block-Tag aus Text, z. B. {@code "strata_epochs:spiralit_senses"}. */
    public static net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> blockTag(String id) {
        return net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                net.minecraft.resources.ResourceLocation.parse(id));
    }

    /** Block aus Text, z. B. {@code "minecraft:calcite"}; unbekannte IDs liefern Luft. */
    public static net.minecraft.world.level.block.Block block(String id) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse(id));
    }
}
