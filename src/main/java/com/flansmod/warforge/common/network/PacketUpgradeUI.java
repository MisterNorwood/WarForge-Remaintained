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

public class PacketUpgradeUI extends PacketBase {

    public static final CustomPacketPayload.Type<PacketUpgradeUI> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetupgradeui"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketUpgradeUI> STREAM_CODEC =
        StreamCodec.ofMember(PacketUpgradeUI::encodeInto, buf -> { PacketUpgradeUI p = new PacketUpgradeUI(); p.decodeInto(buf); return p; });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static PacketUpgradeUI latest = null;

    public UUID mFactionID = Faction.nullUuid;
    public String mFactionName = "";
    public int level = 0;
    public int color = 0xffff;
    public boolean outrankingOfficer = false;

    @Override
    public void encodeInto(FriendlyByteBuf data) {
        writeUUID(data, mFactionID);
        writeUTF(data, mFactionName);
        data.writeInt(level);
        data.writeInt(color);
        data.writeBoolean(outrankingOfficer);
    }

    @Override
    public void decodeInto(FriendlyByteBuf data) {
        mFactionID = readUUID(data);
        mFactionName = readUTF(data);
        level = data.readInt();
        color = data.readInt();
        outrankingOfficer = data.readBoolean();
    }

    @Override
    public void handleServerSide(ServerPlayer player) {
        WarForgeMod.LOGGER.error("Received a Upgrade UI packet on serverside");
    }

    @Override
    public void handleClientSide(Player clientPlayer) {
        latest = this;
        com.flansmod.warforge.common.factories.FactionUpgradeGuiFactory.INSTANCE.openUpgradeScreen();
    }
}
