package com.saoui;

import com.saoui.fx.ShatterEffect;
import com.saoui.hud.BossHud;
import com.saoui.hud.HealthTracker;
import com.saoui.hud.MobBars;
import com.saoui.hud.PlayerHud;
import com.saoui.level.SaoLevelClient;
import com.saoui.level.SaoLevelPayload;
import com.saoui.render.Projection;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

public class SaoUiClient implements ClientModInitializer {
    public static final String MOD_ID = "saoui";

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(SaoLevelPayload.ID, (payload, context) ->
                context.client().execute(() -> SaoLevelClient.update(payload.level(), payload.xp())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SaoLevelClient.reset());

        // capture camera matrices so HUD code can project world positions
        WorldRenderEvents.LAST.register(ctx ->
                Projection.capture(ctx.positionMatrix(), ctx.projectionMatrix(), ctx.camera().getPos()));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ShatterEffect.tick(client);
            if (client.world != null && client.world.getTime() % 100 == 0) HealthTracker.cleanup();
        });

        HudLayerRegistrationCallback.EVENT.register(drawer -> {
            // world-anchored overlays (cursors, mob HP bars, shatter shards) sit under the rest of the HUD
            drawer.attachLayerAfter(IdentifiedLayer.MISC_OVERLAYS, Identifier.of(MOD_ID, "world_overlay"), (ctx, tick) -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.options.hudHidden) return;
                float td = tick.getTickDelta(false);
                ShatterEffect.render(ctx, td);
                MobBars.render(ctx, td);
            });
            drawer.attachLayerAfter(IdentifiedLayer.HOTBAR_AND_BARS, Identifier.of(MOD_ID, "player_hud"), (ctx, tick) -> {
                if (MinecraftClient.getInstance().options.hudHidden) return;
                PlayerHud.render(ctx, tick.getTickDelta(false));
            });
            drawer.replaceLayer(IdentifiedLayer.BOSS_BAR, old -> IdentifiedLayer.of(IdentifiedLayer.BOSS_BAR, (ctx, tick) -> {
                if (MinecraftClient.getInstance().options.hudHidden) return;
                BossHud.render(ctx, tick.getTickDelta(false));
            }));
        });
    }
}
