package com.flansmod.warforge.common.network;

import com.flansmod.warforge.common.CommonProxy;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.server.Leaderboard.FactionStat;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

public class PacketLeaderboardInfo extends PacketBase
{
	// Cheeky hack to make it available to the GUI
	public static LeaderboardInfo sLatestInfo = null;
	
	public LeaderboardInfo mInfo;

	@Override
	public void encodeInto(ChannelHandlerContext ctx, ByteBuf data) 
	{
		data.writeInt(mInfo.firstIndex);
		data.writeInt(mInfo.stat.ordinal());
		
		PacketFactionInfo subPacket = new PacketFactionInfo();
		subPacket.mInfo = mInfo.mMyFaction;
		subPacket.encodeInto(ctx, data);
		
		for(int i = 0; i < LeaderboardInfo.NUM_LEADERBOARD_ENTRIES_PER_PAGE; i++)
		{
			subPacket.mInfo = mInfo.mFactionInfos[i];
			subPacket.encodeInto(ctx, data);
		}
	}

	@Override
	public void decodeInto(ChannelHandlerContext ctx, ByteBuf data) 
	{
		mInfo = new LeaderboardInfo();
		
		mInfo.firstIndex = data.readInt();
		mInfo.stat = FactionStat.values()[data.readInt()];
		
		PacketFactionInfo subPacket = new PacketFactionInfo();
		subPacket.decodeInto(ctx, data);
		mInfo.mMyFaction = subPacket.mInfo;
		
		for(int i = 0; i < LeaderboardInfo.NUM_LEADERBOARD_ENTRIES_PER_PAGE; i++)
		{
			subPacket.decodeInto(ctx, data);
			mInfo.mFactionInfos[i] = subPacket.mInfo;
		}
	}

	@Override
	public void handleServerSide(EntityPlayerMP playerEntity) 
	{
		WarForgeMod.LOGGER.error("Received LeaderboardInfo on server");
	}

	@Override
	public void handleClientSide(EntityPlayer clientPlayer) 
	{
		sLatestInfo = mInfo;
		clientPlayer.openGui(
				WarForgeMod.INSTANCE, 
				CommonProxy.GUI_TYPE_LEADERBOARD, 
				clientPlayer.world, 
				clientPlayer.getPosition().getX(),
				clientPlayer.getPosition().getY(),
				clientPlayer.getPosition().getZ());
	}
}
