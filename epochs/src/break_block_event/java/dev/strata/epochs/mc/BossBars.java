package dev.strata.epochs.mc;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;

/** Boss-Leiste; ab 26.1 bekommt der Konstruktor zusätzlich eine UUID. */
public final class BossBars {

    private BossBars() {}

    public static ServerBossEvent create(Component name) {
        return new ServerBossEvent(java.util.UUID.randomUUID(), name, BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_6);
    }
}
