package com.saoui.render;

import net.minecraft.client.gui.DrawContext;

/**
 * The characteristic SAO HP bar: full height on the left, steps down thinner on the right, slanted end.
 * Styled to match the anime's clean matte-luminous holographic look (thin dark hairline border,
 * translucent smoked track, subtle bottom-lit flat gradient, no glossy plastic highlight).
 */
public final class SaoBar {
    private SaoBar() {}

    public static int fillTop(float frac) {
        if (frac > 0.5f) return 0xFF66AC1E;
        if (frac > 0.2f) return 0xFFD4AA1C;
        return 0xFFC4242C;
    }

    public static int fillBottom(float frac) {
        if (frac > 0.5f) return 0xFF84D42E;
        if (frac > 0.2f) return 0xFFF2D438;
        return 0xFFE8464E;
    }

    public static void stepped(DrawContext ctx, float x, float y, float w, float h, float thinH, float stepAt,
                               float slant, float display, float lag, float frac, int outline, float alpha) {
        float xStep = x + w * stepAt;
        float diag = (h - thinH) * 0.95f;
        float[] xs = {x, xStep, xStep + diag, x + w - slant, x + w};
        float[] tops = {y, y, y, y, y};
        float[] bots = {y + h, y + h, y + thinH, y + thinH, y};

        // Faint outer pale rim (subtle readability on dark scenes, never thick white plastic)
        float b1 = 1.4f;
        float[] rxs = {x - b1, xStep + 0.6f, xStep + diag + 0.6f, x + w - slant + 0.7f, x + w + b1 + 0.5f};
        float[] rtops = {y - b1, y - b1, y - b1, y - b1, y - b1};
        float[] rbots = {y + h + b1, y + h + b1, y + thinH + b1, y + thinH + b1, y - b1};
        SaoDraw.profile(ctx, rxs, rtops, rbots, -1e9f, 1e9f, SaoDraw.withAlpha(outline, alpha * 0.28f));

        // Crisp dark charcoal hairline border (1px)
        float b = 0.9f;
        float[] oxs = {x - b, xStep + 0.35f, xStep + diag + 0.35f, x + w - slant + 0.45f, x + w + b + 0.4f};
        float[] otops = {y - b, y - b, y - b, y - b, y - b};
        float[] obots = {y + h + b, y + h + b, y + thinH + b, y + thinH + b, y - b};
        SaoDraw.profile(ctx, oxs, otops, obots, -1e9f, 1e9f, SaoDraw.withAlpha(0xCC2C3236, alpha));

        // Translucent smoked-grey empty track
        SaoDraw.profileGradient(ctx, xs, tops, bots, -1e9f, 1e9f,
                SaoDraw.withAlpha(0x882A2E32, alpha), SaoDraw.withAlpha(0x703E4246, alpha));

        float fx = x + w * clamp(display);
        float lx = x + w * clamp(lag);
        if (lx > fx) {
            SaoDraw.profile(ctx, xs, tops, bots, fx, lx, SaoDraw.withAlpha(0xD8FFF0B8, alpha));
        }

        float colFrac = clamp(frac);
        float a = alpha;
        if (colFrac <= 0.2f && colFrac > 0f) {
            a *= 0.78f + 0.22f * (float) Math.sin(System.currentTimeMillis() / 130.0);
        }
        int top = fillTop(colFrac), bot = fillBottom(colFrac);
        SaoDraw.profileGradient(ctx, xs, tops, bots, x, fx, SaoDraw.withAlpha(top, a), SaoDraw.withAlpha(bot, a));
    }

    /** Thin parallelogram gauge (matte anime style, used for secondary gauges like hunger/air). */
    public static void slim(DrawContext ctx, float x, float y, float w, float h, float slant,
                            float display, float lag, int top, int bottom, float alpha) {
        float b = 0.8f;
        float[] oxs = {x - b, x + w - slant + 0.35f, x + w + b + 0.35f};
        float[] otop = {y - b, y - b, y - b};
        float[] obot = {y + h + b, y + h + b, y - b};
        SaoDraw.profile(ctx, oxs, otop, obot, -1e9f, 1e9f, SaoDraw.withAlpha(0xC82C3236, alpha));

        float[] xs = {x, x + w - slant, x + w};
        float[] tops = {y, y, y};
        float[] bots = {y + h, y + h, y};
        SaoDraw.profile(ctx, xs, tops, bots, -1e9f, 1e9f, SaoDraw.withAlpha(0x782A2E32, alpha));

        float fx = x + w * clamp(display), lx = x + w * clamp(lag);
        if (lx > fx) {
            SaoDraw.profile(ctx, xs, tops, bots, fx, lx, SaoDraw.withAlpha(0xC8FFFFFF, alpha));
        }
        SaoDraw.profileGradient(ctx, xs, tops, bots, x, fx, SaoDraw.withAlpha(top, alpha), SaoDraw.withAlpha(bottom, alpha));
    }

    private static float clamp(float f) {
        return Math.max(0f, Math.min(1f, f));
    }
}
