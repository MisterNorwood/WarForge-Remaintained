package com.flansmod.warforge.common.network;

import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.server.Faction;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

public class PacketDisbandFaction extends PacketBase 
{

	// No data, you can only use this to disband your faction
	@Override
	public void encodeInto(ChannelHandlerContext ctx, ByteBuf data) 
	{
	}

	@Override
	public void decodeInto(ChannelHandlerContext ctx, ByteBuf data) 
	{
	}

	@Override
	public void handleServerSide(EntityPlayerMP playerEntity) 
	{
		WarForgeMod.FACTIONS.RequestDisbandFaction(playerEntity, Faction.NULL);
	}

	@Override
	public void handleClientSide(EntityPlayer clientPlayer) 
	{
	}

}
