package com.flansmod.warforge.common.factories;

import brachy.modularui.api.IUIHolder;
import brachy.modularui.api.MCHelper;
import brachy.modularui.factory.AbstractUIFactory;
import brachy.modularui.factory.GuiManager;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.ModularScreen;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import com.flansmod.warforge.Tags;
import com.flansmod.warforge.client.ModularCitadelGui;
import com.flansmod.warforge.common.blocks.TileEntityCitadel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

public class CitadelGuiFactory extends AbstractUIFactory<PosGuiData> {
    public static final CitadelGuiFactory INSTANCE = new CitadelGuiFactory();

    private static final IUIHolder<PosGuiData> HOLDER = new IUIHolder<PosGuiData>() {
        @Override
        public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings settings) {
            BlockEntity be = guiData.getBlockEntity();
            if (be instanceof TileEntityCitadel citadel) {
                return ModularCitadelGui.buildUI(guiData, syncManager, settings, citadel);
            }
            return ModularPanel.defaultPanel("citadel_modular", 350, 214).topRel(0.5f);
        }

        @Override
        public ModularScreen createScreen(PosGuiData guiData, ModularPanel mainPanel) {
            return new ModularScreen(Tags.MODID, mainPanel);
        }
    };

    private CitadelGuiFactory() {
        super(new ResourceLocation(Tags.MODID, "citadel"));
    }

    public static void init() {
        if (!GuiManager.hasFactory(INSTANCE.getFactoryName())) {
            GuiManager.registerFactory(INSTANCE);
        }
    }

    public void open(Player player, BlockPos pos) {
        ServerPlayer serverPlayer = verifyServerSide(player);
        GuiManager.open(this, new PosGuiData(serverPlayer, pos), serverPlayer);
    }

    @OnlyIn(Dist.CLIENT)
    public void openClient(BlockPos pos) {
        com.flansmod.warforge.client.DeferredGuiOpen.open(() ->
                GuiManager.openFromClient(this, new PosGuiData(MCHelper.getPlayer(), pos)));
    }

    @Override
    public @NotNull IUIHolder<PosGuiData> getGuiHolder(PosGuiData guiData) {
        return HOLDER;
    }

    @Override
    public void writeGuiData(PosGuiData guiData, FriendlyByteBuf buffer) {
        com.flansmod.warforge.common.WarForgeMod.syncClaimToPlayer(guiData.getPlayer(), guiData.getBlockPos());
        buffer.writeBlockPos(guiData.getBlockPos());
    }

    @Override
    public @NotNull PosGuiData readGuiData(Player player, FriendlyByteBuf buffer) {
        return new PosGuiData(player, buffer.readBlockPos());
    }
}
