package com.flansmod.warforge.common.network;

import com.flansmod.warforge.common.CommonProxy;
import com.flansmod.warforge.common.DimBlockPos;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.server.Faction.Role;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

public class PacketFactionInfo extends PacketBase 
{
	// Cheeky hack to make it available to the GUI
	public static FactionDisplayInfo sLatestInfo = null;
	
	public FactionDisplayInfo mInfo;
	
	@Override
	public void encodeInto(ChannelHandlerContext ctx, ByteBuf data) 
	{
		if(mInfo != null)
		{
			data.writeBoolean(true);
			writeUUID(data, mInfo.mFactionID);
			writeUTF(data, mInfo.mFactionName);
			
			data.writeInt(mInfo.mNotoriety);
			data.writeInt(mInfo.mWealth);
			data.writeInt(mInfo.mLegacy);
			
			data.writeInt(mInfo.mNotorietyRank);
			data.writeInt(mInfo.mWealthRank);
			data.writeInt(mInfo.mLegacyRank);
			data.writeInt(mInfo.mTotalRank);
			
			data.writeInt(mInfo.mNumClaims);
			
			data.writeInt(mInfo.mCitadelPos.mDim);
			data.writeInt(mInfo.mCitadelPos.getX());
			data.writeInt(mInfo.mCitadelPos.getY());
			data.writeInt(mInfo.mCitadelPos.getZ());
			
			// Member list
			data.writeInt(mInfo.mMembers.size());
			for(int i = 0; i < mInfo.mMembers.size(); i++) 
			{
				writeUUID(data, mInfo.mMembers.get(i).mPlayerUUID);
				writeUTF(data, mInfo.mMembers.get(i).mPlayerName);
				data.writeInt(mInfo.mMembers.get(i).mRole.ordinal());
			}
			writeUUID(data, mInfo.mLeaderID);
		}
		else
		{
			data.writeBoolean(false);
		}
		
	}

	@Override
	public void decodeInto(ChannelHandlerContext ctx, ByteBuf data) 
	{
		boolean hasInfo = data.readBoolean();
		
		if(hasInfo)
		{
			mInfo = new FactionDisplayInfo();
			
			mInfo.mFactionID = readUUID(data);
			mInfo.mFactionName = readUTF(data);
			
			mInfo.mNotoriety = data.readInt();
			mInfo.mWealth = data.readInt();
			mInfo.mLegacy = data.readInt();
			
			mInfo.mNotorietyRank = data.readInt();
			mInfo.mWealthRank = data.readInt();
			mInfo.mLegacyRank = data.readInt();
			mInfo.mTotalRank = data.readInt();
			
			mInfo.mNumClaims = data.readInt();
			
			int dim =	data.readInt();
			int x =	data.readInt();
			int y =	data.readInt();
			int z =	data.readInt();
			mInfo.mCitadelPos = new DimBlockPos(dim, x, y, z);
			
			// Member list
			int count = data.readInt();
			for(int i = 0; i < count; i++)
			{
				PlayerDisplayInfo playerInfo = new PlayerDisplayInfo();
				playerInfo.mPlayerUUID = readUUID(data);
				playerInfo.mPlayerName = readUTF(data);
				playerInfo.mRole = Role.values()[data.readInt()];
				mInfo.mMembers.add(playerInfo);
			}
			mInfo.mLeaderID = readUUID(data);
		}
		else
			mInfo = null;
	}

	@Override
	public void handleServerSide(EntityPlayerMP playerEntity) 
	{
		WarForgeMod.LOGGER.error("Received FactionInfo on server");
	}

	@Override
	public void handleClientSide(EntityPlayer clientPlayer) 
	{
		sLatestInfo = mInfo;
		clientPlayer.openGui(
				WarForgeMod.INSTANCE, 
				CommonProxy.GUI_TYPE_FACTION_INFO, 
				clientPlayer.world, 
				clientPlayer.getPosition().getX(),
				clientPlayer.getPosition().getY(),
				clientPlayer.getPosition().getZ());
	}

}
