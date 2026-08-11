package com.flansmod.warforge.common.network;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.factories.FactionMemberManagerGuiData;
import com.flansmod.warforge.server.Faction;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

public class PacketRequestMemberData extends PacketBase {

    public static final CustomPacketPayload.Type<PacketRequestMemberData> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetrequestmemberdata"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketRequestMemberData> STREAM_CODEC =
        StreamCodec.ofMember(PacketRequestMemberData::encodeInto, buf -> { PacketRequestMemberData p = new PacketRequestMemberData(); p.decodeInto(buf); return p; });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public FactionMemberManagerGuiData.Page page = FactionMemberManagerGuiData.Page.MEMBERS;

    @Override
    public void encodeInto(FriendlyByteBuf data) {
        data.writeByte(page.ordinal());
    }

    @Override
    public void decodeInto(FriendlyByteBuf data) {
        page = FactionMemberManagerGuiData.Page.values()[data.readByte()];
    }

    @Override
    public void handleServerSide(ServerPlayer player) {
        PacketMemberData response = buildResponse(player, page);
        WarForgeMod.NETWORK.sendTo(response, player);
    }

    @Override
    public void handleClientSide(Player clientPlayer) {
        WarForgeMod.LOGGER.error("Received PacketRequestMemberData on client side");
    }

    private static PacketMemberData buildResponse(ServerPlayer player, FactionMemberManagerGuiData.Page page) {
        PacketMemberData data = new PacketMemberData();
        data.page = page;

        Faction faction = WarForgeMod.FACTIONS.getFactionOfPlayer(player.getUUID());
        if (faction == null) {
            if (page == FactionMemberManagerGuiData.Page.INVITES) {
                for (Faction inviteFaction : WarForgeMod.FACTIONS.getFactionsWithOpenInvitesTo(player.getUUID())) {
                    FactionMemberManagerGuiData.InviteEntry invite = new FactionMemberManagerGuiData.InviteEntry();
                    invite.incoming = true;
                    invite.factionId = inviteFaction.uuid;
                    invite.factionColor = inviteFaction.colour;
                    invite.username = inviteFaction.name;
                    invite.playerId = inviteFaction.getLeaderId();
                    invite.inviterId = invite.playerId;
                    invite.inviterName = invite.playerId.equals(Faction.nullUuid) ? "" : resolveName(invite.playerId);
                    invite.invited = true;
                    invite.canAccept = true;
                    data.inviteCandidates.add(invite);
                }
                data.inviteCandidates.sort(Comparator.comparing(invite -> invite.username, String.CASE_INSENSITIVE_ORDER));
            }
            return data;
        }

        data.hasFaction = true;
        data.factionId = faction.uuid;
        data.factionName = faction.name;
        data.factionColor = faction.colour;
        data.viewerRole = determineViewerRole(faction, player.getUUID());
        data.canManageMembers = WarForgeMod.isOp(player) || data.viewerRole.ordinal() >= Faction.Role.OFFICER.ordinal();
        data.canInvitePlayers = data.canManageMembers;
        data.canManageAlliances = data.canManageMembers;
        data.allowAllyInteraction = faction.allowAllyInteraction;

        for (Map.Entry<UUID, Faction.PlayerData> entry : faction.members.entrySet()) {
            UUID memberId = entry.getKey();
            FactionMemberManagerGuiData.MemberEntry member = new FactionMemberManagerGuiData.MemberEntry();
            member.playerId = memberId;
            member.username = resolveName(memberId);
            member.role = entry.getValue().role;
            member.online = WarForgeMod.MC_SERVER.getPlayerList().getPlayer(memberId) != null;
            member.self = player.getUUID().equals(memberId);
            member.canKickOrLeave = member.self || WarForgeMod.isOp(player) || faction.isPlayerOutrankingOfficer(player.getUUID(), memberId);
            member.canPromote = data.viewerRole == Faction.Role.LEADER && member.role == Faction.Role.MEMBER && member.online;
            member.canDemote = data.viewerRole == Faction.Role.LEADER && member.role == Faction.Role.OFFICER && member.online;
            member.canTransferLeadership = (WarForgeMod.isOp(player) || data.viewerRole == Faction.Role.LEADER) && !member.self;
            data.members.add(member);
        }

        data.members.sort(Comparator
                .comparingInt((FactionMemberManagerGuiData.MemberEntry member) -> -member.role.ordinal())
                .thenComparing(member -> member.username, String.CASE_INSENSITIVE_ORDER));

        for (ServerPlayer onlinePlayer : WarForgeMod.MC_SERVER.getPlayerList().getPlayers()) {
            if (onlinePlayer.getUUID().equals(player.getUUID())) {
                continue;
            }
            if (WarForgeMod.FACTIONS.getFactionOfPlayer(onlinePlayer.getUUID()) != null) {
                continue;
            }
            FactionMemberManagerGuiData.InviteEntry invite = new FactionMemberManagerGuiData.InviteEntry();
            invite.playerId = onlinePlayer.getUUID();
            invite.username = onlinePlayer.getName().getString();
            invite.invited = faction.isInvitingPlayer(invite.playerId);
            invite.canInvite = data.canInvitePlayers && !invite.invited;
            data.inviteCandidates.add(invite);
        }

        data.inviteCandidates.sort(Comparator.comparing(invite -> invite.username, String.CASE_INSENSITIVE_ORDER));

        if (page == FactionMemberManagerGuiData.Page.ALLIANCES) {
            for (UUID allyId : faction.allies) {
                addAllianceEntry(data, WarForgeMod.FACTIONS.getFaction(allyId), FactionMemberManagerGuiData.AllianceEntry.KIND_ALLY);
            }
            for (UUID requesterId : faction.pendingAllianceRequests) {
                addAllianceEntry(data, WarForgeMod.FACTIONS.getFaction(requesterId), FactionMemberManagerGuiData.AllianceEntry.KIND_PENDING);
            }
            for (Faction invitable : WarForgeMod.FACTIONS.getAlliableFactions(faction)) {
                if (faction.pendingAllianceRequests.contains(invitable.uuid)) {
                    continue;
                }
                addAllianceEntry(data, invitable, FactionMemberManagerGuiData.AllianceEntry.KIND_INVITABLE);
            }
        }

        return data;
    }

    private static void addAllianceEntry(PacketMemberData data, Faction faction, byte kind) {
        if (faction == null) {
            return;
        }
        FactionMemberManagerGuiData.AllianceEntry entry = new FactionMemberManagerGuiData.AllianceEntry();
        entry.factionId = faction.uuid;
        entry.factionName = faction.name;
        entry.factionColor = faction.colour;
        entry.onlineCount = faction.onlinePlayerCount;
        entry.kind = kind;
        data.alliances.add(entry);
    }

    private static Faction.Role determineViewerRole(Faction faction, UUID playerId) {
        if (faction.isPlayerRoleInFaction(playerId, Faction.Role.LEADER)) {
            return Faction.Role.LEADER;
        }
        if (faction.isPlayerRoleInFaction(playerId, Faction.Role.OFFICER)) {
            return Faction.Role.OFFICER;
        }
        return Faction.Role.MEMBER;
    }

    private static String resolveName(UUID playerId) {
        GameProfile profile = WarForgeMod.MC_SERVER.getProfileCache().get(playerId).orElse(null);
        return profile == null ? "Unknown Player" : profile.getName();
    }
}
