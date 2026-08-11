package com.flansmod.warforge.client.util;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.awt.Color;

@OnlyIn(Dist.CLIENT)
public class ColorPickerElement extends UIElement {
    private static final int BAR_HEIGHT = 12;
    private static final int BAR_GAP = 3;
    private static final int BORDER = 0xFF11161A;

    private final float[] hsb = new float[3];

    public ColorPickerElement(int rgb) {
        Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, hsb);
        layout(l -> l.flexDirection(FlexDirection.ROW).gapColumn(8).widthStretch());

        UIElement bars = new UIElement();
        bars.layout(l -> l.flexDirection(FlexDirection.COLUMN).gapRow(BAR_GAP).flexGrow(1));
        for (int channel = 0; channel < 3; channel++) {
            bars.addChild(new Bar(channel));
        }
        addChild(bars);

        Swatch swatch = new Swatch();
        swatch.layout(l -> l.width(40).height(BAR_HEIGHT * 3 + BAR_GAP * 2));
        addChild(swatch);
    }

    public int getRGB() {
        return Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
    }

    private int channelColour(int channel, float fraction) {
        if (channel == 0) {
            return Color.HSBtoRGB(fraction, 1f, 1f);
        }
        if (channel == 1) {
            return Color.HSBtoRGB(hsb[0], fraction, 1f);
        }
        return Color.HSBtoRGB(hsb[0], hsb[1], fraction);
    }

    private final class Bar extends UIElement {
        private final int channel;

        private Bar(int channel) {
            this.channel = channel;
            layout(l -> l.height(BAR_HEIGHT).widthStretch());
            addEventListener(UIEvents.MOUSE_DOWN, (UIEventListener) event -> {
                updateFromMouse(event);
                startDrag(this, null);
                event.stopPropagation();
            });
            addEventListener(UIEvents.DRAG_SOURCE_UPDATE, (UIEventListener) this::updateFromMouse);
        }

        private void updateFromMouse(UIEvent event) {
            float width = getContentWidth();
            if (width <= 0) {
                return;
            }
            float local = getLocalMouse(event.x, event.y).x - getContentX();
            hsb[channel] = Math.max(0f, Math.min(1f, local / width));
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
            for (int n = 0; n < width; n++) {
                float fraction = width <= 1 ? 0f : n / (float) (width - 1);
                int rgb = channelColour(channel, fraction);
                graphics.fill(x + n, y, x + n + 1, y + height, 0xFF000000 | (rgb & 0x00FFFFFF));
            }
            graphics.fill(x, y, x + width, y + 1, BORDER);
            graphics.fill(x, y + height - 1, x + width, y + height, BORDER);
            int knobX = x + Math.round(hsb[channel] * (width - 1));
            graphics.fill(knobX - 1, y - 1, knobX + 2, y + height + 1, 0xFF000000);
            graphics.fill(knobX, y - 1, knobX + 1, y + height + 1, 0xFFFFFFFF);
        }
    }

    private final class Swatch extends UIElement {
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
            int rgb = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
            graphics.fill(x, y, x + width, y + height, 0xFF000000 | (rgb & 0x00FFFFFF));
            graphics.fill(x, y, x + width, y + 1, BORDER);
            graphics.fill(x, y + height - 1, x + width, y + height, BORDER);
            graphics.fill(x, y, x + 1, y + height, BORDER);
            graphics.fill(x + width - 1, y, x + width, y + height, BORDER);
        }
    }
}
