package com.saoui.mixin;

import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dead entities vanish instantly (replaced by the polygon shatter effect) instead of falling over. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void saoui$hideDead(E entity, Frustum frustum, double x, double y, double z,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof LivingEntity le && !(entity instanceof ArmorStandEntity) && le.isDead()) {
            cir.setReturnValue(false);
        }
    }
}
