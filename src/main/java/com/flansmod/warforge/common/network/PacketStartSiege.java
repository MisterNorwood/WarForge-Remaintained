package com.flansmod.warforge.common.network;

import com.flansmod.warforge.common.DimBlockPos;
import com.flansmod.warforge.common.WarForgeMod;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;

public class PacketStartSiege extends PacketBase 
{
	public DimBlockPos mSiegeCampPos;
	public EnumFacing mDirection;
	
	@Override
	public void encodeInto(ChannelHandlerContext ctx, ByteBuf data) 
	{
		data.writeInt(mSiegeCampPos.mDim);
		data.writeInt(mSiegeCampPos.getX());
		data.writeInt(mSiegeCampPos.getY());
		data.writeInt(mSiegeCampPos.getZ());
		
		data.writeByte(mDirection.ordinal());
	}

	@Override
	public void decodeInto(ChannelHandlerContext ctx, ByteBuf data) 
	{
		int dim = data.readInt();
		int x = data.readInt();
		int y = data.readInt();
		int z = data.readInt();
		mSiegeCampPos = new DimBlockPos(dim, x, y, z);
		
		mDirection = EnumFacing.values()[data.readByte()];
	}

	@Override
	public void handleServerSide(EntityPlayerMP playerEntity) 
	{
		WarForgeMod.FACTIONS.RequestStartSiege(playerEntity, mSiegeCampPos, mDirection);
	}

	@Override
	public void handleClientSide(EntityPlayer clientPlayer)
	{
		WarForgeMod.LOGGER.error("Received start siege packet client side");
	}

}
