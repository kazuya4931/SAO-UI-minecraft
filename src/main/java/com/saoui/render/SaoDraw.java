package com.saoui.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

/**
 * Low level 2D drawing helpers: arbitrary quads / polygons so we can draw the
 * slanted, stepped SAO style bars instead of axis aligned rectangles only.
 */
public final class SaoDraw {
    private SaoDraw() {}

    // ---- SAO palette ----
    public static final int HP_GREEN = 0xFF8EDB2C;
    public static final int HP_GREEN_HI = 0xFFC3F27A;
    public static final int HP_YELLOW = 0xFFF2D33A;
    public static final int HP_YELLOW_HI = 0xFFFFEE95;
    public static final int HP_RED = 0xFFE8343A;
    public static final int HP_RED_HI = 0xFFFF8A8E;
    public static final int OUTLINE = 0xFFE9F5B0;
    public static final int BAR_BG = 0xD0202224;
    public static final int PANEL = 0xA82E3033;
    public static final int PANEL_DARK = 0xC01C1D1F;
    public static final int PANEL_LINE = 0xFF6C6F73;
    public static final int TEXT = 0xFFF2F2F2;
    public static final int SAO_ORANGE = 0xFFF5A623;
    public static final int CURSOR_RED = 0xFFF0303A;
    public static final int CURSOR_GREEN = 0xFF4FE04A;
    public static final int CURSOR_YELLOW = 0xFFF5D020;

    public static int hpColor(float frac) {
        if (frac > 0.5f) return HP_GREEN;
        if (frac > 0.2f) return HP_YELLOW;
        return HP_RED;
    }

    public static int hpHighlight(float frac) {
        if (frac > 0.5f) return HP_GREEN_HI;
        if (frac > 0.2f) return HP_YELLOW_HI;
        return HP_RED_HI;
    }

    public static int withAlpha(int argb, float alpha) {
        int a = (int) (((argb >>> 24) & 0xFF) * MathHelper.clamp(alpha, 0f, 1f));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static int lerpColor(int a, int b, float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    /** Quad with vertices in order: top-left, bottom-left, bottom-right, top-right. */
    public static void quad(DrawContext ctx, float x1, float y1, float x2, float y2,
                            float x3, float y3, float x4, float y4, int color) {
        if (((color >>> 24) & 0xFF) == 0) return;
        quadC(ctx, x1, y1, x2, y2, x3, y3, x4, y4, color, color, color, color);
    }

    /** Quad with a colour per vertex (for gradients). */
    public static void quadC(DrawContext ctx, float x1, float y1, float x2, float y2,
                             float x3, float y3, float x4, float y4, int c1, int c2, int c3, int c4) {
        // GUI layer back-face culls: make sure winding matches DrawContext.fill (negative shoelace area)
        float area = (x1 * y2 - x2 * y1) + (x2 * y3 - x3 * y2) + (x3 * y4 - x4 * y3) + (x4 * y1 - x1 * y4);
        if (area > 0) {
            float tx = x2, ty = y2;
            x2 = x4; y2 = y4;
            x4 = tx; y4 = ty;
            int tc = c2; c2 = c4; c4 = tc;
        }
        final float fx2 = x2, fy2 = y2, fx4 = x4, fy4 = y4;
        final int fc2 = c2, fc4 = c4;
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        ctx.draw(provider -> {
            VertexConsumer vc = provider.getBuffer(RenderLayer.getGui());
            vc.vertex(m, x1, y1, 0).color(c1);
            vc.vertex(m, fx2, fy2, 0).color(fc2);
            vc.vertex(m, x3, y3, 0).color(c3);
            vc.vertex(m, fx4, fy4, 0).color(fc4);
        });
    }

    /** Like {@link #profile} but with a vertical gradient (top colour -> bottom colour). */
    public static void profileGradient(DrawContext ctx, float[] xs, float[] tops, float[] bottoms,
                                       float clip0, float clip1, int topColor, int bottomColor) {
        if (clip1 <= clip0) return;
        for (int i = 0; i < xs.length - 1; i++) {
            float a = xs[i], b = xs[i + 1];
            if (b <= a) continue;
            float s0 = Math.max(a, clip0), s1 = Math.min(b, clip1);
            if (s1 <= s0) continue;
            float t0 = (s0 - a) / (b - a), t1 = (s1 - a) / (b - a);
            float top0 = MathHelper.lerp(t0, tops[i], tops[i + 1]);
            float top1 = MathHelper.lerp(t1, tops[i], tops[i + 1]);
            float bot0 = MathHelper.lerp(t0, bottoms[i], bottoms[i + 1]);
            float bot1 = MathHelper.lerp(t1, bottoms[i], bottoms[i + 1]);
            quadC(ctx, s0, top0, s0, bot0, s1, bot1, s1, top1, topColor, bottomColor, bottomColor, topColor);
        }
    }

    public static void tri(DrawContext ctx, float x1, float y1, float x2, float y2, float x3, float y3, int color) {
        quad(ctx, x1, y1, x2, y2, x3, y3, x3, y3, color);
    }

    public static void rect(DrawContext ctx, float x, float y, float w, float h, int color) {
        quad(ctx, x, y, x, y + h, x + w, y + h, x + w, y, color);
    }

    /** Parallelogram leaning right: bottom edge shifted left by {@code slant}. */
    public static void para(DrawContext ctx, float x, float y, float w, float h, float slant, int color) {
        quad(ctx, x + slant, y, x, y + h, x + w - slant, y + h, x + w, y, color);
    }

    /**
     * Draws a shape defined by a piecewise linear top and bottom profile, clipped
     * horizontally to [clip0, clip1]. xs must be increasing.
     */
    public static void profile(DrawContext ctx, float[] xs, float[] tops, float[] bottoms,
                               float clip0, float clip1, int color) {
        if (clip1 <= clip0) return;
        for (int i = 0; i < xs.length - 1; i++) {
            float a = xs[i], b = xs[i + 1];
            if (b <= a) continue;
            float s0 = Math.max(a, clip0), s1 = Math.min(b, clip1);
            if (s1 <= s0) continue;
            float t0 = (s0 - a) / (b - a), t1 = (s1 - a) / (b - a);
            float top0 = MathHelper.lerp(t0, tops[i], tops[i + 1]);
            float top1 = MathHelper.lerp(t1, tops[i], tops[i + 1]);
            float bot0 = MathHelper.lerp(t0, bottoms[i], bottoms[i + 1]);
            float bot1 = MathHelper.lerp(t1, bottoms[i], bottoms[i + 1]);
            quad(ctx, s0, top0, s0, bot0, s1, bot1, s1, top1, color);
        }
    }

    public static void circle(DrawContext ctx, float cx, float cy, float r, int color) {
        int seg = Math.max(16, (int) (r * 2.5f));
        for (int i = 0; i < seg; i += 2) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg, a2 = Math.PI * 2 * (i + 2) / seg;
            quad(ctx, cx, cy,
                    cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r,
                    cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r,
                    cx + (float) Math.cos(a2) * r, cy + (float) Math.sin(a2) * r, color);
        }
    }

    public static void ring(DrawContext ctx, float cx, float cy, float r, float thickness, int color) {
        int seg = Math.max(20, (int) (r * 3f));
        float ri = r - thickness;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            quad(ctx, cx + c0 * r, cy + s0 * r, cx + c0 * ri, cy + s0 * ri, cx + c1 * ri, cy + s1 * ri, cx + c1 * r, cy + s1 * r, color);
        }
    }

    /** Diamond (SAO cursor) centred at cx, with half width w and half height h. */
    public static void diamond(DrawContext ctx, float cx, float cy, float w, float h, int color) {
        quad(ctx, cx, cy - h, cx - w, cy, cx, cy + h, cx + w, cy, color);
    }

    /** Rounded rectangle approximation (chamfered corners). */
    public static void roundRect(DrawContext ctx, float x, float y, float w, float h, float r, int color) {
        r = Math.min(r, Math.min(w, h) / 2f);
        rect(ctx, x + r, y, w - 2 * r, h, color);
        rect(ctx, x, y + r, r, h - 2 * r, color);
        rect(ctx, x + w - r, y + r, r, h - 2 * r, color);
        corner(ctx, x + r, y + r, r, Math.PI, color);
        corner(ctx, x + w - r, y + r, r, Math.PI * 1.5, color);
        corner(ctx, x + w - r, y + h - r, r, 0, color);
        corner(ctx, x + r, y + h - r, r, Math.PI * 0.5, color);
    }

    private static void corner(DrawContext ctx, float cx, float cy, float r, double start, int color) {
        int seg = 4;
        for (int i = 0; i < seg; i += 2) {
            double a0 = start + Math.PI / 2 * i / seg, a1 = start + Math.PI / 2 * (i + 1) / seg, a2 = start + Math.PI / 2 * (i + 2) / seg;
            quad(ctx, cx, cy,
                    cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r,
                    cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r,
                    cx + (float) Math.cos(a2) * r, cy + (float) Math.sin(a2) * r, color);
        }
    }

    public static TextRenderer font() {
        return MinecraftClient.getInstance().textRenderer;
    }

    public static void text(DrawContext ctx, Text t, float x, float y, int color, boolean shadow) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.drawText(font(), t, 0, 0, color, shadow);
        ctx.getMatrices().pop();
    }

    public static void text(DrawContext ctx, String s, float x, float y, int color, boolean shadow) {
        text(ctx, Text.literal(s), x, y, color, shadow);
    }

    public static void textScaled(DrawContext ctx, Text t, float x, float y, float scale, int color, boolean shadow) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(scale, scale, 1f);
        ctx.drawText(font(), t, 0, 0, color, shadow);
        ctx.getMatrices().pop();
    }

    public static void textCentered(DrawContext ctx, Text t, float cx, float y, float scale, int color, boolean shadow) {
        float w = font().getWidth(t) * scale;
        textScaled(ctx, t, cx - w / 2f, y, scale, color, shadow);
    }
}
