package com.flansmod.warforge.common.network;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.factories.FactionInsuranceGuiFactory;
import com.flansmod.warforge.server.Faction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PacketInsuranceData extends PacketBase {

    public static final CustomPacketPayload.Type<PacketInsuranceData> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetinsurancedata"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketInsuranceData> STREAM_CODEC =
        StreamCodec.ofMember(PacketInsuranceData::encodeInto, buf -> { PacketInsuranceData p = new PacketInsuranceData(); p.decodeInto(buf); return p; });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static PacketInsuranceData latest = null;

    public boolean hasFaction = false;
    public UUID factionId = Faction.nullUuid;
    public String factionName = "";
    public int factionColor = 0xFFFFFF;
    public boolean canWithdraw = false;
    public List<ItemStack> stacks = new ArrayList<>();

    @Override
    public void encodeInto(FriendlyByteBuf data) {
        RegistryFriendlyByteBuf rbuf = (RegistryFriendlyByteBuf) data;
        data.writeBoolean(hasFaction);
        writeUUID(data, factionId);
        writeUTF(data, factionName);
        data.writeInt(factionColor);
        data.writeBoolean(canWithdraw);
        data.writeShort(stacks.size());
        for (ItemStack stack : stacks) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(rbuf, stack);
        }
    }

    @Override
    public void decodeInto(FriendlyByteBuf data) {
        RegistryFriendlyByteBuf rbuf = (RegistryFriendlyByteBuf) data;
        hasFaction = data.readBoolean();
        factionId = readUUID(data);
        factionName = readUTF(data);
        factionColor = data.readInt();
        canWithdraw = data.readBoolean();
        int count = data.readShort();
        stacks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            stacks.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(rbuf));
        }
    }

    @Override
    public void handleServerSide(ServerPlayer player) {
        WarForgeMod.LOGGER.error("Received PacketInsuranceData on server side");
    }

    @Override
    public void handleClientSide(Player clientPlayer) {
        latest = this;
        FactionInsuranceGuiFactory.INSTANCE.openInsuranceScreen();
    }
}
