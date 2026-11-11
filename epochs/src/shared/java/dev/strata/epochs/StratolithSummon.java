package dev.strata.epochs;

import dev.strata.core.api.StrataAPI;
import dev.strata.core.mc.Mc;
import dev.strata.epochs.mob.Stratolith;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Beschwörung des Stratolithen: Rechtsklick mit einem Schichtkern auf die Oberseite von Zeitgestein. Nur wer alle
 * drei Epochen im Kodex gemeistert hat (Knoten {@code mastered_<epoche>}), und nur mit Platz (3 × 4 × 3 frei)
 * und nicht auf Friedlich. Der Schichtkern wird verbraucht, der Stratolith steigt aus dem Boden.
 */
public final class StratolithSummon {

    public static final List<String> REQUIRED = List.of("crystal_age", "spore_age", "ash_age");

    private StratolithSummon() {}

    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getItemStack().is(EpochsContent.LAYER_CORE.get())) return;
        if (!event.getLevel().getBlockState(event.getPos()).is(EpochsContent.CHRONOSTONE.get())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getFace() != Direction.UP) {
            Mc.actionbar(player, Component.translatable("message.strata_epochs.stratolith.top").withStyle(ChatFormatting.GRAY));
            return;
        }
        String problem = problem(player, level, event.getPos().above());
        if (problem != null) {
            Mc.actionbar(player, Component.translatable("message.strata_epochs.stratolith." + problem).withStyle(ChatFormatting.GOLD));
            return;
        }
        summon(level, event.getPos().above());
        if (!player.isCreative()) event.getItemStack().shrink(1);
    }

    /** Warum keine Beschwörung geht (Sprachschlüssel-Endung) oder null. Öffentlich für den Selbsttest. */
    public static String problem(ServerPlayer player, ServerLevel level, BlockPos at) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) return "peaceful";
        for (String epoch : REQUIRED) {
            if (!StrataAPI.hasNode(player, StrataEpochs.id("mastered_" + epoch))) return "need_mastery";
        }
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-1, 0, -1), at.offset(1, 3, 1))) {
            if (!level.getBlockState(p).canBeReplaced()) return "no_space";
        }
        return null;
    }

    /** Lässt den Stratolithen bei {@code at} aufsteigen. */
    public static Stratolith summon(ServerLevel level, BlockPos at) {
        Stratolith boss = new Stratolith(EpochsMobs.STRATOLITH.get(), level);
        boss.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        level.addFreshEntity(boss);
        boss.startRise();
        return boss;
    }

    /** Herz der Tiefenzeit in der zweiten Hand: Transplantator mit größerem Radius und ohne Fossil-Kosten. */
    public static boolean hasHeart(ServerPlayer player) {
        return player.getOffhandItem().is(EpochsContent.HEART_OF_DEEP_TIME.get());
    }

    /** Radius des Transplantators für diesen Spieler (mit Herz das Anderthalbfache, höchstens 32). */
    public static int transplantRadius(ServerPlayer player) {
        int base = EpochsConfig.TRANSPLANT_RADIUS.get();
        return hasHeart(player) ? Math.min(32, Math.round(base * 1.5f)) : base;
    }
}
