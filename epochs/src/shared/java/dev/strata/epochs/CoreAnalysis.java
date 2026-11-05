package dev.strata.epochs;

import dev.strata.core.api.StrataAPI;
import dev.strata.core.mc.Mc;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Kernanalyse-Tisch: Rechtsklick mit Bohrkernen analysiert den ganzen Stapel.
 * Die Notiz am Kern zeigt Tiefe, Zeitlimit, Fundstücke und den Kodex-Stand zum Transplantator;
 * wer mit einem analysierten Kern eine Epoche betritt, hat 25 % mehr Zeit bis zur Verfestigung.
 */
final class CoreAnalysis {

    private CoreAnalysis() {}

    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!event.getLevel().getBlockState(event.getPos()).is(EpochsContent.ANALYSIS_TABLE.get())) return;
        ItemStack held = event.getItemStack();
        if (!held.is(EpochsContent.CORE_SAMPLE.get())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) return;
        analyze(player, held, event.getPos());
    }

    static void analyze(ServerPlayer player, ItemStack held, BlockPos table) {
        if (CoreSamples.isAnalyzed(held)) {
            Mc.actionbar(player, Component.translatable("message.strata_epochs.analysis.known").withStyle(ChatFormatting.GRAY));
            return;
        }
        CoreSamples.analyze(held, player);
        ServerLevel level = (ServerLevel) player.level();
        double x = table.getX() + 0.5, y = table.getY() + 1.1, z = table.getZ() + 0.5;
        level.playSound(null, x, y, z, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0f, 1.0f);
        level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8f, 1.6f);
        level.sendParticles(ParticleTypes.ENCHANT, x, y + 0.3, z, 20, 0.3, 0.2, 0.3, 0.5);
        Mc.actionbar(player, Component.translatable("message.strata_epochs.analysis.done",
                CoreSamples.epochName(CoreSamples.epochOf(held).orElse(""))).withStyle(ChatFormatting.AQUA));
        StrataAPI.unlock(player, StrataEpochs.id("first_analysis"));
    }
}
