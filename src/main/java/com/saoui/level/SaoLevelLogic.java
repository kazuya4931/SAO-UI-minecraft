package com.saoui.level;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Core formulas, player stat scaling (Max HP, Attack Damage, Armor, Armor Toughness),
 * death penalty, and client syncing for the independent SAO level system.
 */
public final class SaoLevelLogic {
    private SaoLevelLogic() {}

    public static final Identifier MAX_HEALTH_MODIFIER_ID = Identifier.of("saoui", "sao_level_max_health");
    public static final Identifier ATTACK_DAMAGE_MODIFIER_ID = Identifier.of("saoui", "sao_level_attack_damage");
    public static final Identifier ARMOR_MODIFIER_ID = Identifier.of("saoui", "sao_level_armor");
    public static final Identifier ARMOR_TOUGHNESS_MODIFIER_ID = Identifier.of("saoui", "sao_level_armor_toughness");

    public static final double HP_PER_LEVEL = 1.0;
    public static final double MAX_BONUS_HP = 200.0;

    public static final double ATTACK_PER_LEVEL = 0.25;
    public static final double MAX_BONUS_ATTACK = 50.0;

    public static final double ARMOR_PER_LEVEL = 0.25;
    public static final double MAX_BONUS_ARMOR = 20.0;

    public static final double TOUGHNESS_PER_LEVEL = 0.10;
    public static final double MAX_BONUS_TOUGHNESS = 12.0;

    /** Smooth RPG XP curve required to advance from {@code level} to {@code level + 1}. */
    public static int xpForNextLevel(int level) {
        int n = Math.max(0, level - 1);
        if (n < 16) return 2 * n + 10;
        if (n < 31) return 5 * n - 35;
        return 9 * n - 155;
    }

    /** Gradual max health bonus: +1.0 max HP per level above LV 1, capped at +200.0. */
    public static double bonusMaxHealth(int level) {
        return Math.min(Math.max(0, level - 1) * HP_PER_LEVEL, MAX_BONUS_HP);
    }

    /** Gradual attack damage bonus: +0.25 per level above LV 1, stacking additively with held weapon/tool. */
    public static double bonusAttackDamage(int level) {
        return Math.min(Math.max(0, level - 1) * ATTACK_PER_LEVEL, MAX_BONUS_ATTACK);
    }

    /** Gradual armor bonus: +0.25 per level above LV 1, stacking additively with equipped armor. */
    public static double bonusArmor(int level) {
        return Math.min(Math.max(0, level - 1) * ARMOR_PER_LEVEL, MAX_BONUS_ARMOR);
    }

    /** Gradual armor toughness bonus: +0.10 per level above LV 1 so high-tier armor wearers still gain defense. */
    public static double bonusArmorToughness(int level) {
        return Math.min(Math.max(0, level - 1) * TOUGHNESS_PER_LEVEL, MAX_BONUS_TOUGHNESS);
    }

    /** Death penalty: lose ~25% of current SAO level (at least 1 if above LV 1), never dropping below LV 1. */
    public static int computeDeathPenaltyLevel(int oldLevel) {
        if (oldLevel <= 1) return 1;
        int loss = Math.max(1, Math.round(oldLevel * 0.25f));
        return Math.max(1, oldLevel - loss);
    }

    /** Applies or removes persistent SAO stat modifiers (Max HP, Attack Damage, Armor, Toughness) on the player. */
    public static void applyPlayerStats(ServerPlayerEntity player, int level) {
        applyAdditiveModifier(player, EntityAttributes.MAX_HEALTH, MAX_HEALTH_MODIFIER_ID, bonusMaxHealth(level));
        applyAdditiveModifier(player, EntityAttributes.ATTACK_DAMAGE, ATTACK_DAMAGE_MODIFIER_ID, bonusAttackDamage(level));
        applyAdditiveModifier(player, EntityAttributes.ARMOR, ARMOR_MODIFIER_ID, bonusArmor(level));
        applyAdditiveModifier(player, EntityAttributes.ARMOR_TOUGHNESS, ARMOR_TOUGHNESS_MODIFIER_ID, bonusArmorToughness(level));

        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static void applyAdditiveModifier(ServerPlayerEntity player, RegistryEntry<EntityAttribute> attr,
                                              Identifier id, double value) {
        EntityAttributeInstance inst = player.getAttributeInstance(attr);
        if (inst == null) return;
        if (value > 0.0) {
            inst.overwritePersistentModifier(new EntityAttributeModifier(
                    id, value, EntityAttributeModifier.Operation.ADD_VALUE));
        } else {
            inst.removeModifier(id);
        }
    }

    /** Syncs the player's SAO level and XP to their client if the client supports the payload. */
    public static void sync(ServerPlayerEntity player) {
        if (player.networkHandler == null) return;
        if (!ServerPlayNetworking.canSend(player, SaoLevelPayload.ID)) return;
        SaoLevelHolder holder = (SaoLevelHolder) player;
        ServerPlayNetworking.send(player, new SaoLevelPayload(holder.saoui$getLevel(), holder.saoui$getXp()));
    }
}
