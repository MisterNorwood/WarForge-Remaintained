package com.flansmod.warforge.common.factories;

import brachy.modularui.factory.GuiData;
import com.flansmod.warforge.server.Faction;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FactionMemberManagerGuiData extends GuiData {
    public enum Page {
        MEMBERS,
        INVITES,
        ALLIANCES
    }

    public static class AllianceEntry {
        public static final byte KIND_ALLY = 0;        // a current ally (offer Break)
        public static final byte KIND_PENDING = 1;     // an incoming alliance request (offer Accept/Decline)
        public static final byte KIND_INVITABLE = 2;   // a faction we could invite (offer Invite)

        public UUID factionId = Faction.nullUuid;
        public String factionName = "";
        public int factionColor = 0x4E8E87;
        public int onlineCount;
        public byte kind = KIND_INVITABLE;
    }

    public static class MemberEntry {
        public UUID playerId = Faction.nullUuid;
        public String username = "";
        public Faction.Role role = Faction.Role.MEMBER;
        public boolean online;
        public boolean self;
        public boolean canKickOrLeave;
        public boolean canPromote;
        public boolean canDemote;
        public boolean canTransferLeadership;
    }

    public static class InviteEntry {
        public UUID playerId = Faction.nullUuid;
        public String username = "";
        public UUID factionId = Faction.nullUuid;
        public int factionColor = 0x4E8E87;
        public UUID inviterId = Faction.nullUuid;
        public String inviterName = "";
        public boolean incoming;
        public boolean invited;
        public boolean canInvite;
        public boolean canAccept;
    }

    public Page page;
    public boolean hasFaction;
    public UUID factionId = Faction.nullUuid;
    public String factionName = "";
    public int factionColor = 0x4E8E87;
    public Faction.Role viewerRole = Faction.Role.MEMBER;
    public boolean canManageMembers;
    public boolean canInvitePlayers;
    public boolean canManageAlliances;
    public boolean allowAllyInteraction;
    public final List<MemberEntry> members = new ArrayList<>();
    public final List<InviteEntry> inviteCandidates = new ArrayList<>();
    public final List<AllianceEntry> alliances = new ArrayList<>();

    public FactionMemberManagerGuiData(Player player, Page page) {
        super(player);
        this.page = page;
    }
}
