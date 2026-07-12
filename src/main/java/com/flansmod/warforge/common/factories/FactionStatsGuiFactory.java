package com.flansmod.warforge.common.factories;

import brachy.modularui.api.IUIHolder;
import brachy.modularui.factory.AbstractUIFactory;
import brachy.modularui.factory.GuiManager;
import brachy.modularui.api.MCHelper;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.ModularScreen;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import com.flansmod.warforge.Tags;
import com.flansmod.warforge.client.GuiFactionStats;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.network.FactionDisplayInfo;
import com.flansmod.warforge.server.Faction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class FactionStatsGuiFactory extends AbstractUIFactory<FactionStatsGuiData> {
    public static final FactionStatsGuiFactory INSTANCE = new FactionStatsGuiFactory();

    private static final IUIHolder<FactionStatsGuiData> HOLDER = new IUIHolder<FactionStatsGuiData>() {
        @Override
        public ModularPanel buildUI(FactionStatsGuiData guiData, PanelSyncManager syncManager, UISettings settings) {
            if (guiData.isClient()) {
                return GuiFactionStats.buildPanel(guiData);
            }
            return ModularPanel.defaultPanel("faction_stats", 320, 228).topRel(0.5f);
        }

        @Override
        public ModularScreen createScreen(FactionStatsGuiData guiData, ModularPanel mainPanel) {
            return new ModularScreen(Tags.MODID, mainPanel);
        }
    };

    private FactionStatsGuiFactory() {
        super(new ResourceLocation(Tags.MODID, "faction_stats"));
    }

    public static void init() {
        if (!GuiManager.hasFactory(INSTANCE.getFactoryName())) {
            GuiManager.registerFactory(INSTANCE);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public void openClient(UUID factionId) {
        com.flansmod.warforge.client.DeferredGuiOpen.open(() ->
                GuiManager.openFromClient(this, new FactionStatsGuiData(MCHelper.getPlayer(), factionId)));
    }

    @OnlyIn(Dist.CLIENT)
    public void openClientChild(Runnable reopenParent, UUID factionId) {
        com.flansmod.warforge.client.DeferredGuiOpen.openChild(reopenParent, () ->
                GuiManager.openFromClient(this, new FactionStatsGuiData(MCHelper.getPlayer(), factionId)));
    }

    @OnlyIn(Dist.CLIENT)
    public void openClientSibling(UUID factionId) {
        com.flansmod.warforge.client.DeferredGuiOpen.openSibling(() ->
                GuiManager.openFromClient(this, new FactionStatsGuiData(MCHelper.getPlayer(), factionId)));
    }

    public void open(Player player, UUID factionId) {
        ServerPlayer serverPlayer = verifyServerSide(player);
        GuiManager.open(this, createServerData(serverPlayer, factionId), serverPlayer);
    }

    @Override
    public @NotNull IUIHolder<FactionStatsGuiData> getGuiHolder(FactionStatsGuiData guiData) {
        return HOLDER;
    }

    @Override
    public void writeGuiData(FactionStatsGuiData guiData, FriendlyByteBuf buffer) {
        if (guiData.isClient()) {
            buffer.writeUUID(guiData.requestedFactionId);
            return;
        }

        buffer.writeBoolean(guiData.hasFaction);
        buffer.writeUUID(guiData.factionId);
        buffer.writeUtf(guiData.factionName);
        buffer.writeInt(guiData.factionColor);
        buffer.writeUUID(guiData.leaderId);
        buffer.writeUtf(guiData.leaderName);
        buffer.writeInt(guiData.notoriety);
        buffer.writeInt(guiData.wealth);
        buffer.writeInt(guiData.legacy);
        buffer.writeInt(guiData.total);
        buffer.writeInt(guiData.notorietyRank);
        buffer.writeInt(guiData.wealthRank);
        buffer.writeInt(guiData.legacyRank);
        buffer.writeInt(guiData.totalRank);
        buffer.writeInt(guiData.claimCount);
        buffer.writeInt(guiData.memberCount);
        buffer.writeInt(guiData.level);
        buffer.writeInt(guiData.claimLimit);
        buffer.writeBoolean(guiData.isOwnFaction);
        buffer.writeBoolean(guiData.canManageMembers);
        buffer.writeBoolean(guiData.canUpgrade);
    }

    @Override
    public @NotNull FactionStatsGuiData readGuiData(Player player, FriendlyByteBuf buffer) {
        if (!player.level().isClientSide) {
            UUID requestedFactionId = buffer.readUUID();
            return createServerData((ServerPlayer) player, requestedFactionId);
        }

        FactionStatsGuiData data = new FactionStatsGuiData(player, Faction.nullUuid);
        data.hasFaction = buffer.readBoolean();
        data.factionId = buffer.readUUID();
        data.factionName = buffer.readUtf();
        data.factionColor = buffer.readInt();
        data.leaderId = buffer.readUUID();
        data.leaderName = buffer.readUtf();
        data.notoriety = buffer.readInt();
        data.wealth = buffer.readInt();
        data.legacy = buffer.readInt();
        data.total = buffer.readInt();
        data.notorietyRank = buffer.readInt();
        data.wealthRank = buffer.readInt();
        data.legacyRank = buffer.readInt();
        data.totalRank = buffer.readInt();
        data.claimCount = buffer.readInt();
        data.memberCount = buffer.readInt();
        data.level = buffer.readInt();
        data.claimLimit = buffer.readInt();
        data.isOwnFaction = buffer.readBoolean();
        data.canManageMembers = buffer.readBoolean();
        data.canUpgrade = buffer.readBoolean();
        return data;
    }

    private FactionStatsGuiData createServerData(ServerPlayer player, UUID requestedFactionId) {
        UUID effectiveFactionId = requestedFactionId;
        if (effectiveFactionId.equals(Faction.nullUuid)) {
            Faction playerFaction = WarForgeMod.FACTIONS.getFactionOfPlayer(player.getUUID());
            effectiveFactionId = playerFaction == null ? Faction.nullUuid : playerFaction.uuid;
        }

        FactionStatsGuiData data = new FactionStatsGuiData(player, effectiveFactionId);
        Faction faction = WarForgeMod.FACTIONS.getFaction(effectiveFactionId);
        if (faction == null) {
            return data;
        }

        FactionDisplayInfo info = faction.createInfo();
        data.hasFaction = true;
        data.factionId = faction.uuid;
        data.factionName = info.factionName;
        data.factionColor = faction.colour;
        data.leaderId = info.mLeaderID;
        var leader = info.getPlayerInfo(info.mLeaderID);
        data.leaderName = leader == null ? "Unknown" : leader.username;
        data.notoriety = info.notoriety;
        data.wealth = info.wealth;
        data.legacy = info.legacy;
        data.total = info.notoriety + info.wealth + info.legacy;
        data.notorietyRank = info.notorietyRank;
        data.wealthRank = info.wealthRank;
        data.legacyRank = info.legacyRank;
        data.totalRank = info.totalRank;
        data.claimCount = info.mNumClaims;
        data.memberCount = info.members.size();
        data.level = info.lvl;
        data.claimLimit = WarForgeMod.UPGRADE_HANDLER.getClaimLimitForLevel(info.lvl);
        data.isOwnFaction = faction.isPlayerInFaction(player.getUUID());
        data.canManageMembers = WarForgeMod.isOp(player) || faction.isPlayerRoleInFaction(player.getUUID(), Faction.Role.OFFICER);
        data.canUpgrade = data.canManageMembers;
        return data;
    }
}
