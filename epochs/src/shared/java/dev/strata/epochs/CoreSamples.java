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

    /** Zusatzzeit für Spieler, die mit einem analysierten Kern eine Epoche betreten. */
    public static final double ANALYSIS_BONUS = 0.25;

    public static boolean isAnalyzed(ItemStack stack) {
        return stack.getOrDefault(EpochsContent.ANALYZED.get(), 0) > 0;
    }

    /**
     * Analysiert den Kern für diesen Spieler: Die Notiz zeigt Tiefe, Zeitlimit, Fundstücke und was im Kodex
     * zum Transplantator noch fehlt. Ein analysierter Kern gibt beim Betreten {@link #ANALYSIS_BONUS} mehr Zeit.
     */
    public static void analyze(ItemStack stack, net.minecraft.server.level.ServerPlayer player) {
        String id = epochOf(stack).orElse(null);
        if (id == null) return;
        dev.strata.core.Epoch epoch = dev.strata.core.api.StrataAPI.epochs(player.level().getServer()).get(id);
        List<Component> lines = new java.util.ArrayList<>();
        lines.add(epochName(id));
        lines.add(Component.translatable("tooltip.strata_epochs.analysis.bonus", Math.round(ANALYSIS_BONUS * 100))
                .withStyle(net.minecraft.ChatFormatting.GREEN));
        if (epoch != null) {
            lines.add(Component.translatable("tooltip.strata_epochs.analysis.depth", epoch.depth(),
                    epoch.solidifySeconds() / 60).withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("tooltip.strata_epochs.analysis.finds",
                Component.translatableWithFallback("epoch." + id.replace(':', '.') + ".finds", "?"))
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        if (id.startsWith(StrataEpochs.MOD_ID + ":")) {
            String name = id.substring(id.indexOf(':') + 1);
            boolean visited = dev.strata.core.api.StrataAPI.hasNode(player, StrataEpochs.id("visited_" + name));
            boolean fossil = dev.strata.core.api.StrataAPI.hasNode(player, StrataEpochs.id("fossil_" + name));
            lines.add(Component.translatable("tooltip.strata_epochs.analysis.codex", mark(visited), mark(fossil))
                    .withStyle(visited && fossil ? net.minecraft.ChatFormatting.LIGHT_PURPLE : net.minecraft.ChatFormatting.GRAY));
        }
        stack.set(EpochsContent.ANALYZED.get(), 1);
        stack.set(DataComponents.LORE, new ItemLore(lines));
    }

    private static Component mark(boolean done) {
        return Component.literal(done ? "✔" : "✘")
                .withStyle(done ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED);
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
