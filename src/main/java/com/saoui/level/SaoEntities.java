package com.saoui.level;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.ElderGuardianEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.WardenEntity;

/** Common (server + client) entity classification helpers for bosses and scalable monsters. */
public final class SaoEntities {
    private SaoEntities() {}

    /**
     * Boss-tier monsters exempt from the 10-second HP bar auto-hide rule.
     * Uses unmodified base max health so scaled regular mobs never falsely become bosses.
     */
    public static boolean isBoss(LivingEntity le) {
        if (le instanceof EnderDragonEntity || le instanceof WitherEntity
                || le instanceof WardenEntity || le instanceof ElderGuardianEntity) {
            return true;
        }
        return le instanceof Monster && le.getAttributeBaseValue(EntityAttributes.MAX_HEALTH) >= 150.0;
    }

    /** Hostile monsters and bosses whose HP, defense, and attack scale with player SAO level. */
    public static boolean isScalableMob(LivingEntity le) {
        return le instanceof Monster || isBoss(le);
    }
}
