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

public class PacketRequestLeaderboardInfo extends PacketBase
{
	public static final CustomPacketPayload.Type<PacketRequestLeaderboardInfo> TYPE =
		new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetrequestleaderboardinfo"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PacketRequestLeaderboardInfo> STREAM_CODEC =
		StreamCodec.ofMember(PacketRequestLeaderboardInfo::encodeInto, buf -> { PacketRequestLeaderboardInfo p = new PacketRequestLeaderboardInfo(); p.decodeInto(buf); return p; });

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

	public FactionStat stat = FactionStat.TOTAL;
	public int firstIndex = 0;

	@Override
	public void encodeInto(FriendlyByteBuf data)
	{
		data.writeInt(firstIndex);
		data.writeInt(stat.ordinal());
	}

	@Override
	public void decodeInto(FriendlyByteBuf data)
	{
		firstIndex = data.readInt();
		stat = FactionStat.values()[data.readInt()];
	}

	@Override
	public void handleServerSide(ServerPlayer playerEntity)
	{
		PacketLeaderboardInfo packet = new PacketLeaderboardInfo();
		packet.info = WarForgeMod.LEADERBOARD.CreateInfo(firstIndex, stat, playerEntity.getUUID());
		WarForgeMod.NETWORK.sendTo(packet, playerEntity);
	}

	@Override
	public void handleClientSide(Player clientPlayer)
	{
		WarForgeMod.LOGGER.error("Received LeaderboardInfo request on client");
	}

}
