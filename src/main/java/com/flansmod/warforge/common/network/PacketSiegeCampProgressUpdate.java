package com.flansmod.warforge.common.network;

import com.flansmod.warforge.common.DimBlockPos;
import com.flansmod.warforge.common.WarForgeMod;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

public class PacketSiegeCampProgressUpdate extends PacketBase
{
	public SiegeCampProgressInfo mInfo;
	
	@Override
	public void encodeInto(ChannelHandlerContext ctx, ByteBuf data) 
	{
		// Attack
		data.writeInt(mInfo.mAttackingPos.mDim);
		data.writeInt(mInfo.mAttackingPos.getX());
		data.writeInt(mInfo.mAttackingPos.getY());
		data.writeInt(mInfo.mAttackingPos.getZ());
		data.writeInt(mInfo.mAttackingColour);
		writeUTF(data, mInfo.mAttackingName);
		
		// Defend
		data.writeInt(mInfo.mDefendingPos.mDim);
		data.writeInt(mInfo.mDefendingPos.getX());
		data.writeInt(mInfo.mDefendingPos.getY());
		data.writeInt(mInfo.mDefendingPos.getZ());
		data.writeInt(mInfo.mDefendingColour);
		writeUTF(data, mInfo.mDefendingName);
		
		data.writeInt(mInfo.mProgress);
		data.writeInt(mInfo.mPreviousProgress);
		data.writeInt(mInfo.mCompletionPoint);
	}

	@Override
	public void decodeInto(ChannelHandlerContext ctx, ByteBuf data) 
	{
		mInfo = new SiegeCampProgressInfo();
		
		// Attacking
		int dim = data.readInt();
		int x = data.readInt();
		int y = data.readInt();
		int z = data.readInt();
		mInfo.mAttackingPos = new DimBlockPos(dim, x, y, z);
		mInfo.mAttackingColour = data.readInt();
		mInfo.mAttackingName = readUTF(data);
		
		// Defending
		dim = data.readInt();
		x = data.readInt();
		y = data.readInt();
		z = data.readInt();
		mInfo.mDefendingPos = new DimBlockPos(dim, x, y, z);
		mInfo.mDefendingColour = data.readInt();
		mInfo.mDefendingName = readUTF(data);
		
		mInfo.mProgress = data.readInt();
		mInfo.mPreviousProgress = data.readInt();
		mInfo.mCompletionPoint = data.readInt();
	}

	@Override
	public void handleServerSide(EntityPlayerMP playerEntity) 
	{
		WarForgeMod.LOGGER.error("Received siege info on server");
	}

	@Override
	public void handleClientSide(EntityPlayer clientPlayer) 
	{
		WarForgeMod.proxy.UpdateSiegeInfo(mInfo);
	}

}
