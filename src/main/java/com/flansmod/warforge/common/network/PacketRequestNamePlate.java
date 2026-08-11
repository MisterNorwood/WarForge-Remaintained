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

public class PacketRequestNamePlate extends PacketBase {
    public static final CustomPacketPayload.Type<PacketRequestNamePlate> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetrequestnameplate"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketRequestNamePlate> STREAM_CODEC =
        StreamCodec.ofMember(PacketRequestNamePlate::encodeInto, buf -> { PacketRequestNamePlate p = new PacketRequestNamePlate(); p.decodeInto(buf); return p; });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public String name;

    @Override
    public void encodeInto(FriendlyByteBuf data) {
        writeUTF(data, name);
    }

    @Override
    public void decodeInto(FriendlyByteBuf data) {
        name = readUTF(data);
    }

    @Override
    public void handleServerSide(ServerPlayer playerEntity) {
        WarForgeMod.FACTIONS.requestNamePlateCacheEntry(playerEntity, name);
    }

    @Override
    public void handleClientSide(Player clientPlayer) {

    }
}
