package com.flansmod.warforge.common.network;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.factories.CreateFactionGuiFactory;
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

public class PacketOpenCreateFaction extends PacketBase {
    public static final CustomPacketPayload.Type<PacketOpenCreateFaction> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetopencreatefaction"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketOpenCreateFaction> STREAM_CODEC =
        StreamCodec.ofMember(PacketOpenCreateFaction::encodeInto, buf -> { PacketOpenCreateFaction p = new PacketOpenCreateFaction(); p.decodeInto(buf); return p; });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static DimBlockPos pendingCitadelPos;

    public DimBlockPos pos;

    @Override
    public void encodeInto(FriendlyByteBuf data) {
        writeUTF(data, pos.dim.location().toString());
        data.writeInt(pos.getX());
        data.writeInt(pos.getY());
        data.writeInt(pos.getZ());
    }

    @Override
    public void decodeInto(FriendlyByteBuf data) {
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(readUTF(data)));
        int x = data.readInt();
        int y = data.readInt();
        int z = data.readInt();
        pos = new DimBlockPos(dim, x, y, z);
    }

    @Override
    public void handleServerSide(ServerPlayer playerEntity) {
        // server→client only
    }

    @Override
    public void handleClientSide(Player clientPlayer) {
        pendingCitadelPos = pos;
        CreateFactionGuiFactory.INSTANCE.openCreateScreen();
    }
}
