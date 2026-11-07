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
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Kernanalyse-Tisch für Trichter und Rohre (ab 1.21.11, {@link ResourceHandler}): nimmt nicht analysierte Bohrkerne
 * an und legt sie analysiert in das Inventar darunter. Das Einfügen dort läuft in derselben Transaktion, ein
 * Abbruch nimmt also beides zurück. Der Tisch speichert selbst nichts, deshalb gibt es auch nichts zu entnehmen.
 */
public final class TablePort implements ResourceHandler<ItemResource> {

    private final Block block;
    private final ServerLevel level;
    private final BlockPos pos;

    private TablePort(ServerLevel level, BlockPos pos, Block block) {
        this.level = level;
        this.block = block;
        this.pos = pos.immutable();
    }

    public static void register(IEventBus modBus, Supplier<Block> table) {
        modBus.addListener((RegisterCapabilitiesEvent event) -> event.registerBlock(Capabilities.Item.BLOCK,
                // Nicht von unten: dort sitzt die Ausgabe, ein Rohr darunter bekäme seine Kerne sonst zurück.
                (level, pos, state, be, side) -> level instanceof ServerLevel server && side != Direction.DOWN
                        ? new TablePort(server, pos, state.getBlock()) : null,
                table.get()));
    }

    /** Für den Selbsttest: wie viele der Kerne der Tisch annimmt (ohne etwas zu bewegen, wenn {@code simulate}). */
    public static int insert(ServerLevel level, BlockPos table, ItemStack stack, boolean simulate) {
        ResourceHandler<ItemResource> port = level.getCapability(Capabilities.Item.BLOCK, table, Direction.UP);
        if (port == null) return -1;
        try (Transaction tx = Transaction.openRoot()) {
            int moved = port.insert(ItemResource.of(stack), stack.getCount(), tx);
            if (!simulate) tx.commit();
            return moved;
        }
    }

    /** Für den Selbsttest: ein Cache wie in Rohren (Mekanism & Co.), mit Zähler für Invalidierungen. */
    public static Probe probe(ServerLevel level, BlockPos table) {
        int[] count = {0};
        var cache = net.neoforged.neoforge.capabilities.BlockCapabilityCache.create(Capabilities.Item.BLOCK, level, table,
                Direction.UP, () -> true, () -> count[0]++);
        return new Probe(() -> cache.getCapability() != null, count);
    }

    public record Probe(java.util.function.BooleanSupplier reachable, int[] invalidations) {}

    /** Für den Selbsttest: bietet der Tisch von dieser Seite eine Schnittstelle an? */
    public static boolean offersFrom(ServerLevel level, BlockPos table, Direction side) {
        return level.getCapability(Capabilities.Item.BLOCK, table, side) != null;
    }

    @Override public int size() { return 1; }
    @Override public ItemResource getResource(int index) { return ItemResource.EMPTY; }
    @Override public long getAmountAsLong(int index) { return 0; }
    @Override public long getCapacityAsLong(int index, ItemResource resource) { return 64; }
    @Override public int extract(int index, ItemResource resource, int amount, TransactionContext tx) { return 0; }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return !resource.isEmpty() && !CoreSamples.analyzedByMachine(level.getServer(), resource.toStack(1)).isEmpty();
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext tx) {
        if (resource.isEmpty() || amount <= 0) return 0;
        if (!level.getBlockState(pos).is(block)) return 0;   // abgebaut, nur noch im Cache eines Rohrs
        ItemStack analyzed = CoreSamples.analyzedByMachine(level.getServer(), resource.toStack(1));
        if (analyzed.isEmpty()) return 0;
        ResourceHandler<ItemResource> out = level.getCapability(Capabilities.Item.BLOCK, pos.below(), Direction.UP);
        if (out == null) return 0;
        return ResourceHandlerUtil.insertStacking(out, ItemResource.of(analyzed), amount, tx);
    }
}
