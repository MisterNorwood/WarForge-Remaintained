package com.flansmod.warforge.client.ui;

import com.flansmod.warforge.api.modularui.WarForgeUiTheme;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.network.PacketRequestUpgrade;
import com.flansmod.warforge.common.network.PacketUpgradeUI;
import com.flansmod.warforge.server.Faction;
import com.flansmod.warforge.server.ItemMatcher;
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

import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class FactionUpgradeScreen {
    private FactionUpgradeScreen() {
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        PacketUpgradeUI data = PacketUpgradeUI.latest;
        if (mc.player == null || data == null || data.mFactionID.equals(Faction.nullUuid)) {
            return;
        }

        UIElement root = new UIElement();
        UIElement body = WarForgeUiTheme.frame(root, 320, data.color & 0xFFFFFF);
        Button close = WarForgeUiTheme.closeButton();
        close.setOnClick(event -> Minecraft.getInstance().setScreen(null));

        body.addChild(WarForgeUiTheme.header("Citadel Upgrade", data.mFactionName, data.color & 0xFFFFFF, close));

        UIElement infoSection = WarForgeUiTheme.section();
        infoSection.addChild(WarForgeUiTheme.boldText("Current Level: " + data.level, WarForgeUiTheme.TEXT_PRIMARY));
        body.addChild(infoSection);

        HashMap<ItemMatcher, Integer> requirements = WarForgeMod.UPGRADE_HANDLER.getRequirementsFor(data.level);
        if (requirements == null || requirements.isEmpty()) {
            UIElement maxSection = WarForgeUiTheme.section();
            maxSection.addChild(WarForgeUiTheme.text("Maximum level reached.", WarForgeUiTheme.TEXT_MUTED));
            body.addChild(maxSection);
        } else {
            UIElement reqSection = WarForgeUiTheme.section();
            reqSection.addChild(WarForgeUiTheme.boldText("Requirements for next level:", WarForgeUiTheme.TEXT_SECONDARY));

            List<ItemStack> inventory = mc.player.getInventory().items;
            boolean allMet = true;
            for (Map.Entry<ItemMatcher, Integer> entry : requirements.entrySet()) {
                ItemMatcher matcher = entry.getKey();
                int required = entry.getValue();
                String name = matcher.toStack().getHoverName().getString();
                int has = inventory.stream()
                        .filter(stack -> !stack.isEmpty() && matcher.matches(stack))
                        .mapToInt(ItemStack::getCount)
                        .sum();
                if (has < required) {
                    allMet = false;
                }
                UIElement reqRow = WarForgeUiTheme.row(6);
                reqRow.addChild(WarForgeUiTheme.text(name + " x" + required, WarForgeUiTheme.TEXT_SECONDARY).layout(l -> l.flexGrow(1)));
                reqRow.addChild(WarForgeUiTheme.text(has + "/" + required, has >= required ? WarForgeUiTheme.TEXT_SUCCESS : WarForgeUiTheme.TEXT_DANGER));
                reqSection.addChild(reqRow);
            }
            body.addChild(reqSection);

            if (data.outrankingOfficer) {
                UIElement actionSection = WarForgeUiTheme.section();
                UIElement actions = new UIElement();
                actions.layout(l -> l.flexDirection(FlexDirection.ROW).flexWrap(FlexWrap.WRAP).widthStretch().gapColumn(4).gapRow(4));
                boolean canUpgrade = allMet;
                Button upgradeBtn = new Button().setText("Upgrade");
                WarForgeUiTheme.styleButton(upgradeBtn, 80, canUpgrade ? WarForgeUiTheme.BUTTON_FILL : WarForgeUiTheme.DANGER_FILL);
                upgradeBtn.setOnClick(event -> {
                    PacketRequestUpgrade packet = new PacketRequestUpgrade();
                    packet.factionID = data.mFactionID;
                    WarForgeMod.NETWORK.sendToServer(packet);
                });
                actions.addChild(upgradeBtn);
                actionSection.addChild(actions);
                body.addChild(actionSection);
            }
        }

        mc.setScreen(new ModularUIScreen(ModularUI.of(UI.of(root), mc.player), Component.literal("Citadel Upgrade")));
    }
}
