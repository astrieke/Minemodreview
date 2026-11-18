package dev.strata.epochs;

import com.mojang.authlib.GameProfile;
import dev.strata.core.mc.Mc;
import dev.strata.epochs.mob.StrataAbility;
import dev.strata.epochs.mob.Stratolith;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Bosskampf für die Balance-Messung: Ein Testspieler kämpft gegen den echten Stratolithen (seine KI, Fähigkeiten und
 * Phasen laufen unverändert). Der Spieler schlägt im Takt seines Schwerts mit voll aufgeladenem Schlag zu und geht dem
 * Boss nach, wenn er außer Reichweite ist. Zwei Spielweisen:
 * <ul>
 *   <li><b>geübt:</b> tritt beim Warnkreis der Kristallspitzen 3 Blöcke zur Seite und kontert das Zurückspulen mit
 *       der Spitzhacke von vorn (so wie es die Texte im Spiel erklären);</li>
 *   <li><b>ungeübt:</b> bleibt stehen und schlägt nur zu.</li>
 * </ul>
 * Den Hammerschlag nehmen beide. Wird das Leben knapp (unter 8), heilt der Test voll und zählt das; gemeldet werden
 * Kampfdauer, erlittener Schaden und die nötige Heilung in Heiltränken II (je 8 Leben).
 */
final class BossFight {

    /** Längster Kampf, danach gilt er als nicht gewonnen. */
    static final int MAX_TICKS = 12000;
    private static final int SWORD_TICKS = 13;   // Angriffsgeschwindigkeit 1,6 (Diamant- und Netheritschwert)
    private static final double REACH = 3.0;

    private final String gear;
    private final boolean skilled;
    private final ServerLevel level;
    private final BlockPos center;
    private Stratolith boss;
    private dev.strata.epochs.mc.Combatant player;
    private ItemStack sword;
    private ItemStack pickaxe;
    private int ticks;
    private int swing;
    private float lastHealth;
    private float damageTaken;
    private int counters;
    private int rewinds;
    private int attacks;
    private int outOfReach;
    private float dealt;
    private boolean rewindSeen;
    private boolean dodged;
    private boolean finished;
    private boolean won;

    private BossFight(ServerLevel level, BlockPos center, String gear, boolean skilled) {
        this.level = level;
        this.center = center;
        this.gear = gear;
        this.skilled = skilled;
    }

    static final int REPEATS = 4;

    /** Alle Kämpfe: drei Ausrüstungen × zwei Spielweisen, je {@link #REPEATS}-mal, jeder in einer eigenen Arena. */
    static List<BossFight> setup(ServerLevel level, int x0, int y, int z0) {
        List<BossFight> fights = new ArrayList<>();
        String[] gears = {"Diamant", "Diamant+", "Netherit+"};
        int i = 0;
        for (int r = 0; r < REPEATS; r++) {
            for (String gear : gears) {
                for (boolean skilled : new boolean[] {true, false}) {
                    BossFight f = new BossFight(level, new BlockPos(x0 + (i % 6) * 64, y, z0 + (i / 6) * 64), gear, skilled);
                    f.build(i);
                    fights.add(f);
                    i++;
                }
            }
        }
        return fights;
    }

    /** Mittelwerte je Ausrüstung und Spielweise. */
    static List<String> summary(List<BossFight> fights) {
        List<String> lines = new ArrayList<>();
        for (String gear : new String[] {"Diamant", "Diamant+", "Netherit+"}) {
            for (boolean skilled : new boolean[] {true, false}) {
                var group = fights.stream().filter(f -> f.gear.equals(gear) && f.skilled == skilled).toList();
                long done = group.stream().filter(f -> f.finished).count();
                long won = group.stream().filter(f -> f.won).count();
                double secs = group.stream().filter(f -> f.won).mapToDouble(f -> f.ticks / 20.0).average().orElse(0);
                double dmg = group.stream().mapToDouble(f -> f.damageTaken).average().orElse(0);
                double perMin = group.stream().mapToDouble(f -> f.damageTaken / Math.max(1, f.ticks / 1200.0)).average().orElse(0);
                double potions = group.stream().mapToDouble(f -> Math.max(0, Math.ceil((f.damageTaken - 19) / 8.0))).average().orElse(0);
                double counters = group.stream().mapToDouble(f -> f.counters).average().orElse(0);
                double rewinds = group.stream().mapToDouble(f -> f.rewinds).average().orElse(0);
                lines.add(String.format("[Schläge %.0f, je Schlag %.2f, außer Reichweite %.0f%%] ",
                        group.stream().mapToDouble(f -> f.attacks).average().orElse(0),
                        group.stream().mapToDouble(f -> f.dealt / Math.max(1, f.attacks)).average().orElse(0),
                        group.stream().mapToDouble(f -> 100.0 * f.outOfReach / Math.max(1, f.ticks)).average().orElse(0)) + String.format("%-9s %-8s: %d/%d besiegt (%d fertig), Kampf %.0f s, Schaden %.0f (%.0f je Minute), ≈ %.1f Heiltränke II, "
                                + "Zurückspulen %.1f x, gekontert %.1f x",
                        gear, skilled ? "geübt" : "ungeübt", won, group.size(), done, secs, dmg, perMin, potions, rewinds, counters));
            }
        }
        return lines;
    }

    /** Arena: 21 × 21 Boden aus Zeitgestein, 8 Blöcke frei darüber, Spieler im Süden, Boss in der Mitte. */
    private void build(int index) {
        Transplanter.command(level, "forceload add " + (center.getX() - 16) + " " + (center.getZ() - 16) + " " + (center.getX() + 16) + " " + (center.getZ() + 16));
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-11, -1, -11), center.offset(11, 8, 11))) {
            boolean floor = p.getY() == center.getY() - 1;
            boolean wall = Math.abs(p.getX() - center.getX()) == 11 || Math.abs(p.getZ() - center.getZ()) == 11 || p.getY() == center.getY() + 8;
            level.setBlock(p, floor || wall ? EpochsContent.CHRONOSTONE.get().defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
        boolean netherite = gear.startsWith("Netherit");
        boolean enchanted = gear.endsWith("+");
        player = new dev.strata.epochs.mc.Combatant(level, new GameProfile(
                java.util.UUID.nameUUIDFromBytes(("strata_kampf_" + index).getBytes()), "strata_kampf_" + index));
        if (player.connection.getConnection().channel() == null) new io.netty.channel.embedded.EmbeddedChannel(player.connection.getConnection());
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.setPos(center.getX() + 0.5, center.getY(), center.getZ() - 6 + 0.5);
        Item[] armor = netherite
                ? new Item[] {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS}
                : new Item[] {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int s = 0; s < 4; s++) {
            ItemStack piece = new ItemStack(armor[s]);
            if (enchanted) dev.strata.epochs.mc.MobCompat.enchant(piece, level, "minecraft:protection", 4);
            player.setItemSlot(slots[s], piece);
        }
        sword = new ItemStack(netherite ? Items.NETHERITE_SWORD : Items.DIAMOND_SWORD);
        pickaxe = new ItemStack(netherite ? Items.NETHERITE_PICKAXE : Items.DIAMOND_PICKAXE);
        if (enchanted) dev.strata.epochs.mc.MobCompat.enchant(sword, level, "minecraft:sharpness", 5);
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        level.addNewPlayer(player);
        player.dropSpawnProtection();
        player.tickCombat();   // Ausrüstung übernehmen
        boss = StratolithSummon.summon(level, center.offset(0, 0, 2));
        lastHealth = player.getHealth();
    }

    boolean finished() {
        return finished;
    }

    void tick() {
        if (finished) return;
        ticks++;
        player.tickCombat();
        // Erlittener Schaden: jede Abnahme des Lebens (ein FakePlayer regeneriert nicht von selbst).
        float health = player.getHealth();
        if (health < lastHealth) damageTaken += lastHealth - health;
        if (health < 8) {
            player.setHealth(player.getMaxHealth());
        }
        lastHealth = player.getHealth();
        if (!boss.isAlive()) {
            won = true;
            finish();
            return;
        }
        if (ticks >= MAX_TICKS) {
            finish();
            return;
        }
        if (boss.isRising()) return;

        boolean shardsWarn = boss.shardsPhase() == StrataAbility.Phase.WARNING;
        if (skilled && shardsWarn && !dodged) {
            // Aus dem Warnkreis treten: 3 Blöcke quer zur Richtung zum Boss.
            Vec3 to = boss.position().subtract(player.position()).multiply(1, 0, 1).normalize();
            Vec3 side = new Vec3(-to.z, 0, to.x).scale(3);
            Vec3 dest = clamp(player.position().add(side));
            player.setPos(dest.x, dest.y, dest.z);
            dodged = true;
        }
        if (!shardsWarn) dodged = false;

        boolean rewindWarn = boss.rewindPhase() == StrataAbility.Phase.WARNING;
        if (rewindWarn && !rewindSeen) rewinds++;
        rewindSeen = rewindWarn;
        if (skilled && rewindWarn) {
            // Zum Kontern vor den Boss treten (der Kern sitzt vorn): 1,5 Blöcke vor seine Blickrichtung.
            Vec3 look = new Vec3(boss.getLookAngle().x, 0, boss.getLookAngle().z).normalize();
            Vec3 dest = clamp(boss.position().add(look.scale(boss.getBbWidth() / 2 + 1.5)));
            player.setPos(dest.x, dest.y, dest.z);
        }
        double gap = player.distanceTo(boss) - boss.getBbWidth() / 2;
        if (gap > REACH && !(skilled && shardsWarn)) {
            // Hingehen: so nah, dass er zuschlagen kann (wie ein Spieler, der dem Boss nachgeht).
            Vec3 from = boss.position().subtract(player.position()).multiply(1, 0, 1);
            Vec3 dest = clamp(player.position().add(from.normalize().scale(Math.min(0.28, gap - REACH + 0.2))));
            player.setPos(dest.x, dest.y, dest.z);
        }
        face();
        if (gap > REACH) outOfReach++;
        if (++swing < SWORD_TICKS || gap > REACH) return;
        attacks++;
        float bossBefore = boss.getHealth();
        swing = 0;
        if (skilled && rewindWarn) {
            player.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);
            player.tickCombat();
            player.charge();
            player.attack(boss);
            if (boss.isStunned()) counters++;
            player.setItemInHand(InteractionHand.MAIN_HAND, sword);
            player.tickCombat();
        } else {
            player.charge();
            player.attack(boss);
        }
        dealt += Math.max(0, bossBefore - boss.getHealth());
    }

    /** Blick zum Boss (für den Konter zählt, dass der Spieler vor ihm steht). */
    private void face() {
        Vec3 d = boss.position().subtract(player.position());
        float yaw = (float) (Math.atan2(-d.x, d.z) * 180 / Math.PI);
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
    }

    /** In der Arena bleiben. */
    private Vec3 clamp(Vec3 p) {
        double x = Math.max(center.getX() - 9.5, Math.min(center.getX() + 10.5, p.x));
        double z = Math.max(center.getZ() - 9.5, Math.min(center.getZ() + 10.5, p.z));
        return new Vec3(x, center.getY(), z);
    }

    private void finish() {
        finished = true;
        if (boss.isAlive()) boss.discard();
        level.removePlayerImmediately(player, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
    }
}
