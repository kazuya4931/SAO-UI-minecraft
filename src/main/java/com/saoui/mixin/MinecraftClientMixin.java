package com.saoui.mixin;

import com.saoui.screen.SaoMenuScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Swap the vanilla pause menu for the SAO menu. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen saoui$replacePauseMenu(Screen screen) {
        if (screen instanceof GameMenuScreen gms && gms.shouldShowMenu()) {
            if (SaoMenuScreen.bypass) {
                SaoMenuScreen.bypass = false;
                return screen;
            }
            return new SaoMenuScreen();
        }
        return screen;
    }
}
