package dev.strata.epochs;

import dev.strata.epochs.mc.MobCompat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;

/**
 * Zweite Stufe der Verfestigung: Nach den Blöcken versteinern Gegenstände im Inventar. Ein Stapel wird zu einem
 * „versteinerten Gegenstand“, der das Original unverändert in sich trägt und sich nicht benutzen lässt.
 * Er löst sich von selbst, sobald sein Träger nicht (mehr) verfestigt: zurück in der Oberwelt, nach einer
 * Keimfrische oder in den Händen eines anderen Spielers. Verloren geht dabei nichts.
 *
 * <p>Pro Epoche einstellbar über {@code petrify_items_seconds} (0 = aus). Was nie versteinert, steht im
 * Item-Tag {@code strata_epochs:petrify_immune} (Standard: Zeitanker und Keimfrische, die Mittel dagegen).
 */
public final class ItemPetrification {

    /** Nur die 36 Plätze von Schnellleiste und Rucksack; Rüstung und zweite Hand bleiben frei. */
    public static final int SLOTS = 36;

    public static final String IMMUNE_TAG = StrataEpochs.id("petrify_immune");
    private static final TagKey<Item> IMMUNE = MobCompat.itemTag(IMMUNE_TAG);

    private ItemPetrification() {}

    public static boolean isPetrified(ItemStack stack) {
        return stack.is(EpochsContent.PETRIFIED_ITEM.get());
    }

    /** Kann dieser Stapel versteinern? Leere, schon versteinerte und immune Gegenstände nicht. */
    public static boolean canPetrify(ItemStack stack) {
        return !stack.isEmpty() && !isPetrified(stack) && !stack.is(IMMUNE);
    }

    /** Der versteinerte Gegenstand zu einem Stapel (das Original steckt als Kopie darin). */
    public static ItemStack petrify(ItemStack original) {
        ItemStack lump = new ItemStack(EpochsContent.PETRIFIED_ITEM.get());
        lump.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(original.copy())));
        lump.set(DataComponents.ITEM_NAME, Component.translatable("item.strata_epochs.petrified_item.of", describe(original)));
        lump.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("tooltip.strata_epochs.petrified_item").withStyle(ChatFormatting.GRAY))));
        return lump;
    }

    /** Das Original aus einem versteinerten Gegenstand (leer, wenn nichts darin steckt). */
    public static ItemStack content(ItemStack lump) {
        ItemContainerContents contents = lump.get(DataComponents.CONTAINER);
        return contents == null ? ItemStack.EMPTY : contents.copyOne();
    }

    /**
     * Versteinert einen zufälligen Stapel aus Schnellleiste oder Rucksack. Gibt das Original zurück (für die
     * Meldung) oder leer, wenn nichts mehr versteinern kann.
     */
    public static ItemStack petrifyOne(ServerPlayer player, RandomSource random) {
        var inventory = player.getInventory();
        List<Integer> candidates = new ArrayList<>();
        for (int slot = 0; slot < SLOTS; slot++) {
            if (canPetrify(inventory.getItem(slot))) candidates.add(slot);
        }
        if (candidates.isEmpty()) return ItemStack.EMPTY;
        int slot = candidates.get(random.nextInt(candidates.size()));
        ItemStack original = inventory.getItem(slot);
        inventory.setItem(slot, petrify(original));
        ServerLevel level = (ServerLevel) player.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 1.0f, 0.6f);
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                EpochsContent.PETRIFIED_STONE.get().defaultBlockState()), player.getX(), player.getY() + 1.0,
                player.getZ(), 12, 0.3, 0.4, 0.3, 0.05);
        return original;
    }

    /** Löst alle versteinerten Gegenstände im ganzen Inventar (auch Rüstung und zweite Hand). Gibt die Anzahl zurück. */
    public static int thaw(ServerPlayer player) {
        var inventory = player.getInventory();
        int thawed = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!isPetrified(stack)) continue;
            inventory.setItem(slot, content(stack));
            thawed++;
        }
        if (thawed > 0) {
            ServerLevel level = (ServerLevel) player.level();
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 0.8f, 1.4f);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 1.2f);
            player.sendSystemMessage(Component.translatable(thawed == 1 ? "message.strata_epochs.solidify.thaw_one"
                    : "message.strata_epochs.solidify.thaw", thawed).withStyle(ChatFormatting.GREEN));
        }
        return thawed;
    }

    /** „3× Diamant“ bzw. nur der Name bei einem einzelnen Gegenstand. */
    public static Component describe(ItemStack stack) {
        return stack.getCount() == 1 ? stack.getHoverName()
                : Component.literal(stack.getCount() + "× ").append(stack.getHoverName());
    }
}
