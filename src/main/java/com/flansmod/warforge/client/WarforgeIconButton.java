package com.flansmod.warforge.client;

import com.flansmod.warforge.Tags;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class WarforgeIconButton extends AbstractButton {
    public static final ResourceLocation WARFORGE_BUTTONS_TEXTURE = ResourceLocation.fromNamespaceAndPath(Tags.MODID, "gui/icon_claim.png");
    public static final int WARFORGE_BUTTON_SIZE = 18;

    private final int textureX;
    private final Runnable handler;

    public WarforgeIconButton(int x, int y, int textureX, Runnable handler) {
        super(x, y, WARFORGE_BUTTON_SIZE, WARFORGE_BUTTON_SIZE, CommonComponents.EMPTY);
        this.textureX = textureX;
        this.handler = handler;
        setTooltip(Tooltip.create(Component.literal(labelFor(textureX))));
    }

    private static String labelFor(int textureX) {
        return switch (textureX) {
            case 0 -> "Territory Map";
            case WARFORGE_BUTTON_SIZE -> "Faction Members";
            case WARFORGE_BUTTON_SIZE * 2 -> "Faction Stats";
            case WARFORGE_BUTTON_SIZE * 3 -> "Move Citadel";
            default -> "Operations";
        };
    }

    @Override
    public void onPress() {
        handler.run();
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.blit(WARFORGE_BUTTONS_TEXTURE, getX(), getY(), textureX, 0, WARFORGE_BUTTON_SIZE, WARFORGE_BUTTON_SIZE, 256, 256);
        if (isHoveredOrFocused()) {
            // Vanilla slot-hover highlight (translucent white selection box).
            graphics.fillGradient(getX(), getY(), getX() + WARFORGE_BUTTON_SIZE, getY() + WARFORGE_BUTTON_SIZE, 0x80FFFFFF, 0x80FFFFFF);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
