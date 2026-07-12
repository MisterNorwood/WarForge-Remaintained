package com.flansmod.warforge.client;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.utils.Alignment;
import brachy.modularui.value.StringValue;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.factories.FobGuiData;
import com.flansmod.warforge.common.network.PacketEstablishFob;
import com.flansmod.warforge.common.network.PacketRequestFobWarp;
import net.minecraft.ChatFormatting;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class GuiFob {
    private static final int WIDTH = 300;
    private static final int HEIGHT = 150;
    private static final int CONTENT_LEFT = 12;
    private static final int HEADER_Y = 12;
    private static final int BODY_Y = 54;

    private GuiFob() {
    }

    public static ModularPanel buildPanel(FobGuiData data) {
        int sectionWidth = WIDTH - CONTENT_LEFT * 2;

        ModularPanel panel = ModularPanel.defaultPanel("fob_modular")
                .width(WIDTH)
                .height(HEIGHT)
                .topRel(0.5f);

        panel.child(new IDrawable.DrawableWidget(ModularGuiStyle.headerBackdrop()).name("fob_header_backdrop").size(WIDTH, 40));
        panel.child(new IDrawable.DrawableWidget(ModularGuiStyle.sectionBackdrop()).name("fob_body_section").size(sectionWidth, HEIGHT - BODY_Y - 12).pos(CONTENT_LEFT, BODY_Y));
        panel.child(new IDrawable.DrawableWidget(ModularGuiStyle.colorStripe(data.factionColor)).name("fob_stripe").size(6, HEIGHT));
        panel.child(ModularGuiStyle.subPanelCloseButton(WIDTH));

        panel.child(Text.str("Forward Operating Base").asWidget()
                .name("fob_title")
                .pos(CONTENT_LEFT, HEADER_Y)
                .style(ChatFormatting.BOLD)
                .shadow(true)
                .color(ModularGuiStyle.TEXT_PRIMARY)
                .scale(1.15f));

        if (data.established) {
            buildEstablished(panel, data, sectionWidth);
        } else {
            buildEstablish(panel, data, sectionWidth);
        }

        return panel;
    }

    private static void buildEstablish(ModularPanel panel, FobGuiData data, int sectionWidth) {
        panel.child(Text.str(data.canEstablish
                        ? "Enter a name to establish this FOB."
                        : "Only officers may establish a FOB.").asWidget()
                .name("fob_establish_prompt")
                .pos(CONTENT_LEFT, HEADER_Y + 15)
                .color(ModularGuiStyle.TEXT_SECONDARY));

        StringValue nameValue = new StringValue("");
        panel.child(new TextFieldWidget()
                .value(nameValue)
                .setMaxLength(32)
                .name("fob_name_field")
                .background(ModularGuiStyle.insetBackdrop())
                .pos(CONTENT_LEFT, BODY_Y + 8)
                .size(sectionWidth - 16, 18));

        panel.child(ModularGuiStyle.actionButton("Establish", 90, data.canEstablish, () -> {
            String name = nameValue.getStringValue().trim();
            if (name.isEmpty()) {
                return;
            }
            PacketEstablishFob packet = new PacketEstablishFob();
            packet.mPos = data.pos;
            packet.mName = name;
            WarForgeMod.NETWORK.sendToServer(packet);
            panel.closeIfOpen();
        }).top(BODY_Y + 34).horizontalCenter());
    }

    private static void buildEstablished(ModularPanel panel, FobGuiData data, int sectionWidth) {
        panel.child(Text.str(data.name).asWidget()
                .name("fob_name")
                .pos(CONTENT_LEFT, HEADER_Y + 15)
                .style(ChatFormatting.BOLD)
                .color(ModularGuiStyle.TEXT_PRIMARY));

        panel.child(Text.str("Warp Tickets: " + data.tickets + " / " + data.maxTickets).asWidget()
                .name("fob_tickets")
                .alignment(Alignment.Center)
                .pos(CONTENT_LEFT, BODY_Y + 8)
                .width(sectionWidth)
                .color(data.tickets > 0 ? ModularGuiStyle.TEXT_SUCCESS : ModularGuiStyle.TEXT_WARNING));

        boolean canWarp = data.canWarp && data.tickets > 0;
        panel.child(ModularGuiStyle.actionButton("Warp", 90, canWarp, () -> {
            PacketRequestFobWarp packet = new PacketRequestFobWarp();
            packet.mPos = data.pos;
            WarForgeMod.NETWORK.sendToServer(packet);
            panel.closeIfOpen();
        }).top(BODY_Y + 34).horizontalCenter());
    }
}
