package com.flansmod.warforge.common.network;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.factories.CreateFactionGuiFactory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class PacketOpenRecolour extends PacketBase {
    public static final CustomPacketPayload.Type<PacketOpenRecolour> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetopenrecolour"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketOpenRecolour> STREAM_CODEC =
        StreamCodec.ofMember(PacketOpenRecolour::encodeInto, buf -> { PacketOpenRecolour p = new PacketOpenRecolour(); p.decodeInto(buf); return p; });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public int colour = 0xFFFFFF;

    @Override
    public void encodeInto(FriendlyByteBuf data) {
        data.writeInt(colour);
    }

    @Override
    public void decodeInto(FriendlyByteBuf data) {
        colour = data.readInt();
    }

    @Override
    public void handleServerSide(ServerPlayer playerEntity) {
    }

    @Override
    public void handleClientSide(Player clientPlayer) {
        CreateFactionGuiFactory.INSTANCE.openRecolourScreen(colour);
    }
}
