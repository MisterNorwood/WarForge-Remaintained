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

public class PacketNamePlateChange extends PacketBase {
    public static final CustomPacketPayload.Type<PacketNamePlateChange> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetnameplatechange"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketNamePlateChange> STREAM_CODEC =
        StreamCodec.ofMember(PacketNamePlateChange::encodeInto, buf -> { PacketNamePlateChange p = new PacketNamePlateChange(); p.decodeInto(buf); return p; });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public boolean isRemove = false;
    public String faction = "";
    public String name = "";
    public int color;

    @Override
    public void encodeInto(FriendlyByteBuf data) {
        data.writeBoolean(isRemove);
        writeUTF(data, faction);
        writeUTF(data, name);
        data.writeInt(color);
    }

    @Override
    public void decodeInto(FriendlyByteBuf data) {
        isRemove = data.readBoolean();
        faction = readUTF(data);
        name = readUTF(data);
        color = data.readInt();

    }

    @Override
    public void handleServerSide(ServerPlayer playerEntity) {

    }

    @Override
    public void handleClientSide(Player clientPlayer) {
        WarForgeMod.LOGGER.info("Recieved faction nametag for " + name + " [" + faction + "]");
        if (!isRemove)
            WarForgeMod.NAMETAG_CACHE.add(name, faction, color);
        else
            WarForgeMod.NAMETAG_CACHE.remove(name);


    }
}
