package com.flansmod.warforge.common.factories;

import com.flansmod.warforge.client.ui.InsuranceScreen;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.network.PacketRequestInsurance;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.UUID;

public final class FactionInsuranceGuiFactory {
    public static final FactionInsuranceGuiFactory INSTANCE = new FactionInsuranceGuiFactory();

    private FactionInsuranceGuiFactory() {
    }

    public static void init() {
    }

    @OnlyIn(Dist.CLIENT)
    public void openClient(UUID factionId) {
        requestInsuranceData(factionId);
    }

    @OnlyIn(Dist.CLIENT)
    public void openClientChild(Runnable reopenParent, UUID factionId) {
        requestInsuranceData(factionId);
    }

    @OnlyIn(Dist.CLIENT)
    public void openClientSibling(UUID factionId) {
        requestInsuranceData(factionId);
    }

    @OnlyIn(Dist.CLIENT)
    private void requestInsuranceData(UUID factionId) {
        PacketRequestInsurance packet = new PacketRequestInsurance();
        packet.factionId = factionId;
        WarForgeMod.NETWORK.sendToServer(packet);
    }

    @OnlyIn(Dist.CLIENT)
    public void openInsuranceScreen() {
        InsuranceScreen.open();
    }

    public void open(Player player, UUID factionId) {
    }
}
