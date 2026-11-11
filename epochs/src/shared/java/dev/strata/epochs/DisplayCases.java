package dev.strata.epochs;

import dev.strata.core.Epoch;
import dev.strata.core.api.StrataAPI;
import dev.strata.core.mc.Mc;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Vitrinen und das „Museum der Erdgeschichte“. In einer Vitrine schwebt ein Exponat als Item-Display (dreht sich
 * zum Betrachter); das Display selbst speichert das Item, die Vitrine braucht daher keine BlockEntity.
 * <ul>
 *   <li>Rechtsklick mit einem Gegenstand stellt einen davon aus, Rechtsklick mit leerer Hand nimmt ihn heraus.</li>
 *   <li>Wird die Vitrine entfernt (Abbau, Explosion, Kolben), fällt das Exponat heraus.</li>
 *   <li>Museum: Liegen in Vitrinen im Umkreis von {@link #MUSEUM_RANGE} Blöcken von jeder Epoche mit Dimension
 *       ein Bohrkern und ihr Fossil, schaltet sich der Kodex-Knoten {@code strata_epochs:museum} frei.</li>
 * </ul>
 */
public final class DisplayCases {

    public static final String TAG = "strata_exhibit";
    public static final int MUSEUM_RANGE = 12;
    /** Höhe des Exponats über der Unterkante der Vitrine (in der Glashaube). */
    private static final double HEIGHT = 0.62;

    private DisplayCases() {}

    /** Ein Exponat, das zum Museum gehört: Beschriftung und Prüfung. */
    public record Exhibit(Component label, Predicate<ItemStack> matches) {}

    /** Was das Museum braucht: pro Epoche mit Dimension ein Bohrkern und (falls bekannt) ihr Fossil. */
    public static List<Exhibit> requirements(MinecraftServer server) {
        List<Exhibit> list = new ArrayList<>();
        for (var e : StrataAPI.epochs(server).entrySet()) {
            Epoch epoch = e.getValue();
            if (epoch.dimension().isEmpty()) continue;
            String id = e.getKey();
            list.add(new Exhibit(Component.translatable("message.strata_epochs.museum.core", CoreSamples.epochName(id)),
                    s -> CoreSamples.epochOf(s).map(id::equals).orElse(false)));
            if (FossilPrep.epochOfFossil(new ItemStack(FossilPrep.fossilFor(id))).map(id::equals).orElse(false)) {
                var fossil = FossilPrep.fossilFor(id);
                list.add(new Exhibit(Component.translatable(fossil.getDescriptionId()), s -> s.is(fossil)));
            }
        }
        return list;
    }

    /** Das Display in der Vitrine bei {@code pos} (oder null). */
    public static Display.ItemDisplay find(ServerLevel level, BlockPos pos) {
        // Erkannt an der Lage (in der Vitrine); das Tag dient nur Befehlen, sein Abfragen heißt ab 26.3 anders.
        var found = level.getEntitiesOfClass(Display.ItemDisplay.class, new AABB(pos));
        return found.isEmpty() ? null : found.get(0);
    }

    public static ItemStack exhibit(ServerLevel level, BlockPos pos) {
        Display.ItemDisplay display = find(level, pos);
        return display == null ? ItemStack.EMPTY : display.getSlot(0).get();
    }

    /** Stellt einen Gegenstand aus (nimmt einen vom Stapel). Gibt false zurück, wenn die Vitrine belegt ist. */
    public static boolean put(ServerLevel level, BlockPos pos, ItemStack held) {
        if (held.isEmpty() || find(level, pos) != null) return false;
        Transplanter.command(level, String.format(Locale.ROOT, "summon minecraft:item_display %.2f %.2f %.2f "
                + "{Tags:[\"%s\"],billboard:\"vertical\",transformation:{left_rotation:[0f,0f,0f,1f],"
                + "right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.6f,0.6f,0.6f]}}",
                pos.getX() + 0.5, pos.getY() + HEIGHT, pos.getZ() + 0.5, TAG));
        Display.ItemDisplay display = find(level, pos);
        if (display == null) return false;
        display.getSlot(0).set(held.copyWithCount(1));
        held.shrink(1);
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0f, 1.0f);
        return true;
    }

    /** Nimmt das Exponat heraus (leer, wenn nichts darin war). */
    public static ItemStack take(ServerLevel level, BlockPos pos) {
        Display.ItemDisplay display = find(level, pos);
        if (display == null) return ItemStack.EMPTY;
        ItemStack stack = display.getSlot(0).get().copy();
        display.discard();
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0f, 1.0f);
        return stack;
    }

    /** Vitrine ist weg: Exponat als Item fallen lassen. */
    static void dropExhibit(ServerLevel level, BlockPos pos) {
        ItemStack stack = take(level, pos);
        if (stack.isEmpty()) return;
        ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    /** Welche Museums-Exponate liegen in Vitrinen um {@code center}? Gibt die erfüllten Einträge zurück. */
    public static List<Exhibit> present(ServerLevel level, BlockPos center) {
        List<ItemStack> shown = new ArrayList<>();
        for (var d : level.getEntitiesOfClass(Display.ItemDisplay.class, new AABB(center).inflate(MUSEUM_RANGE))) {
            if (level.getBlockState(d.blockPosition()).is(EpochsContent.DISPLAY_CASE.get())) shown.add(d.getSlot(0).get());
        }
        List<Exhibit> result = new ArrayList<>();
        for (Exhibit ex : requirements(level.getServer())) {
            if (shown.stream().anyMatch(ex.matches())) result.add(ex);
        }
        return result;
    }

    /** Zeigt den Stand des Museums an und schaltet den Kodex-Knoten frei, wenn alles da ist. */
    static void checkMuseum(ServerPlayer player, ServerLevel level, BlockPos pos) {
        List<Exhibit> all = requirements(level.getServer());
        List<Exhibit> have = present(level, pos);
        if (all.isEmpty()) return;
        if (have.size() >= all.size()) {
            boolean fresh = StrataAPI.unlock(player, StrataEpochs.id("museum"));
            Mc.actionbar(player, Component.translatable(fresh ? "message.strata_epochs.museum.done"
                    : "message.strata_epochs.museum.complete").withStyle(ChatFormatting.GOLD));
            return;
        }
        Component missing = all.stream().filter(e -> !have.contains(e)).findFirst().map(Exhibit::label).orElse(Component.empty());
        Mc.actionbar(player, Component.translatable("message.strata_epochs.museum.progress", have.size(), all.size(), missing)
                .withStyle(ChatFormatting.YELLOW));
    }

    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!event.getLevel().getBlockState(event.getPos()).is(EpochsContent.DISPLAY_CASE.get())) return;
        ItemStack held = event.getItemStack();
        if (held.isEmpty() && event.getEntity().isShiftKeyDown()) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)) return;
        BlockPos pos = event.getPos();
        if (held.isEmpty()) {
            ItemStack taken = take(level, pos);
            if (!taken.isEmpty() && !player.getInventory().add(taken)) {
                level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY(), player.getZ(), taken));
            }
            return;
        }
        if (find(level, pos) != null) {
            Mc.actionbar(player, Component.translatable("message.strata_epochs.museum.occupied").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (put(level, pos, held)) checkMuseum(player, level, pos);
    }
}
