package com.flansmod.warforge.common.factories;

import brachy.modularui.api.IUIHolder;
import brachy.modularui.factory.AbstractUIFactory;
import brachy.modularui.factory.GuiManager;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.ModularScreen;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import com.flansmod.warforge.Tags;
import com.flansmod.warforge.client.GuiSiegeCamp;
import com.flansmod.warforge.common.network.PacketSiegeCampInfo;
import com.flansmod.warforge.common.network.SiegeCampAttackInfo;
import com.flansmod.warforge.common.util.DimBlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SiegeCampGuiFactory extends AbstractUIFactory<SiegeCampGuiData> {
    public static final SiegeCampGuiFactory INSTANCE = new SiegeCampGuiFactory();

    private static final IUIHolder<SiegeCampGuiData> HOLDER = new IUIHolder<SiegeCampGuiData>() {
        @Override
        public ModularPanel buildUI(SiegeCampGuiData guiData, PanelSyncManager syncManager, UISettings settings) {
            if (guiData.isClient()) {
                return GuiSiegeCamp.buildPanel(guiData);
            }
            return ModularPanel.defaultPanel("siege_main", 380, 508).topRel(0.5f);
        }

        @Override
        public ModularScreen createScreen(SiegeCampGuiData guiData, ModularPanel mainPanel) {
            return new ModularScreen(Tags.MODID, mainPanel);
        }
    };

    private SiegeCampGuiFactory() {
        super(new ResourceLocation(Tags.MODID, "siege_camp"));
    }

    public static void init() {
        if (!GuiManager.hasFactory(INSTANCE.getFactoryName())) {
            GuiManager.registerFactory(INSTANCE);
        }
    }

    public void open(Player player, DimBlockPos siegeCampPos, List<SiegeCampAttackInfo> possibleAttacks, byte momentum, int color) {
        ServerPlayer serverPlayer = verifyServerSide(player);
        GuiManager.open(this, new SiegeCampGuiData(serverPlayer, siegeCampPos, possibleAttacks, momentum, color), serverPlayer);
    }

    @Override
    public @NotNull IUIHolder<SiegeCampGuiData> getGuiHolder(SiegeCampGuiData guiData) {
        return HOLDER;
    }

    @Override
    public void writeGuiData(SiegeCampGuiData guiData, FriendlyByteBuf buffer) {
        guiData.toPacket().encodeInto(buffer);
    }

    @Override
    public @NotNull SiegeCampGuiData readGuiData(Player player, FriendlyByteBuf buffer) {
        PacketSiegeCampInfo packet = new PacketSiegeCampInfo();
        packet.decodeInto(buffer);
        return new SiegeCampGuiData(player, packet.mSiegeCampPos, packet.mPossibleAttacks, packet.momentum, packet.color);
    }
}
