package com.flansmod.warforge.common.network;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.client.ClientTickHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class PacketTimeUpdates extends PacketBase
{
	public static final CustomPacketPayload.Type<PacketTimeUpdates> TYPE =
		new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packettimeupdates"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PacketTimeUpdates> STREAM_CODEC =
		StreamCodec.ofMember(PacketTimeUpdates::encodeInto, buf -> { PacketTimeUpdates p = new PacketTimeUpdates(); p.decodeInto(buf); return p; });

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

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
	public void handleClientSide(Player clientPlayer)
	{
		long clientNow = System.currentTimeMillis();
		ClientTickHandler.nextSiegeDayMs = clientNow + msUntilNextSiegeDay;
		ClientTickHandler.nextYieldDayMs = clientNow + msUntilNextYieldDay;
	}

}
