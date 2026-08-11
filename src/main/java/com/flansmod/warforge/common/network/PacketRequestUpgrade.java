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

public class PacketRequestUpgrade extends PacketBase {

    public static final CustomPacketPayload.Type<PacketRequestUpgrade> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetrequestupgrade"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketRequestUpgrade> STREAM_CODEC =
        StreamCodec.ofMember(PacketRequestUpgrade::encodeInto, buf -> { PacketRequestUpgrade p = new PacketRequestUpgrade(); p.decodeInto(buf); return p; });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public UUID factionID = Faction.nullUuid;

    @Override
    public void encodeInto(FriendlyByteBuf data) {
        writeUUID(data, factionID);
    }

    @Override
    public void decodeInto(FriendlyByteBuf data) {
        factionID = readUUID(data);
    }

    @Override
    public void handleServerSide(ServerPlayer player) {
        WarForgeMod.FACTIONS.requestLevelUp(player, factionID);
    }

    @Override
    public void handleClientSide(Player clientPlayer) {

    }
}
