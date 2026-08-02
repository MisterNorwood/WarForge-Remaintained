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
import com.flansmod.warforge.client.GuiBasicClaim;
import com.flansmod.warforge.common.blocks.TileEntityBasicClaim;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

public class BasicClaimGuiFactory extends AbstractUIFactory<PosGuiData> {
    public static final BasicClaimGuiFactory INSTANCE = new BasicClaimGuiFactory();

    private static final IUIHolder<PosGuiData> HOLDER = new IUIHolder<PosGuiData>() {
        @Override
        public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings settings) {
            BlockEntity be = guiData.getBlockEntity();
            if (be instanceof TileEntityBasicClaim claim) {
                return GuiBasicClaim.buildUI(guiData, syncManager, settings, claim);
            }
            return ModularPanel.defaultPanel("basic_claim", 200, 182).topRel(0.5f);
        }

        @Override
        public ModularScreen createScreen(PosGuiData guiData, ModularPanel mainPanel) {
            return new ModularScreen(Tags.MODID, mainPanel);
        }
    };

    private BasicClaimGuiFactory() {
        super(new ResourceLocation(Tags.MODID, "basic_claim"));
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
        buffer.writeBlockPos(guiData.getBlockPos());
    }

    @Override
    public @NotNull PosGuiData readGuiData(Player player, FriendlyByteBuf buffer) {
        return new PosGuiData(player, buffer.readBlockPos());
    }
}
