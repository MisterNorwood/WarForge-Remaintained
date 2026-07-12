package com.flansmod.warforge.client;

import brachy.modularui.api.GuiAxis;
import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.utils.Alignment;
import brachy.modularui.widget.Widget;
import brachy.modularui.widgets.ListWidget;
import brachy.modularui.widgets.ScrollingTextWidget;
import brachy.modularui.widgets.layout.Flow;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.factories.OperationsGuiData;
import com.flansmod.warforge.common.network.PacketRequestFobWarp;
import com.flansmod.warforge.common.util.DimBlockPos;
import net.minecraft.ChatFormatting;

public final class GuiOperations {
    private static final int WIDTH = 372;
    private static final int HEIGHT = 268;
    private static final int CONTENT_LEFT = 12;
    private static final int HEADER_Y = 12;
    private static final int SIEGE_Y = 48;
    private static final int FOB_Y = 150;

    private static final int STATUS_GREEN = 0x55FF55;
    private static final int STATUS_YELLOW = 0xFFFF55;
    private static final int STATUS_ORANGE = 0xFFAA00;
    private static final int STATUS_RED = 0xFF5555;

    private GuiOperations() {
    }

    public static ModularPanel buildPanel(OperationsGuiData data) {
        int sectionWidth = WIDTH - CONTENT_LEFT * 2;
        int siegeSectionHeight = FOB_Y - SIEGE_Y - 6;
        int fobSectionHeight = HEIGHT - FOB_Y - 12;

        ModularPanel panel = ModularPanel.defaultPanel("operations")
                .width(WIDTH)
                .height(HEIGHT)
                .topRel(0.40f);

        panel.child(new IDrawable.DrawableWidget(ModularGuiStyle.headerBackdrop()).size(WIDTH, 40));
        panel.child(new IDrawable.DrawableWidget(ModularGuiStyle.colorStripe(data.hasFaction ? data.factionColor : 0x4A4A4A)).size(6, HEIGHT));
        panel.child(ModularGuiStyle.subPanelCloseButton(WIDTH));

        panel.child(Text.str("War Room").asWidget()
                .pos(CONTENT_LEFT, HEADER_Y)
                .style(ChatFormatting.BOLD)
                .color(ModularGuiStyle.TEXT_PRIMARY)
                .shadow(true)
                .scale(1.15f));

        if (!data.hasFaction) {
            panel.child(Text.str("You are not currently in a faction").asWidget()
                    .pos(CONTENT_LEFT, HEADER_Y + 15)
                    .color(ModularGuiStyle.TEXT_SECONDARY));
            return panel;
        }

        panel.child(Text.str(data.factionName).asWidget()
                .pos(CONTENT_LEFT, HEADER_Y + 15)
                .style(ChatFormatting.BOLD)
                .color(data.factionColor));

        Flow siegeSection = ModularGuiStyle.section(sectionWidth, siegeSectionHeight).name("operations_siege_section").pos(CONTENT_LEFT, SIEGE_Y);
        Flow fobSection = ModularGuiStyle.section(sectionWidth, fobSectionHeight).name("operations_fob_section").pos(CONTENT_LEFT, FOB_Y);
        panel.child(siegeSection);
        panel.child(fobSection);

        siegeSection.child(Text.str("Active Sieges").color(ModularGuiStyle.TEXT_MUTED).asWidget()
                .margin(0, 0, 0, 4).style(ChatFormatting.BOLD));
        ListWidget<IWidget, ?> siegeList = new ListWidget<>()
                .name("operations_siege_list")
                .scrollDirection(GuiAxis.Y)
                .background(ModularGuiStyle.insetBackdrop())
                .width(sectionWidth - 10)
                .height(siegeSectionHeight - 28);
        if (data.sieges.isEmpty()) {
            siegeList.addChild(Text.str("Your faction is not involved in any siege.").asWidget().pos(6, 6), 0);
        } else {
            int index = 0;
            for (OperationsGuiData.SiegeEntry siege : data.sieges) {
                siegeList.addChild(createSiegeRow(siege), index++);
            }
        }
        siegeSection.child(siegeList);

        fobSection.child(Text.str("Forward Operating Bases").color(ModularGuiStyle.TEXT_MUTED).asWidget()
                .margin(0, 0, 0, 4).style(ChatFormatting.BOLD));
        fobSection.child(Text.str("Warp is available only while your faction is under siege. Tickets regenerate each siege cycle.")
                .asWidget().margin(0, 0, 0, 6).color(ModularGuiStyle.TEXT_MUTED));
        ListWidget<IWidget, ?> fobList = new ListWidget<>()
                .name("operations_fob_list")
                .scrollDirection(GuiAxis.Y)
                .background(ModularGuiStyle.insetBackdrop())
                .width(sectionWidth - 10)
                .height(fobSectionHeight - 46);
        if (data.fobs.isEmpty()) {
            fobList.addChild(Text.str("No FOBs established yet.").asWidget().pos(6, 6), 0);
        } else {
            int index = 0;
            for (OperationsGuiData.FobEntry fob : data.fobs) {
                fobList.addChild(createFobRow(fob, data.warpTicketCost), index++);
            }
        }
        fobSection.child(fobList);

        return panel;
    }

    private static IWidget createSiegeRow(OperationsGuiData.SiegeEntry siege) {
        Flow row = new Flow(GuiAxis.X);
        row.name(ModularGuiStyle.debugName("siege_row", siege.attackerName + "_" + siege.defenderName));
        row.width(WIDTH - 44);
        row.height(24);
        row.mainAxisAlignment(Alignment.MainAxis.START);
        row.padding(3, 3);
        row.margin(0, 0, 0, 2);
        row.background(ModularGuiStyle.insetBackdrop(0xFF232A30));

        row.child(Text.str(siege.attacking ? "Attacking" : "Defending")
                .color(siege.attacking ? STATUS_ORANGE : ModularGuiStyle.TEXT_SUCCESS).asWidget().width(66));
        row.child(new ScrollingTextWidget(Text.str(siege.attackerName + " vs " + siege.defenderName))
                .margin(5, 0)
                .width(158)
                .tooltip(tooltip -> tooltip.addLine(siege.attackerName + " vs " + siege.defenderName)));
        row.child(Text.str("[" + siege.chunkX + ", " + siege.chunkZ + "]")
                .color(ModularGuiStyle.TEXT_SECONDARY).asWidget().width(70));
        return row;
    }

    private static IWidget createFobRow(OperationsGuiData.FobEntry fob, int warpTicketCost) {
        Flow row = new Flow(GuiAxis.X);
        row.name(ModularGuiStyle.debugName("fob_row", fob.name));
        row.width(WIDTH - 44);
        row.height(24);
        row.mainAxisAlignment(Alignment.MainAxis.START);
        row.padding(3, 3);
        row.margin(0, 0, 0, 2);
        row.background(ModularGuiStyle.insetBackdrop(0xFF232A30));

        int color = statusColor(fob);
        row.child(new ScrollingTextWidget(Text.str(fob.name.isEmpty() ? "FOB" : fob.name))
                .margin(5, 0)
                .width(150)
                .color(color)
                .tooltip(tooltip -> tooltip.addLine(fob.occupied ? "Occupied by enemies" : fob.pos.toFancyString())));
        row.child(Text.str(fob.tickets + " / " + fob.maxTickets)
                .color(color)
                .asWidget()
                .width(58));
        row.child(fobWarpButton(fob.canWarp, fob.pos, warpTicketCost));
        return row;
    }

    private static int statusColor(OperationsGuiData.FobEntry fob) {
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

    private static Widget<?> fobWarpButton(boolean enabled, DimBlockPos pos, int warpTicketCost) {
        return ModularGuiStyle.actionButton("Warp", 52, enabled, () -> {
                    PacketRequestFobWarp packet = new PacketRequestFobWarp();
                    packet.mPos = pos;
                    WarForgeMod.NETWORK.sendToServer(packet);
                })
                .tooltip(tooltip -> tooltip.addLine("Warp cost: " + warpTicketCost + (warpTicketCost == 1 ? " ticket" : " tickets")));
    }
}
