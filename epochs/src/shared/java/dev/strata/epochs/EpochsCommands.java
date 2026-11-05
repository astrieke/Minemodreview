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
                .then(Commands.literal("spiralit").executes(EpochsCommands::spiralit)));
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

    private static int spiralit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Vec3 look = player.getLookAngle();
        Vec3 at = player.position().add(look.x * 2, 1.0, look.z * 2);
        Spiralit.hatch((ServerLevel) player.level(), at, player);
        return 1;
    }
}
