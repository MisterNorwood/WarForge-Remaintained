package com.flansmod.warforge.common.network;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.server.Leaderboard.FactionStat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class PacketLeaderboardInfo extends PacketBase
{
	public static final CustomPacketPayload.Type<PacketLeaderboardInfo> TYPE =
		new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetleaderboardinfo"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PacketLeaderboardInfo> STREAM_CODEC =
		StreamCodec.ofMember(PacketLeaderboardInfo::encodeInto, buf -> { PacketLeaderboardInfo p = new PacketLeaderboardInfo(); p.decodeInto(buf); return p; });

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

	public static LeaderboardInfo sLatestInfo = null;

	public LeaderboardInfo info;

	@Override
	public void encodeInto(FriendlyByteBuf data)
	{
		data.writeInt(info.firstIndex);
		data.writeInt(info.stat.ordinal());

		PacketFactionInfo subPacket = new PacketFactionInfo();
		subPacket.info = info.myFaction;
		subPacket.encodeInto(data);

		for(int i = 0; i < LeaderboardInfo.NUM_LEADERBOARD_ENTRIES_PER_PAGE; i++)
		{
			subPacket.info = info.factionInfos[i];
			subPacket.encodeInto(data);
		}
	}

	@Override
	public void decodeInto(FriendlyByteBuf data)
	{
		info = new LeaderboardInfo();

		info.firstIndex = data.readInt();
		info.stat = FactionStat.values()[data.readInt()];

		PacketFactionInfo subPacket = new PacketFactionInfo();
		subPacket.decodeInto(data);
		info.myFaction = subPacket.info;

		for(int i = 0; i < LeaderboardInfo.NUM_LEADERBOARD_ENTRIES_PER_PAGE; i++)
		{
			subPacket.decodeInto(data);
			info.factionInfos[i] = subPacket.info;
		}
	}

	@Override
	public void handleServerSide(ServerPlayer playerEntity)
	{
		WarForgeMod.LOGGER.error("Received LeaderboardInfo on server");
	}

	@Override
	public void handleClientSide(Player clientPlayer)
	{
		sLatestInfo = info;
	}
}
