package com.flansmod.warforge.common.network;

import com.flansmod.warforge.common.WarForgeMod;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

public class PacketSetFactionColour extends PacketBase
{
	public int mColour = 0xffffff;
	
	@Override
	public void encodeInto(ChannelHandlerContext ctx, ByteBuf data) 
	{
		data.writeInt(mColour);
	}

	@Override
	public void decodeInto(ChannelHandlerContext ctx, ByteBuf data) 
	{
		mColour = data.readInt();
	}

	@Override
	public void handleServerSide(EntityPlayerMP playerEntity) 
	{
		WarForgeMod.FACTIONS.RequestSetFactionColour(playerEntity, mColour);
	}

	@Override
	public void handleClientSide(EntityPlayer clientPlayer) 
	{
		
	}

}
