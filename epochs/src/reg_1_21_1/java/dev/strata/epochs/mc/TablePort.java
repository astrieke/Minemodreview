package dev.strata.epochs.mc;

import dev.strata.epochs.CoreSamples;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Kernanalyse-Tisch für Trichter und Rohre (1.21.1, {@link IItemHandler}): nimmt nicht analysierte Bohrkerne an
 * und legt sie analysiert in das Inventar darunter. Ohne Inventar darunter oder wenn es voll ist, nimmt er nichts.
 * Der Tisch speichert selbst nichts, deshalb gibt es auch nichts zu entnehmen.
 */
public final class TablePort implements IItemHandler {

    private final Block block;
    private final ServerLevel level;
    private final BlockPos pos;

    private TablePort(ServerLevel level, BlockPos pos, Block block) {
        this.level = level;
        this.block = block;
        this.pos = pos.immutable();
    }

    public static void register(IEventBus modBus, Supplier<Block> table) {
        modBus.addListener((RegisterCapabilitiesEvent event) -> event.registerBlock(Capabilities.ItemHandler.BLOCK,
                // Nicht von unten: dort sitzt die Ausgabe, ein Rohr darunter bekäme seine Kerne sonst zurück.
                (level, pos, state, be, side) -> level instanceof ServerLevel server && side != Direction.DOWN
                        ? new TablePort(server, pos, state.getBlock()) : null,
                table.get()));
    }

    /** Für den Selbsttest: wie viele der Kerne der Tisch annimmt (ohne etwas zu bewegen, wenn {@code simulate}). */
    public static int insert(ServerLevel level, BlockPos table, ItemStack stack, boolean simulate) {
        IItemHandler port = level.getCapability(Capabilities.ItemHandler.BLOCK, table, Direction.UP);
        if (port == null) return -1;
        return stack.getCount() - port.insertItem(0, stack, simulate).getCount();
    }

    /** Nur für den Selbsttest: ein Cache wie in Rohren (Mekanism & Co.), mit Zähler für Invalidierungen. */
    public static Probe probe(ServerLevel level, BlockPos table) {
        int[] count = {0};
        boolean[] alive = {true};
        var cache = net.neoforged.neoforge.capabilities.BlockCapabilityCache.create(Capabilities.ItemHandler.BLOCK, level, table,
                Direction.UP, () -> alive[0], () -> count[0]++);
        return new Probe(() -> cache.getCapability() != null, count, () -> alive[0] = false);
    }

    /** {@code close} meldet den Cache am Level ab. */
    public record Probe(java.util.function.BooleanSupplier reachable, int[] invalidations, Runnable close) {}

    /** Für den Selbsttest: bietet der Tisch von dieser Seite eine Schnittstelle an? */
    public static boolean offersFrom(ServerLevel level, BlockPos table, Direction side) {
        return level.getCapability(Capabilities.ItemHandler.BLOCK, table, side) != null;
    }

    @Override public int getSlots() { return 1; }
    @Override public ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }
    @Override public int getSlotLimit(int slot) { return 64; }
    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return !CoreSamples.analyzedByMachine(level.getServer(), stack).isEmpty();
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return stack;
        if (!level.getBlockState(pos).is(block)) return stack;   // abgebaut, nur noch im Cache eines Rohrs
        ItemStack analyzed = CoreSamples.analyzedByMachine(level.getServer(), stack);
        if (analyzed.isEmpty()) return stack;
        IItemHandler out = level.getCapability(Capabilities.ItemHandler.BLOCK, pos.below(), Direction.UP);
        if (out == null) return stack;
        ItemStack rest = ItemHandlerHelper.insertItemStacked(out, analyzed, simulate);
        return rest.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(rest.getCount());
    }
}
