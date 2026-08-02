package com.flansmod.warforge.common.network;

import com.flansmod.warforge.client.ClientTickHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class PacketTimeUpdates extends PacketBase
{
	public long msTimeOfNextSiegeDay = 0L;
	public long msTimeOfNextYieldDay = 0L;
	public long msServerNow = 0L;

	@Override
	public void encodeInto(FriendlyByteBuf data)
	{
		data.writeLong(msTimeOfNextSiegeDay);
		data.writeLong(msTimeOfNextYieldDay);
		data.writeLong(msServerNow);
	}

	@Override
	public void decodeInto(FriendlyByteBuf data)
	{
		msTimeOfNextSiegeDay = data.readLong();
		msTimeOfNextYieldDay = data.readLong();
		msServerNow = data.readLong();
	}

	@Override
	public void handleServerSide(ServerPlayer playerEntity)
	{

	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void handleClientSide(Player clientPlayer)
	{
		long clientNow = System.currentTimeMillis();
		ClientTickHandler.nextSiegeDayMs = clientNow + (msTimeOfNextSiegeDay - msServerNow);
		ClientTickHandler.nextYieldDayMs = clientNow + (msTimeOfNextYieldDay - msServerNow);
	}

}
