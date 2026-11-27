package com.saoui.mixin;

import com.saoui.level.SaoLevelHolder;
import com.saoui.level.SaoLevelLogic;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Persists and updates the independent SAO level on {@link ServerPlayerEntity}.
 * Gains XP alongside positive vanilla XP gains, ignores enchanting/anvil spending,
 * scales max health gradually, and applies a light penalty on death.
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin implements SaoLevelHolder {
    @Unique
    private int saoui$level = 1;

    @Unique
    private int saoui$xp = 0;

    @Override
    public int saoui$getLevel() {
        return saoui$level;
    }

    @Override
    public int saoui$getXp() {
        return saoui$xp;
    }

    @Override
    public void saoui$setLevelAndXp(int level, int xp) {
        this.saoui$level = Math.max(1, level);
        this.saoui$xp = Math.max(0, xp);
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        SaoLevelLogic.applyPlayerStats(self, this.saoui$level);
    }

    @Override
    public void saoui$addXp(int amount) {
        if (amount <= 0) return;
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        this.saoui$xp += amount;
        int prevLevel = this.saoui$level;
        while (this.saoui$xp >= SaoLevelLogic.xpForNextLevel(this.saoui$level)) {
            this.saoui$xp -= SaoLevelLogic.xpForNextLevel(this.saoui$level);
            this.saoui$level++;
        }
        if (this.saoui$level != prevLevel) {
            float oldMax = self.getMaxHealth();
            SaoLevelLogic.applyPlayerStats(self, this.saoui$level);
            float gained = self.getMaxHealth() - oldMax;
            if (gained > 0f && self.isAlive()) {
                self.setHealth(Math.min(self.getMaxHealth(), self.getHealth() + gained));
            }
        }
        SaoLevelLogic.sync(self);
    }

    @Inject(method = "addExperience", at = @At("HEAD"))
    private void saoui$onAddExperience(int experience, CallbackInfo ci) {
        if (experience > 0) {
            saoui$addXp(experience);
        }
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void saoui$writeNbt(NbtCompound nbt, CallbackInfo ci) {
        nbt.putInt("saoui_level", this.saoui$level);
        nbt.putInt("saoui_xp", this.saoui$xp);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void saoui$readNbt(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains("saoui_level")) {
            this.saoui$level = Math.max(1, nbt.getInt("saoui_level"));
            this.saoui$xp = Math.max(0, nbt.getInt("saoui_xp"));
        } else {
            this.saoui$level = 1;
            this.saoui$xp = 0;
        }
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        SaoLevelLogic.applyPlayerStats(self, this.saoui$level);
    }

    @Inject(method = "copyFrom", at = @At("TAIL"))
    private void saoui$copyFrom(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfo ci) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        SaoLevelHolder oldHolder = (SaoLevelHolder) oldPlayer;
        int oldLevel = oldHolder.saoui$getLevel();
        int oldXp = oldHolder.saoui$getXp();

        if (alive) {
            this.saoui$level = Math.max(1, oldLevel);
            this.saoui$xp = Math.max(0, oldXp);
            SaoLevelLogic.applyPlayerStats(self, this.saoui$level);
            self.setHealth(Math.min(oldPlayer.getHealth(), self.getMaxHealth()));
        } else {
            int newLevel = SaoLevelLogic.computeDeathPenaltyLevel(oldLevel);
            this.saoui$level = newLevel;
            this.saoui$xp = 0;
            SaoLevelLogic.applyPlayerStats(self, this.saoui$level);
            self.setHealth(self.getMaxHealth());
        }
    }
}
