package dev.strata.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.strata.common.CodexGraph;
import dev.strata.core.mc.Mc;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import dev.strata.core.api.StrataAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Debug- und Admin-Befehle:
 * <ul>
 *   <li>{@code /strata epoch} – welche Epoche liegt unter mir?</li>
 *   <li>{@code /strata codex list} – meine Knoten und was als Nächstes geht</li>
 *   <li>{@code /strata codex unlock <knoten>} – Knoten für sich selbst freischalten (OP)</li>
 *   <li>{@code /strata codex grant <spieler> <knoten>} – Knoten für Spieler freischalten (OP, z. B. als Quest-Belohnung)</li>
 *   <li>{@code /strata codex validate} – Kodex-Datapack prüfen</li>
 * </ul>
 */
final class StrataCommands {

    private StrataCommands() {}

    static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("strata")
                .then(Commands.literal("epoch").executes(StrataCommands::epoch))
                .then(Commands.literal("codex")
                        .then(Commands.literal("list").executes(StrataCommands::list))
                        .then(Commands.literal("validate").requires(Mc.opOnly()).executes(StrataCommands::validate))
                        .then(Commands.literal("unlock").requires(Mc.opOnly())
                                .then(Commands.argument("node", Mc.idArgument()).executes(StrataCommands::unlock)))
                        .then(Commands.literal("grant").requires(Mc.opOnly())
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("node", Mc.idArgument()).executes(StrataCommands::grant))))));
    }

    private static int epoch(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        long seed = Mc.seed(player);
        var epoch = StrataRegistries.epochSelector(ctx.getSource().getServer())
                .select(seed, player.getBlockX() >> 4, player.getBlockZ() >> 4);
        String text = epoch.map(e -> "Unter dir liegt: " + e.id()).orElse("Keine Epochen geladen.");
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
        return epoch.isPresent() ? 1 : 0;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        CodexProgress progress = Mc.getProgress(player);
        CodexGraph graph = StrataRegistries.codex(ctx.getSource().getServer());
        List<String> next = graph.unlockable(progress.unlocked());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Freigeschaltet: " + progress.unlocked().stream().sorted().toList()
                        + "\nAls Nächstes möglich: " + next), false);
        return progress.unlocked().size();
    }

    private static int validate(CommandContext<CommandSourceStack> ctx) {
        List<String> errors = StrataRegistries.codex(ctx.getSource().getServer()).validate();
        if (errors.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("Kodex ist in Ordnung."), false);
        } else {
            errors.forEach(e -> ctx.getSource().sendFailure(Component.literal(e)));
        }
        return errors.size();
    }

    private static int unlock(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String node = Mc.getId(ctx, "node");
        CodexGraph graph = StrataRegistries.codex(ctx.getSource().getServer());
        CodexProgress progress = Mc.getProgress(player);
        if (!graph.canUnlock(node, progress.unlocked())) {
            ctx.getSource().sendFailure(Component.literal(
                    "Nicht freischaltbar: " + node + " (unbekannt, schon frei oder Voraussetzungen fehlen)"));
            return 0;
        }
        StrataAPI.unlock(player, node);
        ctx.getSource().sendSuccess(() -> Component.literal("Freigeschaltet: " + node), true);
        return 1;
    }

    /** Für Quest-Belohnungen und Command-Blöcke: still, wenn ein Spieler den Knoten schon hat. */
    private static int grant(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String node = Mc.getId(ctx, "node");
        int count = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(ctx, "targets")) {
            if (StrataAPI.unlock(player, node)) count++;
        }
        int granted = count;
        ctx.getSource().sendSuccess(() -> Component.literal(node + " für " + granted + " Spieler freigeschaltet"), true);
        return count;
    }
}
