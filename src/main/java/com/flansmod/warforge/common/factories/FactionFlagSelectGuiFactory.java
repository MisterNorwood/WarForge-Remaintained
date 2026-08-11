package com.flansmod.warforge.common.factories;

import com.flansmod.warforge.client.ui.FlagSelectScreen;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.UUID;

public final class FactionFlagSelectGuiFactory {
    public static final FactionFlagSelectGuiFactory INSTANCE = new FactionFlagSelectGuiFactory();

    private FactionFlagSelectGuiFactory() {
    }

    public static void init() {
    }

    @OnlyIn(Dist.CLIENT)
    public void openClient(UUID factionId) {
        FlagSelectScreen.open(factionId);
    }

    @OnlyIn(Dist.CLIENT)
    public void openClientChild(Runnable reopenParent, UUID factionId) {
        FlagSelectScreen.open(factionId);
    }

    public void open(Player player, UUID factionId) {
    }
}
