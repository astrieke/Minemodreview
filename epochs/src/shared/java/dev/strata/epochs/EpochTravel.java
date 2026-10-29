package dev.strata.epochs;

import dev.strata.core.api.StrataAPI;
import dev.strata.core.mc.Mc;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Reisen in eine Epoche und zurück. */
public final class EpochTravel {

    private EpochTravel() {}

    /** Die Epoche, zu der diese Dimension gehört (leer, wenn keine Epochen-Dimension). */
    public static Optional<String> epochOf(Level level) {
        MinecraftServer server = level.getServer();
        if (server == null) return Optional.empty();
        return StrataAPI.epochOfDimension(server, Mc.levelId(level));
    }

    public static void enter(ServerPlayer player, ServerLevel target) {
        player.setData(EpochsContent.VISIT.get(),
                new EpochVisit(Mc.levelId(player.level()), player.getX(), player.getY(), player.getZ(), 0));
        BlockPos arrival = prepareArrival(target, player.getBlockX(), player.getBlockZ());
        Mc.teleport(player, target, arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5);
        Mc.actionbar(player, Component.translatable("message.strata_epochs.gate.entered"));
    }

    public static void leave(ServerPlayer player) {
        EpochVisit visit = player.getData(EpochsContent.VISIT.get());
        MinecraftServer server = player.level().getServer();
        ServerLevel home = visit.returnDimension().isEmpty() ? null : Mc.level(server, visit.returnDimension());
        player.setData(EpochsContent.VISIT.get(), EpochVisit.NONE);
        if (home == null) {
            home = server.overworld();
            int y = home.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            Mc.teleport(player, home, 0.5, y, 0.5);
        } else {
            Mc.teleport(player, home, visit.x(), visit.y(), visit.z());
        }
    }

    /**
     * Sucht in der Epochen-Dimension einen sicheren Ankunftsplatz nahe (x, z)
     * und stellt daneben ein Epochentor für den Rückweg auf.
     * Gibt es keinen freien Platz, wird eine kleine Kammer ausgehoben.
     */
    public static BlockPos prepareArrival(ServerLevel level, int x, int z) {
        BlockPos spot = findStandingSpot(level, x, z);
        if (spot == null) {
            spot = new BlockPos(x, Math.max(Mc.minY(level) + 16, 32), z);
            carveChamber(level, spot);
        }
        BlockPos gatePos = spot.east(2);
        if (!level.getBlockState(gatePos.below()).isSolid()) {
            level.setBlockAndUpdate(gatePos.below(), EpochsContent.CHRONOSTONE.get().defaultBlockState());
        }
        level.setBlockAndUpdate(gatePos, EpochsContent.EPOCH_GATE.get().defaultBlockState());
        level.setBlockAndUpdate(gatePos.above(), Blocks.AIR.defaultBlockState());
        return spot;
    }

    /** Fester Boden mit zwei Luftblöcken darüber, von oben nach unten gesucht (unter der Bedrock-Decke). */
    static BlockPos findStandingSpot(ServerLevel level, int x, int z) {
        int top = Math.min(Mc.maxY(level) - 8, Mc.minY(level) + 180);
        for (int dx = 0; dx <= 8; dx += 4) {
            for (int y = top; y > Mc.minY(level) + 4; y--) {
                BlockPos feet = new BlockPos(x + dx, y, z);
                BlockState floor = level.getBlockState(feet.below());
                if (floor.isSolid() && !floor.is(Blocks.BEDROCK) && level.getBlockState(feet).isAir()
                        && level.getBlockState(feet.above()).isAir()
                        && level.getFluidState(feet).isEmpty()) {
                    return feet;
                }
            }
        }
        return null;
    }

    private static void carveChamber(ServerLevel level, BlockPos feet) {
        for (int dx = -1; dx <= 3; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlockAndUpdate(feet.offset(dx, -1, dz), EpochsContent.CHRONOSTONE.get().defaultBlockState());
                for (int dy = 0; dy <= 2; dy++) {
                    level.setBlockAndUpdate(feet.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }
}
