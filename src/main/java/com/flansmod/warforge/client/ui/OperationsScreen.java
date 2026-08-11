package com.flansmod.warforge.client.ui;

import com.flansmod.warforge.api.modularui.WarForgeUiTheme;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.factories.OperationsGuiData;
import com.flansmod.warforge.common.network.PacketOperationsData;
import com.flansmod.warforge.common.network.PacketRequestFobWarp;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class OperationsScreen {
    private static final int WIDTH = 372;
    private static final int STATUS_GREEN = 0x55FF55;
    private static final int STATUS_YELLOW = 0xFFFF55;
    private static final int STATUS_ORANGE = 0xFFAA00;
    private static final int STATUS_RED = 0xFF5555;

    private OperationsScreen() {
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        PacketOperationsData data = PacketOperationsData.latest;

        UIElement root = new UIElement();
        UIElement body = WarForgeUiTheme.frame(root, WIDTH, data != null && data.hasFaction ? data.factionColor : 0x4A4A4A);
        Button close = WarForgeUiTheme.closeButton();
        close.setOnClick(event -> Minecraft.getInstance().setScreen(null));

        body.addChild(WarForgeUiTheme.header("War Room", close));

        if (data == null || !data.hasFaction) {
            UIElement noDataSection = WarForgeUiTheme.section();
            noDataSection.addChild(WarForgeUiTheme.text("You are not currently in a faction.", WarForgeUiTheme.TEXT_SECONDARY));
            body.addChild(noDataSection);
            mc.setScreen(new ModularUIScreen(ModularUI.of(UI.of(root), mc.player), Component.literal("Operations")));
            return;
        }

        body.addChild(WarForgeUiTheme.boldText(data.factionName, data.factionColor & 0xFFFFFF));

        UIElement siegeSection = WarForgeUiTheme.section();
        siegeSection.addChild(WarForgeUiTheme.boldText("Active Sieges", WarForgeUiTheme.TEXT_MUTED));
        if (data.sieges.isEmpty()) {
            siegeSection.addChild(WarForgeUiTheme.text("Your faction is not involved in any siege.", WarForgeUiTheme.TEXT_SECONDARY));
        } else {
            for (OperationsGuiData.SiegeEntry siege : data.sieges) {
                siegeSection.addChild(siegeRow(siege));
            }
        }
        body.addChild(siegeSection);

        UIElement fobSection = WarForgeUiTheme.section();
        fobSection.addChild(WarForgeUiTheme.boldText("Forward Operating Bases", WarForgeUiTheme.TEXT_MUTED));
        fobSection.addChild(WarForgeUiTheme.text("Warp is available only while your faction is under siege. Tickets regenerate each siege cycle.", WarForgeUiTheme.TEXT_MUTED));
        if (data.fobs.isEmpty()) {
            fobSection.addChild(WarForgeUiTheme.text("No FOBs established yet.", WarForgeUiTheme.TEXT_SECONDARY));
        } else {
            for (OperationsGuiData.FobEntry fob : data.fobs) {
                fobSection.addChild(fobRow(fob, data.warpTicketCost));
            }
        }
        body.addChild(fobSection);

        mc.setScreen(new ModularUIScreen(ModularUI.of(UI.of(root), mc.player), Component.literal("Operations")));
    }

    private static UIElement siegeRow(OperationsGuiData.SiegeEntry siege) {
        UIElement row = WarForgeUiTheme.row(8);

        int roleColor = siege.attacking ? STATUS_ORANGE : STATUS_GREEN;
        String roleLabel = siege.attacking ? "Attacking" : "Defending";
        row.addChild(WarForgeUiTheme.text(roleLabel, roleColor).layout(l -> l.width(70)));

        String versus = siege.attackerName + " vs " + siege.defenderName;
        row.addChild(WarForgeUiTheme.text(versus, WarForgeUiTheme.TEXT_PRIMARY).layout(l -> l.width(180)));

        String coords = "[" + siege.chunkX + ", " + siege.chunkZ + "]";
        row.addChild(WarForgeUiTheme.text(coords, WarForgeUiTheme.TEXT_SECONDARY).layout(l -> l.width(80)));

        return row;
    }

    private static UIElement fobRow(OperationsGuiData.FobEntry fob, int warpTicketCost) {
        UIElement row = WarForgeUiTheme.row(8);

        int color = fobStatusColor(fob);
        String displayName = fob.name.isEmpty() ? "FOB" : fob.name;
        row.addChild(WarForgeUiTheme.text(displayName, color).layout(l -> l.width(140)));

        String tickets = fob.tickets + " / " + fob.maxTickets;
        row.addChild(WarForgeUiTheme.text(tickets, color).layout(l -> l.width(60)));

        DimBlockPos pos = fob.pos;
        if (fob.canWarp) {
            Button warpBtn = new Button().setText("Warp");
            WarForgeUiTheme.styleButton(warpBtn, 52);
            warpBtn.setOnClick(event -> {
                PacketRequestFobWarp packet = new PacketRequestFobWarp();
                packet.mPos = pos;
                WarForgeMod.NETWORK.sendToServer(packet);
            });
            row.addChild(warpBtn);
        } else {
            row.addChild(WarForgeUiTheme.text("Warp N/A", WarForgeUiTheme.TEXT_DISABLED).layout(l -> l.width(60)));
        }

        return row;
    }

    private static int fobStatusColor(OperationsGuiData.FobEntry fob) {
        if (fob.occupied) {
            return STATUS_RED;
        }
        float fraction = fob.maxTickets > 0 ? (float) fob.tickets / fob.maxTickets : 0f;
        if (fraction >= 0.75f) {
            return STATUS_GREEN;
        }
        if (fraction >= 0.50f) {
            return STATUS_YELLOW;
        }
        if (fraction >= 0.25f) {
            return STATUS_ORANGE;
        }
        return STATUS_RED;
    }
}
