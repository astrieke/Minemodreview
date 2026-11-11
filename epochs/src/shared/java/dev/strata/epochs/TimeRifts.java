package dev.strata.epochs;

import dev.strata.core.Epoch;
import dev.strata.core.Landscape;
import dev.strata.core.api.StrataAPI;
import dev.strata.core.mc.Mc;
import dev.strata.epochs.mc.MobCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Zeitrisse (Verwerfungen): Wer schon in einer Epoche war, bei dem reißt in der Oberwelt ab und zu ein Riss in eine
 * dieser Epochen auf, 24–40 Blöcke entfernt. Solange er offen ist,
 * <ul>
 *   <li>wird alle 2 s ein natürlicher Oberflächenblock (Tag {@code strata_epochs:transplantable}) in der Nähe zum
 *       Boden der Epoche, der Kreis wächst bis Radius 5 (abschaltbar: Config {@code rifts.griefing});</li>
 *   <li>kommt alle 30 s ein Mob der Epoche heraus (höchstens 3 in der Nähe).</li>
 * </ul>
 * Schleichen + Rechtsklick mit einem Zeitanker versiegelt den Riss: Er gibt ein Fossil der Epoche und ein paar
 * Rohstoffe frei. Sonst schließt er sich nach der Lebensdauer von selbst, die Verwerfung im Boden bleibt.
 */
public final class TimeRifts {

    public static final int MIN_DISTANCE = 24, MAX_DISTANCE = 40;
    public static final int MAX_RADIUS = 5;
    public static final int CONVERT_INTERVAL = 40, MOB_INTERVAL = 600, MOB_CAP = 3;
    /** Zwei Risse liegen mindestens so weit auseinander. */
    public static final int SPACING = 96;

    private static final TagKey<Block> TRANSPLANTABLE = MobCompat.blockTag(StrataEpochs.id("transplantable"));

    /** Rohstoff, Farbe und Mob je Epoche von Strata Epochs; Epochen aus Datapacks bekommen nur Boden und Fossil. */
    private record Kind(Supplier<Item> resource, int color, Function<ServerLevel, Mob> mob) {}

    private static final Map<String, Kind> KINDS = Map.of(
            StrataEpochs.id("crystal_age"), new Kind(EpochsContent.CRYSTAL_QUARTZ, 0x9b87ff,
                    l -> new dev.strata.epochs.mob.GeodeCrawler(EpochsMobs.GEODE_CRAWLER.get(), l)),
            StrataEpochs.id("spore_age"), new Kind(EpochsContent.AMBER_SHARD, 0x7fb069,
                    l -> new dev.strata.epochs.mob.SporeBellows(EpochsMobs.SPORE_BELLOWS.get(), l)),
            StrataEpochs.id("ash_age"), new Kind(EpochsContent.EMBER_SHARD, 0xff7a1a,
                    l -> new dev.strata.epochs.mob.EmberBurrower(EpochsMobs.EMBER_BURROWER.get(), l)));

    private TimeRifts() {}

    // ------------------------------------------------------------------ Entstehen

    /** Darf in dieser Dimension ein Riss entstehen (Config {@code rifts.enabled} und {@code rifts.dimensions})? */
    public static boolean allowedIn(ServerLevel level) {
        return EpochsConfig.RIFTS.get() && EpochsConfig.RIFT_DIMENSIONS.get().contains(Mc.levelId(level));
    }

    /** Epochen, in die bei diesem Spieler ein Riss reißen kann: besucht und mit Landschaft (für den Boden). */
    public static List<String> epochsFor(ServerPlayer player) {
        List<String> result = new ArrayList<>();
        for (var e : StrataAPI.epochs(player.level().getServer()).entrySet()) {
            if (e.getValue().landscape().isEmpty() || e.getValue().dimension().isEmpty()) continue;
            String name = e.getKey().substring(e.getKey().indexOf(':') + 1);
            if (StrataAPI.hasNode(player, StrataEpochs.id("visited_" + name))) result.add(e.getKey());
        }
        return result;
    }

    /** Eine Stelle 24–40 Blöcke vom Spieler: geladen, fester Boden ohne Flüssigkeit, frei, kein Riss in der Nähe. */
    public static BlockPos findSpot(ServerLevel level, BlockPos around, RandomSource random) {
        List<RiftRecord> open = level.getData(EpochsContent.RIFTS.get());
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int dist = MIN_DISTANCE + random.nextInt(MAX_DISTANCE - MIN_DISTANCE + 1);
            int x = around.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = around.getZ() + (int) Math.round(Math.sin(angle) * dist);
            if (!level.hasChunkAt(new BlockPos(x, around.getY(), z))) continue;
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            BlockState ground = level.getBlockState(pos.below());
            if (!level.getBlockState(pos).isAir() || !level.getFluidState(pos.below()).isEmpty()
                    || ground.getCollisionShape(level, pos.below()).isEmpty()) continue;
            boolean crowded = open.stream().anyMatch(r -> BlockPos.of(r.pos()).distSqr(pos) < (double) SPACING * SPACING);
            if (!crowded) return pos;
        }
        return null;
    }

    /** Öffnet einen Riss an {@code pos}. Spieler in 64 Blöcken hören ihn und lesen die Richtung im Chat. */
    public static void open(ServerLevel level, BlockPos pos, String epoch) {
        level.setBlockAndUpdate(pos, EpochsContent.TIME_RIFT.get().defaultBlockState());
        List<RiftRecord> records = new ArrayList<>(level.getData(EpochsContent.RIFTS.get()));
        records.add(new RiftRecord(pos.asLong(), epoch, level.getGameTime()));
        level.setData(EpochsContent.RIFTS.get(), records);
        level.playSound(null, pos, SoundEvents.PORTAL_TRIGGER, SoundSource.AMBIENT, 0.6f, 1.6f);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.AMBIENT, 1.5f, 0.5f);
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(pos) > 64 * 64) continue;
            String wind = BiomeBearing.wind(pos.getX() - player.getBlockX(), pos.getZ() - player.getBlockZ());
            player.sendSystemMessage(Component.translatable("message.strata_epochs.rift.opened",
                    CoreSamples.epochName(epoch), Component.translatable("message.strata_epochs.bearing.wind." + wind),
                    (int) Math.round(Math.sqrt(player.blockPosition().distSqr(pos)))).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    // ------------------------------------------------------------------ Laufen lassen

    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        if (now % 20 == 0 && !level.getData(EpochsContent.RIFTS.get()).isEmpty()) {
            tickAll(level, now, EpochsConfig.RIFT_GRIEFING.get(), EpochsConfig.RIFT_LIFETIME.get() * 20L);
        }
        long interval = EpochsConfig.RIFT_INTERVAL.get() * 1200L;
        if (now % interval != 0 || !allowedIn(level)) return;
        for (ServerPlayer player : level.players()) tryOpenNear(level, player);
    }

    /** Würfelt für einen Spieler: mit {@code rifts.chance} öffnet sich in seiner Nähe ein Riss. */
    static boolean tryOpenNear(ServerLevel level, ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) return false;
        if (level.getData(EpochsContent.RIFTS.get()).size() >= EpochsConfig.RIFT_MAX_ACTIVE.get()) return false;
        if (level.getRandom().nextDouble() >= EpochsConfig.RIFT_CHANCE.get()) return false;
        List<String> epochs = epochsFor(player);
        if (epochs.isEmpty()) return false;
        BlockPos spot = findSpot(level, player.blockPosition(), level.getRandom());
        if (spot == null) return false;
        open(level, spot, epochs.get(level.getRandom().nextInt(epochs.size())));
        return true;
    }

    /** Ein Schritt für alle Risse der Dimension (jede Sekunde). Öffentlich für den Selbsttest. */
    public static void tickAll(ServerLevel level, long now, boolean griefing, long lifetimeTicks) {
        List<RiftRecord> records = level.getData(EpochsContent.RIFTS.get());
        List<RiftRecord> keep = new ArrayList<>();
        for (RiftRecord r : records) {
            BlockPos pos = BlockPos.of(r.pos());
            if (!level.hasChunkAt(pos)) {               // entladen: pausiert, die Zeit läuft trotzdem
                keep.add(r);
                continue;
            }
            if (!level.getBlockState(pos).is(EpochsContent.TIME_RIFT.get())) continue;   // per Befehl entfernt
            long age = now - r.openedAt();
            if (age >= lifetimeTicks) {
                close(level, pos, false);
                continue;
            }
            keep.add(r);
            Kind kind = KINDS.get(r.epoch());
            level.sendParticles(MobCompat.dust(kind != null ? kind.color : 0xb48cff, 1.2f),
                    pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 6, 0.25, 0.5, 0.25, 0.0);
            if (age % CONVERT_INTERVAL < 20 && griefing) convertOne(level, r, now, lifetimeTicks, level.getRandom());
            if (age % MOB_INTERVAL < 20 && age > 0 && kind != null) releaseMob(level, pos, kind);
            if (age % 100 < 20) level.playSound(null, pos, SoundEvents.PORTAL_AMBIENT, SoundSource.AMBIENT, 0.25f, 1.5f);
        }
        if (keep.size() != records.size()) level.setData(EpochsContent.RIFTS.get(), keep);
    }

    /**
     * Verwandelt einen natürlichen Oberflächenblock im wachsenden Kreis um den Riss in den Boden der Epoche.
     * Gibt die Position zurück oder null. Öffentlich für den Selbsttest.
     */
    public static BlockPos convertOne(ServerLevel level, RiftRecord rift, long now, long lifetimeTicks, RandomSource random) {
        Epoch epoch = StrataAPI.epochs(level.getServer()).get(rift.epoch());
        if (epoch == null || epoch.landscape().isEmpty()) return null;
        Landscape land = epoch.landscape().get();
        BlockState top = MobCompat.block(land.top()).defaultBlockState();
        BlockPos center = BlockPos.of(rift.pos());
        long age = Math.max(0, now - rift.openedAt());
        int radius = 1 + (int) Math.min(MAX_RADIUS - 1, age * MAX_RADIUS / Math.max(1, lifetimeTicks / 2));
        for (int attempt = 0; attempt < 6; attempt++) {
            int dx = random.nextInt(radius * 2 + 1) - radius, dz = random.nextInt(radius * 2 + 1) - radius;
            if (dx * dx + dz * dz > radius * radius) continue;
            int x = center.getX() + dx, z = center.getZ() + dz;
            BlockPos ground = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
            if (Math.abs(ground.getY() - center.getY()) > 4) continue;
            BlockState old = level.getBlockState(ground);
            if (!old.is(TRANSPLANTABLE) || old.is(top.getBlock())) continue;
            if (!level.getBlockState(ground.above()).isAir()) {
                // Gras, Blumen usw. auf dem Block gehen mit, Bauten darüber nicht.
                if (!level.getBlockState(ground.above()).canBeReplaced()) continue;
                level.setBlockAndUpdate(ground.above(), Blocks.AIR.defaultBlockState());
            }
            level.setBlockAndUpdate(ground, top);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, x + 0.5, ground.getY() + 1.1, z + 0.5, 5, 0.3, 0.1, 0.3, 0.02);
            return ground;
        }
        return null;
    }

    private static void releaseMob(ServerLevel level, BlockPos pos, Kind kind) {
        AABB near = new AABB(pos).inflate(16);
        if (level.getEntitiesOfClass(dev.strata.epochs.mob.StrataMob.class, near).size() >= MOB_CAP) return;
        Mob mob = kind.mob.apply(level);
        mob.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        level.addFreshEntity(mob);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 30, 0.3, 0.6, 0.3, 0.1);
    }

    // ------------------------------------------------------------------ Schließen

    /** Schließt den Riss an {@code pos}. Versiegelt (vom Spieler) gibt er Fossil und Rohstoffe frei. */
    public static void close(ServerLevel level, BlockPos pos, boolean sealed) {
        List<RiftRecord> records = level.getData(EpochsContent.RIFTS.get());
        RiftRecord rift = records.stream().filter(r -> r.pos() == pos.asLong()).findFirst().orElse(null);
        if (rift != null) {
            List<RiftRecord> keep = new ArrayList<>(records);
            keep.remove(rift);
            level.setData(EpochsContent.RIFTS.get(), keep);
        }
        if (level.getBlockState(pos).is(EpochsContent.TIME_RIFT.get())) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        level.playSound(null, pos, sealed ? SoundEvents.RESPAWN_ANCHOR_DEPLETE.value() : SoundEvents.AMETHYST_BLOCK_BREAK,
                SoundSource.BLOCKS, 1.0f, sealed ? 1.2f : 0.7f);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 40, 0.3, 0.6, 0.3, 0.2);
        if (!sealed || rift == null) return;
        List<ItemStack> loot = new ArrayList<>();
        loot.add(FossilPrep.chunk(rift.epoch()));
        Kind kind = KINDS.get(rift.epoch());
        if (kind != null) loot.add(new ItemStack(kind.resource.get(), 2 + level.getRandom().nextInt(3)));
        for (ItemStack stack : loot) {
            ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
        }
    }

    /** Schleichen + Rechtsklick mit einem Zeitanker versiegelt, sonst gibt es einen Hinweis. */
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level world = event.getLevel();
        if (!world.getBlockState(event.getPos()).is(EpochsContent.TIME_RIFT.get())) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(world instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)) return;
        seal(player, level, event.getPos());
    }

    /** Versuch zu versiegeln. Öffentlich für den Selbsttest; gibt zurück, ob es geklappt hat. */
    public static boolean seal(ServerPlayer player, ServerLevel level, BlockPos pos) {
        ItemStack held = player.getMainHandItem();
        if (!player.isShiftKeyDown() || !held.is(EpochsContent.TIME_ANCHOR.get()) || Mc.isOnCooldown(player, held)) {
            Mc.actionbar(player, Component.translatable("message.strata_epochs.rift.how").withStyle(ChatFormatting.GRAY));
            return false;
        }
        close(level, pos, true);
        Mc.addCooldown(player, held, 100);
        Mc.actionbar(player, Component.translatable("message.strata_epochs.rift.sealed").withStyle(ChatFormatting.GREEN));
        StrataAPI.unlock(player, StrataEpochs.id("sealed_rift"));
        return true;
    }

    /** Für den Befehl: alle Risse der Dimension schließen. */
    static int closeAll(ServerLevel level) {
        List<RiftRecord> records = new ArrayList<>(level.getData(EpochsContent.RIFTS.get()));
        for (RiftRecord r : records) close(level, BlockPos.of(r.pos()), false);
        return records.size();
    }
}
