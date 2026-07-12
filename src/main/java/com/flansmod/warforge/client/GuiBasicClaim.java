package com.flansmod.warforge.client;

import brachy.modularui.api.GuiAxis;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.drawable.GuiTextures;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.slot.ItemSlot;
import brachy.modularui.widgets.slot.ModularSlot;

import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.blocks.TileEntityBasicClaim;
import com.flansmod.warforge.common.blocks.TileEntityYieldCollector;
import com.flansmod.warforge.common.factories.FactionStatsGuiFactory;
import com.flansmod.warforge.common.network.PacketMoveCitadel;
import com.flansmod.warforge.common.network.PacketRemoveClaim;

import net.minecraft.ChatFormatting;

/**
 * ModularUI panel for a basic territory claim. Under modern ModularUI there is no per-GUI vanilla
 * {@code Container}; the claim {@link TileEntityBasicClaim} is itself the item handler and the 3x3
 * yield grid is added directly to the panel. Action buttons mirror the legacy citadel menu (the claim
 * GUI reuses the citadel layout).
 */
public final class GuiBasicClaim {

    private static final int SLOT_SIZE = 18;
    private static final int COLUMNS = 3;
    private static final int WIDTH = 200;
    private static final int HEIGHT = 182;
    private static final int CONTENT_LEFT = 8;
    private static final int GRID_Y = 30;

    private GuiBasicClaim() {
    }

    public static ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings, TileEntityBasicClaim claim) {
        ModularPanel panel = ModularPanel.defaultPanel("basic_claim", WIDTH, HEIGHT)
                .topRel(0.40f);
        panel.bindPlayerInventory();

        panel.child(Text.str(claim.getClaimDisplayName()).asWidget()
                .pos(CONTENT_LEFT, 8)
                .style(ChatFormatting.BOLD)
                .color(0xFFFFFF));
        panel.child(Text.str("Yields").asWidget()
                .pos(CONTENT_LEFT, 20)
                .color(0xC7CCD1));

        int slots = claim.getSlots();
        ParentWidget<?> grid = new ParentWidget<>();
        grid.name("basic_claim_grid");
        grid.pos(CONTENT_LEFT, GRID_Y);
        grid.size(COLUMNS * SLOT_SIZE, ((TileEntityYieldCollector.NUM_YIELD_STACKS + COLUMNS - 1) / COLUMNS) * SLOT_SIZE);
        for (int i = 0; i < slots; i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            grid.child(new ItemSlot()
                    .name("basic_claim_slot_" + i)
                    .slot(new ModularSlot(claim, i))
                    .pos(col * SLOT_SIZE, row * SLOT_SIZE));
        }
        panel.child(grid);

        Flow actions = new Flow(GuiAxis.Y)
                .name("basic_claim_actions")
                .pos(CONTENT_LEFT + COLUMNS * SLOT_SIZE + 12, GRID_Y)
                .coverChildren();
        actions.child(actionButton("Info", () -> FactionStatsGuiFactory.INSTANCE.openClient(claim.getFaction())));
        actions.child(actionButton("Unclaim", () -> {
            PacketRemoveClaim packet = new PacketRemoveClaim();
            packet.pos = claim.getClaimPos();
            WarForgeMod.NETWORK.sendToServer(packet);
            panel.closeIfOpen();
        }));
        actions.child(actionButton("Move Citadel", () -> {
            WarForgeMod.NETWORK.sendToServer(new PacketMoveCitadel());
            panel.closeIfOpen();
        }));
        panel.child(actions);

        return panel;
    }

    private static ButtonWidget<?> actionButton(String label, Runnable action) {
        return new ButtonWidget<>()
                .width(100)
                .height(20)
                .margin(0, 2)
                .overlay(Text.str(label).color(0xFFFFFF))
                .background(GuiTextures.MC_BUTTON)
                .hoverBackground(GuiTextures.MC_BUTTON_HOVERED)
                .onMousePressed((context, button) -> {
                    action.run();
                    return true;
                });
    }
}
