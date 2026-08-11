package com.flansmod.warforge.client.ui;

import com.flansmod.warforge.api.modularui.WarForgeUiTheme;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.network.PacketFactionInsuranceAction;
import com.flansmod.warforge.common.network.PacketInsuranceData;
import com.flansmod.warforge.server.Faction;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class InsuranceScreen {
    private static final int WIDTH = 320;

    private InsuranceScreen() {
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        PacketInsuranceData data = PacketInsuranceData.latest;
        if (mc.player == null || data == null) {
            return;
        }

        UIElement root = new UIElement();
        int stripeColor = (data.hasFaction && !data.factionId.equals(Faction.nullUuid))
                ? data.factionColor
                : 0x4A4A4A;
        UIElement body = WarForgeUiTheme.frame(root, WIDTH, stripeColor);
        Button close = WarForgeUiTheme.closeButton();
        close.setOnClick(event -> Minecraft.getInstance().setScreen(null));

        if (!data.hasFaction || data.factionId.equals(Faction.nullUuid)) {
            body.addChild(WarForgeUiTheme.header("Insurance Stash", close));
            UIElement empty = WarForgeUiTheme.section();
            empty.addChild(WarForgeUiTheme.text("No insurance stash is available.", WarForgeUiTheme.TEXT_SECONDARY));
            body.addChild(empty);
            mc.setScreen(new ModularUIScreen(ModularUI.of(UI.of(root), mc.player), Component.literal("Faction Stash")));
            return;
        }

        body.addChild(WarForgeUiTheme.header("Insurance Stash", data.factionName, data.factionColor, close));

        List<ItemStack> stacks = data.stacks;
        boolean canWithdraw = data.canWithdraw;

        UIElement stashSection = WarForgeUiTheme.section();
        if (stacks.isEmpty()) {
            stashSection.addChild(WarForgeUiTheme.text("The stash is empty.", WarForgeUiTheme.TEXT_MUTED));
        } else {
            for (int i = 0; i < stacks.size(); i++) {
                ItemStack stack = stacks.get(i);
                stashSection.addChild(buildStackRow(stack, i, canWithdraw));
            }
        }
        body.addChild(stashSection);

        mc.setScreen(new ModularUIScreen(ModularUI.of(UI.of(root), mc.player), Component.literal("Faction Stash")));
    }

    private static UIElement buildStackRow(ItemStack stack, int slotIndex, boolean canWithdraw) {
        UIElement row = WarForgeUiTheme.row(6);

        String displayName = stack.isEmpty()
                ? "(empty)"
                : stack.getHoverName().getString() + " x" + stack.getCount();
        row.addChild(WarForgeUiTheme.text(displayName, WarForgeUiTheme.TEXT_PRIMARY).layout(l -> l.width(200)));

        if (canWithdraw && !stack.isEmpty()) {
            int captured = slotIndex;
            Button withdraw = new Button().setText("Withdraw");
            WarForgeUiTheme.styleButton(withdraw, 70);
            withdraw.setOnClick(event -> {
                PacketFactionInsuranceAction packet = new PacketFactionInsuranceAction();
                packet.slot = captured;
                WarForgeMod.NETWORK.sendToServer(packet);
            });
            row.addChild(withdraw);
        }

        return row;
    }
}
