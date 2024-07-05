package com.flansmod.warforge.common.network;

import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.server.Leaderboard.FactionStat;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

public class PacketRequestLeaderboardInfo extends PacketBase
{
	public FactionStat mStat = FactionStat.TOTAL;
	public int mFirstIndex = 0;
	
	@Override
	public void encodeInto(ChannelHandlerContext ctx, ByteBuf data) 
	{
		data.writeInt(mFirstIndex);
		data.writeInt(mStat.ordinal());
	}

	@Override
	public void decodeInto(ChannelHandlerContext ctx, ByteBuf data) 
	{
		mFirstIndex = data.readInt();
		mStat = FactionStat.values()[data.readInt()];
	}

	@Override
	public void handleServerSide(EntityPlayerMP playerEntity) 
	{
		PacketLeaderboardInfo packet = new PacketLeaderboardInfo();
		packet.mInfo = WarForgeMod.LEADERBOARD.CreateInfo(mFirstIndex, mStat, playerEntity.getUniqueID());
		WarForgeMod.NETWORK.sendTo(packet, playerEntity);
	}

	@Override
	public void handleClientSide(EntityPlayer clientPlayer) 
	{
		WarForgeMod.LOGGER.error("Received LeaderboardInfo request on client");
	}
	
}
