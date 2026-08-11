package com.flansmod.warforge.client.util;

import com.flansmod.warforge.client.ClientFlagRegistry;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FlagElement extends UIElement {
    private String flagId;

    public FlagElement(String flagId) {
        this.flagId = flagId;
    }

    public FlagElement setFlagId(String flagId) {
        this.flagId = flagId;
        return this;
    }

    public String getFlagId() {
        return flagId;
    }

    @Override
    public void drawContents(GUIContext ctx) {
        super.drawContents(ctx);
        GuiGraphics graphics = ctx.graphics;
        int x = (int) getPositionX();
        int y = (int) getPositionY();
        int width = (int) getSizeWidth();
        int height = (int) getSizeHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        ResourceLocation texture = flagId == null ? null : ClientFlagRegistry.getFlagTexture(flagId);
        int[] dims = flagId == null ? null : ClientFlagRegistry.getFlagDimensions(flagId);
        if (texture == null || dims == null || dims[0] <= 0 || dims[1] <= 0) {
            graphics.fill(x, y, x + width, y + height, 0xFF2D3338);
            graphics.fill(x, y, x + width, y + 1, 0xFF11161A);
            graphics.fill(x, y + height - 1, x + width, y + height, 0xFF11161A);
            graphics.fill(x, y, x + 1, y + height, 0xFF11161A);
            graphics.fill(x + width - 1, y, x + width, y + height, 0xFF11161A);
            return;
        }

        float scale = Math.min(width / (float) dims[0], height / (float) dims[1]);
        int drawWidth = Math.max(1, Math.round(dims[0] * scale));
        int drawHeight = Math.max(1, Math.round(dims[1] * scale));
        int drawX = x + (width - drawWidth) / 2;
        int drawY = y + (height - drawHeight) / 2;

        graphics.blit(texture, drawX, drawY, drawWidth, drawHeight, 0f, 0f, dims[0], dims[1], dims[0], dims[1]);
    }
}
