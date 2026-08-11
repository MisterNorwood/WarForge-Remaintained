package com.flansmod.warforge.client.util;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class PlayerFaceElement extends UIElement {
    private final UUID playerId;

    public PlayerFaceElement(UUID playerId) {
        this.playerId = playerId;
    }

    @Override
    public void drawContents(GUIContext ctx) {
        super.drawContents(ctx);
        int x = (int) getPositionX();
        int y = (int) getPositionY();
        int size = (int) Math.min(getSizeWidth(), getSizeHeight());
        if (size <= 0) {
            return;
        }
        SkinUtil.drawFace(ctx.graphics, playerId, x, y, size);
    }
}
