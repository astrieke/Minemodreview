package dev.strata.epochs;

import dev.strata.core.Epoch;
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
 *   <li>läuft alle 2 s eine von drei Risslinien ein Stück weiter: ein natürlicher Oberflächenblock (Tag
 *       {@code strata_epochs:rift_convertible}) wird zum Boden der Epoche, bis 6 Blöcke weit
 *       (abschaltbar: Config {@code rifts.griefing});</li>
 *   <li>kommt alle 30 s ein Mob der Epoche heraus (höchstens 3 feindliche in der Nähe, nicht auf Friedlich).</li>
 * </ul>
 * Risse öffnen sich nur bei Spielern an der Oberfläche. Der Spalt ist zwei Blöcke hoch, darüber steht eine
 * Partikelsäule in der Farbe der Epoche. Ein Rissblock ohne Datensatz (z. B. nach Ablauf in entladener Gegend)
 * verschwindet beim nächsten Zufallstick.
 * Schleichen + Rechtsklick mit einem Zeitanker versiegelt den Riss: Er gibt ein Fossil der Epoche und ein paar
 * Rohstoffe frei. Sonst schließt er sich nach der Lebensdauer von selbst, die Verwerfung im Boden bleibt.
 */
public final class TimeRifts {

    public static final int MIN_DISTANCE = 24, MAX_DISTANCE = 40;
    public static final int MAX_RADIUS = 6, RAYS = 3;
    /** Höchstens so tief unter der Oberfläche darf ein Spieler sein, damit bei ihm ein Riss aufgeht. */
    public static final int SURFACE_DEPTH = 8;
    public static final int CONVERT_INTERVAL = 40, MOB_INTERVAL = 600, MOB_CAP = 3;
    /** Zwei Risse liegen mindestens so weit auseinander. */
    public static final int SPACING = 96;

    private static final TagKey<Block> CONVERTIBLE = MobCompat.blockTag(StrataEpochs.id("rift_convertible"));

    /**
     * Rohstoff, Farbe, Boden und Mob je Epoche von Strata Epochs; Epochen aus Datapacks bekommen den Boden ihrer
     * Landschaft und ein Fossil. Die Sporenzeit reißt Moos statt Myzel auf, weil Myzel sich bei Tag selbst ausbreitet.
     */
    private record Kind(Supplier<Item> resource, int color, String surface, Function<ServerLevel, Mob> mob) {}

    private static final Map<String, Kind> KINDS = Map.of(
            StrataEpochs.id("crystal_age"), new Kind(EpochsContent.CRYSTAL_QUARTZ, 0x9b87ff, "minecraft:calcite",
                    l -> new dev.strata.epochs.mob.GeodeCrawler(EpochsMobs.GEODE_CRAWLER.get(), l)),
            StrataEpochs.id("spore_age"), new Kind(EpochsContent.AMBER_SHARD, 0x7fb069, "minecraft:moss_block",
                    l -> new dev.strata.epochs.mob.SporeBellows(EpochsMobs.SPORE_BELLOWS.get(), l)),
            StrataEpochs.id("ash_age"), new Kind(EpochsContent.EMBER_SHARD, 0xff7a1a, "strata_epochs:volcanic_ash",
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
        if (level.getBlockState(pos.above()).canBeReplaced()) {
            level.setBlockAndUpdate(pos.above(), EpochsContent.TIME_RIFT.get().defaultBlockState());   // zwei Blöcke hoch
        }
        List<RiftRecord> records = new ArrayList<>(level.getData(EpochsContent.RIFTS.get()));
        records.add(new RiftRecord(pos.asLong(), epoch, level.getGameTime()));
        level.setData(EpochsContent.RIFTS.get(), records);
        level.playSound(null, pos, SoundEvents.PORTAL_TRIGGER, SoundSource.AMBIENT, 0.6f, 1.6f);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.AMBIENT, 1.5f, 0.5f);
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(pos) > 64 * 64 || Math.abs(player.getBlockY() - pos.getY()) > 24) continue;
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
        if (player.isCreative() || player.isSpectator() || !atSurface(level, player.blockPosition())) return false;
        if (activeCount(level, level.getGameTime(), EpochsConfig.RIFT_LIFETIME.get() * 20L) >= EpochsConfig.RIFT_MAX_ACTIVE.get()) {
            return false;
        }
        if (level.getRandom().nextDouble() >= EpochsConfig.RIFT_CHANCE.get()) return false;
        List<String> epochs = epochsFor(player);
        if (epochs.isEmpty()) return false;
        BlockPos spot = findSpot(level, player.blockPosition(), level.getRandom());
        if (spot == null) return false;
        open(level, spot, epochs.get(level.getRandom().nextInt(epochs.size())));
        return true;
    }

    /** Steht {@code pos} an der Oberfläche (höchstens {@link #SURFACE_DEPTH} Blöcke unter der Heightmap)? */
    public static boolean atSurface(ServerLevel level, BlockPos pos) {
        return pos.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) - SURFACE_DEPTH;
    }

    /** Offene Risse, die noch nicht abgelaufen sind (abgelaufene in entladenen Gegenden zählen nicht). */
    public static long activeCount(ServerLevel level, long now, long lifetimeTicks) {
        return level.getData(EpochsContent.RIFTS.get()).stream().filter(r -> now - r.openedAt() < lifetimeTicks).count();
    }

    /** Gibt es zu diesem Rissblock (oder dem darunter) einen Datensatz? Sonst entfernt der Block sich selbst. */
    static boolean hasRecord(ServerLevel level, BlockPos pos) {
        long here = pos.asLong(), below = pos.below().asLong();
        return level.getData(EpochsContent.RIFTS.get()).stream().anyMatch(r -> r.pos() == here || r.pos() == below);
    }

    /** Ein Schritt für alle Risse der Dimension (jede Sekunde). Öffentlich für den Selbsttest. */
    public static void tickAll(ServerLevel level, long now, boolean griefing, long lifetimeTicks) {
        List<RiftRecord> records = level.getData(EpochsContent.RIFTS.get());
        List<RiftRecord> keep = new ArrayList<>();
        for (RiftRecord r : records) {
            BlockPos pos = BlockPos.of(r.pos());
            if (!level.hasChunkAt(pos)) {
                // Entladen: die Zeit läuft trotzdem. Abgelaufen fällt der Datensatz weg, der Block verschwindet
                // dann beim nächsten Zufallstick (TimeRiftBlock), sobald jemand vorbeikommt.
                if (now - r.openedAt() < lifetimeTicks) keep.add(r);
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
            // Partikelsäule über dem Spalt, damit man den Riss von weitem findet.
            var dust = MobCompat.dust(kind != null ? kind.color : 0xb48cff, 2.0f);
            for (int h = 0; h < 10; h++) {
                level.sendParticles(dust, pos.getX() + 0.5, pos.getY() + 2.2 + h, pos.getZ() + 0.5, 2, 0.08, 0.3, 0.08, 0.0);
            }
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                    6, 0.25, 0.8, 0.25, 0.02);
            if (age % CONVERT_INTERVAL < 20 && griefing) convertOne(level, r, now, lifetimeTicks, level.getRandom());
            if (age % MOB_INTERVAL < 20 && age > 0 && kind != null) releaseMob(level, pos, kind);
            if (age % 100 < 20) level.playSound(null, pos, SoundEvents.PORTAL_AMBIENT, SoundSource.AMBIENT, 0.25f, 1.5f);
        }
        if (keep.size() != records.size()) level.setData(EpochsContent.RIFTS.get(), keep);
    }

    /**
     * Treibt eine der drei Risslinien um einen Block weiter: der nächste natürliche Oberflächenblock auf der Linie
     * wird zum Boden der Epoche. Die Linien wachsen über die halbe Lebensdauer bis {@link #MAX_RADIUS}.
     * Gibt die Position zurück oder null. Öffentlich für den Selbsttest.
     */
    public static BlockPos convertOne(ServerLevel level, RiftRecord rift, long now, long lifetimeTicks, RandomSource random) {
        BlockState top = surfaceFor(level, rift.epoch());
        if (top == null) return null;
        BlockPos center = BlockPos.of(rift.pos());
        long age = Math.max(0, now - rift.openedAt());
        int radius = 1 + (int) Math.min(MAX_RADIUS - 1, age * MAX_RADIUS / Math.max(1, lifetimeTicks / 2));
        // Drei Linien mit fester, pro Riss zufälliger Richtung, die sich leicht schlängeln.
        double base = Math.floorMod(rift.pos() * 31 + 7, 360) * Math.PI / 180;
        for (int attempt = 0; attempt < RAYS * 2; attempt++) {
            int ray = random.nextInt(RAYS);
            double angle = base + ray * 2 * Math.PI / RAYS;
            for (int d = 1; d <= radius; d++) {
                double wobble = Math.sin(d * 1.3 + ray) * 0.6;
                int x = center.getX() + (int) Math.round(Math.cos(angle) * d - Math.sin(angle) * wobble);
                int z = center.getZ() + (int) Math.round(Math.sin(angle) * d + Math.cos(angle) * wobble);
                BlockPos ground = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
                if (Math.abs(ground.getY() - center.getY()) > 4) break;
                BlockState old = level.getBlockState(ground);
                if (old.is(top.getBlock())) continue;            // schon umgewandelt: weiter die Linie entlang
                if (!old.is(CONVERTIBLE)) break;                 // Bau oder Fremdes: Linie endet hier
                BlockState above = level.getBlockState(ground.above());
                if (!above.isAir()) {
                    // Gras, Blumen usw. auf dem Block gehen mit, Bauten darüber nicht.
                    if (!above.canBeReplaced() || above.is(EpochsContent.TIME_RIFT.get())) break;
                    level.setBlockAndUpdate(ground.above(), Blocks.AIR.defaultBlockState());
                }
                level.setBlockAndUpdate(ground, top);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, x + 0.5, ground.getY() + 1.1, z + 0.5, 5, 0.3, 0.1, 0.3, 0.02);
                return ground;
            }
        }
        return null;
    }

    /** Boden, den ein Riss in diese Epoche aufreißt (null, wenn die Epoche keine Landschaft hat). */
    static BlockState surfaceFor(ServerLevel level, String epochId) {
        Kind kind = KINDS.get(epochId);
        if (kind != null) return MobCompat.block(kind.surface).defaultBlockState();
        Epoch epoch = StrataAPI.epochs(level.getServer()).get(epochId);
        if (epoch == null || epoch.landscape().isEmpty()) return null;
        return MobCompat.block(epoch.landscape().get().top()).defaultBlockState();
    }

    /** Darf ein Riss hier einen Mob freilassen? Nicht auf Friedlich und nicht, wenn schon genug feindliche da sind. */
    public static boolean mayReleaseMob(ServerLevel level, BlockPos pos) {
        if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) return false;
        AABB near = new AABB(pos).inflate(16);
        return level.getEntitiesOfClass(dev.strata.epochs.mob.StrataMob.class, near,
                m -> !(m instanceof dev.strata.epochs.mob.Spiralit)).size() < MOB_CAP;
    }

    private static void releaseMob(ServerLevel level, BlockPos pos, Kind kind) {
        if (!mayReleaseMob(level, pos)) return;
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
        for (BlockPos p : List.of(pos.above(), pos)) {
            if (level.getBlockState(p).is(EpochsContent.TIME_RIFT.get())) level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
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
        BlockPos pos = event.getPos();
        if (level.getBlockState(pos.below()).is(EpochsContent.TIME_RIFT.get())) pos = pos.below();   // oberer Teil
        seal(player, level, pos);
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
