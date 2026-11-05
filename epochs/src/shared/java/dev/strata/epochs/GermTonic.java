package dev.strata.epochs;

import dev.strata.core.mc.Mc;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Keimfrische: Rechtsklick in einer Epoche dreht die Verfestigungs-Uhr um 60 s zurück.
 * Die Antwort auf den Sporenbalg, hergestellt aus seinem Sporenbeutel.
 */
final class GermTonic {

    static final int REWIND_TICKS = 1200;
    private static final int COOLDOWN = 100;

    private GermTonic() {}

    static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(EpochsContent.GERM_TONIC.get()) || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (Mc.isOnCooldown(player, stack) || EpochTravel.epochOf(player.level()).isEmpty()) return;
        Solidification.advance(player, -REWIND_TICKS);
        Mc.addCooldown(player, stack, COOLDOWN);
        if (!player.isCreative()) stack.shrink(1);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_EMPTY,
                SoundSource.PLAYERS, 1.0f, 1.2f);
        Mc.actionbar(player, Component.translatable("message.strata_epochs.germ_tonic", REWIND_TICKS / 20)
                .withStyle(ChatFormatting.GREEN));
    }
}
