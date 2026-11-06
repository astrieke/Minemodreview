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
    public static final Supplier<SoundEvent> SPIRALIT_AMBIENT = REG.sound("entity.spiralit.ambient");
    public static final Supplier<SoundEvent> SPIRALIT_HURT = REG.sound("entity.spiralit.hurt");
    public static final Supplier<SoundEvent> SPIRALIT_DEATH = REG.sound("entity.spiralit.death");

    private EpochsMobs() {}

    static void register(IEventBus modBus) {
        REG.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent event) -> {
            event.put(GEODE_CRAWLER.get(), GeodeCrawler.createAttributes().build());
            event.put(SPORE_BELLOWS.get(), SporeBellows.createAttributes().build());
            event.put(PETRIFIED_WITNESS.get(), PetrifiedWitness.createAttributes().build());
            event.put(SPIRALIT.get(), Spiralit.createAttributes().build());
            event.put(EMBER_BURROWER.get(), EmberBurrower.createAttributes().build());
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
        });
        NeoForge.EVENT_BUS.addListener(StrataMob::handleIncomingDamage);
        NeoForge.EVENT_BUS.addListener(SporeBellows::onDeath);
        NeoForge.EVENT_BUS.addListener(SporeClouds::onLevelTick);
        NeoForge.EVENT_BUS.addListener(WitnessAwakening::onLevelTick);
        NeoForge.EVENT_BUS.addListener(Spiralit::onInteract);
        NeoForge.EVENT_BUS.addListener(Spiralit::onChangedDimension);
        NeoForge.EVENT_BUS.addListener(Spiralit::onServerStopped);
    }

    /** Höchstzahl aller Strata-Mobs pro Dimension (Config {@code mobs.max_per_epoch}). */
    private static boolean belowEpochLimit(ServerLevelAccessor accessor) {
        ServerLevel level = accessor.getLevel();
        int max = EpochsConfig.MAX_MOBS_PER_EPOCH.get();
        return level.getEntities(EntityTypeTest.forClass(StrataMob.class), e -> true).size() < max;
    }
}
