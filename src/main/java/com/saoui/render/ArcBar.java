package com.saoui.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * SAO floating curved HP gauge: a ribbon that wraps part-way around the mob on a horizontal
 * circle (like the anime's bars that hover beside monsters and curl away at the end).
 * Built in world space and projected to the screen so it gets real perspective.
 */
public final class ArcBar {
    private ArcBar() {}

    private static final int SEGMENTS = 28;

    public static final class Geo {
        Vec3d center;      // circle centre (x,z) ; y unused
        Vec3d toCam, right;
        double radius, yTop, hFull, hThin;
        float startDeg, spanDeg, stepAt = 0.55f;

        Vec3d point(double u, double y) {
            double th = Math.toRadians(startDeg + spanDeg * u);
            double c = Math.cos(th) * radius, s = Math.sin(th) * radius;
            return new Vec3d(center.x + toCam.x * c + right.x * s, y, center.z + toCam.z * c + right.z * s);
        }

        /** band thickness at u : full -> step -> thin -> slanted end. */
        double height(double u) {
            double h;
            if (u < stepAt) h = hFull;
            else if (u < stepAt + 0.06) h = MathHelper.lerp((u - stepAt) / 0.06, hFull, hThin);
            else h = hThin;
            double endCut = 0.07;
            if (u > 1 - endCut) h *= (1 - u) / endCut;
            return h;
        }
    }

    /**
     * @param camPos camera position
     * @return screen position of the ribbon start (top) for labels, or null
     */
    public static Geo build(Vec3d mobCenter, Vec3d camPos, double radius, double yTop, double hFull, double hThin,
                            float startDeg, float spanDeg) {
        Vec3d d = new Vec3d(camPos.x - mobCenter.x, 0, camPos.z - mobCenter.z);
        if (d.lengthSquared() < 1e-6) d = new Vec3d(1, 0, 0);
        d = d.normalize();
        Vec3d r = new Vec3d(d.z, 0, -d.x);
        // make sure "right" is screen-right
        float[] p0 = Projection.project(mobCenter.x, yTop, mobCenter.z);
        float[] p1 = Projection.project(mobCenter.x + r.x, yTop, mobCenter.z + r.z);
        if (p0 != null && p1 != null && p1[0] < p0[0]) r = r.multiply(-1);
        Geo g = new Geo();
        g.center = mobCenter;
        g.toCam = d;
        g.right = r;
        g.radius = radius;
        g.yTop = yTop;
        g.hFull = hFull;
        g.hThin = hThin;
        g.startDeg = startDeg;
        g.spanDeg = spanDeg;
        return g;
    }

    public static float[] screenAt(Geo g, double u, double yOff) {
        Vec3d p = g.point(u, g.yTop + yOff);
        return Projection.project(p.x, p.y, p.z);
    }

    /** Draw a band region [u0,u1] with top offset/bottom offset (world units) and vertical gradient. */
    private static void band(DrawContext ctx, Geo g, double u0, double u1, double grow, double uGrow,
                             boolean highlightOnly, int top, int bottom) {
        if (u1 <= u0) return;
        for (int i = 0; i < SEGMENTS; i++) {
            double a = (double) i / SEGMENTS, b = (double) (i + 1) / SEGMENTS;
            double s0 = Math.max(a, u0), s1 = Math.min(b, u1);
            if (s1 <= s0) continue;
            double sa = s0 - (s0 == u0 ? uGrow : 0), sb = s1 + (s1 == u1 ? uGrow : 0);
            double h0 = g.height(MathHelper.clamp(s0, 0, 1)), h1 = g.height(MathHelper.clamp(s1, 0, 1));
            double t0 = g.yTop + grow, t1 = g.yTop + grow;
            double b0 = highlightOnly ? g.yTop - h0 * 0.35 : g.yTop - h0 - grow;
            double b1 = highlightOnly ? g.yTop - h1 * 0.35 : g.yTop - h1 - grow;
            Vec3d pt0 = g.point(sa, t0), pb0 = g.point(sa, b0), pb1 = g.point(sb, b1), pt1 = g.point(sb, t1);
            float[] q1 = Projection.project(pt0.x, pt0.y, pt0.z);
            float[] q2 = Projection.project(pb0.x, pb0.y, pb0.z);
            float[] q3 = Projection.project(pb1.x, pb1.y, pb1.z);
            float[] q4 = Projection.project(pt1.x, pt1.y, pt1.z);
            if (q1 == null || q2 == null || q3 == null || q4 == null) continue;
            SaoDraw.quadC(ctx, q1[0], q1[1], q2[0], q2[1], q3[0], q3[1], q4[0], q4[1], top, bottom, bottom, top);
        }
    }

    public static void draw(DrawContext ctx, Geo g, float display, float lag, float frac, int outline, float alpha) {
        double e = g.hFull * 0.14;
        // outline / rim
        band(ctx, g, 0, 1, e, 0.012, false, SaoDraw.withAlpha(0xFFFFFFFF, alpha), SaoDraw.withAlpha(outline, alpha));
        // dark track
        band(ctx, g, 0, 1, 0, 0, false, SaoDraw.withAlpha(0xE0181A1C, alpha), SaoDraw.withAlpha(0xD0464A4E, alpha));
        float d = MathHelper.clamp(display, 0, 1), l = MathHelper.clamp(lag, 0, 1), f = MathHelper.clamp(frac, 0, 1);
        if (l > d) band(ctx, g, d, l, 0, 0, false, SaoDraw.withAlpha(0xFFFFFFFF, alpha * 0.9f), SaoDraw.withAlpha(0xFFFF9A9A, alpha * 0.8f));
        float a = alpha;
        if (f <= 0.2f && f > 0) a *= 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 120.0);
        band(ctx, g, 0, d, 0, 0, false, SaoDraw.withAlpha(SaoBar.fillTop(f), a), SaoDraw.withAlpha(SaoBar.fillBottom(f), a));
        // gloss
        band(ctx, g, 0, d, 0, 0, true, SaoDraw.withAlpha(0xFFFFFFFF, a * 0.5f), SaoDraw.withAlpha(0xFFFFFFFF, a * 0.08f));
    }
}
