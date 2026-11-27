package com.saoui.hud;

import com.saoui.level.SaoEntities;
import com.saoui.mixin.BossBarHudAccessor;
import com.saoui.render.SaoBar;
import com.saoui.render.SaoDraw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** SAO floor boss style bars: name + main stepped bar + stacked reserve bars (multiple HP gauges). */
public final class BossHud {
    private BossHud() {}

    public static final int SEGMENTS = 3;
    private static final double BOSS_RANGE = 48;

    private record Entry(Object key, Text name, float frac, String hpText) {}

    public static boolean isBoss(LivingEntity le) {
        return SaoEntities.isBoss(le);
    }

    public static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        List<Entry> entries = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (ClientBossBar bar : ((BossBarHudAccessor) mc.inGameHud.getBossBarHud()).saoui$getBossBars().values()) {
            entries.add(new Entry(bar.getUuid(), bar.getName(), bar.getPercent(), null));
            names.add(bar.getName().getString());
        }
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || !le.isAlive() || !isBoss(le)) continue;
            if (le.squaredDistanceTo(mc.player) > BOSS_RANGE * BOSS_RANGE) continue;
            if (names.contains(le.getDisplayName().getString())) continue;
            float max = Math.max(1f, le.getMaxHealth());
            entries.add(new Entry(le.getUuid() + "#hud", le.getDisplayName(),
                    MathHelper.clamp(le.getHealth() / max, 0, 1),
                    (int) Math.ceil(le.getHealth()) + " / " + (int) Math.ceil(max)));
        }

        int sw = ctx.getScaledWindowWidth();
        float y = 8;
        for (Entry en : entries) {
            if (y > ctx.getScaledWindowHeight() / 3f) break;
            drawBoss(ctx, en, sw / 2f, y);
            y += 36;
        }
    }

    private static void drawBoss(DrawContext ctx, Entry en, float cx, float y) {
        HealthTracker.State st = HealthTracker.update(en.key, en.frac);
        float frac = en.frac;

        int seg = Math.max(1, (int) Math.ceil(frac * SEGMENTS - 1e-4)); // current gauge (1..N)
        float base = (seg - 1) / (float) SEGMENTS;
        float segDisp = MathHelper.clamp((st.display - base) * SEGMENTS, 0, 1);
        float segLag = MathHelper.clamp((st.lag - base) * SEGMENTS, 0, 1);
        float segFrac = MathHelper.clamp((frac - base) * SEGMENTS, 0, 1);
        if (frac <= 0) { segDisp = segFrac = 0; }

        float bw = 200, bx = cx - bw / 2f - 10;

        // name with red cursor diamond
        Text name = en.name;
        float nw = SaoDraw.font().getWidth(name) * 1.1f;
        SaoDraw.diamond(ctx, cx - nw / 2f - 9, y + 4.5f, 3.5f, 6, SaoDraw.CURSOR_RED);
        SaoDraw.textScaled(ctx, name, cx - nw / 2f, y, 1.1f, 0xFFFFFFFF, true);

        // backing panel
        float py = y + 12;
        SaoDraw.para(ctx, bx - 8, py - 2, bw + 16, 15, 4, 0x70000000);

        // main gauge: colour reflects the overall HP
        SaoBar.stepped(ctx, bx, py + 1, bw, 9, 5, 0.7f, 5, segDisp, segLag, frac, 0xFFFFD0D0, 1f);

        // stacked reserve gauges on the right (like the floor boss in the anime)
        float rx = bx + bw + 8;
        for (int i = 0; i < SEGMENTS - 1; i++) {
            float gy = py + 10 - i * 4.5f;
            float gw = 16 - i * 2;
            float gx = rx + i * 2.5f;
            boolean full = i < seg - 1;
            int c = full ? SaoDraw.hpColor(frac) : 0x90303030;
            SaoDraw.para(ctx, gx - 0.8f, gy - 0.8f, gw + 1.6f, 4.6f, 2, full ? 0xFFE9F5B0 : 0x80000000);
            SaoDraw.para(ctx, gx, gy, gw, 3, 2, c);
        }

        String info = en.hpText != null ? en.hpText : (int) Math.ceil(frac * 100) + "%";
        info = info + "  \u00d7" + seg;
        float iw = SaoDraw.font().getWidth(info) * 0.75f;
        SaoDraw.textScaled(ctx, Text.literal(info), bx + bw - iw, py + 13, 0.75f, 0xFFE8E8E8, true);
    }
}
