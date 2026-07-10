package com.flansmod.warforge.client;

import com.flansmod.warforge.common.network.PacketBase;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class ClientPacketHandler {
    private ClientPacketHandler() {}

    public static void handle(PacketBase msg) {
        msg.handleClientSide(Minecraft.getMinecraft().player);
    }

    public static void sendToServer(Packet<?> packet) {
        Minecraft.getMinecraft().player.connection.sendPacket(packet);
    }
}
