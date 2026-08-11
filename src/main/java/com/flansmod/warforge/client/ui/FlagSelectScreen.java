package com.flansmod.warforge.client.ui;

import com.flansmod.warforge.api.modularui.WarForgeUiTheme;
import com.flansmod.warforge.client.ClientFlagRegistry;
import com.flansmod.warforge.client.util.FlagElement;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.network.PacketChooseFactionFlag;
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

import java.util.List;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public final class FlagSelectScreen {

    private static final int FLAG_W = 42;
    private static final int FLAG_H = 24;
    private static final int CELL_W = FLAG_W + 80;
    private static final int CELL_H = FLAG_H + 8;
    private static final int GRID_W = 332;

    private FlagSelectScreen() {
    }

    public static void open(UUID factionId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        Faction faction = WarForgeMod.FACTIONS.getFaction(factionId);
        String factionName = faction != null ? faction.name : "";
        int factionColour = faction != null ? faction.colour : 0xFFFFFF;
        String currentFlagId = faction != null ? faction.flagId : "";
        boolean canChoose = currentFlagId.isEmpty();

        List<String> availableFlags = ClientFlagRegistry.getAvailableFlags();

        UIElement root = new UIElement();
        UIElement body = WarForgeUiTheme.frame(root, GRID_W, factionColour);

        if (!factionName.isEmpty()) {
            body.addChild(WarForgeUiTheme.header("Faction Flag", factionName, factionColour));
        } else {
            body.addChild(WarForgeUiTheme.header("Faction Flag"));
        }

        String statusText = canChoose
                ? "Choose once. This cannot be changed later."
                : "Flag locked: " + displayName(currentFlagId);
        body.addChild(WarForgeUiTheme.text(statusText, WarForgeUiTheme.TEXT_SECONDARY));

        UIElement gridSection = WarForgeUiTheme.section();
        UIElement grid = new UIElement();
        grid.layout(l -> l.flexDirection(FlexDirection.ROW).flexWrap(FlexWrap.WRAP).width(GRID_W - 20));

        for (String flagId : availableFlags) {
            grid.addChild(buildCell(flagId, factionId, currentFlagId, canChoose));
        }

        gridSection.addChild(grid);
        body.addChild(gridSection);

        mc.setScreen(new ModularUIScreen(ModularUI.of(UI.of(root), mc.player), Component.literal("Faction Flag")));
    }

    private static UIElement buildCell(String flagId, UUID factionId, String currentFlagId, boolean canChoose) {
        UIElement cell = new UIElement();
        cell.layout(l -> l.flexDirection(FlexDirection.ROW).width(CELL_W).height(CELL_H).paddingAll(3).gapColumn(4));

        FlagElement flag = new FlagElement(flagId);
        flag.layout(l -> l.width(FLAG_W).height(FLAG_H));
        cell.addChild(flag);

        UIElement nameCol = new UIElement();
        nameCol.layout(l -> l.flexDirection(FlexDirection.COLUMN).flex(1));
        nameCol.addChild(WarForgeUiTheme.text(displayName(flagId), WarForgeUiTheme.TEXT_PRIMARY));
        cell.addChild(nameCol);

        boolean isChosen = flagId.equals(currentFlagId);
        if (isChosen) {
            Button selectedBtn = new Button();
            selectedBtn.setText("Selected");
            WarForgeUiTheme.styleButton(selectedBtn, 56);
            cell.addChild(selectedBtn);
        } else if (canChoose) {
            Button chooseBtn = new Button();
            chooseBtn.setText("Choose");
            WarForgeUiTheme.styleButton(chooseBtn, 56);
            chooseBtn.setOnClick(event -> {
                PacketChooseFactionFlag packet = new PacketChooseFactionFlag();
                packet.factionId = factionId;
                packet.flagId = flagId;
                WarForgeMod.NETWORK.sendToServer(packet);
            });
            cell.addChild(chooseBtn);
        }

        return cell;
    }

    private static String displayName(String flagId) {
        String raw = flagId.contains(":") ? flagId.substring(flagId.indexOf(':') + 1) : flagId;
        return raw.replace('_', ' ');
    }
}
