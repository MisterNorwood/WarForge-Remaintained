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

import java.util.UUID;

public class PacketRequestFactionInfo extends PacketBase
{
	public static final CustomPacketPayload.Type<PacketRequestFactionInfo> TYPE =
		new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetrequestfactioninfo"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PacketRequestFactionInfo> STREAM_CODEC =
		StreamCodec.ofMember(PacketRequestFactionInfo::encodeInto, buf -> { PacketRequestFactionInfo p = new PacketRequestFactionInfo(); p.decodeInto(buf); return p; });

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

	public UUID mFactionIDRequest = Faction.nullUuid;
	public String mFactionNameRequest = "";

	@Override
	public void encodeInto(FriendlyByteBuf data)
	{
		writeUUID(data, mFactionIDRequest);
		writeUTF(data, mFactionNameRequest);
	}

	@Override
	public void decodeInto(FriendlyByteBuf data)
	{
		mFactionIDRequest = readUUID(data);
		mFactionNameRequest = readUTF(data);
	}

	@Override
	public void handleServerSide(ServerPlayer playerEntity)
	{
		Faction faction = null;
		if(!mFactionIDRequest.equals(Faction.nullUuid))
		{
			faction = WarForgeMod.FACTIONS.getFaction(mFactionIDRequest);
		}
		else if(!mFactionNameRequest.isEmpty())
		{
			faction = WarForgeMod.FACTIONS.getFaction(mFactionNameRequest);
		}
		else
		{
			WarForgeMod.LOGGER.error("Player " + playerEntity.getName().getString() + " made a request for faction info with no valid key");
		}

		if(faction != null)
		{
			PacketFactionInfo packet = new PacketFactionInfo();
			packet.info = faction.createInfo();
			WarForgeMod.NETWORK.sendTo(packet, playerEntity);
		}
		else
		{
			WarForgeMod.LOGGER.error("Could not find faction for info");
		}
	}

	@Override
	public void handleClientSide(Player clientPlayer)
	{
		WarForgeMod.LOGGER.error("Received a faction info request client side");
	}
}
