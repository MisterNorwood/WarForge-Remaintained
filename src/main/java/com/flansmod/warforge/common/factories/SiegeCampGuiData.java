package com.flansmod.warforge.common.factories;

import com.flansmod.warforge.common.network.PacketSiegeCampInfo;
import com.flansmod.warforge.common.network.SiegeCampAttackInfo;
import com.flansmod.warforge.common.util.DimBlockPos;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

public class SiegeCampGuiData {
    public final DimBlockPos siegeCampPos;
    public final List<SiegeCampAttackInfo> possibleAttacks;
    public final byte momentum;
    public final int color;

    public SiegeCampGuiData(Player player, DimBlockPos siegeCampPos, List<SiegeCampAttackInfo> possibleAttacks, byte momentum, int color) {
        this.siegeCampPos = siegeCampPos;
        this.possibleAttacks = new ArrayList<>(possibleAttacks);
        this.momentum = momentum;
        this.color = color;
    }

    public PacketSiegeCampInfo toPacket() {
        PacketSiegeCampInfo packet = new PacketSiegeCampInfo();
        packet.mSiegeCampPos = siegeCampPos;
        packet.mPossibleAttacks.addAll(possibleAttacks);
        packet.momentum = momentum;
        packet.color = color;
        return packet;
    }
}
