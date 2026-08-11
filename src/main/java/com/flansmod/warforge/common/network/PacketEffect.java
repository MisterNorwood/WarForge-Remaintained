package com.flansmod.warforge.common.network;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.effect.EffectRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class PacketEffect extends PacketBase {
    public static final CustomPacketPayload.Type<PacketEffect> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packeteffect"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketEffect> STREAM_CODEC =
        StreamCodec.ofMember(PacketEffect::encodeInto, buf -> { PacketEffect p = new PacketEffect(); p.decodeInto(buf); return p; });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public double x, y, z;
    public String type = "";
    public String dataNBT = "";


    @Override
    public void encodeInto(FriendlyByteBuf data) {
        data.writeDouble(x);
        data.writeDouble(y);
        data.writeDouble(z);
        writeUTF(data, type);
        writeUTF(data, dataNBT);
    }

    @Override
    public void decodeInto(FriendlyByteBuf data) {
        this.x = data.readDouble();
        this.y = data.readDouble();
        this.z = data.readDouble();
        this.type = readUTF(data);
        this.dataNBT = readUTF(data);
    }

    @Override
    public void handleServerSide(ServerPlayer playerEntity) {
        WarForgeMod.LOGGER.error("Recieved effect packet on Server side!");
    }

    @Override
    public void handleClientSide(Player clientPlayer) {
        CompoundTag compound;
        try {
            compound = TagParser.parseTag(dataNBT);
        } catch (Exception e) {
            WarForgeMod.LOGGER.error("Malformed effect data NBT for " + type);
            return;
        }
        EffectRegistry.runClientEffect(type, clientPlayer, x, y, z, compound);
    }
}
