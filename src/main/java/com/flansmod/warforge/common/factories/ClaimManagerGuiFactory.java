package com.flansmod.warforge.common.factories;

import brachy.modularui.api.IUIHolder;
import brachy.modularui.factory.AbstractUIFactory;
import brachy.modularui.factory.GuiManager;
import brachy.modularui.api.MCHelper;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.ModularScreen;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import com.flansmod.warforge.Tags;
import com.flansmod.warforge.client.GuiClaimManager;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.network.PacketClaimChunksData;
import com.flansmod.warforge.common.util.DimChunkPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

public class ClaimManagerGuiFactory extends AbstractUIFactory<ClaimManagerGuiData> {
    public static final ClaimManagerGuiFactory INSTANCE = new ClaimManagerGuiFactory();

    public static boolean siegeTargetMode = false;
    public static DimChunkPos siegeStartPickFor = null;

    public static boolean isRemoteSiegeView() {
        return siegeStartPickFor != null;
    }

    public static void resetSiegeState() {
        siegeTargetMode = false;
        siegeStartPickFor = null;
    }

    private static final IUIHolder<ClaimManagerGuiData> HOLDER = new IUIHolder<>() {
        @Override
        public ModularPanel buildUI(ClaimManagerGuiData guiData, PanelSyncManager syncManager, UISettings settings) {
            if (guiData.isClient()) {
                return GuiClaimManager.buildPanel(guiData);
            }
            return ModularPanel.defaultPanel("claim_manager", 636, 764)
                    .topRel(0.5f);
        }

        @Override
        public ModularScreen createScreen(ClaimManagerGuiData guiData, ModularPanel mainPanel) {
            return new ModularScreen(Tags.MODID, mainPanel);
        }
    };

    private ClaimManagerGuiFactory() {
        super(new ResourceLocation(Tags.MODID, "claim_manager"));
    }

    public static void init() {
        if (!GuiManager.hasFactory(INSTANCE.getFactoryName())) {
            GuiManager.registerFactory(INSTANCE);
        }
    }

    public void open(Player player, DimChunkPos center, int radius, int pageX, int pageZ) {
        ServerPlayer serverPlayer = verifyServerSide(player);
        GuiManager.open(this, createServerData(serverPlayer, center, radius, pageX, pageZ, true), serverPlayer);
    }

    @OnlyIn(Dist.CLIENT)
    public void openClient(DimChunkPos center, int radius, int pageX, int pageZ) {
        ClaimManagerGuiData data = new ClaimManagerGuiData(verifyClientSide(MCHelper.getPlayer()), center, radius, pageX, pageZ);
        if (GuiClaimManager.rebuild(data)) {
            return;
        }
        com.flansmod.warforge.client.DeferredGuiOpen.open(() -> GuiManager.openFromClient(this, data));
    }

    @Override
    public @NotNull IUIHolder<ClaimManagerGuiData> getGuiHolder(ClaimManagerGuiData guiData) {
        return HOLDER;
    }

    @Override
    public void writeGuiData(ClaimManagerGuiData guiData, FriendlyByteBuf buffer) {
        if (guiData.isClient()) {
            buffer.writeUtf(guiData.dim.location().toString());
            buffer.writeInt(guiData.centerX);
            buffer.writeInt(guiData.centerZ);
            buffer.writeByte(guiData.radius);
            buffer.writeInt(guiData.pageX);
            buffer.writeInt(guiData.pageZ);
            return;
        }

        buffer.writeInt(guiData.pageX);
        buffer.writeInt(guiData.pageZ);
        guiData.toPacket().encodeInto(buffer);
    }

    @Override
    public @NotNull ClaimManagerGuiData readGuiData(Player player, FriendlyByteBuf buffer) {
        if (!player.level().isClientSide()) {
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(buffer.readUtf()));
            DimChunkPos center = new DimChunkPos(dim, buffer.readInt(), buffer.readInt());
            int radius = buffer.readByte();
            int pageX = buffer.readInt();
            int pageZ = buffer.readInt();
            return createServerData((ServerPlayer) player, center, radius, pageX, pageZ, true);
        }

        int pageX = buffer.readInt();
        int pageZ = buffer.readInt();
        PacketClaimChunksData packet = new PacketClaimChunksData();
        packet.decodeInto(buffer);
        return new ClaimManagerGuiData(player, packet, pageX, pageZ);
    }

    private ClaimManagerGuiData createServerData(ServerPlayer player, DimChunkPos center, int radius, int pageX, int pageZ, boolean syncClaimCache) {
        PacketClaimChunksData packet = WarForgeMod.FACTIONS.createClaimChunksData(player, center, radius);
        if (syncClaimCache) {
            WarForgeMod.NETWORK.sendTo(packet, player);
        }
        return new ClaimManagerGuiData(player, packet, pageX, pageZ);
    }
}
