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
import com.flansmod.warforge.client.GuiFactionFlagSelect;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.server.Faction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class FactionFlagSelectGuiFactory extends AbstractUIFactory<FactionFlagSelectGuiData> {
    public static final FactionFlagSelectGuiFactory INSTANCE = new FactionFlagSelectGuiFactory();

    private static final IUIHolder<FactionFlagSelectGuiData> HOLDER = new IUIHolder<>() {
        @Override
        public ModularPanel buildUI(FactionFlagSelectGuiData guiData, PanelSyncManager syncManager, UISettings settings) {
            return GuiFactionFlagSelect.buildPanel(guiData);
        }

        @Override
        public ModularScreen createScreen(FactionFlagSelectGuiData guiData, ModularPanel mainPanel) {
            return new ModularScreen(Tags.MODID, mainPanel);
        }
    };

    private FactionFlagSelectGuiFactory() {
        super(new ResourceLocation(Tags.MODID, "faction_flag_select"));
    }

    public static void init() {
        if (!GuiManager.hasFactory(INSTANCE.getFactoryName())) {
            GuiManager.registerFactory(INSTANCE);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public void openClient(UUID factionId) {
        com.flansmod.warforge.client.DeferredGuiOpen.open(() ->
                GuiManager.openFromClient(this, new FactionFlagSelectGuiData(verifyClientSide(MCHelper.getPlayer()), factionId)));
    }

    @OnlyIn(Dist.CLIENT)
    public void openClientChild(Runnable reopenParent, UUID factionId) {
        com.flansmod.warforge.client.DeferredGuiOpen.openChild(reopenParent, () ->
                GuiManager.openFromClient(this, new FactionFlagSelectGuiData(verifyClientSide(MCHelper.getPlayer()), factionId)));
    }

    public void open(Player player, UUID factionId) {
        ServerPlayer serverPlayer = verifyServerSide(player);
        GuiManager.open(this, createServerData(serverPlayer, factionId), serverPlayer);
    }

    @Override
    public @NotNull IUIHolder<FactionFlagSelectGuiData> getGuiHolder(FactionFlagSelectGuiData guiData) {
        return HOLDER;
    }

    @Override
    public void writeGuiData(FactionFlagSelectGuiData guiData, FriendlyByteBuf buffer) {
        buffer.writeUUID(guiData.factionId);
        if (guiData.isClient()) {
            return;
        }
        buffer.writeUtf(guiData.factionName);
        buffer.writeInt(guiData.factionColor);
        buffer.writeUtf(guiData.currentFlagId);
        buffer.writeBoolean(guiData.canChoose);
        buffer.writeShort(guiData.availableFlags.size());
        for (String id : guiData.availableFlags) {
            buffer.writeUtf(id);
        }
    }

    @Override
    public @NotNull FactionFlagSelectGuiData readGuiData(Player player, FriendlyByteBuf buffer) {
        UUID factionId = buffer.readUUID();
        if (!player.level().isClientSide) {
            return createServerData((ServerPlayer) player, factionId);
        }

        FactionFlagSelectGuiData data = new FactionFlagSelectGuiData(player, factionId);
        data.factionName = buffer.readUtf();
        data.factionColor = buffer.readInt();
        data.currentFlagId = buffer.readUtf();
        data.canChoose = buffer.readBoolean();
        int count = buffer.readShort();
        for (int i = 0; i < count; i++) {
            data.availableFlags.add(buffer.readUtf());
        }
        return data;
    }

    private FactionFlagSelectGuiData createServerData(ServerPlayer player, UUID factionId) {
        Faction faction = WarForgeMod.FACTIONS.getFaction(factionId);
        FactionFlagSelectGuiData data = new FactionFlagSelectGuiData(player, factionId);
        if (faction == null) {
            return data;
        }
        data.factionName = faction.name;
        data.factionColor = faction.colour;
        data.currentFlagId = faction.flagId;
        boolean isOp = player.getServer() != null && player.getServer().getPlayerList().isOp(player.getGameProfile());
        data.canChoose = (isOp || faction.isPlayerRoleInFaction(player.getUUID(), Faction.Role.LEADER)) && faction.flagId.isEmpty();
        data.availableFlags.addAll(WarForgeMod.FLAG_REGISTRY.getAvailableFlagIds(player.getUUID()));
        return data;
    }
}
