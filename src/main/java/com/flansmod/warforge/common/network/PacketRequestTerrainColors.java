package com.flansmod.warforge.common.network;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.util.DimChunkPos;
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

public class PacketRequestTerrainColors extends PacketBase {
    public static final CustomPacketPayload.Type<PacketRequestTerrainColors> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetrequestterraincolors"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketRequestTerrainColors> STREAM_CODEC =
        StreamCodec.ofMember(PacketRequestTerrainColors::encodeInto, buf -> { PacketRequestTerrainColors p = new PacketRequestTerrainColors(); p.decodeInto(buf); return p; });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public DimChunkPos center = new DimChunkPos(Level.OVERWORLD, 0, 0);
    public int radius = 4;

    @Override
    public void encodeInto(FriendlyByteBuf data) {
        data.writeUtf(center.dim.location().toString());
        data.writeInt(center.x);
        data.writeInt(center.z);
        data.writeByte(radius);
    }

    @Override
    public void decodeInto(FriendlyByteBuf data) {
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(data.readUtf()));
        center = new DimChunkPos(dim, data.readInt(), data.readInt());
        radius = data.readByte();
    }

    @Override
    public void handleServerSide(ServerPlayer playerEntity) {
        WarForgeMod.FACTIONS.sendTerrainColors(playerEntity, center, radius);
    }

    @Override
    public void handleClientSide(Player clientPlayer) {
    }
}
