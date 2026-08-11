package com.flansmod.warforge.common.network;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.factories.FactionFlagSelectGuiFactory;
import com.flansmod.warforge.server.Faction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public class PacketOpenFlagSelect extends PacketBase {
    public static final CustomPacketPayload.Type<PacketOpenFlagSelect> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetopenflagselect"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketOpenFlagSelect> STREAM_CODEC =
        StreamCodec.ofMember(PacketOpenFlagSelect::encodeInto, buf -> { PacketOpenFlagSelect p = new PacketOpenFlagSelect(); p.decodeInto(buf); return p; });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public UUID factionId = Faction.nullUuid;

    @Override
    public void encodeInto(FriendlyByteBuf data) {
        writeUUID(data, factionId);
    }

    @Override
    public void decodeInto(FriendlyByteBuf data) {
        factionId = readUUID(data);
    }

    @Override
    public void handleServerSide(ServerPlayer playerEntity) {
    }

    @Override
    public void handleClientSide(Player clientPlayer) {
        FactionFlagSelectGuiFactory.INSTANCE.openClient(factionId);
    }
}
