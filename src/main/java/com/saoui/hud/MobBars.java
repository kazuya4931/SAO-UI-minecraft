package com.saoui.hud;

import com.saoui.level.SaoEntities;
import com.saoui.render.ArcBar;
import com.saoui.render.Projection;
import com.saoui.render.SaoDraw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.entity.mob.BreezeEntity;
import net.minecraft.entity.mob.CreakingEntity;
import net.minecraft.entity.mob.ElderGuardianEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.mob.GiantEntity;
import net.minecraft.entity.mob.GuardianEntity;
import net.minecraft.entity.mob.HoglinEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.entity.mob.RavagerEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.mob.ZoglinEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.PandaEntity;
import net.minecraft.entity.passive.PolarBearEntity;
import net.minecraft.entity.passive.SnifferEntity;
import net.minecraft.entity.passive.SquidEntity;
import net.minecraft.entity.passive.StriderEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Floating SAO cursors (diamond) and curved HP gauges hovering beside mobs / players.
 * Non-boss HP bars appear only when attacked and auto-hide after 10 seconds without being hit;
 * boss-tier monsters always display their gauges. Large-model mobs push gauges outward to clear the 3D model.
 */
public final class MobBars {
    private MobBars() {}

    private static final double RANGE = 32.0;
    private static final double BOSS_RANGE = 64.0;
    private static final long HIT_VISIBLE_MS = 10_000L;

    public static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        PlayerEntity self = mc.player;
        Entity camEntity = mc.getCameraEntity();
        Vec3d cam = Projection.cameraPos();

        Entity crosshairTarget = mc.crosshairTarget instanceof EntityHitResult ehr ? ehr.getEntity() : null;

        List<LivingEntity> list = new ArrayList<>();
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le)) continue;
            if (e == camEntity && mc.options.getPerspective().isFirstPerson()) continue;
            if (e == self && mc.options.getPerspective().isFirstPerson()) continue;
            if (e instanceof ArmorStandEntity) continue;
            if (!le.isAlive() || le.isDead()) continue;
            if (e.isInvisibleTo(self)) continue;
            double maxRange = SaoEntities.isBoss(le) ? BOSS_RANGE : RANGE;
            if (e.squaredDistanceTo(cam) > maxRange * maxRange) continue;
            if (e != self && !self.canSee(e)) continue;
            list.add(le);
        }
        list.sort((a, b) -> Double.compare(b.squaredDistanceTo(cam), a.squaredDistanceTo(cam)));

        long now = System.currentTimeMillis();
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        for (LivingEntity le : list) {
            Vec3d p = le.getLerpedPos(tickDelta);
            float[] head = Projection.project(p.x, p.y + le.getHeight() + 0.3, p.z);
            if (head == null) continue;
            if (head[0] < -150 || head[0] > sw + 150 || head[1] < -150 || head[1] > sh + 150) continue;

            double dist = Math.sqrt(le.squaredDistanceTo(cam));
            float max = Math.max(1f, le.getMaxHealth());
            float frac = MathHelper.clamp(le.getHealth() / max, 0f, 1f);
            HealthTracker.State st = HealthTracker.update(le.getUuid(), frac);

            // Detect attacks via hurtTime spike in addition to HP drop
            if (le.hurtTime > st.lastHurtTime) {
                st.lastHitMs = now;
            }
            st.lastHurtTime = le.hurtTime;

            boolean boss = SaoEntities.isBoss(le);
            boolean targeted = le == crosshairTarget;
            long sinceHit = now - st.lastHitMs;
            boolean recentlyHit = st.lastHitMs > 0 && sinceHit < HIT_VISIBLE_MS;
            boolean showBar = boss || recentlyHit;

            double maxRange = boss ? BOSS_RANGE : RANGE;
            float fade = MathHelper.clamp((float) ((maxRange - dist) / 6.0), 0f, 1f);

            if (showBar) {
                float hitFade = boss ? 1f : MathHelper.clamp((HIT_VISIBLE_MS - sinceHit) / 500f, 0f, 1f);
                drawGauge(ctx, le, p, cam, st, frac, boss, targeted, fade * hitFade);
            }

            // SAO cursor: floating, slowly bobbing crystal above the head
            float scale = MathHelper.clamp((float) (7.0 / Math.max(dist, 0.1)), 0.45f, 1.0f);
            int cursor = cursorColor(le, self);
            float bob = (float) Math.sin((now + le.getId() * 337L) / 400.0) * 1.5f;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(head[0], head[1], 0);
            ctx.getMatrices().scale(scale, scale, 1f);
            float cy = -10 + bob;
            SaoDraw.diamond(ctx, 0, cy, 5, 10, SaoDraw.withAlpha(0xFF000000, 0.3f * fade));
            SaoDraw.diamond(ctx, 0, cy, 4, 9, SaoDraw.withAlpha(cursor, fade));
            SaoDraw.quad(ctx, 0, cy - 9, -4, cy, 0, cy, 0, cy - 9, SaoDraw.withAlpha(0xFFFFFFFF, 0.4f * fade));
            SaoDraw.quad(ctx, 0, cy, 0, cy + 9, 4, cy, 4, cy, SaoDraw.withAlpha(0xFF000000, 0.2f * fade));
            ctx.getMatrices().pop();
        }
    }

    /**
     * Computes {radius, yTopOffset, startDeg} based on the entity's actual rendered 3D model size
     * (wings, heads, shoulders, tentacles, splayed legs, long bodies) so the gauge clears the model.
     */
    private static double[] visualGeometry(LivingEntity le, boolean boss) {
        double w = le.getWidth(), h = le.getHeight();
        // Use cube half-diagonal (~0.72 * w) as baseline visual radius instead of hitbox half-width
        double radius = w * 0.72 + (boss ? 1.1 : 0.65);
        double yTopOff = h * 0.92;
        double startDeg = 24.0;

        if (le instanceof EnderDragonEntity) {
            radius = Math.max(radius, 8.5);
            yTopOff = h + 1.8;
            startDeg = 42.0;
        } else if (le instanceof WitherEntity) {
            radius = Math.max(radius, 2.3);
            yTopOff = h + 0.35;
            startDeg = 36.0;
        } else if (le instanceof WardenEntity) {
            radius = Math.max(radius, 2.1);
            yTopOff = h + 0.25;
            startDeg = 34.0;
        } else if (le instanceof GhastEntity) {
            radius = Math.max(radius, 3.8);
            yTopOff = h * 0.95;
            startDeg = 38.0;
        } else if (le instanceof ElderGuardianEntity) {
            radius = Math.max(radius, 2.9);
            yTopOff = h + 0.2;
            startDeg = 36.0;
        } else if (le instanceof GiantEntity) {
            radius = Math.max(radius, 4.5);
            yTopOff = h * 0.95;
            startDeg = 38.0;
        } else if (le instanceof RavagerEntity) {
            radius = Math.max(radius, 2.7);
            yTopOff = h + 0.25;
            startDeg = 35.0;
        } else if (le instanceof SnifferEntity) {
            radius = Math.max(radius, 2.6);
            yTopOff = h + 0.2;
            startDeg = 34.0;
        } else if (le instanceof IronGolemEntity) {
            radius = Math.max(radius, 2.0);
            yTopOff = h + 0.15;
            startDeg = 32.0;
        } else if (le instanceof PhantomEntity) {
            radius = Math.max(radius, 2.1);
            startDeg = 34.0;
        } else if (le instanceof GuardianEntity) {
            radius = Math.max(radius, 1.6);
            startDeg = 30.0;
        } else if (le instanceof BlazeEntity || le instanceof BreezeEntity) {
            radius = Math.max(radius, 1.65);
            startDeg = 30.0;
        } else if (le instanceof SquidEntity) {
            radius = Math.max(radius, 1.8);
            startDeg = 30.0;
        } else if (le instanceof SpiderEntity) {
            radius = Math.max(radius, w * 0.95 + 0.9);
            startDeg = 30.0;
        } else if (le instanceof SlimeEntity) {
            radius = Math.max(radius, w * 0.9 + 0.8);
            startDeg = 28.0;
        } else if (le instanceof AbstractHorseEntity || le instanceof PolarBearEntity
                || le instanceof PandaEntity || le instanceof HoglinEntity
                || le instanceof ZoglinEntity || le instanceof StriderEntity) {
            radius = Math.max(radius, w * 1.0 + 1.0);
            startDeg = 32.0;
        } else if (le instanceof EndermanEntity || le instanceof CreakingEntity) {
            radius = Math.max(radius, 1.45);
            startDeg = 28.0;
        } else if (w >= 1.3 || h >= 2.5) {
            radius = Math.max(radius, w * 0.95 + 0.95);
            startDeg = 30.0;
        }

        return new double[]{radius, yTopOff, startDeg};
    }

    private static void drawGauge(DrawContext ctx, LivingEntity le, Vec3d p, Vec3d cam, HealthTracker.State st,
                                  float frac, boolean boss, boolean targeted, float fade) {
        double h = le.getHeight();
        double[] geom = visualGeometry(le, boss);
        double radius = geom[0];
        double yTop = p.y + geom[1];
        float start = (float) geom[2];
        double hFull = MathHelper.clamp(h * 0.075, 0.09, boss ? 0.45 : 0.22);
        double hThin = hFull * 0.62;
        Vec3d center = new Vec3d(p.x, p.y, p.z);
        float span = boss ? 100f : 90f;
        int outline = targeted ? 0xFFFFFFFF : 0xFFB9C6CC;

        if (boss) {
            // stacked gauges: the top one is the gauge being depleted, the ones below are full reserves
            int n = BossHud.SEGMENTS;
            int seg = Math.max(1, (int) Math.ceil(frac * n - 1e-4));
            float base = (seg - 1) / (float) n;
            float segDisp = MathHelper.clamp((st.display - base) * n, 0, 1);
            float segLag = MathHelper.clamp((st.lag - base) * n, 0, 1);
            double gap = hFull * 1.7;
            for (int k = seg - 1; k >= 0; k--) {
                ArcBar.Geo g = ArcBar.build(center, cam, radius, yTop - k * gap, hFull, hThin, start, span);
                if (k == 0) ArcBar.draw(ctx, g, segDisp, segLag, frac, outline, fade);
                else ArcBar.draw(ctx, g, 1, 1, 1, outline, fade);
            }
        } else {
            ArcBar.Geo g = ArcBar.build(center, cam, radius, yTop, hFull, hThin, start, span);
            ArcBar.draw(ctx, g, st.display, st.lag, frac, outline, fade);
        }

        // name label above the start of the ribbon
        ArcBar.Geo g0 = ArcBar.build(center, cam, radius, yTop, hFull, hThin, start, span);
        float[] s = ArcBar.screenAt(g0, 0.02, hFull * 0.4);
        if (s == null) return;
        double dist = Math.sqrt(le.squaredDistanceTo(cam));
        float ts = MathHelper.clamp((float) (6.0 / Math.max(dist, 0.1)), 0.45f, 0.9f);
        Text name = le.getDisplayName();
        int textAlpha = SaoDraw.withAlpha(0xFFFFFFFF, Math.max(0.1f, fade));
        SaoDraw.textScaled(ctx, name, s[0], s[1] - 9 * ts, ts, textAlpha, true);

        String hp = (int) Math.ceil(le.getHealth()) + "/" + (int) Math.ceil(le.getMaxHealth());
        float[] e = ArcBar.screenAt(g0, 0.6, -hFull * 1.3);
        if (e != null) {
            SaoDraw.textScaled(ctx, Text.literal(hp), e[0], e[1], ts * 0.75f,
                    SaoDraw.withAlpha(0xFFE8E8E8, Math.max(0.1f, fade)), true);
        }
    }

    public static int cursorColor(LivingEntity le, PlayerEntity self) {
        if (le instanceof PlayerEntity) return SaoDraw.CURSOR_GREEN;
        if (le instanceof Monster) return SaoDraw.CURSOR_RED;
        if (le instanceof MobEntity mob && mob.getTarget() == self) return SaoDraw.CURSOR_RED;
        if (le instanceof Angerable ang && ang.getAngryAt() != null) return SaoDraw.CURSOR_RED;
        return SaoDraw.CURSOR_YELLOW;
    }
}
