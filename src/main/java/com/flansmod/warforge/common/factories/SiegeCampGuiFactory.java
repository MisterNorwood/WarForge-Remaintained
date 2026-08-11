package com.flansmod.warforge.common.factories;

import com.flansmod.warforge.client.ui.SiegeCampScreen;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.network.PacketSiegeCampInfo;
import com.flansmod.warforge.common.network.SiegeCampAttackInfo;
import com.flansmod.warforge.common.util.DimBlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

public final class SiegeCampGuiFactory {
    public static final SiegeCampGuiFactory INSTANCE = new SiegeCampGuiFactory();

    private SiegeCampGuiFactory() {
    }

    public static void init() {
    }

    public void open(Player player, DimBlockPos siegeCampPos, List<SiegeCampAttackInfo> possibleAttacks, byte momentum, int color) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        PacketSiegeCampInfo packet = new PacketSiegeCampInfo();
        packet.mSiegeCampPos = siegeCampPos;
        packet.mPossibleAttacks.addAll(possibleAttacks);
        packet.momentum = momentum;
        packet.color = color;
        WarForgeMod.NETWORK.sendTo(packet, serverPlayer);
    }

    @OnlyIn(Dist.CLIENT)
    public void openClient(PacketSiegeCampInfo data) {
        SiegeCampScreen.open(data);
    }
}
