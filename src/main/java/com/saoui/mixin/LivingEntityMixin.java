package com.saoui.mixin;

import com.saoui.level.MobScaling;
import com.saoui.level.SaoEntities;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Scales indirect/ranged monster attacks (arrows, fireballs, wither skulls, sonic boom)
 * and non-attribute boss attacks (e.g. Ender Dragon charge) according to SAO level,
 * without double-scaling melee mobs that already scale via {@link EntityAttributes#ATTACK_DAMAGE}.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @ModifyVariable(
            method = "damage(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/damage/DamageSource;F)Z",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private float saoui$scaleIndirectAndBossDamage(float amount, ServerWorld world, DamageSource source) {
        if (amount <= 0f) return amount;
        if (source.getAttacker() instanceof LivingEntity attacker && SaoEntities.isScalableMob(attacker)) {
            if (!source.isDirect() || attacker.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE) == null) {
                int lvl = MobScaling.targetLevelFor(world, attacker);
                double mult = MobScaling.damageMultiplier(lvl);
                if (mult > 0.0) {
                    return (float) (amount * (1.0 + mult));
                }
            }
        }
        return amount;
    }
}
