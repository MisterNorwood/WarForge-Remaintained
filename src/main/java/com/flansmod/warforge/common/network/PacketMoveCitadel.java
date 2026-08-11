package com.flansmod.warforge.common.network;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.util.DimBlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class PacketMoveCitadel extends PacketBase
{
	public static final CustomPacketPayload.Type<PacketMoveCitadel> TYPE =
		new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetmovecitadel"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PacketMoveCitadel> STREAM_CODEC =
		StreamCodec.ofMember(PacketMoveCitadel::encodeInto, buf -> { PacketMoveCitadel p = new PacketMoveCitadel(); p.decodeInto(buf); return p; });

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

	public DimBlockPos pos = DimBlockPos.ZERO;

	@Override
	public void encodeInto(FriendlyByteBuf data)
	{
		data.writeUtf(pos.dim.location().toString());
		data.writeInt(pos.getX());
		data.writeInt(pos.getY());
		data.writeInt(pos.getZ());
	}

	@Override
	public void decodeInto(FriendlyByteBuf data)
	{
		ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(data.readUtf()));
		pos = new DimBlockPos(dim, data.readInt(), data.readInt(), data.readInt());
	}

	@Override
	public void handleServerSide(ServerPlayer player)
	{
		WarForgeMod.FACTIONS.requestMoveCitadel(player, pos);
	}

	@Override
	public void handleClientSide(Player clientPlayer)
	{

	}

}
