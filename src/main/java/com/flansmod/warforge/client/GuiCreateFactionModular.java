package com.flansmod.warforge.client;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.drawable.GuiDraw;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.value.StringValue;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.factories.CreateFactionGuiData;
import com.flansmod.warforge.common.network.PacketCreateFaction;
import com.flansmod.warforge.common.network.PacketSetFactionColour;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.awt.Color;

@OnlyIn(Dist.CLIENT)
public final class GuiCreateFactionModular {
    private static final int WIDTH = 264;
    private static final int CONTENT_LEFT = 12;
    private static final int HEADER_Y = 12;
    private static final int BODY_Y = 46;
    private static final int BAR_WIDTH = 156;
    private static final int BAR_HEIGHT = 12;
    private static final int BAR_GAP = 3;

    private GuiCreateFactionModular() {
    }

    public static ModularPanel buildPanel(CreateFactionGuiData data) {
        boolean recolour = data.isRecolour;
        int height = recolour ? 150 : 190;
        int sectionWidth = WIDTH - CONTENT_LEFT * 2;

        float[] hsb = new float[3];
        Color.RGBtoHSB((data.colour >> 16) & 0xFF, (data.colour >> 8) & 0xFF, data.colour & 0xFF, hsb);

        ModularPanel panel = ModularPanel.defaultPanel("create_faction_modular")
                .width(WIDTH)
                .height(height)
                .topRel(0.40f);

        panel.child(new IDrawable.DrawableWidget(ModularGuiStyle.headerBackdrop()).name("create_faction_header").size(WIDTH, 34));
        panel.child(new IDrawable.DrawableWidget(ModularGuiStyle.sectionBackdrop()).name("create_faction_body").size(sectionWidth, height - BODY_Y - 10).pos(CONTENT_LEFT, BODY_Y));
        panel.child(ModularGuiStyle.subPanelCloseButton(WIDTH));

        panel.child(Text.str(recolour ? "Set Faction Colour" : "Create Faction").asWidget()
                .name("create_faction_title")
                .pos(CONTENT_LEFT, HEADER_Y)
                .style(ChatFormatting.BOLD)
                .shadow(true)
                .color(ModularGuiStyle.TEXT_PRIMARY)
                .scale(1.15f));

        final StringValue nameValue = new StringValue("");
        int colourLabelY = BODY_Y + 8;
        if (!recolour) {
            panel.child(Text.str("Faction Name").asWidget()
                    .pos(CONTENT_LEFT, BODY_Y + 8)
                    .color(ModularGuiStyle.TEXT_SECONDARY));
            panel.child(new TextFieldWidget()
                    .value(nameValue)
                    .setMaxLength(64)
                    .name("create_faction_name")
                    .background(ModularGuiStyle.insetBackdrop())
                    .pos(CONTENT_LEFT, BODY_Y + 18)
                    .size(sectionWidth - 16, 16));
            colourLabelY = BODY_Y + 42;
        }

        panel.child(Text.str("Colour").asWidget()
                .pos(CONTENT_LEFT, colourLabelY)
                .color(ModularGuiStyle.TEXT_SECONDARY));

        int barsY = colourLabelY + 12;
        panel.child(colourBar(hsb, 0, CONTENT_LEFT, barsY));
        panel.child(colourBar(hsb, 1, CONTENT_LEFT, barsY + BAR_HEIGHT + BAR_GAP));
        panel.child(colourBar(hsb, 2, CONTENT_LEFT, barsY + (BAR_HEIGHT + BAR_GAP) * 2));

        int swatchX = CONTENT_LEFT + BAR_WIDTH + 12;
        int swatchHeight = BAR_HEIGHT * 3 + BAR_GAP * 2;
        panel.child(new IDrawable.DrawableWidget((context, x, y, w, h, theme) -> {
            GuiGraphics graphics = context.getGraphics();
            int rgb = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
            GuiDraw.drawRect(graphics, x, y, w, h, 0xFF000000 | (rgb & 0x00FFFFFF));
            GuiDraw.drawRect(graphics, x, y, w, 1, ModularGuiStyle.SECTION_BORDER);
            GuiDraw.drawRect(graphics, x, y + h - 1, w, 1, ModularGuiStyle.SECTION_BORDER);
            GuiDraw.drawRect(graphics, x, y, 1, h, ModularGuiStyle.SECTION_BORDER);
            GuiDraw.drawRect(graphics, x + w - 1, y, 1, h, ModularGuiStyle.SECTION_BORDER);
        }).name("create_faction_swatch").size(WIDTH - swatchX - CONTENT_LEFT, swatchHeight).pos(swatchX, barsY));

        int btnY = height - 30;
        panel.child(ModularGuiStyle.actionButton(recolour ? "Set Colour" : "Create", 74, () -> {
            if (recolour) {
                PacketSetFactionColour packet = new PacketSetFactionColour();
                packet.mColour = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
                WarForgeMod.NETWORK.sendToServer(packet);
            } else {
                String name = nameValue.getStringValue().trim();
                if (name.isEmpty()) {
                    return;
                }
                PacketCreateFaction packet = new PacketCreateFaction();
                packet.mCitadelPos = data.citadelPos;
                packet.mFactionName = name;
                packet.mColour = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
                WarForgeMod.NETWORK.sendToServer(packet);
            }
            DeferredGuiOpen.closeOrReturnToParent();
        }).pos(CONTENT_LEFT, btnY));

        panel.child(ModularGuiStyle.actionButton("Cancel", 74, DeferredGuiOpen::closeOrReturnToParent)
                .pos(CONTENT_LEFT + 82, btnY));

        return panel;
    }

    private static ButtonWidget<?> colourBar(float[] hsb, int channel, int x, int y) {
        int[] rect = new int[]{x, BAR_WIDTH};
        IDrawable bar = (context, drawX, drawY, drawWidth, drawHeight, theme) -> {
            rect[0] = drawX;
            rect[1] = drawWidth;
            GuiGraphics graphics = context.getGraphics();
            for (int n = 0; n < drawWidth; n++) {
                float fraction = drawWidth <= 1 ? 0f : n / (float) (drawWidth - 1);
                int rgb = componentColour(hsb, channel, fraction);
                GuiDraw.drawRect(graphics, drawX + n, drawY, 1, drawHeight, 0xFF000000 | (rgb & 0x00FFFFFF));
            }
            GuiDraw.drawRect(graphics, drawX, drawY, drawWidth, 1, ModularGuiStyle.SECTION_BORDER);
            GuiDraw.drawRect(graphics, drawX, drawY + drawHeight - 1, drawWidth, 1, ModularGuiStyle.SECTION_BORDER);

            int knobX = drawX + Math.round(hsb[channel] * (drawWidth - 1));
            GuiDraw.drawRect(graphics, knobX - 1, drawY - 1, 3, drawHeight + 2, 0xFF000000);
            GuiDraw.drawRect(graphics, knobX, drawY - 1, 1, drawHeight + 2, 0xFFFFFFFF);
        };

        return new ButtonWidget<>()
                .name("create_faction_colour_bar_" + channel)
                .size(BAR_WIDTH, BAR_HEIGHT)
                .pos(x, y)
                .background((ctx, bx, by, bw, bh, th) -> {})
                .overlay(bar)
                .onMousePressed((context, mouseButton) -> {
                    int width = rect[1] <= 0 ? BAR_WIDTH : rect[1];
                    float value = (context.getMouseX() - rect[0]) / (float) width;
                    hsb[channel] = Math.max(0f, Math.min(1f, value));
                    return true;
                });
    }

    private static int componentColour(float[] hsb, int channel, float fraction) {
        if (channel == 0) {
            return Color.HSBtoRGB(fraction, 1.0f, 1.0f);
        }
        if (channel == 1) {
            return Color.HSBtoRGB(hsb[0], fraction, 1.0f);
        }
        return Color.HSBtoRGB(hsb[0], hsb[1], fraction);
    }
}
