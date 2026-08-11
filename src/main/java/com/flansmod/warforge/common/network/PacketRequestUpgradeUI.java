package com.flansmod.warforge.common.network;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.server.Faction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class PacketRequestUpgradeUI extends PacketRequestFactionInfo {

    public static final CustomPacketPayload.Type<PacketRequestUpgradeUI> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetrequestupgradeui"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketRequestUpgradeUI> STREAM_CODEC =
        StreamCodec.ofMember(PacketRequestUpgradeUI::encodeInto, buf -> { PacketRequestUpgradeUI p = new PacketRequestUpgradeUI(); p.decodeInto(buf); return p; });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    @Override
    public void handleServerSide(ServerPlayer player) {
        Faction faction = null;
        if (!mFactionIDRequest.equals(Faction.nullUuid)) {
            faction = WarForgeMod.FACTIONS.getFaction(mFactionIDRequest);
        } else if (!mFactionNameRequest.isEmpty()) {
            faction = WarForgeMod.FACTIONS.getFaction(mFactionNameRequest);
        } else {
            WarForgeMod.LOGGER.error("Player " + player.getName().getString() + " made a request for faction info with no valid key");
        }

        if (faction != null) {
            UUID playerUUID = player.getUUID();
            PacketUpgradeUI packet = new PacketUpgradeUI();
            packet.mFactionID = faction.uuid;
            packet.mFactionName = faction.name;
            packet.color = faction.colour;
            packet.level = faction.citadelLevel;
            packet.outrankingOfficer = faction.isPlayerRoleInFaction(playerUUID, Faction.Role.OFFICER);
            WarForgeMod.NETWORK.sendTo(packet, player);
        } else {
            WarForgeMod.LOGGER.error("Could not find faction for info");
        }
    }
}
