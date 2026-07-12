package com.flansmod.warforge.client;

import brachy.modularui.api.GuiAxis;
import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.drawable.Rectangle;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.UISettings;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.ListWidget;
import brachy.modularui.widgets.SlotGroupWidget;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.slot.ItemSlot;
import brachy.modularui.widgets.slot.ModularSlot;

import com.flansmod.warforge.common.blocks.TileEntityIslandCollector;
import com.flansmod.warforge.server.Faction;

import net.minecraft.ChatFormatting;
import net.minecraftforge.items.IItemHandlerModifiable;

/**
 * ModularUI panel for the faction yield collector. The storage slots are extract-only: players may
 * pull items out but never put items in. Built on both client and server so slot syncing is wired up
 * symmetrically; no {@code net.minecraft.client.*} references at build time.
 */
public final class GuiIslandCollector {

    private static final int COLUMNS = 10;
    private static final int SLOT_SIZE = 18;
    private static final int VISIBLE_ROWS = 6;
    private static final int CONTENT_LEFT = 12;
    private static final int HEADER_Y = 12;
    private static final int HEADER_HEIGHT = 40;
    private static final int BODY_Y = 54;
    private static final int LIST_WIDTH = COLUMNS * SLOT_SIZE;
    private static final int LIST_HEIGHT = VISIBLE_ROWS * SLOT_SIZE;
    private static final int SECTION_WIDTH = LIST_WIDTH + 10;
    private static final int SECTION_HEIGHT = LIST_HEIGHT + 28;
    private static final int WIDTH = CONTENT_LEFT * 2 + SECTION_WIDTH;
    private static final int HEIGHT = BODY_Y + SECTION_HEIGHT + 12;

    private static final int HEADER_FILL = 0xFF171B1F;
    private static final int SECTION_FILL = 0xEE20262B;
    private static final int INSET_FILL = 0xFF262D33;
    private static final int TEXT_PRIMARY = 0xFFFFFF;
    private static final int TEXT_SECONDARY = 0xC7CCD1;

    private GuiIslandCollector() {
    }

    public static ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings, TileEntityIslandCollector collector) {
        IItemHandlerModifiable storage = collector.getStorageHandler();
        int slots = storage.getSlots();
        boolean hasFaction = !collector.getFaction().equals(Faction.nullUuid);

        ModularPanel panel = ModularPanel.defaultPanel("island_collector", WIDTH, HEIGHT)
                .topRel(0.5f);
        panel.child(ModularGuiStyle.playerInventoryPanel(202));

        panel.child(new IDrawable.DrawableWidget(new Rectangle().color(HEADER_FILL))
                .name("island_collector_header_backdrop")
                .size(WIDTH, HEADER_HEIGHT));

        Flow storageSection = new Flow(GuiAxis.Y)
                .name("island_collector_storage_section")
                .background(new Rectangle().color(SECTION_FILL))
                .size(SECTION_WIDTH, SECTION_HEIGHT)
                .padding(5)
                .pos(CONTENT_LEFT, BODY_Y);
        panel.child(storageSection);

        panel.child(new IDrawable.DrawableWidget(new Rectangle().color(0xFF000000 | ((hasFaction ? collector.colour : 0x4A4A4A) & 0x00FFFFFF)))
                .name("island_collector_color_stripe")
                .size(6, HEIGHT));
        panel.child(ButtonWidget.panelCloseButton().name("panel_close_button").top(8).right(8).size(10));

        panel.child(Text.str("Faction Yield Storage").asWidget()
                .pos(CONTENT_LEFT, HEADER_Y)
                .style(ChatFormatting.BOLD)
                .color(TEXT_PRIMARY)
                .shadow(true)
                .scale(1.15f));
        panel.child(Text.str(hasFaction ? collector.factionName : "Unclaimed").asWidget()
                .pos(CONTENT_LEFT, HEADER_Y + 15)
                .style(ChatFormatting.BOLD)
                .color(hasFaction ? collector.colour : TEXT_SECONDARY));

        storageSection.child(Text.str("Collected Yield").asWidget()
                .margin(0, 0, 0, 4)
                .style(ChatFormatting.BOLD)
                .color(TEXT_PRIMARY));

        ListWidget<IWidget, ?> list = new ListWidget<>();
        list.scrollDirection(GuiAxis.Y);
        list.background(new Rectangle().color(INSET_FILL));
        list.width(LIST_WIDTH);
        list.height(LIST_HEIGHT);
        list.name("island_collector_list");

        int rows = (slots + COLUMNS - 1) / COLUMNS;
        for (int row = 0; row < rows; row++) {
            list.addChild(buildRow(storage, row, slots), row);
        }
        storageSection.child(list);

        return panel;
    }

    private static ParentWidget<?> buildRow(IItemHandlerModifiable storage, int row, int slots) {
        ParentWidget<?> rowWidget = new ParentWidget<>();
        rowWidget.name("island_collector_row_" + row);
        rowWidget.size(LIST_WIDTH, SLOT_SIZE);

        for (int col = 0; col < COLUMNS; col++) {
            int slot = row * COLUMNS + col;
            if (slot >= slots) {
                break;
            }
            rowWidget.child(new ItemSlot()
                    .name("island_collector_slot_" + slot)
                    // canPut = false (nothing can be put in), canTake = true (players may extract)
                    .slot(new ModularSlot(storage, slot).accessibility(false, true))
                    .size(SLOT_SIZE)
                    .pos(col * SLOT_SIZE, 0));
        }
        return rowWidget;
    }
}
