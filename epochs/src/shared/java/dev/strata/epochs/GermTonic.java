package dev.strata.epochs;

import dev.strata.core.api.StrataAPI;
import dev.strata.core.mc.Mc;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Items mit Rechtsklick-Wirkung, über Events statt eigener Item-Klassen (gleich in allen Versionen):
 * <ul>
 *   <li><b>Keimfrische</b>: dreht in einer Epoche die Verfestigungs-Uhr um 60 s zurück.
 *       Die Antwort auf den Sporenbalg, hergestellt aus seinem Sporenbeutel.</li>
 *   <li><b>Kodex-Fragment</b> (Drop des Steinzeugen): schaltet den Knoten „Echo eines Reisenden“ frei.</li>
 * </ul>
 */
final class GermTonic {

    static final int REWIND_TICKS = 1200;
    private static final int COOLDOWN = 100;

    private GermTonic() {}

    static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (stack.is(EpochsContent.CODEX_FRAGMENT.get())) {
            readFragment(player, stack);
        } else if (stack.is(EpochsContent.GERM_TONIC.get())) {
            drink(player, stack);
        } else if (stack.is(EpochsContent.RESONANT_SPLINTER.get())) {
            dev.strata.epochs.mob.ResonantShard.useSplinter(player, stack);
        }
    }

    private static void drink(ServerPlayer player, ItemStack stack) {
        if (Mc.isOnCooldown(player, stack) || EpochTravel.epochOf(player.level()).isEmpty()) return;
        Solidification.advance(player, -REWIND_TICKS);
        Mc.addCooldown(player, stack, COOLDOWN);
        if (!player.isCreative()) stack.shrink(1);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_EMPTY,
                SoundSource.PLAYERS, 1.0f, 1.2f);
        Mc.actionbar(player, Component.translatable("message.strata_epochs.germ_tonic", REWIND_TICKS / 20)
                .withStyle(ChatFormatting.GREEN));
    }

    private static void readFragment(ServerPlayer player, ItemStack stack) {
        if (StrataAPI.unlock(player, StrataEpochs.id("witness_echo"))) {
            if (!player.isCreative()) stack.shrink(1);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN,
                    SoundSource.PLAYERS, 1.0f, 0.8f);
            Mc.actionbar(player, Component.translatable("message.strata_epochs.codex_fragment").withStyle(ChatFormatting.GOLD));
        } else {
            Mc.actionbar(player, Component.translatable("message.strata_epochs.codex_fragment.known").withStyle(ChatFormatting.GRAY));
        }
    }
}
