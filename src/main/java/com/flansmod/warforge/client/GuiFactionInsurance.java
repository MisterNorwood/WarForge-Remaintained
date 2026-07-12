package com.flansmod.warforge.client;

import brachy.modularui.api.GuiAxis;
import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.drawable.GuiTextures;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.ListWidget;
import brachy.modularui.widgets.SlotGroupWidget;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.slot.ItemSlot;
import brachy.modularui.widgets.slot.ModularSlot;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.factories.FactionInsuranceGuiData;
import com.flansmod.warforge.common.network.PacketFactionInsuranceAction;
import net.minecraft.ChatFormatting;

public final class GuiFactionInsurance {
    private static final int WIDTH = 356;
    private static final int HEIGHT = 180;
    private static final int CONTENT_LEFT = 12;
    private static final int HEADER_Y = 12;
    private static final int BODY_Y = 54;
    private static final int INVENTORY_GAP = 8;
    private static final int INVENTORY_BOX_HEIGHT = 86;
    private static final int INVENTORY_Y = HEIGHT + INVENTORY_GAP;
    private static final int GROUP_VERTICAL_OFFSET = -(INVENTORY_GAP + INVENTORY_BOX_HEIGHT) / 2;
    private static final int STASH_CELL_SIZE = 18;
    private static final int STASH_ROW_HEIGHT = STASH_CELL_SIZE;
    private static final int STASH_LIST_HEIGHT = 56;

    private GuiFactionInsurance() {
    }

    public static ModularPanel buildPanel(FactionInsuranceGuiData data, PanelSyncManager syncManager) {
        syncManager.bindPlayerInventory(data.getPlayer());
        int sectionWidth = WIDTH - CONTENT_LEFT * 2;

        ModularPanel panel = ModularPanel.defaultPanel("faction_insurance")
                .width(WIDTH)
                .height(HEIGHT)
                .topRel(0.5f, data.hasFaction ? GROUP_VERTICAL_OFFSET : 0, 0.5f);

        Flow bodySection = ModularGuiStyle.section(sectionWidth, 118).name("insurance_body_section").pos(CONTENT_LEFT, BODY_Y);

        panel.child(new IDrawable.DrawableWidget(ModularGuiStyle.headerBackdrop()).size(WIDTH, 40));
        panel.child(bodySection);
        panel.child(new IDrawable.DrawableWidget(ModularGuiStyle.colorStripe(data.hasFaction ? data.factionColor : 0x4A4A4A)).size(6, HEIGHT));
        panel.child(ModularGuiStyle.subPanelCloseButton(WIDTH));

        panel.child(Text.str("Insurance Stash").asWidget()
                .pos(CONTENT_LEFT, HEADER_Y)
                .style(ChatFormatting.BOLD)
                .shadow(true)
                .color(ModularGuiStyle.TEXT_PRIMARY)
                .scale(1.15f));
        panel.child(Text.str(data.hasFaction ? data.factionName : "No faction selected").asWidget()
                .pos(CONTENT_LEFT, HEADER_Y + 15)
                .color(data.hasFaction ? data.factionColor : ModularGuiStyle.TEXT_SECONDARY)
                .style(ChatFormatting.BOLD));

        if (!data.hasFaction) {
            bodySection.child(Text.str("No insurance stash is available.").asWidget()
                    .margin(0, 6)
                    .color(0xD5D9DE));
            return panel;
        }

        bodySection.child(Text.str("Protected Reserve").asWidget()
                .margin(0, 0, 0, 4)
                .style(ChatFormatting.BOLD)
                .color(ModularGuiStyle.TEXT_PRIMARY));
        bodySection.child(Text.str(data.canDeposit ? "Officers and leaders may deposit. Stored items cannot be withdrawn." : "Only officers and leaders may deposit into the stash.").asWidget()
                .margin(0, 0, 0, 2)
                .color(ModularGuiStyle.TEXT_MUTED));
        bodySection.child(Text.str(data.canVoid ? "Leader actions: use the red X to permanently void a slot." : "Only the leader can void stored items to free space.").asWidget()
                .margin(0, 0, 0, 4)
                .color(data.canVoid ? 0xFFCC88 : ModularGuiStyle.TEXT_MUTED));

        int stashListWidth = sectionWidth - 10;
        int stashColumns = Math.max(1, stashListWidth / STASH_CELL_SIZE);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ListWidget<ParentWidget<?>, ?> stashList = new ListWidget();
        stashList.scrollDirection(GuiAxis.Y)
                .name("insurance_stash_list")
                .background(ModularGuiStyle.insetBackdrop())
                .width(stashListWidth)
                .height(STASH_LIST_HEIGHT);
        for (int slot = 0, row = 0; slot < data.slotCount; slot += stashColumns, row++) {
            stashList.addChild(createInsuranceRow(data, slot, stashColumns, stashListWidth), row);
        }
        bodySection.child(stashList);

        panel.child(new ParentWidget<>()
                .child(SlotGroupWidget.playerInventory(false))
                .background(GuiTextures.MC_BACKGROUND)
                .coverChildren()
                .padding(5)
                .horizontalCenter()
                .top(INVENTORY_Y));

        return panel;
    }

    private static ParentWidget<?> createInsuranceRow(FactionInsuranceGuiData data, int firstSlot, int stashColumns, int rowWidth) {
        ParentWidget<?> row = new ParentWidget<>();
        row.name("insurance_row_" + (firstSlot / stashColumns));
        row.size(rowWidth, STASH_ROW_HEIGHT);

        for (int column = 0; column < stashColumns; column++) {
            int slot = firstSlot + column;
            if (slot >= data.slotCount) {
                break;
            }

            row.child(createInsuranceCell(data, slot).pos(column * STASH_CELL_SIZE, 0));
        }

        return row;
    }

    private static ParentWidget<?> createInsuranceCell(FactionInsuranceGuiData data, int slot) {
        ParentWidget<?> cell = new ParentWidget<>();
        cell.name("insurance_cell_" + slot);
        cell.size(STASH_CELL_SIZE, STASH_CELL_SIZE);
        cell.child(new ItemSlot()
                .slot(new ModularSlot(data.insuranceHandler, slot).accessibility(data.canDeposit, false))
                .name("insurance_slot_" + slot)
                .size(STASH_CELL_SIZE));
        if (data.canVoid) {
            ButtonWidget<?> voidButton = ModularGuiStyle.smallDangerButton("x", 8, 8, () -> {
                PacketFactionInsuranceAction packet = new PacketFactionInsuranceAction();
                packet.slot = slot;
                WarForgeMod.NETWORK.sendToServer(packet);
            });
            voidButton.name("insurance_void_slot_" + slot);
            voidButton.tooltip(tooltip -> tooltip.addLine("Click to void the slot").addLine("Warning, this cannot be undone"));
            cell.child(voidButton.pos(10, 0));
        }
        return cell;
    }
}
