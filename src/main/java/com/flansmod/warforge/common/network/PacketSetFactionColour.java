package com.flansmod.warforge.common.network;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.WarForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class PacketSetFactionColour extends PacketBase
{
	public static final CustomPacketPayload.Type<PacketSetFactionColour> TYPE =
		new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetsetfactioncolour"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PacketSetFactionColour> STREAM_CODEC =
		StreamCodec.ofMember(PacketSetFactionColour::encodeInto, buf -> { PacketSetFactionColour p = new PacketSetFactionColour(); p.decodeInto(buf); return p; });

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

	public int mColour = 0xffffff;

	@Override
	public void encodeInto(FriendlyByteBuf data)
	{
		data.writeInt(mColour);
	}

	@Override
	public void decodeInto(FriendlyByteBuf data)
	{
		mColour = data.readInt();
	}

	@Override
	public void handleServerSide(ServerPlayer playerEntity)
	{
		WarForgeMod.FACTIONS.requestSetFactionColour(playerEntity, mColour);
	}

	@Override
	public void handleClientSide(Player clientPlayer)
	{

	}

}
