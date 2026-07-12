package com.flansmod.warforge.common.factories;

import brachy.modularui.api.IUIHolder;
import brachy.modularui.api.MCHelper;
import brachy.modularui.factory.AbstractUIFactory;
import brachy.modularui.factory.GuiManager;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.ModularScreen;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import com.flansmod.warforge.Tags;
import com.flansmod.warforge.client.DeferredGuiOpen;
import com.flansmod.warforge.client.GuiCreateFactionModular;
import com.flansmod.warforge.common.util.DimBlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

public class CreateFactionGuiFactory extends AbstractUIFactory<CreateFactionGuiData> {
    public static final CreateFactionGuiFactory INSTANCE = new CreateFactionGuiFactory();

    private static final IUIHolder<CreateFactionGuiData> HOLDER = new IUIHolder<>() {
        @Override
        public ModularPanel buildUI(CreateFactionGuiData guiData, PanelSyncManager syncManager, UISettings settings) {
            if (guiData.isClient()) {
                return GuiCreateFactionModular.buildPanel(guiData);
            }
            return ModularPanel.defaultPanel("create_faction_modular", 264, 190).topRel(0.5f);
        }

        @Override
        public ModularScreen createScreen(CreateFactionGuiData guiData, ModularPanel mainPanel) {
            return new ModularScreen(Tags.MODID, mainPanel);
        }
    };

    private CreateFactionGuiFactory() {
        super(new ResourceLocation(Tags.MODID, "create_faction"));
    }

    public static void init() {
        if (!GuiManager.hasFactory(INSTANCE.getFactoryName())) {
            GuiManager.registerFactory(INSTANCE);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public void openClientChild(Runnable reopenParent, DimBlockPos citadelPos, int colour, boolean isRecolour) {
        DeferredGuiOpen.openChild(reopenParent, () ->
                GuiManager.openFromClient(this, new CreateFactionGuiData(verifyClientSide(MCHelper.getPlayer()), citadelPos, colour, isRecolour)));
    }

    @Override
    public @NotNull IUIHolder<CreateFactionGuiData> getGuiHolder(CreateFactionGuiData guiData) {
        return HOLDER;
    }

    @Override
    public void writeGuiData(CreateFactionGuiData guiData, FriendlyByteBuf buffer) {
        buffer.writeUtf(guiData.citadelPos.dim.location().toString());
        buffer.writeInt(guiData.citadelPos.getX());
        buffer.writeInt(guiData.citadelPos.getY());
        buffer.writeInt(guiData.citadelPos.getZ());
        buffer.writeInt(guiData.colour);
        buffer.writeBoolean(guiData.isRecolour);
    }

    @Override
    public @NotNull CreateFactionGuiData readGuiData(Player player, FriendlyByteBuf buffer) {
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(buffer.readUtf(32767)));
        int x = buffer.readInt();
        int y = buffer.readInt();
        int z = buffer.readInt();
        int colour = buffer.readInt();
        boolean isRecolour = buffer.readBoolean();
        return new CreateFactionGuiData(player, new DimBlockPos(dim, x, y, z), colour, isRecolour);
    }
}
