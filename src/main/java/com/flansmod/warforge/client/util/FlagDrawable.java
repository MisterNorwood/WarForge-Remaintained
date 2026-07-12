package com.flansmod.warforge.client.util;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.drawable.GuiDraw;
import brachy.modularui.screen.viewport.GuiContext;
import brachy.modularui.theme.WidgetTheme;
import com.flansmod.warforge.client.ClientFlagRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public class FlagDrawable implements IDrawable {
    private final String flagId;

    public FlagDrawable(String flagId) {
        this.flagId = flagId;
    }

    @Override
    public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme widgetTheme) {
        ResourceLocation texture = ClientFlagRegistry.getFlagTexture(flagId);
        int[] dims = ClientFlagRegistry.getFlagDimensions(flagId);
        if (texture == null || dims == null || dims[0] <= 0 || dims[1] <= 0) {
            GuiGraphics graphics = context.getGraphics();
            GuiDraw.drawRect(graphics, x, y, width, height, 0xFF2D3338);
            GuiDraw.drawRect(graphics, x, y, width, 1, 0xFF11161A);
            GuiDraw.drawRect(graphics, x, y + height - 1, width, 1, 0xFF11161A);
            GuiDraw.drawRect(graphics, x, y, 1, height, 0xFF11161A);
            GuiDraw.drawRect(graphics, x + width - 1, y, 1, height, 0xFF11161A);
            return;
        }

        float scale = Math.min(width / (float) dims[0], height / (float) dims[1]);
        int drawWidth = Math.max(1, Math.round(dims[0] * scale));
        int drawHeight = Math.max(1, Math.round(dims[1] * scale));
        int drawX = x + (width - drawWidth) / 2;
        int drawY = y + (height - drawHeight) / 2;

        GuiGraphics graphics = context.getGraphics();
        graphics.flush();
        GuiDraw.drawTexture(context.getLastGraphicsPose(), texture, drawX, drawY, drawX + drawWidth, drawY + drawHeight,
                0f, 0f, 1f, 1f, true);
    }
}
