package com.flansmod.warforge.common.factories;

import com.flansmod.warforge.client.ui.CreateFactionScreen;
import com.flansmod.warforge.common.util.DimBlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public final class CreateFactionGuiFactory {
    public static final CreateFactionGuiFactory INSTANCE = new CreateFactionGuiFactory();

    private CreateFactionGuiFactory() {
    }

    public static void init() {
    }

    @OnlyIn(Dist.CLIENT)
    public void openClientChild(Runnable reopenParent, DimBlockPos citadelPos, int colour, boolean isRecolour) {
    }

    @OnlyIn(Dist.CLIENT)
    public void openCreateScreen() {
        CreateFactionScreen.open();
    }
}
