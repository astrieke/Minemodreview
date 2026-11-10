package dev.strata.epochs.mc;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Kleine Unterschiede im Mob-Code: Minecraft 1.21.11 bis 26.3. */
public final class MobCompat {

    private MobCompat() {}

    public static void hurt(Entity target, DamageSource source, float amount) {
        if (target.level() instanceof ServerLevel level) target.hurtServer(level, source, amount);
    }

    public static ParticleOptions dust(int rgb, float scale) {
        return new DustParticleOptions(rgb, scale);
    }

    /** Grundattribute eines Mobs. Ab 1.21.6 braucht das Anlocken mit Items (TemptGoal) die Reichweite als Attribut. */
    public static AttributeSupplier.Builder baseAttributes() {
        return Mob.createMobAttributes().add(Attributes.TEMPT_RANGE, 10.0);
    }

    /** Block-Tag aus Text, z. B. {@code "strata_epochs:spiralit_senses"}. */
    public static net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome> biomeTag(String id) {
        return net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME,
                net.minecraft.resources.Identifier.parse(id));
    }

    public static net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> blockTag(String id) {
        return net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                net.minecraft.resources.Identifier.parse(id));
    }

    /** Block aus Text, z. B. {@code "minecraft:calcite"}; unbekannte IDs liefern Luft. */
    public static net.minecraft.world.level.block.Block block(String id) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse(id));
    }
}
