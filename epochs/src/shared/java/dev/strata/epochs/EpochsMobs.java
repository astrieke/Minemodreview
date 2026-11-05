package dev.strata.epochs;

import dev.strata.epochs.mc.MobReg;
import dev.strata.epochs.mob.GeodeCrawler;
import dev.strata.epochs.mob.SporeBellows;
import dev.strata.epochs.mob.SporeClouds;
import dev.strata.epochs.mob.StrataMob;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/** Die Mobs von Strata Epochs: Typen, Spawn-Eier, Sounds, Attribute und Spawnregeln. */
public final class EpochsMobs {

    private static final MobReg REG = new MobReg(StrataEpochs.MOD_ID);

    public static final Supplier<EntityType<GeodeCrawler>> GEODE_CRAWLER =
            REG.entity("geode_crawler", GeodeCrawler::new, MobCategory.CREATURE, 0.9f, 0.7f, 8);
    public static final Supplier<Item> GEODE_CRAWLER_SPAWN_EGG =
            REG.spawnEgg("geode_crawler_spawn_egg", GEODE_CRAWLER, 0x5e5268, 0xb48cff);

    public static final Supplier<EntityType<SporeBellows>> SPORE_BELLOWS =
            REG.entity("spore_bellows", SporeBellows::new, MobCategory.MONSTER, 0.75f, 1.0f, 8);
    public static final Supplier<Item> SPORE_BELLOWS_SPAWN_EGG =
            REG.spawnEgg("spore_bellows_spawn_egg", SPORE_BELLOWS, 0xcfc39a, 0xc0502a);

    public static final Supplier<SoundEvent> GEODE_CRAWLER_AMBIENT = REG.sound("entity.geode_crawler.ambient");
    public static final Supplier<SoundEvent> GEODE_CRAWLER_HURT = REG.sound("entity.geode_crawler.hurt");
    public static final Supplier<SoundEvent> GEODE_CRAWLER_DEATH = REG.sound("entity.geode_crawler.death");
    public static final Supplier<SoundEvent> SPORE_BELLOWS_AMBIENT = REG.sound("entity.spore_bellows.ambient");
    public static final Supplier<SoundEvent> SPORE_BELLOWS_HURT = REG.sound("entity.spore_bellows.hurt");
    public static final Supplier<SoundEvent> SPORE_BELLOWS_DEATH = REG.sound("entity.spore_bellows.death");

    private EpochsMobs() {}

    static void register(IEventBus modBus) {
        REG.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent event) -> {
            event.put(GEODE_CRAWLER.get(), GeodeCrawler.createAttributes().build());
            event.put(SPORE_BELLOWS.get(), SporeBellows.createAttributes().build());
        });
        modBus.addListener((RegisterSpawnPlacementsEvent event) -> {
            event.register(GEODE_CRAWLER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (type, level, reason, pos, random) -> EpochsConfig.GEODE_CRAWLER.get()
                            && Mob.checkMobSpawnRules(type, level, reason, pos, random)
                            && belowEpochLimit(level) && GeodeCrawler.canSpawnHere(level, pos),
                    RegisterSpawnPlacementsEvent.Operation.REPLACE);
            event.register(SPORE_BELLOWS.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (type, level, reason, pos, random) -> EpochsConfig.SPORE_BELLOWS.get()
                            && Mob.checkMobSpawnRules(type, level, reason, pos, random)
                            && belowEpochLimit(level) && SporeBellows.canSpawnHere(level, pos),
                    RegisterSpawnPlacementsEvent.Operation.REPLACE);
        });
        NeoForge.EVENT_BUS.addListener(StrataMob::handleIncomingDamage);
        NeoForge.EVENT_BUS.addListener(SporeBellows::onDeath);
        NeoForge.EVENT_BUS.addListener(SporeClouds::onLevelTick);
    }

    /** Höchstzahl aller Strata-Mobs pro Dimension (Config {@code mobs.max_per_epoch}). */
    private static boolean belowEpochLimit(ServerLevelAccessor accessor) {
        ServerLevel level = accessor.getLevel();
        int max = EpochsConfig.MAX_MOBS_PER_EPOCH.get();
        return level.getEntities(EntityTypeTest.forClass(StrataMob.class), e -> true).size() < max;
    }
}
