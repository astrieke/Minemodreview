package dev.strata.epochs;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/** Erzeugen und Auslesen von Bohrkernen. */
public final class CoreSamples {

    private CoreSamples() {}

    public static ItemStack create(String epochId) {
        ItemStack stack = new ItemStack(EpochsContent.CORE_SAMPLE.get());
        stack.set(EpochsContent.SAMPLE_EPOCH.get(), epochId);
        stack.set(DataComponents.LORE, new ItemLore(List.of(epochName(epochId))));
        return stack;
    }

    public static Optional<String> epochOf(ItemStack stack) {
        if (!stack.is(EpochsContent.CORE_SAMPLE.get())) return Optional.empty();
        return Optional.ofNullable(stack.get(EpochsContent.SAMPLE_EPOCH.get()));
    }

    /** Übersetzbarer Name, z. B. {@code epoch.strata_epochs.crystal_age}. */
    public static Component epochName(String epochId) {
        return Component.translatable("epoch." + epochId.replace(':', '.'));
    }
}
