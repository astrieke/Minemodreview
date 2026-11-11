package dev.strata.epochs;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Epochen-Pflanze (Quarzdistel, Harzfarn, Glutknolle): vier Stufen wie Rote Bete. Sie steht nur auf dem Boden
 * ihrer Epoche ({@code #strata_epochs:soil/<epoche>}) und nur in einem Biom der Epoche
 * ({@code #strata_epochs:epoch/<epoche>}), also in der Epoche selbst oder auf transplantiertem Boden.
 * Licht braucht sie nicht, die Epochen sind Höhlenwelten. Knochenmehl wirkt nicht (siehe CropBase).
 */
public class EpochCrop extends dev.strata.epochs.mc.CropBase {

    /** Im Mittel wächst sie bei jedem fünften Zufallstick eine Stufe (etwa 17 Minuten bis zur Reife). */
    public static final int GROWTH_CHANCE = 5;

    private final String epoch;
    private final TagKey<Block> soil;
    private final TagKey<Biome> biomes;
    private final Supplier<? extends Item> seed;

    public EpochCrop(Properties props, String epochName, Supplier<? extends Item> seed) {
        super(props);
        this.epoch = StrataEpochs.id(epochName);
        this.soil = dev.strata.epochs.mc.MobCompat.blockTag(StrataEpochs.id("soil/" + epochName));
        this.biomes = dev.strata.epochs.mc.MobCompat.biomeTag(StrataEpochs.id("epoch/" + epochName));
        this.seed = seed;
    }

    /** Die Epoche, z. B. {@code strata_epochs:crystal_age}. */
    public String epoch() {
        return epoch;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return seed.get();
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(soil);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return mayPlaceOn(level.getBlockState(below), level, below) && level.getBiome(pos).is(biomes);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int age = state.getValue(getAgeProperty());
        if (age >= getMaxAge()) return;
        // Über die NeoForge-Hooks, damit Jahreszeiten- und Wachstums-Mods mitreden können.
        if (net.neoforged.neoforge.common.CommonHooks.canCropGrow(level, pos, state, random.nextInt(GROWTH_CHANCE) == 0)) {
            level.setBlock(pos, getStateForAge(age + 1), Block.UPDATE_CLIENTS);
            net.neoforged.neoforge.common.CommonHooks.fireCropGrowPost(level, pos, level.getBlockState(pos));
        }
    }

    /** Chance, dass beim Präparieren eines Fossilbrockens ein Samen seiner Epoche mit herauskommt. */
    public static final double FOSSIL_SEED_CHANCE = 0.35;

    /** Saatgut der Epoche (oder null, wenn sie keine Pflanze hat, z. B. eine Epoche aus einem Datapack). */
    public static Item seedFor(String epochId) {
        for (Supplier<Block> crop : java.util.List.of(EpochsContent.QUARTZ_THISTLE, EpochsContent.RESIN_FERN,
                EpochsContent.EMBER_TUBERS)) {
            if (crop.get() instanceof EpochCrop c && c.epoch.equals(epochId)) return c.seed.get();
        }
        return null;
    }

    /**
     * Zeitessenz auf eine Epochen-Pflanze: alle Epochen-Pflanzen im 3×3-Feld um sie wachsen eine Stufe, das kostet
     * eine Essenz. Ist keine davon jung genug, passiert nichts und nichts wird verbraucht. Gibt die Zahl der
     * gewachsenen Pflanzen zurück (für den Selbsttest öffentlich). Wie Knochenmehl ohne die NeoForge-Wachstumshooks:
     * Dünger ist ein bewusster Eingriff des Spielers, keine natürliche Wachstumsphase.
     */
    public static int applyEssence(ServerLevel level, BlockPos pos, ItemStack essence, boolean consume) {
        if (!essence.is(EpochsContent.TIME_ESSENCE.get())) return 0;
        int grown = 0;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 0, 1))) {
            BlockState state = level.getBlockState(p);
            if (!(state.getBlock() instanceof EpochCrop crop) || crop.isMaxAge(state)) continue;
            level.setBlockAndUpdate(p, crop.getStateForAge(state.getValue(crop.getAgeProperty()) + 1));
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, p.getX() + 0.5, p.getY() + 0.5,
                    p.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.05);
            grown++;
        }
        if (grown == 0) return 0;
        if (consume) essence.shrink(1);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.4f);
        return grown;
    }

    static void onUseEssence(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!event.getItemStack().is(EpochsContent.TIME_ESSENCE.get())) return;
        if (!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof EpochCrop)) return;
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return;
        applyEssence(level, event.getPos(), event.getItemStack(), !player.isCreative());
    }

    /**
     * Rechtsklick mit Saatgut an einer Stelle, wo die Pflanze nicht wachsen kann: Hinweis in der Aktionsleiste,
     * welcher Boden und welche Epoche gebraucht werden (sonst passiert einfach nichts).
     */
    static void onRightClickBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        // Nur beim Pflanzen von oben auf einen Block ohne Menü (nicht beim Öffnen einer Truhe mit Samen in der Hand).
        if (event.getLevel().isClientSide() || event.getFace() != net.minecraft.core.Direction.UP) return;
        if (event.getLevel().getBlockState(event.getPos()).getMenuProvider(event.getLevel(), event.getPos()) != null) return;
        if (!(event.getItemStack().getItem() instanceof net.minecraft.world.item.BlockItem item)) return;
        if (!(item.getBlock() instanceof EpochCrop crop)) return;
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return;
        BlockPos target = event.getLevel().getBlockState(event.getPos()).canBeReplaced()
                ? event.getPos() : event.getPos().relative(event.getFace());
        if (!event.getLevel().getBlockState(target).canBeReplaced()) return;
        if (crop.defaultBlockState().canSurvive(event.getLevel(), target)) return;
        String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(crop).getPath();
        dev.strata.core.mc.Mc.actionbar(player, net.minecraft.network.chat.Component.translatable(
                "message.strata_epochs.crop.wrong_place." + name).withStyle(net.minecraft.ChatFormatting.GOLD));
    }

    /** Für den Selbsttest: der Biom-Tag der Epoche. */
    public TagKey<Biome> biomes() {
        return biomes;
    }
}
