package com.flansmod.warforge.common.network;

import com.flansmod.warforge.client.ClientFlagRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class PacketFlagChunk extends PacketBase {
    public String flagId = "";
    public int width;
    public int height;
    public int partIndex;
    public int totalParts;
    public byte[] data = new byte[0];

    @Override
    public void encodeInto(ChannelHandlerContext ctx, ByteBuf out) {
        writeUTF(out, flagId);
        out.writeShort(width);
        out.writeShort(height);
        out.writeShort(partIndex);
        out.writeShort(totalParts);
        out.writeInt(data.length);
        out.writeBytes(data);
    }

    @Override
    public void decodeInto(ChannelHandlerContext ctx, ByteBuf in) {
        flagId = readUTF(in);
        width = in.readShort();
        height = in.readShort();
        partIndex = in.readShort();
        totalParts = in.readShort();
        int length = in.readInt();
        data = new byte[length];
        in.readBytes(data);
    }

    @Override
    public void handleServerSide(EntityPlayerMP playerEntity) {
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClientSide(EntityPlayer clientPlayer) {
        ClientFlagRegistry.receiveCustomFlagChunk(flagId, width, height, partIndex, totalParts, data);
    }

    @Override
    public boolean canUseCompression() {
        return true;
    }
}
