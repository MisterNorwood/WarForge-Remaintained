package com.flansmod.warforge.common.network;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.server.Faction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class PacketDisbandFaction extends PacketBase
{
	public static final CustomPacketPayload.Type<PacketDisbandFaction> TYPE =
		new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetdisbandfaction"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PacketDisbandFaction> STREAM_CODEC =
		StreamCodec.ofMember(PacketDisbandFaction::encodeInto, buf -> { PacketDisbandFaction p = new PacketDisbandFaction(); p.decodeInto(buf); return p; });

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

	@Override
	public void encodeInto(FriendlyByteBuf data)
	{
	}

	@Override
	public void decodeInto(FriendlyByteBuf data)
	{
	}

	@Override
	public void handleServerSide(ServerPlayer playerEntity)
	{
		WarForgeMod.FACTIONS.requestDisbandFaction(playerEntity, Faction.nullUuid);
	}

	@Override
	public void handleClientSide(Player clientPlayer)
	{
	}

}
