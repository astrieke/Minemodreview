package dev.strata.epochs;

import dev.strata.core.mc.Mc;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Balance-Messung ({@code -Dstrata.balance=<Minuten>}, siehe {@code scripts/balance.sh}): misst Erträge, die bisher nur
 * gerechnet waren, über echte Spielzeit im Schnelllauf ({@code /tick sprint}).
 *
 * <ul>
 *   <li><b>Harzschnecke:</b> Pferche in der Sporenzeit mit je einer Schnecke, ungefüttert und gefüttert (alle 10 min neu
 *       gefüttert). Ein simulierter Sammler baut fertige Bernsteinplatten sofort ab, wie ein Spieler, der vorbeikommt.</li>
 *   <li><b>Harzfalle:</b> Pferche mit zwei Bernsteinmotten, einer Harzfalle und einer Fackel daneben. Gemessen wird, wann
 *       eine Motte festklebt und wann die Falle aushärtet; danach steht eine neue leere Falle und neue Motten bereit.</li>
 * </ul>
 * Gemessen wird ohne Spieler in zwangsgeladenen Chunks; Zufallsticks und Mob-KI laufen dort wie neben einem Spieler.
 */
final class Balance {

    private static final int SNAIL_PENS = 12;
    private static final int TRAP_PENS = 6;
    private static final int PEN_SPACING = 12;


    private final MinecraftServer server;
    private final ServerLevel level;
    private final int totalTicks;
    private final List<BlockPos> unfed = new ArrayList<>();
    private final List<BlockPos> fed = new ArrayList<>();
    private final List<BlockPos> traps = new ArrayList<>();
    private int unfedAmber;
    private int fedAmber;
    private final long[] trapSince = new long[TRAP_PENS];
    private final long[] caughtAt = new long[TRAP_PENS];
    private final List<Long> catchTimes = new ArrayList<>();
    private final List<Long> hardenTimes = new ArrayList<>();
    private int tick;
    private boolean done;
    private List<BossFight> fights = List.of();
    /** Kalibrierung: Kakteen-Sonden zeigen, ob das Spiel hier ohne Spieler selbst Zufallsticks vergibt. */
    private static final int CALIBRATION_TICKS = 2400;
    private static final int PROBES = 64;
    private final List<BlockPos> probes = new ArrayList<>();
    private boolean simulateRandomTicks;
    private int measured;

    private Balance(MinecraftServer server, ServerLevel level, int minutes) {
        this.server = server;
        this.level = level;
        this.totalTicks = minutes * 1200;
    }

    static void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        String value = System.getProperty("strata.balance");
        if (value == null || value.isEmpty() || value.equals("false")) return;
        int minutes = value.equals("true") ? 30 : Integer.parseInt(value);
        MinecraftServer server = event.getServer();
        ServerLevel level = Mc.level(server, StrataEpochs.id("spore_age"));
        if (level == null) {
            StrataEpochs.LOG.error("[Strata-Balance] Sporenzeit fehlt");
            return;
        }
        Balance run = new Balance(server, level, minutes);
        run.setup();
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> run.tick());
        Transplanter.command(server.overworld(), "tick sprint " + (run.totalTicks + CALIBRATION_TICKS + 100));
    }

    // ------------------------------------------------------------------ Aufbau

    private void setup() {
        int y = Math.max(Mc.minY(level) + 20, 40);
        int x0 = 2000, z0 = 2000;
        int pens = SNAIL_PENS * 2 + TRAP_PENS;
        Transplanter.command(level, "forceload add " + (x0 - 16) + " " + (z0 - 16) + " " + (x0 + pens * PEN_SPACING + 16) + " " + (z0 + 32));
        for (int i = 0; i < pens; i++) {
            BlockPos center = new BlockPos(x0 + i * PEN_SPACING, y, z0);
            buildPen(center);
            if (i < SNAIL_PENS) {
                unfed.add(center);
                summon("resin_snail", center);
            } else if (i < SNAIL_PENS * 2) {
                fed.add(center);
                summon("resin_snail", center);
            } else {
                traps.add(center);
            }
        }
        for (int t = 0; t < TRAP_PENS; t++) armTrap(t);
        ServerLevel crystal = Mc.level(server, StrataEpochs.id("crystal_age"));
        Transplanter.command(server.overworld(), "difficulty normal");   // Server-Standard ist leicht; Spielwelten meist normal
        if (crystal != null) fights = BossFight.setup(crystal, 6000, Math.max(Mc.minY(crystal) + 20, 40), 6000);
        // Sonden: 64 Kakteen auf Sand (je ein Block Abstand); jeder Zufallstick erhöht ihr Alter um 1.
        BlockPos field = new BlockPos(x0, y, z0 + 12);
        for (BlockPos p : BlockPos.betweenClosed(field.offset(-1, -1, -1), field.offset(16, 3, 16))) {
            level.setBlock(p, p.getY() == y - 1 ? Blocks.SAND.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
        for (int i = 0; i < PROBES; i++) {
            BlockPos p = field.offset((i % 8) * 2, 0, (i / 8) * 2);
            level.setBlock(p, Blocks.CACTUS.defaultBlockState(), 2);
            probes.add(p);
        }
        StrataEpochs.LOG.info("[Strata-Balance] Aufbau: {} Schnecken ungefüttert, {} gefüttert, {} Fallen; {} min Spielzeit",
                SNAIL_PENS, SNAIL_PENS, TRAP_PENS, totalTicks / 1200);
    }

    /** Geschlossener Pferch: Boden aus Stein, 7 × 7 innen, 4 hoch, Wände und Decke aus Zeitgestein. */
    private void buildPen(BlockPos c) {
        BlockState wall = EpochsContent.CHRONOSTONE.get().defaultBlockState();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-4, -1, -4), c.offset(4, 4, 4))) {
            boolean shell = Math.abs(p.getX() - c.getX()) == 4 || Math.abs(p.getZ() - c.getZ()) == 4 || p.getY() == c.getY() - 1
                    || p.getY() == c.getY() + 4;
            level.setBlock(p, shell ? (p.getY() == c.getY() - 1 ? Blocks.STONE.defaultBlockState() : wall) : Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private void summon(String mob, BlockPos at) {
        Transplanter.command(level, "summon strata_epochs:" + mob + " " + at.getX() + " " + at.getY() + " " + at.getZ()
                + " {PersistenceRequired:1b,Tags:[\"strata_balance\"]}");
    }

    /** Neue leere Falle mit Fackel daneben in der Pferchecke, dazu zwei frische Motten in der Mitte. */
    private void armTrap(int t) {
        BlockPos c = traps.get(t);
        BlockPos trap = c.offset(-3, 0, -3);
        level.setBlockAndUpdate(trap, EpochsContent.RESIN_TRAP.get().defaultBlockState());
        level.setBlockAndUpdate(trap.east(), Blocks.TORCH.defaultBlockState());
        Transplanter.command(level, "kill @e[type=strata_epochs:amber_moth,tag=strata_balance,x=" + (c.getX() - 4) + ",y=" + (c.getY() - 1)
                + ",z=" + (c.getZ() - 4) + ",dx=8,dy=5,dz=8]");
        summon("amber_moth", c.above());
        summon("amber_moth", c.above().east());
        trapSince[t] = tick;
        caughtAt[t] = -1;
    }

    // ------------------------------------------------------------------ Ablauf

    private void tick() {
        if (done) return;
        tick++;
        if (tick == CALIBRATION_TICKS) calibrate();
        // Füttern beim Start und alle 10 min (wie ein Spieler, der Sporen nachlegt).
        if (tick == 40 || tick % 12000 == 0) feedAll();
        if (simulateRandomTicks) randomTicks();
        if (tick % 20 == 0) {
            keepActive();
            int unfedNow = harvest(unfed);
            int fedNow = harvest(fed);
            checkTraps();
            if (tick > CALIBRATION_TICKS) {
                unfedAmber += unfedNow;
                fedAmber += fedNow;
            }
        }
        for (BossFight f : fights) f.tick();
        if (tick <= CALIBRATION_TICKS) return;   // Kalibrierung: Erträge zählen noch nicht
        measured++;
        if (measured % 6000 == 0 || (totalTicks <= 6000 && measured % 600 == 0)) report(false);   // kurze Läufe: jede halbe Minute
        if (measured >= totalTicks) {
            done = true;
            report(true);
            Transplanter.command(server.overworld(), "tick sprint stop");
            server.halt(false);
        }
    }

    /**
     * Zufallsticks wie neben einem Spieler: Vanilla gibt je Abschnitt (16³) und Tick 3 zufälligen Blöcken einen
     * Zufallstick, also jedem Block mit Chance 3/4096. Bis 26.2 vergibt das Spiel ohne Spieler in der Nähe keine (auch
     * nicht in zwangsgeladenen Chunks); im echten Spiel ist man während der Ernte ja dort. Ob nötig, zeigt die
     * Kalibrierung.
     */
    private void randomTicks() {
        int speed = 3;   // Vanilla-Standard der Spielregel randomTickSpeed
        var random = level.getRandom();
        List<BlockPos> all = new ArrayList<>(unfed);
        all.addAll(fed);
        all.addAll(traps);
        for (BlockPos c : all) {
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-3, 0, -3), c.offset(3, 0, 3))) {
                if (random.nextInt(4096) >= speed) continue;
                BlockState s = level.getBlockState(p);
                if (s.isRandomlyTicking()) s.randomTick(level, p.immutable(), random);
            }
        }
    }

    private void feedAll() {
        int n = 0;
        for (BlockPos c : fed) {
            for (var snail : level.getEntitiesOfClass(dev.strata.epochs.mob.ResinSnail.class, new net.minecraft.world.phys.AABB(c).inflate(5))) {
                snail.feed();
                n++;
            }
        }
        if (n != fed.size()) StrataEpochs.LOG.warn("[Strata-Balance] {} von {} Schnecken gefüttert", n, fed.size());
    }

    /** Fertige Platten abbauen (1 Bernstein je Platte, wie der Block-Abwurf). */
    private int harvest(List<BlockPos> pens) {
        int n = 0;
        for (BlockPos c : pens) {
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-3, 0, -3), c.offset(3, 0, 3))) {
                if (level.getBlockState(p).is(EpochsContent.AMBER_PLATE.get())) {
                    level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
                    n++;
                }
            }
        }
        return n;
    }

    private void checkTraps() {
        for (int t = 0; t < TRAP_PENS; t++) {
            BlockPos trap = traps.get(t).offset(-3, 0, -3);
            BlockState s = level.getBlockState(trap);
            if (caughtAt[t] < 0 && s.is(EpochsContent.RESIN_TRAP_MOTH.get())) {
                caughtAt[t] = tick;
                catchTimes.add(tick - trapSince[t]);
            } else if (s.is(EpochsContent.RESIN_TRAP_HARDENED.get())) {
                if (caughtAt[t] < 0) caughtAt[t] = tick;   // zwischen zwei Prüfungen gefangen und ausgehärtet
                hardenTimes.add(tick - caughtAt[t]);
                armTrap(t);
            }
        }
    }

    /** Haben die Kakteen ohne unser Zutun Zufallsticks bekommen? Erwartet wären bei 3 je Abschnitt etwa 112. */
    private void calibrate() {
        int ages = 0;
        for (BlockPos p : probes) {
            BlockState s = level.getBlockState(p);
            if (s.is(Blocks.CACTUS)) ages += s.getValue(net.minecraft.world.level.block.CactusBlock.AGE);
            if (!level.getBlockState(p.above()).isAir()) ages += 16;   // schon gewachsen
        }
        simulateRandomTicks = ages == 0;
        StrataEpochs.LOG.info("[Strata-Balance] Kalibrierung: Kakteen-Alter {} nach {} Ticks -> Zufallsticks {}", ages, CALIBRATION_TICKS,
                simulateRandomTicks ? "simuliert (das Spiel vergibt ohne Spieler keine)" : "vom Spiel (auch ohne Spieler)");
        catchTimes.clear();
        hardenTimes.clear();
        for (int t = 0; t < TRAP_PENS; t++) armTrap(t);
    }

    /**
     * Wie ein Spieler in der Nähe: Ab 26.3 setzt Minecraft den Untätigkeitszähler auch dauerhafter Mobs nur zurück,
     * wenn ein Spieler in 32 Blöcken steht; sonst hört das Umherwandern nach 5 s auf.
     */
    private void keepActive() {
        List<BlockPos> all = new ArrayList<>(unfed);
        all.addAll(fed);
        for (BlockPos c : all) {
            for (var snail : level.getEntitiesOfClass(dev.strata.epochs.mob.ResinSnail.class, new net.minecraft.world.phys.AABB(c).inflate(5))) {
                snail.setNoActionTime(0);
            }
        }
    }

    private void report(boolean last) {
        double minutes = measured / 1200.0;
        double perSnailUnfed = unfedAmber / (double) SNAIL_PENS / minutes * 10;
        double perSnailFed = fedAmber / (double) SNAIL_PENS / minutes * 10;
        StrataEpochs.LOG.info("[Strata-Balance] {} nach {} min: Harzschnecke {} Bernstein je Schnecke und 10 min (gefüttert {}); "
                        + "Harzfalle: {} Fänge, Fangzeit Mittel {} s, Aushärten {}x, Mittel {} s; Fossilien je Falle und 10 min {}",
                last ? "Ergebnis" : "Zwischenstand", String.format("%.1f", minutes), String.format("%.2f", perSnailUnfed),
                String.format("%.2f", perSnailFed), catchTimes.size(), mean(catchTimes), hardenTimes.size(), mean(hardenTimes),
                String.format("%.2f", hardenTimes.size() / (double) TRAP_PENS / minutes * 10));
        if (last) for (String line : BossFight.summary(fights)) StrataEpochs.LOG.info("[Strata-Balance] Stratolith {}", line);
        StrataEpochs.LOG.info("[Strata-Balance] Stand: {}", state());
        if (last) StrataEpochs.LOG.info("[Strata-Balance] Fertig");
    }

    /** Kurzer Stand je Pferch (Mobs und Spur), damit eine Null nicht unbemerkt bleibt. */
    private String state() {
        StringBuilder b = new StringBuilder();
        List<BlockPos> all = new ArrayList<>(unfed);
        all.addAll(fed);
        all.addAll(traps);
        for (BlockPos c : all) {
            var box = new net.minecraft.world.phys.AABB(c).inflate(5);
            var snails = level.getEntitiesOfClass(dev.strata.epochs.mob.ResinSnail.class, box);
            int moths = level.getEntitiesOfClass(dev.strata.epochs.mob.AmberMoth.class, box).size();
            int trail = 0;
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-3, 0, -3), c.offset(3, 0, 3))) {
                if (level.getBlockState(p).is(EpochsContent.RESIN_TRAIL.get())) trail++;
            }
            b.append(snails.isEmpty() ? "" : "S" + snails.size() + "@" + snails.get(0).blockPosition().toShortString()
                    + " eigen=" + snails.get(0).ownTrail(level).size())
                    .append(moths > 0 ? "M" + moths : "").append(" T").append(trail);
            if (traps.contains(c)) {
                BlockPos trap = c.offset(-3, 0, -3);
                b.append(" F=").append(level.getBlockState(trap).getBlock()).append(" lit=").append(dev.strata.epochs.mob.AmberMoth.lit(level, trap))
                        .append(" Fackel=").append(level.getBlockState(trap.east()).getBlock());
                level.getEntitiesOfClass(dev.strata.epochs.mob.AmberMoth.class, new net.minecraft.world.phys.AABB(c).inflate(5))
                        .forEach(m -> b.append(" m@").append(m.blockPosition().toShortString()));
            }
            b.append(" | ");
        }
        return b.toString();
    }

    private static String mean(List<Long> ticks) {
        return ticks.isEmpty() ? "-" : String.format("%.0f", ticks.stream().mapToLong(Long::longValue).average().orElse(0) / 20.0);
    }
}
