package com.flansmod.warforge.common.factories;

import com.flansmod.warforge.client.ui.OperationsScreen;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.network.PacketRequestOperations;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public final class OperationsGuiFactory {
    public static final OperationsGuiFactory INSTANCE = new OperationsGuiFactory();

    private OperationsGuiFactory() {
    }

    public static void init() {
    }

    @OnlyIn(Dist.CLIENT)
    public void openClient() {
        WarForgeMod.NETWORK.sendToServer(new PacketRequestOperations());
    }

    public void open(Player player) {
    }

    @OnlyIn(Dist.CLIENT)
    public void openOperationsScreen() {
        OperationsScreen.open();
    }
}
