package com.flansmod.warforge.client;

import brachy.modularui.api.GuiAxis;
import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.drawable.GuiDraw;
import brachy.modularui.drawable.GuiTextures;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.RichTextWidget;
import brachy.modularui.widgets.SlotGroupWidget;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.layout.Grid;
import brachy.modularui.widgets.slot.ItemSlot;
import brachy.modularui.widgets.slot.ModularSlot;
import com.flansmod.warforge.client.util.FlagDrawable;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.blocks.TileEntityCitadel;
import com.flansmod.warforge.common.factories.CreateFactionGuiFactory;
import com.flansmod.warforge.common.factories.FactionFlagSelectGuiFactory;
import com.flansmod.warforge.common.factories.FactionInsuranceGuiFactory;
import com.flansmod.warforge.common.factories.FactionMemberManagerGuiData;
import com.flansmod.warforge.common.factories.FactionMemberManagerGuiFactory;
import com.flansmod.warforge.common.factories.FactionStatsGuiFactory;
import com.flansmod.warforge.common.factories.FactionUpgradeGuiFactory;
import com.flansmod.warforge.common.network.PacketDisbandFaction;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.server.Faction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;

public final class ModularCitadelGui {
    private static final int PANEL_WIDTH = 350;
    private static final int PANEL_HEIGHT = 200;
    private static final int SLOT_SIZE = 18;
    private static final int CONTENT_LEFT = 12;
    private static final int HEADER_Y = 12;
    private static final int STORAGE_Y = 40;
    private static final int ACTIONS_Y = 130;

    private ModularCitadelGui() {
    }

    public static ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings, TileEntityCitadel citadel) {
        syncManager.bindPlayerInventory(data.getPlayer());
        boolean hasFaction = !citadel.getFaction().equals(Faction.nullUuid);
        // Sub-GUIs opened from here return to this citadel when their X button is pressed.
        net.minecraft.core.BlockPos citadelPos = citadel.getBlockPos();
        Runnable reopenCitadel = () -> com.flansmod.warforge.common.factories.CitadelGuiFactory.INSTANCE.openClient(citadelPos);

        ModularPanel panel = ModularPanel.defaultPanel("citadel_modular")
                .width(PANEL_WIDTH)
                .height(PANEL_HEIGHT)
                .topRel(0.40f);

        var yeldPanel = new Flow(GuiAxis.Y)
                .background(sectionBackdrop(100, 80, 0xEE20262B, 0xEE11161A))
                .size(100, 80)
                .pos(CONTENT_LEFT, STORAGE_Y)
                .padding(5)
                .margin(5);
        panel.child(yeldPanel);

        var flagPanel = new Flow(GuiAxis.Y)
                .background(sectionBackdrop(215, 80, 0xEE20262B, 0xEE11161A))
                .size(210, 80)
                .pos(CONTENT_LEFT + 110, STORAGE_Y)
                .padding(5)
                .margin(5);
        panel.child(flagPanel);

        panel.child(new IDrawable.DrawableWidget(sectionBackdrop(PANEL_WIDTH, 36, 0xFF171B1F, 0xFF0D1013)).size(PANEL_WIDTH, 36));

        var actionsPanel = new Flow(GuiAxis.Y)
                .background(sectionBackdrop(PANEL_WIDTH - CONTENT_LEFT * 2, 65, 0xEE20262B, 0xEE11161A))
                .padding(5)
                .margin(5)
                .size(PANEL_WIDTH - CONTENT_LEFT * 2, 65)
                .pos(CONTENT_LEFT, ACTIONS_Y);
        panel.child(actionsPanel);

        panel.child(new IDrawable.DrawableWidget(colorStripe(citadel.colour, 6, PANEL_HEIGHT)).size(6, PANEL_HEIGHT));
        panel.child(ModularGuiStyle.panelCloseButton(PANEL_WIDTH));

        panel.child(Text.str(hasFaction ? citadel.getClaimDisplayName() : "Unclaimed Citadel").asWidget()
                .pos(CONTENT_LEFT, HEADER_Y)
                .style(ChatFormatting.BOLD)
                .color(hasFaction ? citadel.colour : 0xFFFFFF)
                .shadow(true)
                .scale(1.2f));
        panel.child(Text.str(hasFaction ? "Faction vault, banner relay, and command center" : "Claimed by the placer until a faction is founded").asWidget()
                .pos(CONTENT_LEFT, HEADER_Y + 14)
                .color(0xC7CCD1));

        yeldPanel.child(new RichTextWidget()
                .textBuilder(text -> text.add(ChatFormatting.BOLD + "Yield Storage").textColor(0xFFFFFF))
                .tooltip(tooltip -> tooltip.addLine("Resources collected from claimed territory"))
                .width(90)
                .height(10)
                .margin(0, 0, 0, 5)
        );

        Grid yieldGrid = new Grid();
        for (int slot = 0; slot < TileEntityCitadel.NUM_YIELD_STACKS; slot++) {
            if (slot > 0 && slot % 3 == 0) {
                yieldGrid.nextRow();
            }
            yieldGrid.child(new ItemSlot()
                    .slot(new ModularSlot(citadel, slot))
                    .size(SLOT_SIZE));
        }
        yieldGrid.size(3 * SLOT_SIZE, 3 * SLOT_SIZE);
        yeldPanel.child(yieldGrid);

        flagPanel.child(Text.str("Faction Flag").asWidget()
                .color(0xFFFFFF)
                .right(130)
                .margin(0, 0, 0, 5)
                .style(ChatFormatting.BOLD));
        flagPanel.child(Text.str("Choose once. This cannot be changed later.").asWidget().setEnabledIf(w -> citadel.factionFlagId.isEmpty())
                .color(0xB8BDC3));
        if (!citadel.factionFlagId.isEmpty()) {
            flagPanel.child(new IDrawable.DrawableWidget(new FlagDrawable(citadel.factionFlagId)).margin(4, 8).size(95, 54).relativeToParent().horizontalCenter().top(20));
        } else if (hasFaction) {
            var chooseBtn = openButton("Choose", 228, () -> FactionFlagSelectGuiFactory.INSTANCE.openClientChild(reopenCitadel, citadel.getFaction()));
            flagPanel.child(chooseBtn.width(50));
        } else {
            panel.child(Text.str("Create a faction first").asWidget().pos(180, STORAGE_Y + 42).color(0xD6DBE0));
        }

        actionsPanel.child(Text.str("Command Surface").asWidget()
                .margin(0, 0, 0, 4)
                .color(0xFFFFFF)
                .style(ChatFormatting.BOLD)
                .right(220)
        );

        // Rows fill the panel's content width and use horizontal-only padding/margin so the 18px-tall
        // buttons fit within the row height and the row fits within the column (vertical padding/margin
        // would push the children past the parent's content box).
        int actionRowWidth = PANEL_WIDTH - CONTENT_LEFT * 2 - 10;
        var firstRow = new Flow(GuiAxis.X)
                .padding(2, 0)
                .margin(0, 2)
                .width(actionRowWidth);
        actionsPanel.child(firstRow);
        if (hasFaction) {
            firstRow.height(18);
            var secondRow = new Flow(GuiAxis.X)
                    .padding(2, 0)
                    .margin(0, 2)
                    .width(actionRowWidth)
                    .height(18);
            firstRow.child(openButton("Insurance", 75, () -> FactionInsuranceGuiFactory.INSTANCE.openClientChild(reopenCitadel, citadel.getFaction())));
            firstRow.child(openButton("Faction Stats", 75, () -> FactionStatsGuiFactory.INSTANCE.openClientChild(reopenCitadel, citadel.getFaction())));
            firstRow.child(openButton("Members", 75, () -> FactionMemberManagerGuiFactory.INSTANCE.openClientChild(reopenCitadel, FactionMemberManagerGuiData.Page.MEMBERS)));

            if (WarForgeConfig.ENABLE_CITADEL_UPGRADES) {
                secondRow.child(openButton("Upgrade", 75, () -> FactionUpgradeGuiFactory.INSTANCE.openClientChild(reopenCitadel, citadel.getFaction())));
            }
            secondRow.child(openButton("Recolor", 75, () -> CreateFactionGuiFactory.INSTANCE.openClientChild(reopenCitadel, new DimBlockPos(citadel), citadel.colour, true)));
            secondRow.child(dangerButton("Disband", 75));
            actionsPanel.child(secondRow);
        } else {
            firstRow.coverChildrenHeight();
            var columnStart = new Flow(GuiAxis.Y);
            columnStart.child(openButton("Create Faction", 100, () -> CreateFactionGuiFactory.INSTANCE.openClientChild(reopenCitadel, new DimBlockPos(citadel), citadel.colour, false)).margin(2));
            columnStart.child(Text.str("The placer can establish the faction from here.").asWidget()
                    .color(0xB8BDC3).margin(2));
            firstRow.child(columnStart);
        }

        panel.child(new ParentWidget<>().child(SlotGroupWidget.playerInventory(false)).background(GuiTextures.MC_BACKGROUND).coverChildren().margin(5).padding(5).horizontalCenter().top(PANEL_HEIGHT));

        return panel;
    }

    private static IDrawable sectionBackdrop(int width, int height, int fillColor, int borderColor) {
        return (context, drawX, drawY, drawWidth, drawHeight, theme) -> {
            GuiGraphics graphics = context.getGraphics();
            GuiDraw.drawRect(graphics, drawX, drawY, width, height, fillColor);
            GuiDraw.drawRect(graphics, drawX, drawY, width, 1, borderColor);
            GuiDraw.drawRect(graphics, drawX, drawY + height - 1, width, 1, borderColor);
            GuiDraw.drawRect(graphics, drawX, drawY, 1, height, borderColor);
            GuiDraw.drawRect(graphics, drawX + width - 1, drawY, 1, height, borderColor);
        };
    }

    private static IDrawable colorStripe(int color, int width, int height) {
        return (context, drawX, drawY, drawWidth, drawHeight, theme) ->
                GuiDraw.drawRect(context.getGraphics(), drawX, drawY, width, height, 0xFF000000 | (color & 0x00FFFFFF));
    }

    private static ButtonWidget<?> openButton(String label, int width, Runnable action) {
        ButtonWidget<?> button = ModularGuiStyle.actionButton(label, width, action);
        button.margin(10, 0);
        return button;
    }

    private static ButtonWidget<?> dangerButton(String label, int width) {
        ButtonWidget<?> button = ModularGuiStyle.dangerButton(label, width, () -> WarForgeMod.NETWORK.sendToServer(new PacketDisbandFaction()));
        button.margin(10, 0);
        return button;
    }
}
