package com.saoui;

import com.saoui.level.MobScaling;
import com.saoui.level.SaoEntities;
import com.saoui.level.SaoLevelHolder;
import com.saoui.level.SaoLevelLogic;
import com.saoui.level.SaoLevelPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;

/** Common entrypoint registering SAO level networking, player stat syncing, and dynamic mob scaling. */
public class SaoUiMod implements ModInitializer {
    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(SaoLevelPayload.ID, SaoLevelPayload.CODEC);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            SaoLevelHolder holder = (SaoLevelHolder) handler.player;
            SaoLevelLogic.applyPlayerStats(handler.player, holder.saoui$getLevel());
            SaoLevelLogic.sync(handler.player);
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            SaoLevelHolder holder = (SaoLevelHolder) newPlayer;
            SaoLevelLogic.applyPlayerStats(newPlayer, holder.saoui$getLevel());
            SaoLevelLogic.sync(newPlayer);
        });

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            SaoLevelLogic.sync(player);
        });

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof LivingEntity le && SaoEntities.isScalableMob(le)) {
                MobScaling.applyToMob(world, le);
            }
        });

        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % 40 == 0) {
                MobScaling.refreshWorld(world);
            }
        });
    }
}
