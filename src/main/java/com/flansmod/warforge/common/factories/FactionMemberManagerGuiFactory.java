package com.flansmod.warforge.common.factories;

import com.flansmod.warforge.client.ui.MemberManagerScreen;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.network.PacketRequestMemberData;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public final class FactionMemberManagerGuiFactory {
    public static final FactionMemberManagerGuiFactory INSTANCE = new FactionMemberManagerGuiFactory();

    private FactionMemberManagerGuiFactory() {
    }

    public static void init() {
    }

    public void open(Player player, FactionMemberManagerGuiData.Page page) {
    }

    @OnlyIn(Dist.CLIENT)
    public void openClient(FactionMemberManagerGuiData.Page page) {
        requestMemberData(page);
    }

    @OnlyIn(Dist.CLIENT)
    public void openClientChild(Runnable reopenParent, FactionMemberManagerGuiData.Page page) {
        requestMemberData(page);
    }

    @OnlyIn(Dist.CLIENT)
    public void openClientSibling(FactionMemberManagerGuiData.Page page) {
        requestMemberData(page);
    }

    @OnlyIn(Dist.CLIENT)
    private void requestMemberData(FactionMemberManagerGuiData.Page page) {
        PacketRequestMemberData packet = new PacketRequestMemberData();
        packet.page = page;
        WarForgeMod.NETWORK.sendToServer(packet);
    }

    @OnlyIn(Dist.CLIENT)
    public void openMemberScreen() {
        MemberManagerScreen.open();
    }
}
