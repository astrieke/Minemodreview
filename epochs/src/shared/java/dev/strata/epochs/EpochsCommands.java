package dev.strata.epochs;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.strata.core.Epoch;
import dev.strata.core.api.StrataAPI;
import dev.strata.core.mc.Mc;
import dev.strata.epochs.mob.Spiralit;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Befehle für Pack-Autoren und Tests (OP):
 * <ul>
 *   <li>{@code /strata_epochs transplant <epoche> [radius]} – Transplantation an der eigenen Position,
 *       ohne Kodex-Voraussetzung und ohne Verbrauch</li>
 *   <li>{@code /strata_epochs spiralit} – lässt einen Spiralit für sich schlüpfen</li>
 *   <li>{@code /strata_epochs status} – Zeitanker (auch in Curios-Slots) und Zeitbonus des Spielers</li>
 * </ul>
 */
final class EpochsCommands {

    private EpochsCommands() {}

    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("strata_epochs").requires(Mc.opOnly())
                .then(Commands.literal("transplant")
                        .then(Commands.argument("epoch", Mc.idArgument())
                                .executes(ctx -> transplant(ctx, EpochsConfig.TRANSPLANT_RADIUS.get()))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(2, 32))
                                        .executes(ctx -> transplant(ctx, IntegerArgumentType.getInteger(ctx, "radius"))))))
                .then(Commands.literal("spiralit").executes(EpochsCommands::spiralit))
                .then(Commands.literal("status").executes(EpochsCommands::status))
                .then(Commands.literal("bearing").executes(EpochsCommands::bearing))
                .then(Commands.literal("rift")
                        .then(Commands.literal("close").executes(EpochsCommands::closeRifts))
                        .then(Commands.argument("epoch", Mc.idArgument()).executes(EpochsCommands::openRift))));
    }

    private static int transplant(CommandContext<CommandSourceStack> ctx, int radius) {
        String id = Mc.getId(ctx, "epoch");
        Epoch epoch = StrataAPI.epochs(ctx.getSource().getServer()).get(id);
        if (epoch == null || epoch.landscape().isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(id + " hat keine Landschaft (Feld landscape)"));
            return 0;
        }
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
        Transplanter.start(level, pos, id, epoch.landscape().get(), radius, ctx.getSource().getPlayer());
        ctx.getSource().sendSuccess(() -> Component.literal("Transplantation gestartet: " + id + ", Radius " + radius), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        boolean anchor = Solidification.hasTimeAnchor(player);
        long bonus = Math.round(Solidification.timeBonus(player) * 100);
        String text = "[Strata] " + player.getName().getString() + ": Zeitanker " + (anchor ? "ja" : "nein")
                + ", Zeitbonus +" + bonus + " %";
        StrataEpochs.LOG.info(text);
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
        return anchor ? 1 : 0;
    }

    /** Peilung wie im Epochentor (Schleichen + Rechtsklick), zum Testen ohne Tor. */
    private static int bearing(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (EpochTravel.epochOf(player.level()).isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Nur in einer Epoche"));
            return 0;
        }
        BiomeBearing.tell(player);
        return 1;
    }

    private static int spiralit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Vec3 look = player.getLookAngle();
        Vec3 at = player.position().add(look.x * 2, 1.0, look.z * 2);
        Spiralit.hatch((ServerLevel) player.level(), at, player);
        return 1;
    }

    /** Öffnet einen Zeitriss 3 Blöcke vor dem Spieler (zum Ausprobieren). */
    private static int openRift(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String id = Mc.getId(ctx, "epoch");
        var epoch = dev.strata.core.api.StrataAPI.epochs(ctx.getSource().getServer()).get(id);
        if (epoch == null || epoch.landscape().isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(id + " hat keine Landschaft (Feld landscape)"));
            return 0;
        }
        var player = ctx.getSource().getPlayerOrException();
        var level = (net.minecraft.server.level.ServerLevel) player.level();
        var look = player.getLookAngle();
        int x = (int) Math.floor(player.getX() + look.x * 3), z = (int) Math.floor(player.getZ() + look.z * 3);
        var pos = new net.minecraft.core.BlockPos(x,
                level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        TimeRifts.open(level, pos, id);
        ctx.getSource().sendSuccess(() -> Component.literal("Zeitriss geöffnet: " + id + " bei " + pos.toShortString()), true);
        return 1;
    }

    private static int closeRifts(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        int n = TimeRifts.closeAll(ctx.getSource().getLevel());
        ctx.getSource().sendSuccess(() -> Component.literal(n + " Zeitrisse geschlossen"), true);
        return n;
    }
}
