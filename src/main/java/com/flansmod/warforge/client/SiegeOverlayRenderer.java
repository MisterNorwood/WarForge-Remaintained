package com.flansmod.warforge.client;

import brachy.modularui.drawable.GuiDraw;
import com.flansmod.warforge.Tags;
import com.flansmod.warforge.client.util.ScreenSpaceUtil;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.network.SiegeCampProgressInfo;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.UUID;

public final class SiegeOverlayRenderer {
    private static final int STRIPE_W = 6;
    private static final int PAD = 6;
    private static final int PANEL_H = 35;
    private static final int NAMES_Y = 4;
    private static final int TRACK_Y = 14;
    private static final int TRACK_H = 8;
    private static final int LABELS_Y = 24;

    private static final int PX_PER_POINT = 18;
    private static final int TRACK_MIN_W = 300;
    private static final int TRACK_MAX_W = 470;
    private static final int ICON_SIZE = 10;
    private static final int ABANDON_H = 12;

    private static final ResourceLocation SHIELD_ICON = new ResourceLocation(Tags.MODID, "gui/icon_siege_shield.png");
    private static final ResourceLocation AXE_ICON = new ResourceLocation(Tags.MODID, "gui/icon_siege_axe.png");

    private static final int TRACK_FILL = argb(0xFF, 0x0E1216);
    private static final int TRACK_BORDER = ModularGuiStyle.SECTION_BORDER;
    private static final int NOTCH_MINOR = argb(0x77, 0xC7CCD1);
    private static final int CENTER_LINE = argb(0xFF, 0xFFFFFF);
    private static final int MARKER_FILL = argb(0xFF, 0xFFFFFF);
    private static final int TEXT_NEUTRAL = 0xFFFFFFFF;
    private static final int TEXT_DANGER = 0xFFFF5555;
    private static final int TEXT_ABANDON = 0xFFC79A3A;

    private SiegeOverlayRenderer() {
    }

    public static void render(Minecraft mc, GuiGraphics graphics, SiegeCampProgressInfo info, float partialTicks) {
        Font font = mc.font;

        int attackPts = Math.max(1, info.completionPoint);
        int defendPts = Math.max(1, WarForgeConfig.SIEGE_DEFENCE_THRESHOLD);
        int span = attackPts + defendPts;

        int maxTrackW = Math.min(TRACK_MAX_W, Math.max(TRACK_MIN_W, ScreenSpaceUtil.RESOLUTIONX - 80));
        int idealTrackW = span * PX_PER_POINT;
        boolean counterMode = idealTrackW > maxTrackW;
        int trackW = counterMode ? maxTrackW : Mth.clamp(idealTrackW, TRACK_MIN_W, maxTrackW);
        int panelW = trackW + (STRIPE_W + PAD) * 2;

        boolean showAbandon = info.attackerAbandonSeconds > 0
                && ClientClaimChunkCache.playerFactionId != null
                && ClientClaimChunkCache.playerFactionId.equals(info.attackingFactionId);

        ScreenSpaceUtil.ScreenPos pos = WarForgeConfig.POS_SIEGE;
        int px = ScreenSpaceUtil.getX(pos, panelW);
        int py = ScreenSpaceUtil.getY(pos, PANEL_H + (showAbandon ? ABANDON_H : 0));

        int attackRGB = info.attackingColour & 0xFFFFFF;
        int defendRGB = info.defendingColour & 0xFFFFFF;

        int trackLeft = px + STRIPE_W + PAD;
        int trackRight = px + panelW - STRIPE_W - PAD;
        int trackTop = py + TRACK_Y;
        int trackWpx = trackRight - trackLeft;
        float slotW = (float) trackWpx / span;

        int prog = Mth.clamp(info.progress, -defendPts, attackPts);
        float centerX = pointX(trackLeft, slotW, defendPts, 0);
        float markerX = pointX(trackLeft, slotW, defendPts, prog);

        RenderSystem.enableBlend();

        borderedFill(graphics, px, py, panelW, PANEL_H, ModularGuiStyle.SECTION_FILL, ModularGuiStyle.SECTION_BORDER);
        GuiDraw.drawRect(graphics, px, py, STRIPE_W, PANEL_H, argb(0xFF, defendRGB));
        GuiDraw.drawRect(graphics, px + panelW - STRIPE_W, py, STRIPE_W, PANEL_H, argb(0xFF, attackRGB));
        GuiDraw.drawRect(graphics, px + STRIPE_W, py, 1, PANEL_H, ModularGuiStyle.SECTION_BORDER);
        GuiDraw.drawRect(graphics, px + panelW - STRIPE_W - 1, py, 1, PANEL_H, ModularGuiStyle.SECTION_BORDER);

        borderedFill(graphics, trackLeft, trackTop, trackWpx, TRACK_H, TRACK_FILL, TRACK_BORDER);

        renderFill(graphics, prog, attackRGB, defendRGB, centerX, markerX, trackTop);

        if (counterMode) {
            drawNotch(graphics, centerX, trackTop, true, CENTER_LINE);
            drawNotch(graphics, pointX(trackLeft, slotW, defendPts, attackPts), trackTop, false, argb(0xFF, lighten(attackRGB, 0.15f)));
            drawNotch(graphics, pointX(trackLeft, slotW, defendPts, -defendPts), trackTop, false, argb(0xFF, lighten(defendRGB, 0.15f)));
        } else {
            renderNotches(graphics, trackLeft, trackTop, slotW, defendPts, attackPts, attackRGB, defendRGB);
        }

        renderMarker(graphics, info, markerX, trackTop, attackRGB, defendRGB);

        if (counterMode) {
            renderCounter(graphics, font, prog, attackPts, defendPts, px, panelW, trackTop, attackRGB, defendRGB);
        }

        renderNames(graphics, font, info, px, panelW, trackLeft, trackRight, py, attackRGB, defendRGB);
        renderLabels(graphics, font, info, prog, attackPts, defendPts, px, panelW, trackLeft, trackRight, py, attackRGB, defendRGB);
        renderAbandonTimer(graphics, font, info, px, panelW, py);

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static void renderFill(GuiGraphics graphics, int prog, int attackRGB, int defendRGB, float centerX, float markerX, int trackTop) {
        if (prog == 0) {
            return;
        }

        boolean attackerSide = prog > 0;
        int baseRGB = attackerSide ? attackRGB : defendRGB;
        float left = Math.min(centerX, markerX);
        float width = Math.abs(markerX - centerX);

        int dark = argb(0xFF, darken(baseRGB, 0.15f));
        int light = argb(0xFF, lighten(baseRGB, 0.30f));
        int colorLeft = attackerSide ? dark : light;
        int colorRight = attackerSide ? light : dark;
        GuiDraw.drawHorizontalGradientRect(graphics, left, trackTop + 1, width, TRACK_H - 2, colorLeft, colorRight);
    }

    private static void renderNotches(GuiGraphics graphics, int trackLeft, int trackTop, float slotW, int defendPts, int attackPts, int attackRGB, int defendRGB) {
        for (int p = -defendPts; p <= attackPts; p++) {
            float nx = pointX(trackLeft, slotW, defendPts, p);
            boolean center = p == 0;
            int col;
            if (p == attackPts) {
                col = argb(0xFF, lighten(attackRGB, 0.15f));
            } else if (p == -defendPts) {
                col = argb(0xFF, lighten(defendRGB, 0.15f));
            } else if (center) {
                col = CENTER_LINE;
            } else {
                col = NOTCH_MINOR;
            }
            drawNotch(graphics, nx, trackTop, center, col);
        }
    }

    private static void drawNotch(GuiGraphics graphics, float nx, int trackTop, boolean center, int col) {
        float nh = center ? TRACK_H + 4 : TRACK_H - 2;
        float nw = center ? 2f : 1f;
        float ny = trackTop + (TRACK_H - nh) / 2f;
        GuiDraw.drawRect(graphics, nx - nw / 2f, ny, nw, nh, col);
    }

    private static void renderMarker(GuiGraphics graphics, SiegeCampProgressInfo info, float markerX, int trackTop, int attackRGB, int defendRGB) {
        float markerW = 3f;
        float glowW = markerW + 4f;
        float top = trackTop - 2f;
        float height = TRACK_H + 4f;

        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 300.0);
        int leadRGB;
        if (info.progress > info.mPreviousProgress) {
            leadRGB = attackRGB;
        } else if (info.progress < info.mPreviousProgress) {
            leadRGB = defendRGB;
        } else {
            leadRGB = 0xFFFFFF;
        }
        int glowAlpha = (int) (0x30 + pulse * 0x50);

        GuiDraw.drawRect(graphics, markerX - glowW / 2f, top, glowW, height, argb(glowAlpha, leadRGB));
        GuiDraw.drawRect(graphics, markerX - markerW / 2f, top, markerW, height, MARKER_FILL);
    }

    private static void renderCounter(GuiGraphics graphics, Font font, int prog, int attackPts, int defendPts, int px, int panelW, int trackTop, int attackRGB, int defendRGB) {
        String standing;
        int color;
        if (prog > 0) {
            standing = prog + " / " + attackPts;
            color = 0xFF000000 | lighten(attackRGB, 0.25f);
        } else if (prog < 0) {
            standing = (-prog) + " / " + defendPts;
            color = 0xFF000000 | lighten(defendRGB, 0.25f);
        } else {
            standing = "EVEN";
            color = TEXT_NEUTRAL;
        }

        int textWidth = font.width(standing);
        int tx = (int) (px + panelW / 2f - textWidth / 2f);
        int ty = (int) (trackTop + (TRACK_H - font.lineHeight) / 2f + 1f);
        drawOutlinedString(graphics, font, standing, tx, ty, color);
    }

    private static void renderNames(GuiGraphics graphics, Font font, SiegeCampProgressInfo info, int px, int panelW, int trackLeft, int trackRight, int py, int attackRGB, int defendRGB) {
        int defColor = 0xFF000000 | defendRGB;
        int attColor = 0xFF000000 | attackRGB;
        int iconY = py + NAMES_Y - 1;

        drawIcon(graphics, SHIELD_ICON, trackLeft, iconY, ICON_SIZE, ICON_SIZE);
        drawOutlinedString(graphics, font, info.defendingName, trackLeft + ICON_SIZE + 3, py + NAMES_Y, defColor);

        drawIcon(graphics, AXE_ICON, trackRight - ICON_SIZE, iconY, ICON_SIZE, ICON_SIZE);
        int attackNameWidth = font.width(info.attackingName);
        drawOutlinedString(graphics, font, info.attackingName, trackRight - ICON_SIZE - 3 - attackNameWidth, py + NAMES_Y, attColor);

        String vs = "VS";
        graphics.drawString(font, vs, (int) (px + panelW / 2f - font.width(vs) / 2f), py + NAMES_Y, TEXT_NEUTRAL, true);
    }

    private static void renderLabels(GuiGraphics graphics, Font font, SiegeCampProgressInfo info, int prog, int attackPts, int defendPts, int px, int panelW, int trackLeft, int trackRight, int py, int attackRGB, int defendRGB) {
        int defColor = 0xFF000000 | defendRGB;
        int attColor = 0xFF000000 | attackRGB;

        String toWin = prog < attackPts ? (attackPts - prog) + " to win" : "Station siege to win";
        String toDefend = (prog + defendPts) + " to defend";

        drawOutlinedString(graphics, font, toDefend, trackLeft, py + LABELS_Y, defColor);
        drawOutlinedString(graphics, font, toWin, trackRight - font.width(toWin), py + LABELS_Y, attColor);

        if (WarForgeConfig.SIEGE_ENABLE_NEW_TIMER) {
            long remain = info.endTimestamp - System.currentTimeMillis();
            String timer = ClientTickHandler.formatPaddedTimer(remain);
            int tColor = remain < 60000 ? TEXT_DANGER : TEXT_NEUTRAL;
            graphics.drawString(font, timer, (int) (px + panelW / 2f - font.width(timer) / 2f), py + LABELS_Y, tColor, true);
        }
    }

    private static void renderAbandonTimer(GuiGraphics graphics, Font font, SiegeCampProgressInfo info, int px, int panelW, int py) {
        if (info.attackerAbandonSeconds <= 0) {
            return;
        }
        UUID playerFaction = ClientClaimChunkCache.playerFactionId;
        if (playerFaction == null || !playerFaction.equals(info.attackingFactionId)) {
            return;
        }

        String text = "Siege abandoned in: " + ClientTickHandler.formatPaddedTimer(info.attackerAbandonSeconds * 1000L);
        int color = info.attackerAbandonSeconds <= 10 ? TEXT_DANGER : TEXT_ABANDON;
        graphics.drawString(font, text, (int) (px + panelW / 2f - font.width(text) / 2f), py + PANEL_H + 2, color, true);
    }

    private static void drawIcon(GuiGraphics graphics, ResourceLocation icon, int x, int y, int w, int h) {
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        Matrix4f pose = graphics.pose().last().pose();
        GuiDraw.drawTexture(pose, icon, x, y, x + w, y + h, 0f, 0f, 1f, 1f, true);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static void drawOutlinedString(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        int outline = isBright(color) ? 0xFF000000 : 0xFFFFFFFF;
        graphics.drawString(font, text, x - 1, y, outline, false);
        graphics.drawString(font, text, x + 1, y, outline, false);
        graphics.drawString(font, text, x, y - 1, outline, false);
        graphics.drawString(font, text, x, y + 1, outline, false);
        graphics.drawString(font, text, x, y, color, false);
    }

    private static boolean isBright(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 > 128;
    }

    private static void borderedFill(GuiGraphics graphics, int x, int y, int w, int h, int fill, int border) {
        GuiDraw.drawRect(graphics, x, y, w, h, fill);
        GuiDraw.drawRect(graphics, x, y, w, 1, border);
        GuiDraw.drawRect(graphics, x, y + h - 1, w, 1, border);
        GuiDraw.drawRect(graphics, x, y, 1, h, border);
        GuiDraw.drawRect(graphics, x + w - 1, y, 1, h, border);
    }

    private static float pointX(int trackLeft, float slotW, int defendPts, int point) {
        return trackLeft + (point + defendPts) * slotW;
    }

    private static int argb(int alpha, int rgb) {
        return ((alpha & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }

    private static int lighten(int rgb, float amount) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = Math.round(r + (255 - r) * amount);
        g = Math.round(g + (255 - g) * amount);
        b = Math.round(b + (255 - b) * amount);
        return (r << 16) | (g << 8) | b;
    }

    private static int darken(int rgb, float amount) {
        int r = Math.round(((rgb >> 16) & 0xFF) * (1f - amount));
        int g = Math.round(((rgb >> 8) & 0xFF) * (1f - amount));
        int b = Math.round((rgb & 0xFF) * (1f - amount));
        return (r << 16) | (g << 8) | b;
    }
}
