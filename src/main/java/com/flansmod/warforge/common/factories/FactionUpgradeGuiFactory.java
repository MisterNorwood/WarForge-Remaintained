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
import com.flansmod.warforge.client.GUIUpgradePanel;
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

public class FactionUpgradeGuiFactory extends AbstractUIFactory<FactionUpgradeGuiData> {
    public static final FactionUpgradeGuiFactory INSTANCE = new FactionUpgradeGuiFactory();

    private static final IUIHolder<FactionUpgradeGuiData> HOLDER = new IUIHolder<>() {
        @Override
        public ModularPanel buildUI(FactionUpgradeGuiData guiData, PanelSyncManager syncManager, UISettings settings) {
            if (guiData.isClient()) {
                return GUIUpgradePanel.buildPanel(guiData);
            }
            return ModularPanel.defaultPanel("citadel_upgrade_panel")
                    .width(GUIUpgradePanel.WIDTH)
                    .height(GUIUpgradePanel.HEIGHT)
                    .topRel(0.5f);
        }

        @Override
        public ModularScreen createScreen(FactionUpgradeGuiData guiData, ModularPanel mainPanel) {
            return new ModularScreen(Tags.MODID, mainPanel);
        }
    };

    private FactionUpgradeGuiFactory() {
        super(new ResourceLocation(Tags.MODID, "faction_upgrade"));
    }

    public static void init() {
        if (!GuiManager.hasFactory(INSTANCE.getFactoryName())) {
            GuiManager.registerFactory(INSTANCE);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public void openClient(UUID factionId) {
        com.flansmod.warforge.client.DeferredGuiOpen.open(() ->
                GuiManager.openFromClient(this, new FactionUpgradeGuiData(MCHelper.getPlayer(), factionId)));
    }

    @OnlyIn(Dist.CLIENT)
    public void openClientChild(Runnable reopenParent, UUID factionId) {
        com.flansmod.warforge.client.DeferredGuiOpen.openChild(reopenParent, () ->
                GuiManager.openFromClient(this, new FactionUpgradeGuiData(MCHelper.getPlayer(), factionId)));
    }

    @OnlyIn(Dist.CLIENT)
    public void openClientSibling(UUID factionId) {
        com.flansmod.warforge.client.DeferredGuiOpen.openSibling(() ->
                GuiManager.openFromClient(this, new FactionUpgradeGuiData(MCHelper.getPlayer(), factionId)));
    }

    public void open(Player player, UUID factionId) {
        ServerPlayer serverPlayer = verifyServerSide(player);
        GuiManager.open(this, createServerData(serverPlayer, factionId), serverPlayer);
    }

    @Override
    public @NotNull IUIHolder<FactionUpgradeGuiData> getGuiHolder(FactionUpgradeGuiData guiData) {
        return HOLDER;
    }

    @Override
    public void writeGuiData(FactionUpgradeGuiData guiData, FriendlyByteBuf buffer) {
        if (guiData.isClient()) {
            buffer.writeUUID(guiData.requestedFactionId);
            return;
        }

        buffer.writeUUID(guiData.factionId);
        buffer.writeUtf(guiData.factionName);
        buffer.writeInt(guiData.level);
        buffer.writeInt(guiData.color);
        buffer.writeBoolean(guiData.outrankingOfficer);
    }

    @Override
    public @NotNull FactionUpgradeGuiData readGuiData(Player player, FriendlyByteBuf buffer) {
        if (!player.level().isClientSide) {
            return createServerData((ServerPlayer) player, buffer.readUUID());
        }

        FactionUpgradeGuiData data = new FactionUpgradeGuiData(player, Faction.nullUuid);
        data.factionId = buffer.readUUID();
        data.factionName = buffer.readUtf();
        data.level = buffer.readInt();
        data.color = buffer.readInt();
        data.outrankingOfficer = buffer.readBoolean();
        return data;
    }

    private FactionUpgradeGuiData createServerData(ServerPlayer player, UUID requestedFactionId) {
        UUID effectiveFactionId = requestedFactionId;
        if (effectiveFactionId.equals(Faction.nullUuid)) {
            Faction playerFaction = WarForgeMod.FACTIONS.getFactionOfPlayer(player.getUUID());
            effectiveFactionId = playerFaction == null ? Faction.nullUuid : playerFaction.uuid;
        }

        FactionUpgradeGuiData data = new FactionUpgradeGuiData(player, effectiveFactionId);
        Faction faction = WarForgeMod.FACTIONS.getFaction(effectiveFactionId);
        if (faction == null) {
            return data;
        }

        data.factionId = faction.uuid;
        data.factionName = faction.name;
        data.level = faction.citadelLevel;
        data.color = faction.colour;
        data.outrankingOfficer = faction.isPlayerRoleInFaction(player.getUUID(), Faction.Role.OFFICER) || WarForgeMod.isOp(player);
        return data;
    }
}
