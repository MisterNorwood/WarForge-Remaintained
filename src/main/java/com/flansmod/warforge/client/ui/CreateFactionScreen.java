package com.flansmod.warforge.client.ui;

import com.flansmod.warforge.api.modularui.WarForgeUiTheme;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.network.PacketCreateFaction;
import com.flansmod.warforge.common.network.PacketOpenCreateFaction;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import dev.vfyjxf.taffy.style.FlexDirection;
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

        UIElement root = new UIElement();
        UIElement body = WarForgeUiTheme.frame(root, 240);

        body.addChild(WarForgeUiTheme.header("Create Faction"));

        UIElement section = WarForgeUiTheme.section();
        section.addChild(WarForgeUiTheme.text("Faction Name", WarForgeUiTheme.TEXT_SECONDARY));
        section.addChild(nameField);

        UIElement buttonRow = WarForgeUiTheme.row(6);

        Button createBtn = new Button().setText("Create Faction");
        WarForgeUiTheme.styleButton(createBtn, 100);
        createBtn.setOnClick(event -> {
            PacketCreateFaction packet = new PacketCreateFaction();
            packet.mCitadelPos = PacketOpenCreateFaction.pendingCitadelPos;
            packet.mFactionName = nameField.getValue();
            packet.mColour = 0xFFFFFF;
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
}
