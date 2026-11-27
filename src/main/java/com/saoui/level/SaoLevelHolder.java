package com.saoui.level;

/** Duck interface implemented by {@code ServerPlayerEntity} via mixin to store independent SAO level state. */
public interface SaoLevelHolder {
    int saoui$getLevel();

    int saoui$getXp();

    void saoui$setLevelAndXp(int level, int xp);

    void saoui$addXp(int amount);
}
