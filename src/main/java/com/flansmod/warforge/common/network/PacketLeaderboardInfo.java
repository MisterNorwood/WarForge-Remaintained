package com.flansmod.warforge.common.network;

import com.flansmod.warforge.common.CommonProxy;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.Tags;
import com.flansmod.warforge.server.Leaderboard.FactionStat;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class PacketLeaderboardInfo extends PacketBase
{
	// Cheeky hack to make it available to the GUI
	public static LeaderboardInfo sLatestInfo = null;
	
	public LeaderboardInfo info;

	@Override
	public void encodeInto(ChannelHandlerContext ctx, ByteBuf data) 
	{
		data.writeInt(info.firstIndex);
		data.writeInt(info.stat.ordinal());
		
		PacketFactionInfo subPacket = new PacketFactionInfo();
		subPacket.info = info.myFaction;
		subPacket.encodeInto(ctx, data);
		
		for(int i = 0; i < LeaderboardInfo.NUM_LEADERBOARD_ENTRIES_PER_PAGE; i++)
		{
			subPacket.info = info.factionInfos[i];
			subPacket.encodeInto(ctx, data);
		}
	}

	@Override
	public void decodeInto(ChannelHandlerContext ctx, ByteBuf data) 
	{
		info = new LeaderboardInfo();
		
		info.firstIndex = data.readInt();
		info.stat = FactionStat.values()[data.readInt()];
		
		PacketFactionInfo subPacket = new PacketFactionInfo();
		subPacket.decodeInto(ctx, data);
		info.myFaction = subPacket.info;
		
		for(int i = 0; i < LeaderboardInfo.NUM_LEADERBOARD_ENTRIES_PER_PAGE; i++)
		{
			subPacket.decodeInto(ctx, data);
			info.factionInfos[i] = subPacket.info;
		}
	}

	@Override
	public void handleServerSide(EntityPlayerMP playerEntity) 
	{
		WarForgeMod.LOGGER.error("Received LeaderboardInfo on server");
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void handleClientSide(EntityPlayer clientPlayer)
	{
		sLatestInfo = info;
		clientPlayer.openGui(
				WarForgeMod.INSTANCE, 
				CommonProxy.GUI_TYPE_LEADERBOARD, 
				clientPlayer.world, 
				clientPlayer.getPosition().getX(),
				clientPlayer.getPosition().getY(),
				clientPlayer.getPosition().getZ());
	}
}
