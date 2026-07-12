package com.flansmod.warforge.common.factories;

import brachy.modularui.api.IUIHolder;
import brachy.modularui.api.MCHelper;
import brachy.modularui.factory.AbstractUIFactory;
import brachy.modularui.factory.GuiManager;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.ModularScreen;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import com.flansmod.warforge.Tags;
import com.flansmod.warforge.client.GuiFactionMemberManager;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.server.Faction;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

public class FactionMemberManagerGuiFactory extends AbstractUIFactory<FactionMemberManagerGuiData> {
    public static final FactionMemberManagerGuiFactory INSTANCE = new FactionMemberManagerGuiFactory();

    private static final IUIHolder<FactionMemberManagerGuiData> HOLDER = new IUIHolder<>() {
        @Override
        public ModularPanel buildUI(FactionMemberManagerGuiData guiData, PanelSyncManager syncManager, UISettings settings) {
            if (guiData.isClient()) {
                return GuiFactionMemberManager.buildPanel(guiData);
            }
            return ModularPanel.defaultPanel("faction_member_manager")
                    .width(372)
                    .height(268)
                    .topRel(0.5f);
        }

        @Override
        public ModularScreen createScreen(FactionMemberManagerGuiData guiData, ModularPanel mainPanel) {
            return new ModularScreen(Tags.MODID, mainPanel);
        }
    };

    private FactionMemberManagerGuiFactory() {
        super(new ResourceLocation(Tags.MODID, "faction_members"));
    }

    public static void init() {
        if (!GuiManager.hasFactory(INSTANCE.getFactoryName())) {
            GuiManager.registerFactory(INSTANCE);
        }
    }

    public void open(Player player, FactionMemberManagerGuiData.Page page) {
        ServerPlayer serverPlayer = verifyServerSide(player);
        GuiManager.open(this, createServerData(serverPlayer, page), serverPlayer);
    }

    @OnlyIn(Dist.CLIENT)
    public void openClient(FactionMemberManagerGuiData.Page page) {
        com.flansmod.warforge.client.DeferredGuiOpen.open(() ->
                GuiManager.openFromClient(this, new FactionMemberManagerGuiData(verifyClientSide(MCHelper.getPlayer()), page)));
    }

    @OnlyIn(Dist.CLIENT)
    public void openClientChild(Runnable reopenParent, FactionMemberManagerGuiData.Page page) {
        com.flansmod.warforge.client.DeferredGuiOpen.openChild(reopenParent, () ->
                GuiManager.openFromClient(this, new FactionMemberManagerGuiData(verifyClientSide(MCHelper.getPlayer()), page)));
    }

    @OnlyIn(Dist.CLIENT)
    public void openClientSibling(FactionMemberManagerGuiData.Page page) {
        com.flansmod.warforge.client.DeferredGuiOpen.openSibling(() ->
                GuiManager.openFromClient(this, new FactionMemberManagerGuiData(verifyClientSide(MCHelper.getPlayer()), page)));
    }

    @Override
    public @NotNull IUIHolder<FactionMemberManagerGuiData> getGuiHolder(FactionMemberManagerGuiData guiData) {
        return HOLDER;
    }

    @Override
    public void writeGuiData(FactionMemberManagerGuiData guiData, FriendlyByteBuf buffer) {
        if (guiData.isClient()) {
            buffer.writeByte(guiData.page.ordinal());
            return;
        }

        buffer.writeByte(guiData.page.ordinal());
        buffer.writeBoolean(guiData.hasFaction);
        buffer.writeLong(guiData.factionId.getMostSignificantBits());
        buffer.writeLong(guiData.factionId.getLeastSignificantBits());
        buffer.writeUtf(guiData.factionName);
        buffer.writeInt(guiData.factionColor);
        buffer.writeByte(guiData.viewerRole.ordinal());
        buffer.writeBoolean(guiData.canManageMembers);
        buffer.writeBoolean(guiData.canInvitePlayers);
        buffer.writeBoolean(guiData.canManageAlliances);
        buffer.writeBoolean(guiData.allowAllyInteraction);

        buffer.writeShort(guiData.members.size());
        for (FactionMemberManagerGuiData.MemberEntry member : guiData.members) {
            buffer.writeLong(member.playerId.getMostSignificantBits());
            buffer.writeLong(member.playerId.getLeastSignificantBits());
            buffer.writeUtf(member.username);
            buffer.writeByte(member.role.ordinal());
            buffer.writeBoolean(member.online);
            buffer.writeBoolean(member.self);
            buffer.writeBoolean(member.canKickOrLeave);
            buffer.writeBoolean(member.canPromote);
            buffer.writeBoolean(member.canDemote);
            buffer.writeBoolean(member.canTransferLeadership);
        }

        buffer.writeShort(guiData.inviteCandidates.size());
        for (FactionMemberManagerGuiData.InviteEntry invite : guiData.inviteCandidates) {
            buffer.writeLong(invite.playerId.getMostSignificantBits());
            buffer.writeLong(invite.playerId.getLeastSignificantBits());
            buffer.writeUtf(invite.username);
            buffer.writeLong(invite.factionId.getMostSignificantBits());
            buffer.writeLong(invite.factionId.getLeastSignificantBits());
            buffer.writeInt(invite.factionColor);
            buffer.writeLong(invite.inviterId.getMostSignificantBits());
            buffer.writeLong(invite.inviterId.getLeastSignificantBits());
            buffer.writeUtf(invite.inviterName);
            buffer.writeBoolean(invite.incoming);
            buffer.writeBoolean(invite.invited);
            buffer.writeBoolean(invite.canInvite);
            buffer.writeBoolean(invite.canAccept);
        }

        buffer.writeShort(guiData.alliances.size());
        for (FactionMemberManagerGuiData.AllianceEntry ally : guiData.alliances) {
            buffer.writeLong(ally.factionId.getMostSignificantBits());
            buffer.writeLong(ally.factionId.getLeastSignificantBits());
            buffer.writeUtf(ally.factionName);
            buffer.writeInt(ally.factionColor);
            buffer.writeShort(ally.onlineCount);
            buffer.writeByte(ally.kind);
        }

    }

    @Override
    public @NotNull FactionMemberManagerGuiData readGuiData(Player player, FriendlyByteBuf buffer) {
        FactionMemberManagerGuiData.Page page = FactionMemberManagerGuiData.Page.values()[buffer.readByte()];
        if (!player.level().isClientSide) {
            return createServerData((ServerPlayer) player, page);
        }

        FactionMemberManagerGuiData data = new FactionMemberManagerGuiData(player, page);
        data.hasFaction = buffer.readBoolean();
        data.factionId = new UUID(buffer.readLong(), buffer.readLong());
        data.factionName = buffer.readUtf();
        data.factionColor = buffer.readInt();
        data.viewerRole = Faction.Role.values()[buffer.readByte()];
        data.canManageMembers = buffer.readBoolean();
        data.canInvitePlayers = buffer.readBoolean();
        data.canManageAlliances = buffer.readBoolean();
        data.allowAllyInteraction = buffer.readBoolean();

        int memberCount = buffer.readShort();
        for (int i = 0; i < memberCount; i++) {
            FactionMemberManagerGuiData.MemberEntry member = new FactionMemberManagerGuiData.MemberEntry();
            member.playerId = new UUID(buffer.readLong(), buffer.readLong());
            member.username = buffer.readUtf();
            member.role = Faction.Role.values()[buffer.readByte()];
            member.online = buffer.readBoolean();
            member.self = buffer.readBoolean();
            member.canKickOrLeave = buffer.readBoolean();
            member.canPromote = buffer.readBoolean();
            member.canDemote = buffer.readBoolean();
            member.canTransferLeadership = buffer.readBoolean();
            data.members.add(member);
        }

        int inviteCount = buffer.readShort();
        for (int i = 0; i < inviteCount; i++) {
            FactionMemberManagerGuiData.InviteEntry invite = new FactionMemberManagerGuiData.InviteEntry();
            invite.playerId = new UUID(buffer.readLong(), buffer.readLong());
            invite.username = buffer.readUtf();
            invite.factionId = new UUID(buffer.readLong(), buffer.readLong());
            invite.factionColor = buffer.readInt();
            invite.inviterId = new UUID(buffer.readLong(), buffer.readLong());
            invite.inviterName = buffer.readUtf();
            invite.incoming = buffer.readBoolean();
            invite.invited = buffer.readBoolean();
            invite.canInvite = buffer.readBoolean();
            invite.canAccept = buffer.readBoolean();
            data.inviteCandidates.add(invite);
        }

        int allianceCount = buffer.readShort();
        for (int i = 0; i < allianceCount; i++) {
            FactionMemberManagerGuiData.AllianceEntry ally = new FactionMemberManagerGuiData.AllianceEntry();
            ally.factionId = new UUID(buffer.readLong(), buffer.readLong());
            ally.factionName = buffer.readUtf();
            ally.factionColor = buffer.readInt();
            ally.onlineCount = buffer.readShort();
            ally.kind = buffer.readByte();
            data.alliances.add(ally);
        }

        return data;
    }

    private FactionMemberManagerGuiData createServerData(ServerPlayer player, FactionMemberManagerGuiData.Page page) {
        FactionMemberManagerGuiData data = new FactionMemberManagerGuiData(player, page);
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
                    continue; // already listed as an incoming request
                }
                addAllianceEntry(data, invitable, FactionMemberManagerGuiData.AllianceEntry.KIND_INVITABLE);
            }
        }

        return data;
    }

    private static void addAllianceEntry(FactionMemberManagerGuiData data, Faction faction, byte kind) {
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
