package com.flansmod.warforge.common.network;

import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.util.DimChunkPos;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

public class PacketRequestClaimChunks extends PacketBase {
    public DimChunkPos center = new DimChunkPos(0, 0, 0);
    public int radius = 4;
    public boolean outlineOnly = false;

    @Override
    public void encodeInto(ChannelHandlerContext ctx, ByteBuf data) {
        data.writeInt(center.dim);
        data.writeInt(center.x);
        data.writeInt(center.z);
        data.writeByte(radius);
        data.writeBoolean(outlineOnly);
    }

    @Override
    public void decodeInto(ChannelHandlerContext ctx, ByteBuf data) {
        center = new DimChunkPos(data.readInt(), data.readInt(), data.readInt());
        radius = data.readByte();
        outlineOnly = data.readBoolean();
    }

    @Override
    public void handleServerSide(EntityPlayerMP playerEntity) {
        WarForgeMod.FACTIONS.sendClaimChunks(playerEntity, center, radius, outlineOnly);
    }

    @Override
    public void handleClientSide(EntityPlayer clientPlayer) {
        // noop
    }
}
