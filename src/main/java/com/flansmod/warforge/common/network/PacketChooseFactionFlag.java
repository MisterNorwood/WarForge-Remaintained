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

import java.util.UUID;

public class PacketChooseFactionFlag extends PacketBase {

    public static final CustomPacketPayload.Type<PacketChooseFactionFlag> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetchoosefactionflag"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketChooseFactionFlag> STREAM_CODEC =
        StreamCodec.ofMember(PacketChooseFactionFlag::encodeInto, buf -> { PacketChooseFactionFlag p = new PacketChooseFactionFlag(); p.decodeInto(buf); return p; });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public UUID factionId;
    public String flagId = "";

    @Override
    public void encodeInto(FriendlyByteBuf data) {
        writeUUID(data, factionId);
        writeUTF(data, flagId);
    }

    @Override
    public void decodeInto(FriendlyByteBuf data) {
        factionId = readUUID(data);
        flagId = readUTF(data);
    }

    @Override
    public void handleServerSide(ServerPlayer player) {
        WarForgeMod.FACTIONS.requestChooseFactionFlag(player, factionId, flagId);
    }

    @Override
    public void handleClientSide(Player clientPlayer) {
    }
}
