package com.flansmod.warforge.api.modularui;

import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

public final class WarForgeUiTheme {
    public static final int PANEL_FILL = 0xFF14181B;
    public static final int PANEL_BORDER = 0xFF0D1013;
    public static final int HEADER_FILL = 0xFF171B1F;
    public static final int HEADER_BORDER = 0xFF0D1013;
    public static final int SECTION_FILL = 0xFF20262B;
    public static final int SECTION_BORDER = 0xFF11161A;
    public static final int BUTTON_FILL = 0xFF242B31;
    public static final int BUTTON_BORDER = 0xFF0D1013;
    public static final int DANGER_FILL = 0xFF7A2D2D;

    public static final int TEXT_PRIMARY = 0xFFFFFF;
    public static final int TEXT_SECONDARY = 0xC7CCD1;
    public static final int TEXT_MUTED = 0xB8BDC3;
    public static final int TEXT_DISABLED = 0x8A8A8A;
    public static final int TEXT_SUCCESS = 0x55FF55;
    public static final int TEXT_WARNING = 0xFFE19A;
    public static final int TEXT_DANGER = 0xFFEAEA;

    private static final int STRIPE_DEFAULT = 0x4A4A4A;

    private WarForgeUiTheme() {
    }

    public static IGuiTexture borderedFill(int fill, int border) {
        return IGuiTexture.group(new ColorRectTexture(fill), new ColorBorderTexture(border, 1));
    }

    public static <T extends UIElement> T panel(T root) {
        root.style(s -> s.background(borderedFill(PANEL_FILL, PANEL_BORDER)));
        return root;
    }

    public static UIElement frame(UIElement root, int width, int stripeColor) {
        root.layout(l -> l.flexDirection(FlexDirection.ROW).width(width));
        root.style(s -> s.background(borderedFill(PANEL_FILL, PANEL_BORDER)));

        UIElement stripe = new UIElement();
        stripe.layout(l -> l.width(6).heightStretch());
        stripe.style(s -> s.background(new ColorRectTexture(0xFF000000 | (stripeColor & 0xFFFFFF))));
        root.addChild(stripe);

        UIElement body = new UIElement();
        body.layout(l -> l.flexDirection(FlexDirection.COLUMN).flexGrow(1).paddingAll(8).gapRow(6));
        root.addChild(body);
        return body;
    }

    public static UIElement frame(UIElement root, int width) {
        return frame(root, width, STRIPE_DEFAULT);
    }

    public static UIElement header(String title) {
        UIElement header = new UIElement();
        header.layout(l -> l.flexDirection(FlexDirection.COLUMN).widthStretch().paddingHorizontal(8).paddingVertical(6).gapRow(2).marginBottom(2));
        header.style(s -> s.background(borderedFill(HEADER_FILL, HEADER_BORDER)));
        header.addChild(boldText(title, TEXT_PRIMARY));
        return header;
    }

    public static UIElement header(String title, String subtitle, int subtitleColor) {
        UIElement header = header(title);
        header.addChild(boldText(subtitle, subtitleColor));
        return header;
    }

    public static UIElement section() {
        UIElement section = new UIElement();
        section.layout(l -> l.flexDirection(FlexDirection.COLUMN).widthStretch().paddingAll(6).gapRow(4).marginBottom(2));
        section.style(s -> s.background(borderedFill(SECTION_FILL, SECTION_BORDER)));
        return section;
    }

    public static UIElement row(int gap) {
        UIElement row = new UIElement();
        row.layout(l -> l.flexDirection(FlexDirection.ROW).gapColumn(gap));
        return row;
    }

    public static Label text(String value, int rgb) {
        return new Label().setValue(Component.literal(value)
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb & 0xFFFFFF))));
    }

    public static Label boldText(String value, int rgb) {
        return new Label().setValue(Component.literal(value)
                .withStyle(Style.EMPTY.withBold(true).withColor(TextColor.fromRgb(rgb & 0xFFFFFF))));
    }

    public static Button styleButton(Button button, int width) {
        return styleButton(button, width, BUTTON_FILL);
    }

    public static Button styleButton(Button button, int width, int fill) {
        button.layout(l -> l.width(width).height(18));
        button.style(s -> s.background(borderedFill(fill, BUTTON_BORDER)));
        return button;
    }
}
