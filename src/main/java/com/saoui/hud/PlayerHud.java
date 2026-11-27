package com.saoui.hud;

import com.saoui.level.SaoLevelClient;
import com.saoui.render.SaoBar;
import com.saoui.render.SaoDraw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Top-left SAO anime-style first-person player HUD:
 * [ Name [=====stepped HP bar=====]  [status tiles...]
 * [ [=== hunger ===]  HP / MAX  LV
 */
public final class PlayerHud {
    private PlayerHud() {}

    private static final Map<RegistryEntry<StatusEffect>, Long> EFFECT_SEEN = new HashMap<>();

    public static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null || !mc.interactionManager.hasStatusBars()) return;

        float max = Math.max(1f, p.getMaxHealth());
        float hp = MathHelper.clamp(p.getHealth(), 0f, max);
        float frac = hp / max;
        HealthTracker.State st = HealthTracker.update("player_hud", frac);

        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        float bracketW = 15f;
        float gap = 2f;
        float W = 231f, H = 30f;
        // Match SAO anime first-person HUD proportion (~32.3% screen width, ~7.3% screen height)
        float scale = Math.min((sw * 0.323f) / (bracketW + gap + W), (sh * 0.073f) / H);
        scale = MathHelper.clamp(scale, 0.35f, 1.5f);

        float ox = 0f, oy = 0f;

        // hurt shake
        long sinceHit = System.currentTimeMillis() - st.lastHitMs;
        if (sinceHit < 250) {
            float k = 1f - sinceHit / 250f;
            ox += (float) Math.sin(sinceHit * 0.25) * 2f * k;
        }

        ctx.getMatrices().push();
        ctx.getMatrices().translate(6f, 6f, 0f);
        ctx.getMatrices().scale(scale, scale, 1f);

        // --- Unified light frosted silver-white translucent backdrop ---
        int frostLeft = 0x6EEAECEF;
        int frostFade = 0x10EAECEF;

        // Left C-shaped frosted bracket (matching the anime's `[` shape)
        drawFrostedBracket(ctx, ox, oy, bracketW, H, 8f, 9f, frostLeft);

        // Main panel: light frosted silver-white fading smoothly left -> right
        float px = ox + bracketW + gap;
        SaoDraw.quadC(ctx, px, oy, px, oy + H, px + W, oy + H, px + W, oy,
                frostLeft, frostLeft, frostFade, frostFade);

        // Player name (clean crisp white, vertically centered)
        Text name = p.getName();
        int nw = Math.max(32, SaoDraw.font().getWidth(name));
        SaoDraw.text(ctx, name, px + 7f, oy + (H - 8f) / 2f, 0xFFF8F8F8, true);

        // --- Anime stepped HP bar (stepAt ~0.52, thin tail = 50% height, matte-luminous fill) ---
        float bx = px + 7f + nw + 7f;
        float bEnd = px + W - 8f;
        float bw = Math.max(60f, bEnd - bx);
        float barH = 10.5f;
        float thinH = 5.5f;
        float barY = oy + 6f;
        SaoBar.stepped(ctx, bx, barY, bw, barH, thinH, 0.52f, 4.5f, st.display, st.lag, frac, 0xFFE0E6EA, 1f);

        // Absorption shown as crisp golden hairline along the top edge
        float abs = p.getAbsorptionAmount();
        if (abs > 0) {
            float af = Math.min(1f, abs / max);
            SaoDraw.rect(ctx, bx, barY - 2f, bw * af, 1.3f, 0xFFEEC43A);
        }

        // --- HP / MAX | LV: X box (same light frosted silver-white translucent color as the main frame) ---
        String hpStr = (int) Math.ceil(hp) + " / " + (int) Math.ceil(max);
        if (abs > 0) hpStr += " +" + (int) Math.ceil(abs);
        String lvStr = "LV: " + SaoLevelClient.getDisplayLevel(p);
        float infoScale = 0.78f;
        float hpW = SaoDraw.font().getWidth(hpStr) * infoScale;
        float lvW = SaoDraw.font().getWidth(lvStr) * infoScale;
        float hpCellW = hpW + 12f;
        float lvCellW = lvW + 12f;
        float boxW = hpCellW + 1f + lvCellW;
        float boxH = 13f;
        float boxX = bx + bw - boxW;
        float boxY = oy + 18.5f;

        // Matching light frosted silver-white translucent panel + subtle vertical divider
        SaoDraw.roundRect(ctx, boxX, boxY, boxW, boxH, 2.0f, frostLeft);
        SaoDraw.rect(ctx, boxX + hpCellW, boxY + 2f, 1f, boxH - 4f, 0x55FFFFFF);
        float textY = boxY + (boxH - 7f * infoScale) / 2f;
        SaoDraw.textScaled(ctx, Text.literal(hpStr), boxX + (hpCellW - hpW) / 2f, textY, infoScale, 0xFFF8F8F8, true);
        SaoDraw.textScaled(ctx, Text.literal(lvStr), boxX + hpCellW + 1f + (lvCellW - lvW) / 2f, textY, infoScale, 0xFFF8F8F8, true);

        // --- Matte slim hunger bar inside the SAME main frame (under the thick left half of HP bar) ---
        int food = p.getHungerManager().getFoodLevel();
        HealthTracker.State fst = HealthTracker.update("player_food", food / 20f);
        float fw = Math.min(bw * 0.50f, Math.max(28f, (boxX - bx) - 6f));
        int fTop, fBot;
        if (p.hasStatusEffect(StatusEffects.HUNGER)) { fTop = 0xFF8AA83A; fBot = 0xFFA2C24A; }
        else if (food <= 6) { fTop = 0xFFD0482A; fBot = 0xFFE8683C; }
        else { fTop = 0xFFD49220; fBot = 0xFFF0B432; }
        float fy = oy + 19.5f;
        SaoBar.slim(ctx, bx, fy, fw, 3.8f, 2.5f, fst.display, fst.lag, fTop, fBot, 0.95f);

        // Subtle saturation hairline
        float sat = MathHelper.clamp(p.getHungerManager().getSaturationLevel() / 20f, 0f, 1f);
        if (sat > 0) {
            SaoDraw.rect(ctx, bx, fy - 1.2f, (fw - 2.5f) * sat, 0.85f, 0x90FFE89A);
        }

        // Air gauge (only when underwater, neatly inside the bottom edge of the same frame)
        int air = p.getAir(), maxAir = p.getMaxAir();
        if (air < maxAir) {
            float af = Math.max(0, air) / (float) maxAir;
            SaoBar.slim(ctx, bx, oy + 25.2f, fw, 2.4f, 1.8f, af, af, 0xFF3CA4E0, 0xFF7AD4FF, 0.95f);
        }

        // --- status tiles (only while an effect is active) ---
        renderStatusTiles(ctx, mc, p, px + W + 4f, oy + 7f);
        ctx.getMatrices().pop();

        // low HP red vignette pulse
        if (frac <= 0.2f && frac > 0) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 180.0);
            int c = SaoDraw.withAlpha(0xFFB01010, 0.18f + 0.12f * pulse);
            ctx.fillGradient(0, 0, sw, sh / 5, c, 0x00B01010);
            ctx.fillGradient(0, sh - sh / 5, sw, sh, 0x00B01010, c);
        }
    }

    /** Draws the iconic SAO anime frosted C-shaped left bracket (`[`). */
    private static void drawFrostedBracket(DrawContext ctx, float x, float y, float w, float h,
                                           float notchW, float armH, int color) {
        SaoDraw.rect(ctx, x, y, w, armH, color);
        SaoDraw.rect(ctx, x + notchW, y + armH, w - notchW, h - 2f * armH, color);
        SaoDraw.rect(ctx, x, y + h - armH, w, armH, color);
    }

    private static void renderStatusTiles(DrawContext ctx, MinecraftClient mc, PlayerEntity p, float x, float y) {
        List<StatusEffectInstance> list = new ArrayList<>();
        for (StatusEffectInstance inst : p.getStatusEffects()) {
            if (inst.shouldShowIcon()) list.add(inst);
        }
        long now = System.currentTimeMillis();
        EFFECT_SEEN.keySet().removeIf(k -> list.stream().noneMatch(i -> i.getEffectType().equals(k)));
        if (list.isEmpty()) return;
        list.sort((a, b) -> {
            boolean ha = !a.getEffectType().value().isBeneficial(), hb = !b.getEffectType().value().isBeneficial();
            if (ha != hb) return ha ? -1 : 1;
            return Integer.compare(b.getDuration(), a.getDuration());
        });

        float tw = 20, th = 15, slant = 4, gap = 2;
        int shown = Math.min(list.size(), 6);
        for (int i = 0; i < shown; i++) {
            StatusEffectInstance inst = list.get(i);
            RegistryEntry<StatusEffect> type = inst.getEffectType();
            long seen = EFFECT_SEEN.computeIfAbsent(type, k -> now);
            float appear = MathHelper.clamp((now - seen) / 200f, 0, 1);
            float a = appear;
            if (!inst.isInfinite() && inst.isDurationBelow(200)) {
                a *= 0.55f + 0.45f * (float) Math.abs(Math.sin(now / 160.0));
            }
            float tx = x + i * (tw - slant + gap) + (1 - appear) * 8;
            int color = effectColor(type);
            int dark = SaoDraw.lerpColor(color, 0xFF000000, 0.35f);
            SaoDraw.para(ctx, tx - 1, y - 1, tw + 2, th + 2, slant, SaoDraw.withAlpha(0xFFF4F4F4, a));
            SaoDraw.quadC(ctx, tx + slant, y, tx, y + th, tx + tw - slant, y + th, tx + tw, y,
                    SaoDraw.withAlpha(SaoDraw.lerpColor(color, 0xFFFFFFFF, 0.25f), a), SaoDraw.withAlpha(dark, a),
                    SaoDraw.withAlpha(dark, a), SaoDraw.withAlpha(SaoDraw.lerpColor(color, 0xFFFFFFFF, 0.25f), a));
            Sprite sprite = mc.getStatusEffectSpriteManager().getSprite(type);
            int iconColor = SaoDraw.withAlpha(0xFFFFFFFF, a);
            ctx.drawSpriteStretched(RenderLayer::getGuiTextured, sprite, (int) (tx + tw / 2f - 5.5f), (int) (y + 2), 11, 11, iconColor);
            if (inst.getAmplifier() > 0) {
                SaoDraw.textScaled(ctx, Text.literal(String.valueOf(inst.getAmplifier() + 1)),
                        tx + tw - slant - 3, y + th - 5, 0.6f, iconColor, true);
            }
        }
        if (list.size() > shown) {
            SaoDraw.text(ctx, "+" + (list.size() - shown), x + shown * (tw - slant + gap) + 2, y + 4, SaoDraw.TEXT, true);
        }
    }

    private static int effectColor(RegistryEntry<StatusEffect> e) {
        if (e.equals(StatusEffects.REGENERATION) || e.equals(StatusEffects.INSTANT_HEALTH)) return 0xFFE8609A;
        if (e.equals(StatusEffects.POISON)) return 0xFF5DAA2E;
        if (e.equals(StatusEffects.WITHER)) return 0xFF3A3340;
        if (e.equals(StatusEffects.ABSORPTION)) return 0xFFF0B828;
        if (e.equals(StatusEffects.STRENGTH)) return 0xFFE0A030;
        if (e.equals(StatusEffects.SPEED)) return 0xFF2FA8D8;
        if (e.equals(StatusEffects.RESISTANCE)) return 0xFF3A78C8;
        if (e.equals(StatusEffects.FIRE_RESISTANCE)) return 0xFFD8402A;
        if (e.equals(StatusEffects.SLOWNESS) || e.equals(StatusEffects.WEAKNESS)) return 0xFF6A6458;
        if (e.equals(StatusEffects.HUNGER)) return 0xFF7A8A3A;
        if (e.equals(StatusEffects.NIGHT_VISION)) return 0xFF3A50C8;
        if (e.equals(StatusEffects.BLINDNESS) || e.equals(StatusEffects.DARKNESS)) return 0xFF22222E;
        return 0xFF000000 | e.value().getColor();
    }
}
