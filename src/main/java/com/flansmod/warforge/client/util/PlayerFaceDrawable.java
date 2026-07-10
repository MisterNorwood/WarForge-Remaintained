package com.flansmod.warforge.client.util;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;

import java.util.UUID;

public class PlayerFaceDrawable implements IDrawable {
    private final UUID playerId;

    public PlayerFaceDrawable(UUID playerId) {
        this.playerId = playerId;
    }

    @Override
    public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme theme) {
        SkinUtil.drawFace(context, playerId, x, y, Math.min(width, height));
    }
}
