package com.flansmod.warforge.common.factories;

import brachy.modularui.api.IUIHolder;
import brachy.modularui.factory.AbstractUIFactory;
import brachy.modularui.factory.GuiManager;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.ModularScreen;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import com.flansmod.warforge.Tags;
import com.flansmod.warforge.client.GuiFob;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.blocks.TileEntityFob;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.server.Faction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

public class FobGuiFactory extends AbstractUIFactory<FobGuiData> {
    public static final FobGuiFactory INSTANCE = new FobGuiFactory();

    private static final IUIHolder<FobGuiData> HOLDER = new IUIHolder<FobGuiData>() {
        @Override
        public ModularPanel buildUI(FobGuiData guiData, PanelSyncManager syncManager, UISettings settings) {
            if (guiData.isClient()) {
                return GuiFob.buildPanel(guiData);
            }
            return ModularPanel.defaultPanel("fob_modular", 300, 180).topRel(0.40f);
        }

        @Override
        public ModularScreen createScreen(FobGuiData guiData, ModularPanel mainPanel) {
            return new ModularScreen(Tags.MODID, mainPanel);
        }
    };

    private FobGuiFactory() {
        super(new ResourceLocation(Tags.MODID, "fob"));
    }

    public static void init() {
        if (!GuiManager.hasFactory(INSTANCE.getFactoryName())) {
            GuiManager.registerFactory(INSTANCE);
        }
    }

    public void open(Player player, BlockPos pos) {
        ServerPlayer serverPlayer = verifyServerSide(player);
        GuiManager.open(this, createServerData(serverPlayer, pos), serverPlayer);
    }

    @Override
    public @NotNull IUIHolder<FobGuiData> getGuiHolder(FobGuiData guiData) {
        return HOLDER;
    }

    @Override
    public void writeGuiData(FobGuiData guiData, FriendlyByteBuf buffer) {
        buffer.writeUtf(guiData.pos.dim.location().toString());
        buffer.writeInt(guiData.pos.getX());
        buffer.writeInt(guiData.pos.getY());
        buffer.writeInt(guiData.pos.getZ());
        buffer.writeBoolean(guiData.established);
        buffer.writeUtf(guiData.name);
        buffer.writeInt(guiData.tickets);
        buffer.writeInt(guiData.maxTickets);
        buffer.writeBoolean(guiData.canEstablish);
        buffer.writeBoolean(guiData.canWarp);
    }

    @Override
    public @NotNull FobGuiData readGuiData(Player player, FriendlyByteBuf buffer) {
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(buffer.readUtf(32767)));
        int x = buffer.readInt();
        int y = buffer.readInt();
        int z = buffer.readInt();
        FobGuiData data = new FobGuiData(player, new DimBlockPos(dim, x, y, z));
        data.established = buffer.readBoolean();
        data.name = buffer.readUtf(32767);
        data.tickets = buffer.readInt();
        data.maxTickets = buffer.readInt();
        data.canEstablish = buffer.readBoolean();
        data.canWarp = buffer.readBoolean();
        return data;
    }

    private FobGuiData createServerData(ServerPlayer player, BlockPos pos) {
        DimBlockPos dimPos = new DimBlockPos(player.level().dimension(), pos);
        FobGuiData data = new FobGuiData(player, dimPos);

        Faction faction = WarForgeMod.FACTIONS.getFactionOfPlayer(player.getUUID());
        boolean isOfficer = faction != null
                && faction.isPlayerRoleInFaction(player.getUUID(), Faction.Role.OFFICER);

        BlockEntity be = player.level().getBlockEntity(pos);
        if (be instanceof TileEntityFob fob && !fob.ownerFaction.equals(Faction.nullUuid)) {
            data.established = true;
            data.name = fob.name;
            data.tickets = fob.tickets;
            data.maxTickets = fob.maxTickets;
            data.canWarp = faction != null && faction.uuid.equals(fob.ownerFaction) && WarForgeMod.FACTIONS.isFactionInActiveSiege(faction.uuid);
        } else {
            data.canEstablish = isOfficer;
        }
        return data;
    }
}
