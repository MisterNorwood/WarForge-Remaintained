package com.flansmod.warforge.client.ui;

import com.flansmod.warforge.api.modularui.WarForgeUiTheme;
import com.flansmod.warforge.client.util.ColorPickerElement;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.network.PacketCreateFaction;
import com.flansmod.warforge.common.network.PacketOpenCreateFaction;
import com.flansmod.warforge.common.network.PacketSetFactionColour;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class CreateFactionScreen {
    private CreateFactionScreen() {
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        TextField nameField = new TextField().setText("").setAnyString();
        nameField.layout(l -> l.width(220).height(16));

        ColorPickerElement picker = new ColorPickerElement(0xFFFFFF);

        UIElement root = new UIElement();
        UIElement body = WarForgeUiTheme.frame(root, 240);

        Button close = WarForgeUiTheme.closeButton();
        close.setOnClick(event -> Minecraft.getInstance().setScreen(null));
        body.addChild(WarForgeUiTheme.header("Create Faction", close));

        UIElement section = WarForgeUiTheme.section();
        section.addChild(WarForgeUiTheme.text("Faction Name", WarForgeUiTheme.TEXT_SECONDARY));
        section.addChild(nameField);
        section.addChild(WarForgeUiTheme.text("Colour", WarForgeUiTheme.TEXT_SECONDARY));
        section.addChild(picker);

        UIElement buttonRow = WarForgeUiTheme.row(6);

        Button createBtn = new Button().setText("Create Faction");
        WarForgeUiTheme.styleButton(createBtn, 100);
        createBtn.setOnClick(event -> {
            String name = nameField.getValue().trim();
            if (name.isEmpty()) {
                return;
            }
            PacketCreateFaction packet = new PacketCreateFaction();
            packet.mCitadelPos = PacketOpenCreateFaction.pendingCitadelPos;
            packet.mFactionName = name;
            packet.mColour = picker.getRGB();
            WarForgeMod.NETWORK.sendToServer(packet);
            Minecraft.getInstance().setScreen(null);
        });

        Button cancelBtn = new Button().setText("Cancel");
        WarForgeUiTheme.styleButton(cancelBtn, 66);
        cancelBtn.setOnClick(event -> Minecraft.getInstance().setScreen(null));

        buttonRow.addChild(createBtn);
        buttonRow.addChild(cancelBtn);
        section.addChild(buttonRow);
        body.addChild(section);

        mc.setScreen(new ModularUIScreen(ModularUI.of(UI.of(root), mc.player), Component.literal("Create Faction")));
    }

    public static void openRecolour(int colour) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        ColorPickerElement picker = new ColorPickerElement(colour);

        UIElement root = new UIElement();
        UIElement body = WarForgeUiTheme.frame(root, 240, colour);

        Button close = WarForgeUiTheme.closeButton();
        close.setOnClick(event -> Minecraft.getInstance().setScreen(null));
        body.addChild(WarForgeUiTheme.header("Set Faction Colour", close));

        UIElement section = WarForgeUiTheme.section();
        section.addChild(WarForgeUiTheme.text("Colour", WarForgeUiTheme.TEXT_SECONDARY));
        section.addChild(picker);

        UIElement buttonRow = WarForgeUiTheme.row(6);

        Button setBtn = new Button().setText("Set Colour");
        WarForgeUiTheme.styleButton(setBtn, 100);
        setBtn.setOnClick(event -> {
            PacketSetFactionColour packet = new PacketSetFactionColour();
            packet.mColour = picker.getRGB();
            WarForgeMod.NETWORK.sendToServer(packet);
            Minecraft.getInstance().setScreen(null);
        });

        Button cancelBtn = new Button().setText("Cancel");
        WarForgeUiTheme.styleButton(cancelBtn, 66);
        cancelBtn.setOnClick(event -> Minecraft.getInstance().setScreen(null));

        buttonRow.addChild(setBtn);
        buttonRow.addChild(cancelBtn);
        section.addChild(buttonRow);
        body.addChild(section);

        mc.setScreen(new ModularUIScreen(ModularUI.of(UI.of(root), mc.player), Component.literal("Set Faction Colour")));
    }
}
