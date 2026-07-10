package com.flansmod.warforge.common.network;

import com.flansmod.warforge.client.ClientFlagRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

public class PacketFlagManifest extends PacketBase {
    public List<String> flagIds = new ArrayList<String>();

    @Override
    public void encodeInto(ChannelHandlerContext ctx, ByteBuf data) {
        data.writeShort(flagIds.size());
        for (String flagId : flagIds) {
            writeUTF(data, flagId);
        }
    }

    @Override
    public void decodeInto(ChannelHandlerContext ctx, ByteBuf data) {
        flagIds.clear();
        int size = data.readShort();
        for (int i = 0; i < size; i++) {
            flagIds.add(readUTF(data));
        }
    }

    @Override
    public void handleServerSide(EntityPlayerMP playerEntity) {
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClientSide(EntityPlayer clientPlayer) {
        ClientFlagRegistry.setAvailableFlags(flagIds);
    }
}
