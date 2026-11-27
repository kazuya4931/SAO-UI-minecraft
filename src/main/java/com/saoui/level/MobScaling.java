package com.saoui.level;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/**
 * Scales hostile monsters and bosses (Max Health, Armor, Attack Damage) dynamically
 * according to the nearby player's current SAO level.
 */
public final class MobScaling {
    private MobScaling() {}

    public static final Identifier MOB_HP_ID = Identifier.of("saoui", "mob_scale_hp");
    public static final Identifier MOB_ARMOR_ID = Identifier.of("saoui", "mob_scale_armor");
    public static final Identifier MOB_DAMAGE_ID = Identifier.of("saoui", "mob_scale_damage");

    private static final double NEARBY_RANGE_SQ = 128.0 * 128.0;

    /** +5% base max health per SAO level above LV 1 (up to +400%, i.e., 5x base HP). */
    public static double hpMultiplier(int saoLevel) {
        return Math.min(Math.max(0, saoLevel - 1) * 0.05, 4.0);
    }

    /** +3.5% base attack damage per SAO level above LV 1 (up to +300%, i.e., 4x base attack). */
    public static double damageMultiplier(int saoLevel) {
        return Math.min(Math.max(0, saoLevel - 1) * 0.035, 3.0);
    }

    /** +0.20 flat armor per SAO level above LV 1 (up to +16.0 armor). */
    public static double armorBonus(int saoLevel) {
        return Math.min(Math.max(0, saoLevel - 1) * 0.20, 16.0);
    }

    /**
     * Resolves the reference SAO level for a mob in {@code world}:
     * uses the nearest non-spectator player within 128 blocks, or the highest SAO level player in that world.
     */
    public static int targetLevelFor(ServerWorld world, LivingEntity mob) {
        List<ServerPlayerEntity> players = world.getPlayers();
        if (players.isEmpty()) return 1;

        ServerPlayerEntity nearest = null;
        double bestDistSq = NEARBY_RANGE_SQ;
        int highestInWorld = 1;

        for (ServerPlayerEntity p : players) {
            if (p.isSpectator()) continue;
            int lvl = ((SaoLevelHolder) p).saoui$getLevel();
            if (lvl > highestInWorld) highestInWorld = lvl;
            double dSq = p.squaredDistanceTo(mob);
            if (dSq <= bestDistSq) {
                bestDistSq = dSq;
                nearest = p;
            }
        }
        if (nearest != null) {
            return ((SaoLevelHolder) nearest).saoui$getLevel();
        }
        return highestInWorld;
    }

    /** Applies or updates scaling modifiers on a hostile mob / boss while preserving its current HP fraction. */
    public static void applyToMob(ServerWorld world, LivingEntity mob) {
        if (!SaoEntities.isScalableMob(mob) || !mob.isAlive()) return;
        EntityAttributeInstance hpInst = mob.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (hpInst == null) return;

        int level = targetLevelFor(world, mob);
        double hpMult = hpMultiplier(level);
        EntityAttributeModifier existing = hpInst.getModifier(MOB_HP_ID);

        if (hpMult <= 0.0) {
            if (existing == null) return;
        } else if (existing != null && Math.abs(existing.value() - hpMult) < 1e-6) {
            return;
        }

        float oldMax = Math.max(1f, mob.getMaxHealth());
        float frac = MathHelper.clamp(mob.getHealth() / oldMax, 0f, 1f);

        applyModifier(mob, EntityAttributes.MAX_HEALTH, MOB_HP_ID, hpMult, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        applyModifier(mob, EntityAttributes.ARMOR, MOB_ARMOR_ID, armorBonus(level), EntityAttributeModifier.Operation.ADD_VALUE);
        applyModifier(mob, EntityAttributes.ATTACK_DAMAGE, MOB_DAMAGE_ID, damageMultiplier(level), EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);

        if (mob.isAlive()) {
            float newMax = mob.getMaxHealth();
            mob.setHealth(MathHelper.clamp(frac * newMax, 1f, newMax));
        }
    }

    /** Periodically refreshes loaded hostile mobs & bosses in the world when players level up or down. */
    public static void refreshWorld(ServerWorld world) {
        if (world.getPlayers().isEmpty()) return;
        for (Entity e : world.iterateEntities()) {
            if (e instanceof LivingEntity le && SaoEntities.isScalableMob(le)) {
                applyToMob(world, le);
            }
        }
    }

    private static void applyModifier(LivingEntity mob, RegistryEntry<EntityAttribute> attr,
                                      Identifier id, double value, EntityAttributeModifier.Operation op) {
        EntityAttributeInstance inst = mob.getAttributeInstance(attr);
        if (inst == null) return;
        if (value > 0.0) {
            inst.overwritePersistentModifier(new EntityAttributeModifier(id, value, op));
        } else {
            inst.removeModifier(id);
        }
    }
}
