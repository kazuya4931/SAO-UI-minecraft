package com.saoui.level;

import net.minecraft.entity.player.PlayerEntity;

/** Client-side cache of the local player's synced SAO level, with fallback to vanilla level on unmodded servers. */
public final class SaoLevelClient {
    private SaoLevelClient() {}

    private static int level = 1;
    private static int xp = 0;
    private static boolean synced = false;

    public static void update(int newLevel, int newXp) {
        level = Math.max(1, newLevel);
        xp = Math.max(0, newXp);
        synced = true;
    }

    public static void reset() {
        level = 1;
        xp = 0;
        synced = false;
    }

    public static int getDisplayLevel(PlayerEntity player) {
        return synced ? level : player.experienceLevel;
    }

    public static int getXp() {
        return xp;
    }

    public static boolean isSynced() {
        return synced;
    }
}
