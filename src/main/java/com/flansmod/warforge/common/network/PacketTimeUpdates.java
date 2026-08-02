package com.flansmod.warforge.common.network;

import com.flansmod.warforge.client.ClientTickHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class PacketTimeUpdates extends PacketBase
{
	public long msUntilNextSiegeDay = 0L;
	public long msUntilNextYieldDay = 0L;

	@Override
	public void encodeInto(FriendlyByteBuf data)
	{
		data.writeLong(msUntilNextSiegeDay);
		data.writeLong(msUntilNextYieldDay);
	}

	@Override
	public void decodeInto(FriendlyByteBuf data)
	{
		msUntilNextSiegeDay = data.readLong();
		msUntilNextYieldDay = data.readLong();
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
		ClientTickHandler.nextSiegeDayMs = clientNow + msUntilNextSiegeDay;
		ClientTickHandler.nextYieldDayMs = clientNow + msUntilNextYieldDay;
	}

}
