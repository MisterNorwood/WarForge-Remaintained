package com.flansmod.warforge.common.network;

import com.flansmod.warforge.client.ClientTickHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class PacketTimeUpdates extends PacketBase
{
	public long msTimeOfNextSiegeDay = 0L;
	public long msTimeOfNextYieldDay = 0L;
	
	@Override
	public void encodeInto(ChannelHandlerContext ctx, ByteBuf data) 
	{
		data.writeLong(msTimeOfNextSiegeDay);
		data.writeLong(msTimeOfNextYieldDay);
	}

	@Override
	public void decodeInto(ChannelHandlerContext ctx, ByteBuf data) 
	{
		msTimeOfNextSiegeDay = data.readLong();
		msTimeOfNextYieldDay = data.readLong();
	}

	@Override
	public void handleServerSide(EntityPlayerMP playerEntity) 
	{
		
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void handleClientSide(EntityPlayer clientPlayer)
	{
		ClientTickHandler.nextSiegeDayMs = msTimeOfNextSiegeDay;
		ClientTickHandler.nextYieldDayMs = msTimeOfNextYieldDay;
	}

}
