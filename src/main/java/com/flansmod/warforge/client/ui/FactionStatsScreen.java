package com.flansmod.warforge.client.ui;

import com.flansmod.warforge.api.modularui.WarForgeUiTheme;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.factories.FactionFlagSelectGuiFactory;
import com.flansmod.warforge.common.factories.FactionInsuranceGuiFactory;
import com.flansmod.warforge.common.factories.FactionMemberManagerGuiData;
import com.flansmod.warforge.common.factories.FactionMemberManagerGuiFactory;
import com.flansmod.warforge.common.factories.FactionUpgradeGuiFactory;
import com.flansmod.warforge.common.factories.OperationsGuiFactory;
import com.flansmod.warforge.common.network.FactionDisplayInfo;
import com.flansmod.warforge.common.network.PacketFactionInfo;
import com.flansmod.warforge.common.network.PacketRequestFactionInfo;
import com.flansmod.warforge.common.network.PlayerDisplayInfo;
import com.flansmod.warforge.server.Faction;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public final class FactionStatsScreen {
    private FactionStatsScreen() {
    }

    public static void open(UUID factionId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        FactionDisplayInfo info = PacketFactionInfo.latestInfo;
        boolean hasFaction = info != null && !info.factionId.equals(Faction.nullUuid);
        int stripeColor = hasFaction ? info.colour : 0x4A4A4A;

        UIElement root = new UIElement();
        UIElement body = WarForgeUiTheme.frame(root, 320, stripeColor);
        Button close = WarForgeUiTheme.closeButton();
        close.setOnClick(event -> Minecraft.getInstance().setScreen(null));

        if (!hasFaction) {
            body.addChild(WarForgeUiTheme.header("Faction Stats", close));
            UIElement empty = WarForgeUiTheme.section();
            empty.addChild(WarForgeUiTheme.text("No faction information is available.", WarForgeUiTheme.TEXT_SECONDARY));
            body.addChild(empty);
            mc.setScreen(new ModularUIScreen(ModularUI.of(UI.of(root), mc.player), Component.literal("Faction Stats")));
            return;
        }

        body.addChild(WarForgeUiTheme.header("Faction Stats", info.factionName, info.colour, close));

        int total = info.notoriety + info.wealth + info.legacy;
        int claimLimit = WarForgeMod.UPGRADE_HANDLER.getClaimLimitForLevel(info.lvl);
        int stashSlots = WarForgeMod.UPGRADE_HANDLER.getInsuranceSlotsForLevel(info.lvl);

        UIElement statsSection = WarForgeUiTheme.section();
        statsSection.addChild(WarForgeUiTheme.boldText("Faction Overview", WarForgeUiTheme.TEXT_PRIMARY));
        PlayerDisplayInfo leader = info.getPlayerInfo(info.mLeaderID);
        UIElement leaderRow = WarForgeUiTheme.row(6);
        com.flansmod.warforge.client.util.PlayerFaceElement leaderFace = new com.flansmod.warforge.client.util.PlayerFaceElement(info.mLeaderID);
        leaderFace.layout(l -> l.width(18).height(18));
        leaderRow.addChild(leaderFace);
        leaderRow.addChild(WarForgeUiTheme.text("Leader: " + (leader == null ? "Unknown" : leader.username), WarForgeUiTheme.TEXT_SECONDARY));
        statsSection.addChild(leaderRow);
        statsSection.addChild(statRow("Notoriety", String.valueOf(info.notoriety), "#" + info.notorietyRank));
        statsSection.addChild(statRow("Wealth", String.valueOf(info.wealth), "#" + info.wealthRank));
        statsSection.addChild(statRow("Legacy", String.valueOf(info.legacy), "#" + info.legacyRank));
        statsSection.addChild(statRow("Total", String.valueOf(total), "#" + info.totalRank));
        statsSection.addChild(statRow("Claims", String.valueOf(info.mNumClaims), claimLimit < 0 ? "INF" : String.valueOf(claimLimit)));
        statsSection.addChild(statRow("Members", String.valueOf(info.members.size()), ""));
        if (WarForgeConfig.ENABLE_CITADEL_UPGRADES) {
            statsSection.addChild(statRow("Citadel", "Lvl " + info.lvl, ""));
        }
        statsSection.addChild(statRow("Stash", String.valueOf(stashSlots), "slots"));
        body.addChild(statsSection);

        PlayerDisplayInfo self = info.getPlayerInfo(mc.player.getUUID());
        boolean isOwnFaction = self != null;
        boolean isOfficer = self != null && self.role.ordinal() >= Faction.Role.OFFICER.ordinal();
        UUID displayedFaction = info.factionId;

        UIElement actionSection = WarForgeUiTheme.section();
        UIElement actions = new UIElement();
        actions.layout(l -> l.flexDirection(FlexDirection.ROW).flexWrap(FlexWrap.WRAP).widthStretch().gapColumn(4).gapRow(4));
        if (isOwnFaction) {
            Button stashBtn = new Button().setText("Stash");
            WarForgeUiTheme.styleButton(stashBtn, 66);
            stashBtn.setOnClick(event -> FactionInsuranceGuiFactory.INSTANCE.openClientSibling(displayedFaction));
            actions.addChild(stashBtn);

            Button membersBtn = new Button().setText("Members");
            WarForgeUiTheme.styleButton(membersBtn, 66);
            membersBtn.setOnClick(event -> FactionMemberManagerGuiFactory.INSTANCE.openClientSibling(FactionMemberManagerGuiData.Page.MEMBERS));
            actions.addChild(membersBtn);

            if (WarForgeConfig.ENABLE_CITADEL_UPGRADES && isOfficer) {
                Button upgradeBtn = new Button().setText("Upgrade");
                WarForgeUiTheme.styleButton(upgradeBtn, 70);
                upgradeBtn.setOnClick(event -> FactionUpgradeGuiFactory.INSTANCE.openClientSibling(displayedFaction));
                actions.addChild(upgradeBtn);
            }

            Button opsBtn = new Button().setText("Operations");
            WarForgeUiTheme.styleButton(opsBtn, 84);
            opsBtn.setOnClick(event -> OperationsGuiFactory.INSTANCE.openClient());
            actions.addChild(opsBtn);

            Button flagBtn = new Button().setText("Flag");
            WarForgeUiTheme.styleButton(flagBtn, 66);
            flagBtn.setOnClick(event -> FactionFlagSelectGuiFactory.INSTANCE.openClient(displayedFaction));
            actions.addChild(flagBtn);
        }
        Button refreshBtn = new Button().setText("Refresh");
        WarForgeUiTheme.styleButton(refreshBtn, 66);
        refreshBtn.setOnClick(event -> {
            PacketRequestFactionInfo packet = new PacketRequestFactionInfo();
            packet.mFactionIDRequest = displayedFaction;
            WarForgeMod.NETWORK.sendToServer(packet);
        });
        actions.addChild(refreshBtn);
        actionSection.addChild(actions);
        body.addChild(actionSection);

        mc.setScreen(new ModularUIScreen(ModularUI.of(UI.of(root), mc.player), Component.literal("Faction Stats")));
    }

    private static UIElement statRow(String label, String value, String extra) {
        UIElement row = WarForgeUiTheme.row(6);
        row.addChild(WarForgeUiTheme.text(label, WarForgeUiTheme.TEXT_SECONDARY).layout(l -> l.width(90)));
        row.addChild(WarForgeUiTheme.text(value, WarForgeUiTheme.TEXT_PRIMARY).layout(l -> l.width(70)));
        row.addChild(WarForgeUiTheme.text(extra, WarForgeUiTheme.TEXT_MUTED).layout(l -> l.width(60)));
        return row;
    }
}
