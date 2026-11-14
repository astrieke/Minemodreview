package dev.strata.epochs;

import dev.strata.epochs.mc.MobReg;
import dev.strata.epochs.mob.EmberBurrower;
import dev.strata.epochs.mob.GeodeCrawler;
import dev.strata.epochs.mob.PetrifiedWitness;
import dev.strata.epochs.mob.WitnessAwakening;
import dev.strata.epochs.mob.SporeBellows;
import dev.strata.epochs.mob.SporeClouds;
import dev.strata.epochs.mob.Spiralit;
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

    public static final Supplier<EntityType<PetrifiedWitness>> PETRIFIED_WITNESS =
            REG.entity("petrified_witness", PetrifiedWitness::new, MobCategory.MONSTER, 1.3f, 2.4f, 10);
    public static final Supplier<Item> PETRIFIED_WITNESS_SPAWN_EGG =
            REG.spawnEgg("petrified_witness_spawn_egg", PETRIFIED_WITNESS, 0x3c3742, 0xffd86b);

    public static final Supplier<EntityType<EmberBurrower>> EMBER_BURROWER =
            REG.entity("ember_burrower", EmberBurrower::new, MobCategory.MONSTER, 0.9f, 0.8f, 8);
    public static final Supplier<Item> EMBER_BURROWER_SPAWN_EGG =
            REG.spawnEgg("ember_burrower_spawn_egg", EMBER_BURROWER, 0x4f4844, 0xe0531c);

    /** Mini-Boss, nur per Schichtkern beschworen (spawnt nicht natürlich). Breite inkl. Arme, Höhe je Phase im Code. */
    public static final Supplier<EntityType<dev.strata.epochs.mob.Stratolith>> STRATOLITH =
            REG.entity("stratolith", dev.strata.epochs.mob.Stratolith::new, MobCategory.MONSTER, 2.3f, 3.1f, 10);
    public static final Supplier<Item> STRATOLITH_SPAWN_EGG =
            REG.spawnEgg("stratolith_spawn_egg", STRATOLITH, 0x3c3742, 0x9b87ff);

    /** Ruheplatz der Kristallzeit; Trefferfläche Spitze, Kern und Ring (die Zinken ragen darüber hinaus). */
    public static final Supplier<EntityType<dev.strata.epochs.mob.ResonantShard>> RESONANT_SHARD =
            REG.entity("resonant_shard", dev.strata.epochs.mob.ResonantShard::new, MobCategory.CREATURE, 0.75f, 0.8f, 8);
    public static final Supplier<Item> RESONANT_SHARD_SPAWN_EGG =
            REG.spawnEgg("resonant_shard_spawn_egg", RESONANT_SHARD, 0xdcd6c8, 0x6f6cf0);

    /** Begleiter aus dem Ammonit-Fossil, spawnt nicht natürlich. */
    public static final Supplier<EntityType<Spiralit>> SPIRALIT =
            REG.entity("spiralit", Spiralit::new, MobCategory.CREATURE, 0.6f, 0.9f, 8);

    public static final Supplier<SoundEvent> GEODE_CRAWLER_AMBIENT = REG.sound("entity.geode_crawler.ambient");
    public static final Supplier<SoundEvent> GEODE_CRAWLER_HURT = REG.sound("entity.geode_crawler.hurt");
    public static final Supplier<SoundEvent> GEODE_CRAWLER_DEATH = REG.sound("entity.geode_crawler.death");
    public static final Supplier<SoundEvent> SPORE_BELLOWS_AMBIENT = REG.sound("entity.spore_bellows.ambient");
    public static final Supplier<SoundEvent> SPORE_BELLOWS_HURT = REG.sound("entity.spore_bellows.hurt");
    public static final Supplier<SoundEvent> SPORE_BELLOWS_DEATH = REG.sound("entity.spore_bellows.death");
    public static final Supplier<SoundEvent> PETRIFIED_WITNESS_AMBIENT = REG.sound("entity.petrified_witness.ambient");
    public static final Supplier<SoundEvent> PETRIFIED_WITNESS_HURT = REG.sound("entity.petrified_witness.hurt");
    public static final Supplier<SoundEvent> PETRIFIED_WITNESS_DEATH = REG.sound("entity.petrified_witness.death");
    public static final Supplier<SoundEvent> EMBER_BURROWER_AMBIENT = REG.sound("entity.ember_burrower.ambient");
    public static final Supplier<SoundEvent> EMBER_BURROWER_HURT = REG.sound("entity.ember_burrower.hurt");
    public static final Supplier<SoundEvent> EMBER_BURROWER_DEATH = REG.sound("entity.ember_burrower.death");
    public static final Supplier<SoundEvent> EMBER_BURROWER_DIG = REG.sound("entity.ember_burrower.dig");
    public static final Supplier<SoundEvent> EMBER_BURROWER_TREMOR = REG.sound("entity.ember_burrower.tremor");
    public static final Supplier<SoundEvent> EMBER_BURROWER_EMERGE = REG.sound("entity.ember_burrower.emerge");
    public static final Supplier<SoundEvent> SPIRALIT_AMBIENT = REG.sound("entity.spiralit.ambient");
    public static final Supplier<SoundEvent> SPIRALIT_HURT = REG.sound("entity.spiralit.hurt");
    public static final Supplier<SoundEvent> SPIRALIT_DEATH = REG.sound("entity.spiralit.death");
    public static final Supplier<SoundEvent> RESONANT_SHARD_AMBIENT = REG.sound("entity.resonant_shard.ambient");
    public static final Supplier<SoundEvent> RESONANT_SHARD_HURT = REG.sound("entity.resonant_shard.hurt");
    public static final Supplier<SoundEvent> RESONANT_SHARD_DEATH = REG.sound("entity.resonant_shard.death");
    public static final Supplier<SoundEvent> RESONANT_SHARD_RESONATE = REG.sound("entity.resonant_shard.resonate");
    public static final Supplier<SoundEvent> RESONANT_SHARD_DISSONANCE = REG.sound("entity.resonant_shard.dissonance");
    public static final Supplier<SoundEvent> RESONANT_SHARD_SHRIEK = REG.sound("entity.resonant_shard.shriek");
    public static final Supplier<SoundEvent> STRATOLITH_AMBIENT = REG.sound("entity.stratolith.ambient");
    public static final Supplier<SoundEvent> STRATOLITH_HURT = REG.sound("entity.stratolith.hurt");
    public static final Supplier<SoundEvent> STRATOLITH_DEATH = REG.sound("entity.stratolith.death");
    public static final Supplier<SoundEvent> STRATOLITH_RISE = REG.sound("entity.stratolith.rise");
    public static final Supplier<SoundEvent> STRATOLITH_REWIND = REG.sound("entity.stratolith.rewind");
    public static final Supplier<SoundEvent> STRATOLITH_SLAM = REG.sound("entity.stratolith.slam");

    private EpochsMobs() {}

    static void register(IEventBus modBus) {
        REG.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent event) -> {
            event.put(GEODE_CRAWLER.get(), GeodeCrawler.createAttributes().build());
            event.put(SPORE_BELLOWS.get(), SporeBellows.createAttributes().build());
            event.put(PETRIFIED_WITNESS.get(), PetrifiedWitness.createAttributes().build());
            event.put(SPIRALIT.get(), Spiralit.createAttributes().build());
            event.put(EMBER_BURROWER.get(), EmberBurrower.createAttributes().build());
            event.put(STRATOLITH.get(), dev.strata.epochs.mob.Stratolith.createAttributes().build());
            event.put(RESONANT_SHARD.get(), dev.strata.epochs.mob.ResonantShard.createAttributes().build());
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
            event.register(EMBER_BURROWER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (type, level, reason, pos, random) -> EpochsConfig.EMBER_BURROWER.get()
                            && Mob.checkMobSpawnRules(type, level, reason, pos, random)
                            && belowEpochLimit(level) && EmberBurrower.canSpawnHere(level, pos),
                    RegisterSpawnPlacementsEvent.Operation.REPLACE);
            event.register(RESONANT_SHARD.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (type, level, reason, pos, random) -> EpochsConfig.RESONANT_SHARD.get()
                            && belowEpochLimit(level) && dev.strata.epochs.mob.ResonantShard.canSpawnHere(level, pos),
                    RegisterSpawnPlacementsEvent.Operation.REPLACE);
        });
        NeoForge.EVENT_BUS.addListener(StrataMob::handleIncomingDamage);
        NeoForge.EVENT_BUS.addListener(SporeBellows::onDeath);
        NeoForge.EVENT_BUS.addListener(SporeClouds::onLevelTick);
        NeoForge.EVENT_BUS.addListener(WitnessAwakening::onLevelTick);
        NeoForge.EVENT_BUS.addListener(Spiralit::onInteract);
        NeoForge.EVENT_BUS.addListener(Spiralit::onChangedDimension);
        NeoForge.EVENT_BUS.addListener(Spiralit::onServerStopped);
        NeoForge.EVENT_BUS.addListener(dev.strata.epochs.mob.ResonantShard::onInteract);
        NeoForge.EVENT_BUS.addListener(dev.strata.epochs.mob.ResonantShard::onStartBreaking);
        NeoForge.EVENT_BUS.addListener(dev.strata.epochs.mob.ResonantShard::onDamage);
        dev.strata.epochs.mc.BreakHook.onBroken(dev.strata.epochs.mob.ResonantShard::onBlockBroken);
    }

    /** Höchstzahl aller Strata-Mobs pro Dimension (Config {@code mobs.max_per_epoch}). */
    private static boolean belowEpochLimit(ServerLevelAccessor accessor) {
        ServerLevel level = accessor.getLevel();
        int max = EpochsConfig.MAX_MOBS_PER_EPOCH.get();
        return level.getEntities(EntityTypeTest.forClass(StrataMob.class), e -> true).size() < max;
    }
}
