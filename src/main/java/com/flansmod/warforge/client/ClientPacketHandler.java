package com.flansmod.warforge.client;

import com.flansmod.warforge.common.network.PacketBase;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientPacketHandler {
    private ClientPacketHandler() {}

    public static void handle(PacketBase msg) {
        msg.handleClientSide(Minecraft.getInstance().player);
    }

    public static void sendToServer(Packet<?> packet) {
        Minecraft.getInstance().getConnection().send(packet);
    }
}
