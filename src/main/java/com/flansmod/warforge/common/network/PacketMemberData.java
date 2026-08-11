package com.flansmod.warforge.common.network;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.factories.FactionMemberManagerGuiData;
import com.flansmod.warforge.common.factories.FactionMemberManagerGuiFactory;
import com.flansmod.warforge.server.Faction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PacketMemberData extends PacketBase {

    public static final CustomPacketPayload.Type<PacketMemberData> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetmemberdata"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketMemberData> STREAM_CODEC =
        StreamCodec.ofMember(PacketMemberData::encodeInto, buf -> { PacketMemberData p = new PacketMemberData(); p.decodeInto(buf); return p; });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static PacketMemberData latest = null;

    public FactionMemberManagerGuiData.Page page = FactionMemberManagerGuiData.Page.MEMBERS;
    public boolean hasFaction = false;
    public UUID factionId = Faction.nullUuid;
    public String factionName = "";
    public int factionColor = 0x4E8E87;
    public Faction.Role viewerRole = Faction.Role.MEMBER;
    public boolean canManageMembers = false;
    public boolean canInvitePlayers = false;
    public boolean canManageAlliances = false;
    public boolean allowAllyInteraction = false;
    public final List<FactionMemberManagerGuiData.MemberEntry> members = new ArrayList<>();
    public final List<FactionMemberManagerGuiData.InviteEntry> inviteCandidates = new ArrayList<>();
    public final List<FactionMemberManagerGuiData.AllianceEntry> alliances = new ArrayList<>();

    @Override
    public void encodeInto(FriendlyByteBuf data) {
        data.writeByte(page.ordinal());
        data.writeBoolean(hasFaction);
        writeUUID(data, factionId);
        writeUTF(data, factionName);
        data.writeInt(factionColor);
        data.writeByte(viewerRole.ordinal());
        data.writeBoolean(canManageMembers);
        data.writeBoolean(canInvitePlayers);
        data.writeBoolean(canManageAlliances);
        data.writeBoolean(allowAllyInteraction);

        data.writeShort(members.size());
        for (FactionMemberManagerGuiData.MemberEntry member : members) {
            writeUUID(data, member.playerId);
            writeUTF(data, member.username);
            data.writeByte(member.role.ordinal());
            data.writeBoolean(member.online);
            data.writeBoolean(member.self);
            data.writeBoolean(member.canKickOrLeave);
            data.writeBoolean(member.canPromote);
            data.writeBoolean(member.canDemote);
            data.writeBoolean(member.canTransferLeadership);
        }

        data.writeShort(inviteCandidates.size());
        for (FactionMemberManagerGuiData.InviteEntry invite : inviteCandidates) {
            writeUUID(data, invite.playerId);
            writeUTF(data, invite.username);
            writeUUID(data, invite.factionId);
            data.writeInt(invite.factionColor);
            writeUUID(data, invite.inviterId);
            writeUTF(data, invite.inviterName);
            data.writeBoolean(invite.incoming);
            data.writeBoolean(invite.invited);
            data.writeBoolean(invite.canInvite);
            data.writeBoolean(invite.canAccept);
        }

        data.writeShort(alliances.size());
        for (FactionMemberManagerGuiData.AllianceEntry ally : alliances) {
            writeUUID(data, ally.factionId);
            writeUTF(data, ally.factionName);
            data.writeInt(ally.factionColor);
            data.writeShort(ally.onlineCount);
            data.writeByte(ally.kind);
        }
    }

    @Override
    public void decodeInto(FriendlyByteBuf data) {
        page = FactionMemberManagerGuiData.Page.values()[data.readByte()];
        hasFaction = data.readBoolean();
        factionId = readUUID(data);
        factionName = readUTF(data);
        factionColor = data.readInt();
        viewerRole = Faction.Role.values()[data.readByte()];
        canManageMembers = data.readBoolean();
        canInvitePlayers = data.readBoolean();
        canManageAlliances = data.readBoolean();
        allowAllyInteraction = data.readBoolean();

        int memberCount = data.readShort();
        for (int i = 0; i < memberCount; i++) {
            FactionMemberManagerGuiData.MemberEntry member = new FactionMemberManagerGuiData.MemberEntry();
            member.playerId = readUUID(data);
            member.username = readUTF(data);
            member.role = Faction.Role.values()[data.readByte()];
            member.online = data.readBoolean();
            member.self = data.readBoolean();
            member.canKickOrLeave = data.readBoolean();
            member.canPromote = data.readBoolean();
            member.canDemote = data.readBoolean();
            member.canTransferLeadership = data.readBoolean();
            members.add(member);
        }

        int inviteCount = data.readShort();
        for (int i = 0; i < inviteCount; i++) {
            FactionMemberManagerGuiData.InviteEntry invite = new FactionMemberManagerGuiData.InviteEntry();
            invite.playerId = readUUID(data);
            invite.username = readUTF(data);
            invite.factionId = readUUID(data);
            invite.factionColor = data.readInt();
            invite.inviterId = readUUID(data);
            invite.inviterName = readUTF(data);
            invite.incoming = data.readBoolean();
            invite.invited = data.readBoolean();
            invite.canInvite = data.readBoolean();
            invite.canAccept = data.readBoolean();
            inviteCandidates.add(invite);
        }

        int allianceCount = data.readShort();
        for (int i = 0; i < allianceCount; i++) {
            FactionMemberManagerGuiData.AllianceEntry ally = new FactionMemberManagerGuiData.AllianceEntry();
            ally.factionId = readUUID(data);
            ally.factionName = readUTF(data);
            ally.factionColor = data.readInt();
            ally.onlineCount = data.readShort();
            ally.kind = data.readByte();
            alliances.add(ally);
        }
    }

    @Override
    public void handleServerSide(ServerPlayer player) {
        WarForgeMod.LOGGER.error("Received PacketMemberData on server side");
    }

    @Override
    public void handleClientSide(Player clientPlayer) {
        latest = this;
        FactionMemberManagerGuiFactory.INSTANCE.openMemberScreen();
    }
}
