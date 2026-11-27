package com.saoui.screen;

import com.saoui.hud.HealthTracker;
import com.saoui.level.SaoLevelClient;
import com.saoui.render.SaoBar;
import com.saoui.render.SaoDraw;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.OpenToLanScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.StatsScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.advancement.AdvancementsScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.SocialInteractionsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.realms.gui.screen.RealmsMainScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * SAO style cascading menu:
 * - Left white/grey player card with pointer toward the active category
 * - Vertical column of 5 circular vector-icon buttons (active highlighted in gold)
 * - Right cascading submenu list with circular badges and golden hover highlight
 * - Classic 3-band SAO Message dialog for logout confirmation (rendered at high Z to prevent depth clipping)
 */
public class SaoMenuScreen extends Screen {
    /** Set to true to let the vanilla pause menu through once. */
    public static boolean bypass = false;

    private enum Icon { PLAYER, ITEMS, QUESTS, SETTINGS, LOGOUT }

    private record SubItem(Text label, Icon icon, Runnable action, boolean danger) {}
    private record Category(Icon icon, List<SubItem> subs, Runnable directAction) {}

    private final List<Category> categories = new ArrayList<>();
    private long openMs;
    private int activeCat = 0;
    private int hoveredSub = -1;
    private boolean confirmLogout = false;
    private long confirmMs;
    private int confirmHover = -1;

    private static final float R = 12f;
    private static final float SPACING = 29f;
    private static final float CARD_W = 114f;
    private static final float CARD_H = 152f;
    private static final float SUB_W = 118f;
    private static final float SUB_H = 22f;

    // SAO Light/Silver/Gold palette matching the anime & SAO Utils style
    private static final int GOLD_TOP = 0xFFF7BC2C;
    private static final int GOLD_BOT = 0xFFE2940C;
    private static final int GOLD_SOLID = 0xFFECA618;
    private static final int PANEL_WHITE = 0xF2F7F7F7;
    private static final int PANEL_HEADER = 0xEDE4E4E4;
    private static final int PANEL_FOOTER = 0xEDD4D4D4;
    private static final int PANEL_MID_RECESS = 0xEDC8C8C8;
    private static final int SUB_BG = 0xEDEAEAEA;
    private static final int DIVIDER = 0xFFA2A2A2;
    private static final int TEXT_DARK = 0xFF3E3E3E;
    private static final int TEXT_MUTED = 0xFF666666;
    private static final int ICON_DARK = 0xFF555555;

    public SaoMenuScreen() {
        super(Text.translatable("saoui.menu.title"));
    }

    @Override
    protected void init() {
        categories.clear();
        openMs = System.currentTimeMillis();
        activeCat = 0;
        hoveredSub = -1;
        confirmLogout = false;
        assert client != null && client.player != null;

        // 0: Player (Resume, Status)
        List<SubItem> playerSubs = List.of(
                new SubItem(Text.translatable("saoui.menu.resume"), Icon.PLAYER, () -> client.setScreen(null), false),
                new SubItem(Text.translatable("saoui.menu.stats"), Icon.QUESTS,
                        () -> client.setScreen(new StatsScreen(this, client.player.getStatHandler())), false)
        );
        categories.add(new Category(Icon.PLAYER, playerSubs, () -> client.setScreen(null)));

        // 1: Items (Inventory)
        List<SubItem> itemSubs = List.of(
                new SubItem(Text.translatable("saoui.menu.items"), Icon.ITEMS,
                        () -> client.setScreen(new InventoryScreen(client.player)), false)
        );
        categories.add(new Category(Icon.ITEMS, itemSubs, () -> client.setScreen(new InventoryScreen(client.player))));

        // 2: Quests & Social
        List<SubItem> questSubs = new ArrayList<>();
        questSubs.add(new SubItem(Text.translatable("saoui.menu.quests"), Icon.QUESTS,
                () -> client.setScreen(new AdvancementsScreen(client.player.networkHandler.getAdvancementHandler(), this)), false));
        if (client.isIntegratedServerRunning() && client.getServer() != null && !client.getServer().isRemote()) {
            questSubs.add(new SubItem(Text.translatable("saoui.menu.lan"), Icon.PLAYER,
                    () -> client.setScreen(new OpenToLanScreen(this)), false));
        } else {
            questSubs.add(new SubItem(Text.translatable("saoui.menu.friends"), Icon.PLAYER,
                    () -> client.setScreen(new SocialInteractionsScreen(this)), false));
        }
        categories.add(new Category(Icon.QUESTS, questSubs, null));

        // 3: Settings & System
        List<SubItem> settingSubs = List.of(
                new SubItem(Text.translatable("saoui.menu.options"), Icon.SETTINGS,
                        () -> client.setScreen(new OptionsScreen(this, client.options)), false),
                new SubItem(Text.translatable("saoui.menu.vanilla"), Icon.SETTINGS, () -> {
                    bypass = true;
                    client.setScreen(new GameMenuScreen(true));
                }, false)
        );
        categories.add(new Category(Icon.SETTINGS, settingSubs, null));

        // 4: Logout
        Runnable openLogout = () -> {
            confirmLogout = true;
            confirmMs = System.currentTimeMillis();
            play(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.6f);
        };
        List<SubItem> logoutSubs = List.of(
                new SubItem(Text.translatable("saoui.menu.logout"), Icon.LOGOUT, openLogout, true)
        );
        categories.add(new Category(Icon.LOGOUT, logoutSubs, openLogout));

        play(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 2.0f);
    }

    private void play(net.minecraft.sound.SoundEvent ev, float pitch) {
        if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(ev, pitch, 0.55f));
    }

    private float colX() {
        return width / 2f - 4f;
    }

    private float colY(int i) {
        float total = (categories.size() - 1) * SPACING;
        return height / 2f - total / 2f + i * SPACING;
    }

    private float subX() {
        return colX() + R + 16f;
    }

    private float subStartY(int catIdx) {
        List<SubItem> subs = categories.get(catIdx).subs;
        float totalH = subs.size() * SUB_H;
        float cy = colY(catIdx);
        float y = cy - totalH / 2f;
        return MathHelper.clamp(y, 16f, height - totalH - 16f);
    }

    private float appear(int i) {
        float t = (System.currentTimeMillis() - openMs - i * 40L) / 200f;
        return MathHelper.clamp(t, 0f, 1f);
    }

    private static float ease(float t) {
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        // Update hover state when dialog is not open
        if (!confirmLogout) {
            int prevCat = activeCat;
            for (int i = 0; i < categories.size(); i++) {
                float dx = mouseX - colX(), dy = mouseY - colY(i);
                if (dx * dx + dy * dy <= (R + 3f) * (R + 3f)) {
                    activeCat = i;
                    break;
                }
            }
            if (activeCat != prevCat) {
                play(SoundEvents.UI_BUTTON_CLICK.value(), 1.8f);
            }

            int newSub = -1;
            if (activeCat >= 0 && activeCat < categories.size()) {
                List<SubItem> subs = categories.get(activeCat).subs;
                float sx = subX();
                float sy = subStartY(activeCat);
                for (int j = 0; j < subs.size(); j++) {
                    float ry = sy + j * SUB_H;
                    if (mouseX >= sx && mouseX <= sx + SUB_W && mouseY >= ry && mouseY < ry + SUB_H) {
                        newSub = j;
                        break;
                    }
                }
            }
            if (newSub != hoveredSub && newSub >= 0) {
                play(SoundEvents.UI_BUTTON_CLICK.value(), 2.0f);
            }
            hoveredSub = newSub;
        }

        drawPlayerCard(ctx, mouseX, mouseY);
        drawCategoryColumn(ctx);
        drawSubmenu(ctx);

        if (confirmLogout) {
            // Flush prior draws and push Z forward so 3D entity depth from drawPlayerCard never clips the dialog
            ctx.draw();
            ctx.getMatrices().push();
            ctx.getMatrices().translate(0f, 0f, 400f);
            drawConfirm(ctx, mouseX, mouseY);
            ctx.getMatrices().pop();
        }
    }

    private void drawPlayerCard(DrawContext ctx, int mouseX, int mouseY) {
        if (client == null || client.player == null) return;
        float a = ease(MathHelper.clamp((System.currentTimeMillis() - openMs) / 260f, 0f, 1f));
        float x = colX() - R - 18f - CARD_W - (1f - a) * 18f;
        float y = height / 2f - CARD_H / 2f;

        // Soft drop shadow
        SaoDraw.rect(ctx, x + 2, y + 3, CARD_W, CARD_H, SaoDraw.withAlpha(0x38000000, a));

        // Main white body
        SaoDraw.rect(ctx, x, y, CARD_W, CARD_H, SaoDraw.withAlpha(PANEL_WHITE, a));

        // Top header band + thin divider
        float headH = 18f;
        SaoDraw.rect(ctx, x, y, CARD_W, headH, SaoDraw.withAlpha(PANEL_HEADER, a));
        SaoDraw.rect(ctx, x + 6, y + headH, CARD_W - 12, 1f, SaoDraw.withAlpha(DIVIDER, a));

        // Bottom grey footer band with small upward left notch (like the reference card)
        float footH = 40f;
        float footY = y + CARD_H - footH;
        SaoDraw.rect(ctx, x, footY, CARD_W, footH, SaoDraw.withAlpha(PANEL_FOOTER, a));
        SaoDraw.tri(ctx, x + 10, footY, x + 15, footY - 4, x + 20, footY, SaoDraw.withAlpha(PANEL_FOOTER, a));

        // Right-pointing white triangle arrow pointing toward the active category circle
        float ptrY = MathHelper.clamp(colY(activeCat), y + headH + 10, footY - 10);
        SaoDraw.tri(ctx, x + CARD_W, ptrY - 5, x + CARD_W, ptrY + 5, x + CARD_W + 7, ptrY, SaoDraw.withAlpha(PANEL_WHITE, a));

        if (a < 0.35f) return;

        // Header: player name centered
        SaoDraw.textCentered(ctx, client.player.getName(), x + CARD_W / 2f, y + 5, 0.9f, TEXT_DARK, false);

        // 3D character preview in the white body (skip when confirm dialog is open to keep dialog crisp)
        if (!confirmLogout) {
            InventoryScreen.drawEntity(ctx, (int) (x + 8), (int) (y + headH + 4),
                    (int) (x + CARD_W - 8), (int) (footY - 4), 40,
                    0.0625f, mouseX, mouseY, client.player);
        }

        // Footer: mini stepped HP bar + HP / LV / Armor
        float max = Math.max(1f, client.player.getMaxHealth());
        float hp = client.player.getHealth();
        float frac = MathHelper.clamp(hp / max, 0f, 1f);
        HealthTracker.State st = HealthTracker.update("player_hud", frac);

        SaoDraw.textScaled(ctx, Text.literal("LV: " + SaoLevelClient.getDisplayLevel(client.player)), x + 8, footY + 5, 0.8f, TEXT_DARK, false);
        String hpStr = (int) Math.ceil(hp) + " / " + (int) Math.ceil(max);
        float hpW = textRenderer.getWidth(hpStr) * 0.8f;
        SaoDraw.textScaled(ctx, Text.literal(hpStr), x + CARD_W - 8 - hpW, footY + 5, 0.8f, TEXT_MUTED, false);

        SaoBar.stepped(ctx, x + 8, footY + 16, CARD_W - 16, 7, 4.5f, 0.62f, 3.5f,
                st.display, st.lag, frac, 0xFF909090, a);

        String sub = "FOOD " + client.player.getHungerManager().getFoodLevel() + "   DEF " + client.player.getArmor();
        SaoDraw.textScaled(ctx, Text.literal(sub), x + 8, footY + 28, 0.72f, TEXT_MUTED, false);
    }

    private void drawCategoryColumn(DrawContext ctx) {
        float cx = colX();
        for (int i = 0; i < categories.size(); i++) {
            float a = ease(appear(i));
            if (a <= 0f) continue;
            float cy = colY(i) - (1f - a) * 16f;
            boolean active = (i == activeCat);

            if (active) {
                // Golden outer halo + golden circle
                SaoDraw.ring(ctx, cx, cy, R + 3f, 1.2f, SaoDraw.withAlpha(GOLD_TOP, 0.85f * a));
                SaoDraw.circle(ctx, cx, cy, R, SaoDraw.withAlpha(GOLD_SOLID, a));
                SaoDraw.ring(ctx, cx, cy, R, 1.0f, SaoDraw.withAlpha(0xFFFFFFFF, 0.9f * a));
                drawVectorIcon(ctx, categories.get(i).icon, cx, cy, 1.0f, SaoDraw.withAlpha(0xFFFFFFFF, a));
            } else {
                // Translucent silver-white circle with outer ring
                SaoDraw.circle(ctx, cx, cy, R, SaoDraw.withAlpha(0xB8E8E8E8, a));
                SaoDraw.ring(ctx, cx, cy, R + 2f, 0.9f, SaoDraw.withAlpha(0x88FFFFFF, a));
                SaoDraw.ring(ctx, cx, cy, R, 0.9f, SaoDraw.withAlpha(0xCCFFFFFF, a));
                drawVectorIcon(ctx, categories.get(i).icon, cx, cy, 1.0f, SaoDraw.withAlpha(ICON_DARK, a));
            }
        }
    }

    private void drawSubmenu(DrawContext ctx) {
        if (activeCat < 0 || activeCat >= categories.size()) return;
        float a = ease(appear(activeCat));
        if (a <= 0.1f) return;

        List<SubItem> subs = categories.get(activeCat).subs;
        float sx = subX() + (1f - a) * 10f;
        float sy = subStartY(activeCat);
        float totalH = subs.size() * SUB_H;

        // Soft drop shadow behind submenu block
        SaoDraw.rect(ctx, sx + 2, sy + 2, SUB_W, totalH, SaoDraw.withAlpha(0x38000000, a));

        // Left-pointing golden connector triangle from active circle to submenu
        float ptrY = MathHelper.clamp(colY(activeCat), sy + 6, sy + totalH - 6);
        int ptrCol = (hoveredSub >= 0 && sy + hoveredSub * SUB_H <= ptrY && ptrY <= sy + (hoveredSub + 1) * SUB_H)
                ? GOLD_SOLID : PANEL_WHITE;
        SaoDraw.tri(ctx, sx - 6, ptrY, sx, ptrY - 5, sx, ptrY + 5, SaoDraw.withAlpha(ptrCol, a));

        for (int j = 0; j < subs.size(); j++) {
            SubItem sub = subs.get(j);
            float ry = sy + j * SUB_H;
            boolean hov = (j == hoveredSub);

            if (hov) {
                int top = sub.danger ? 0xFFE85A50 : GOLD_TOP;
                int bot = sub.danger ? 0xFFC8322A : GOLD_BOT;
                SaoDraw.quadC(ctx, sx, ry, sx, ry + SUB_H, sx + SUB_W, ry + SUB_H, sx + SUB_W, ry,
                        SaoDraw.withAlpha(top, a), SaoDraw.withAlpha(bot, a),
                        SaoDraw.withAlpha(bot, a), SaoDraw.withAlpha(top, a));
            } else {
                SaoDraw.rect(ctx, sx, ry, SUB_W, SUB_H, SaoDraw.withAlpha(SUB_BG, a));
            }

            // Thin horizontal separator between rows
            if (j > 0) {
                SaoDraw.rect(ctx, sx, ry, SUB_W, 1f, SaoDraw.withAlpha(DIVIDER, a));
            }

            // Small circular icon badge on the left of each row (matching screenshot's submenu list)
            float bx = sx + 13f;
            float by = ry + SUB_H / 2f;
            int badgeBg = hov ? 0xFFFFFFFF : ICON_DARK;
            int badgeFg = hov ? (sub.danger ? 0xFFD83A30 : GOLD_BOT) : 0xFFF2F2F2;
            SaoDraw.circle(ctx, bx, by, 6.5f, SaoDraw.withAlpha(badgeBg, a));
            drawVectorIcon(ctx, sub.icon, bx, by, 0.56f, SaoDraw.withAlpha(badgeFg, a));

            // Row text
            int txtCol = hov ? 0xFFFFFFFF : TEXT_DARK;
            SaoDraw.textScaled(ctx, sub.label, sx + 25f, ry + (SUB_H - 7f) / 2f, 0.92f, SaoDraw.withAlpha(txtCol, a), false);
        }
    }

    /** Crisp 2D SAO vector icons drawn purely with polygons (no 3D item depth issues). */
    private void drawVectorIcon(DrawContext ctx, Icon icon, float cx, float cy, float s, int color) {
        switch (icon) {
            case PLAYER -> {
                // Head + shoulders silhouette
                SaoDraw.circle(ctx, cx, cy - 2.6f * s, 2.3f * s, color);
                SaoDraw.quad(ctx,
                        cx - 4.4f * s, cy + 4.4f * s,
                        cx + 4.4f * s, cy + 4.4f * s,
                        cx + 3.2f * s, cy + 0.8f * s,
                        cx - 3.2f * s, cy + 0.8f * s, color);
            }
            case ITEMS -> {
                // Sleek satchel / inventory box with top handle
                SaoDraw.rect(ctx, cx - 4.5f * s, cy - 2.2f * s, 9f * s, 6.4f * s, color);
                SaoDraw.rect(ctx, cx - 2.2f * s, cy - 4.2f * s, 4.4f * s, 1.2f * s, color);
                SaoDraw.rect(ctx, cx - 2.2f * s, cy - 4.2f * s, 1.1f * s, 2.2f * s, color);
                SaoDraw.rect(ctx, cx + 1.1f * s, cy - 4.2f * s, 1.1f * s, 2.2f * s, color);
            }
            case QUESTS -> {
                // List / quest lines
                SaoDraw.rect(ctx, cx - 4.2f * s, cy - 3.5f * s, 8.4f * s, 1.5f * s, color);
                SaoDraw.rect(ctx, cx - 4.2f * s, cy - 0.7f * s, 8.4f * s, 1.5f * s, color);
                SaoDraw.rect(ctx, cx - 4.2f * s, cy + 2.1f * s, 5.8f * s, 1.5f * s, color);
            }
            case SETTINGS -> {
                // Gear ring with 4 cardinal notches
                SaoDraw.ring(ctx, cx, cy, 4.0f * s, 1.5f * s, color);
                SaoDraw.rect(ctx, cx - 0.9f * s, cy - 5.2f * s, 1.8f * s, 1.6f * s, color);
                SaoDraw.rect(ctx, cx - 0.9f * s, cy + 3.6f * s, 1.8f * s, 1.6f * s, color);
                SaoDraw.rect(ctx, cx - 5.2f * s, cy - 0.9f * s, 1.6f * s, 1.8f * s, color);
                SaoDraw.rect(ctx, cx + 3.6f * s, cy - 0.9f * s, 1.6f * s, 1.8f * s, color);
            }
            case LOGOUT -> {
                // Power icon: ring + vertical bar at top
                SaoDraw.ring(ctx, cx, cy + 0.5f * s, 4.2f * s, 1.4f * s, color);
                SaoDraw.rect(ctx, cx - 0.85f * s, cy - 4.8f * s, 1.7f * s, 4.5f * s, color);
            }
        }
    }

    /** Classic 3-band SAO "Message" dialog matching the screenshot. */
    private void drawConfirm(DrawContext ctx, int mouseX, int mouseY) {
        float a = ease(MathHelper.clamp((System.currentTimeMillis() - confirmMs) / 180f, 0f, 1f));
        ctx.fill(0, 0, width, height, SaoDraw.withAlpha(0x66000000, a));

        float w = 230f, h = 114f;
        float x = width / 2f - w / 2f;
        float y = height / 2f - h / 2f + (1f - a) * 8f;

        float topH = 28f, midH = 48f, botH = h - topH - midH;

        // Soft drop shadow
        SaoDraw.rect(ctx, x + 3, y + 4, w, h, SaoDraw.withAlpha(0x45000000, a));

        // Top band (Title)
        SaoDraw.rect(ctx, x, y, w, topH, SaoDraw.withAlpha(PANEL_WHITE, a));
        // Middle recessed band (Message)
        float midY = y + topH;
        SaoDraw.rect(ctx, x, midY, w, midH, SaoDraw.withAlpha(PANEL_MID_RECESS, a));
        // Subtle inner top & bottom shadow gradients inside the recessed band
        SaoDraw.quadC(ctx, x, midY, x, midY + 6, x + w, midY + 6, x + w, midY,
                SaoDraw.withAlpha(0x28000000, a), 0x00000000, 0x00000000, SaoDraw.withAlpha(0x28000000, a));
        SaoDraw.quadC(ctx, x, midY + midH - 6, x, midY + midH, x + w, midY + midH, x + w, midY + midH - 6,
                0x00000000, SaoDraw.withAlpha(0x24000000, a), SaoDraw.withAlpha(0x24000000, a), 0x00000000);
        // Bottom band (O / X buttons)
        float botY = midY + midH;
        SaoDraw.rect(ctx, x, botY, w, botH, SaoDraw.withAlpha(PANEL_WHITE, a));

        // Text
        SaoDraw.textCentered(ctx, Text.translatable("saoui.menu.logout"), width / 2f, y + 10, 1.05f, TEXT_DARK, false);
        SaoDraw.textCentered(ctx, Text.translatable("saoui.confirm.logout"), width / 2f, midY + 20, 0.95f, TEXT_DARK, false);

        // Blue O (Confirm) and Red X (Cancel) circular buttons
        float by = botY + botH / 2f;
        float okX = width / 2f - 44f, noX = width / 2f + 44f;
        confirmHover = -1;
        if (dist(mouseX, mouseY, okX, by) <= 13f) confirmHover = 0;
        if (dist(mouseX, mouseY, noX, by) <= 13f) confirmHover = 1;

        if (confirmHover == 0) {
            SaoDraw.ring(ctx, okX, by, 13.5f, 1.2f, SaoDraw.withAlpha(0xFF4CA0F0, a));
        }
        SaoDraw.circle(ctx, okX, by, 11f, SaoDraw.withAlpha(confirmHover == 0 ? 0xFF358AE6 : 0xFF54A2EE, a));
        SaoDraw.ring(ctx, okX, by, 5.8f, 1.8f, SaoDraw.withAlpha(0xFFFFFFFF, a));

        if (confirmHover == 1) {
            SaoDraw.ring(ctx, noX, by, 13.5f, 1.2f, SaoDraw.withAlpha(0xFFE85050, a));
        }
        SaoDraw.circle(ctx, noX, by, 11f, SaoDraw.withAlpha(confirmHover == 1 ? 0xFFD03030 : 0xFFE65050, a));
        // Clean X mark
        SaoDraw.quad(ctx, noX - 4.5f, by - 5.5f, noX - 5.5f, by - 4.5f, noX + 4.5f, by + 5.5f, noX + 5.5f, by + 4.5f, SaoDraw.withAlpha(0xFFFFFFFF, a));
        SaoDraw.quad(ctx, noX + 4.5f, by - 5.5f, noX - 5.5f, by + 4.5f, noX - 4.5f, by + 5.5f, noX + 5.5f, by - 4.5f, SaoDraw.withAlpha(0xFFFFFFFF, a));
    }

    private static float dist(double mx, double my, float x, float y) {
        return (float) Math.sqrt((mx - x) * (mx - x) + (my - y) * (my - y));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (confirmLogout) {
            if (confirmHover == 0) {
                play(SoundEvents.BLOCK_GLASS_BREAK, 1.6f);
                disconnect();
            } else if (confirmHover == 1) {
                confirmLogout = false;
                play(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f);
            }
            return true;
        }

        // Click on a submenu item
        if (activeCat >= 0 && activeCat < categories.size() && hoveredSub >= 0) {
            List<SubItem> subs = categories.get(activeCat).subs;
            if (hoveredSub < subs.size()) {
                play(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 2.0f);
                subs.get(hoveredSub).action.run();
                return true;
            }
        }

        // Click directly on a category circle
        for (int i = 0; i < categories.size(); i++) {
            float dx = (float) mouseX - colX(), dy = (float) mouseY - colY(i);
            if (dx * dx + dy * dy <= (R + 3f) * (R + 3f)) {
                activeCat = i;
                Category cat = categories.get(i);
                if (cat.directAction != null) {
                    play(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 2.0f);
                    cat.directAction.run();
                } else {
                    play(SoundEvents.UI_BUTTON_CLICK.value(), 1.8f);
                }
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (confirmLogout && keyCode == 256) {
            confirmLogout = false;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void disconnect() {
        if (client == null || client.world == null) return;
        boolean singleplayer = client.isInSingleplayer();
        ServerInfo serverInfo = client.getCurrentServerEntry();
        client.world.disconnect();
        if (singleplayer) {
            client.disconnect(new MessageScreen(Text.translatable("menu.savingLevel")));
        } else {
            client.disconnect();
        }
        TitleScreen title = new TitleScreen();
        if (singleplayer) {
            client.setScreen(title);
        } else if (serverInfo != null && serverInfo.isRealm()) {
            client.setScreen(new RealmsMainScreen(title));
        } else {
            client.setScreen(new MultiplayerScreen(title));
        }
    }

    @Override
    public boolean shouldPause() {
        return true;
    }
}
