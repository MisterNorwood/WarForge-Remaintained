package com.flansmod.warforge.common.factories;

import com.flansmod.warforge.client.ui.FactionUpgradeScreen;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.network.PacketRequestUpgradeUI;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.UUID;

public final class FactionUpgradeGuiFactory {
    public static final FactionUpgradeGuiFactory INSTANCE = new FactionUpgradeGuiFactory();

    private FactionUpgradeGuiFactory() {
    }

    public static void init() {
    }

    @OnlyIn(Dist.CLIENT)
    public void openClient(UUID factionId) {
        requestUpgradeUI(factionId);
    }

    @OnlyIn(Dist.CLIENT)
    public void openClientChild(Runnable reopenParent, UUID factionId) {
        requestUpgradeUI(factionId);
    }

    @OnlyIn(Dist.CLIENT)
    public void openClientSibling(UUID factionId) {
        requestUpgradeUI(factionId);
    }

    @OnlyIn(Dist.CLIENT)
    private void requestUpgradeUI(UUID factionId) {
        PacketRequestUpgradeUI packet = new PacketRequestUpgradeUI();
        packet.mFactionIDRequest = factionId;
        WarForgeMod.NETWORK.sendToServer(packet);
    }

    @OnlyIn(Dist.CLIENT)
    public void openUpgradeScreen() {
        FactionUpgradeScreen.open();
    }

    public void open(Player player, UUID factionId) {
    }
}
