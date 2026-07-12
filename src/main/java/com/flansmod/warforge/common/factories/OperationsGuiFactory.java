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
import com.flansmod.warforge.client.GuiOperations;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.server.Faction;
import com.flansmod.warforge.server.Siege;
import com.flansmod.warforge.server.fob.Fob;
import com.flansmod.warforge.server.fob.FobPresence;
import com.flansmod.warforge.server.fob.FobWarpQueue;
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

import java.util.Map;

public class OperationsGuiFactory extends AbstractUIFactory<OperationsGuiData> {
    public static final OperationsGuiFactory INSTANCE = new OperationsGuiFactory();

    private static final IUIHolder<OperationsGuiData> HOLDER = new IUIHolder<OperationsGuiData>() {
        @Override
        public ModularPanel buildUI(OperationsGuiData guiData, PanelSyncManager syncManager, UISettings settings) {
            if (guiData.isClient()) {
                return GuiOperations.buildPanel(guiData);
            }
            return ModularPanel.defaultPanel("operations", 372, 268).topRel(0.5f);
        }

        @Override
        public ModularScreen createScreen(OperationsGuiData guiData, ModularPanel mainPanel) {
            return new ModularScreen(Tags.MODID, mainPanel);
        }
    };

    private OperationsGuiFactory() {
        super(new ResourceLocation(Tags.MODID, "operations"));
    }

    public static void init() {
        if (!GuiManager.hasFactory(INSTANCE.getFactoryName())) {
            GuiManager.registerFactory(INSTANCE);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public void openClient() {
        com.flansmod.warforge.client.DeferredGuiOpen.open(() ->
                GuiManager.openFromClient(this, new OperationsGuiData(MCHelper.getPlayer())));
    }

    public void open(Player player) {
        ServerPlayer serverPlayer = verifyServerSide(player);
        GuiManager.open(this, createServerData(serverPlayer), serverPlayer);
    }

    @Override
    public @NotNull IUIHolder<OperationsGuiData> getGuiHolder(OperationsGuiData guiData) {
        return HOLDER;
    }

    @Override
    public void writeGuiData(OperationsGuiData guiData, FriendlyByteBuf buffer) {
        if (guiData.isClient()) {
            return;
        }
        buffer.writeBoolean(guiData.hasFaction);
        buffer.writeUUID(guiData.factionId);
        buffer.writeUtf(guiData.factionName);
        buffer.writeInt(guiData.factionColor);
        buffer.writeInt(guiData.warpTicketCost);

        buffer.writeShort(guiData.sieges.size());
        for (OperationsGuiData.SiegeEntry siege : guiData.sieges) {
            buffer.writeUtf(siege.attackerName);
            buffer.writeUtf(siege.defenderName);
            buffer.writeInt(siege.attackerColor);
            buffer.writeInt(siege.defenderColor);
            buffer.writeInt(siege.chunkX);
            buffer.writeInt(siege.chunkZ);
            buffer.writeUtf(siege.dimName);
            buffer.writeBoolean(siege.attacking);
        }

        buffer.writeShort(guiData.fobs.size());
        for (OperationsGuiData.FobEntry fob : guiData.fobs) {
            buffer.writeUtf(fob.pos.dim.location().toString());
            buffer.writeInt(fob.pos.getX());
            buffer.writeInt(fob.pos.getY());
            buffer.writeInt(fob.pos.getZ());
            buffer.writeUtf(fob.name);
            buffer.writeInt(fob.tickets);
            buffer.writeInt(fob.maxTickets);
            buffer.writeBoolean(fob.occupied);
            buffer.writeBoolean(fob.canWarp);
        }
    }

    @Override
    public @NotNull OperationsGuiData readGuiData(Player player, FriendlyByteBuf buffer) {
        if (!player.level().isClientSide) {
            return createServerData((ServerPlayer) player);
        }
        OperationsGuiData data = new OperationsGuiData(player);
        data.hasFaction = buffer.readBoolean();
        data.factionId = buffer.readUUID();
        data.factionName = buffer.readUtf();
        data.factionColor = buffer.readInt();
        data.warpTicketCost = buffer.readInt();

        int siegeCount = buffer.readShort();
        for (int i = 0; i < siegeCount; i++) {
            OperationsGuiData.SiegeEntry siege = new OperationsGuiData.SiegeEntry();
            siege.attackerName = buffer.readUtf();
            siege.defenderName = buffer.readUtf();
            siege.attackerColor = buffer.readInt();
            siege.defenderColor = buffer.readInt();
            siege.chunkX = buffer.readInt();
            siege.chunkZ = buffer.readInt();
            siege.dimName = buffer.readUtf();
            siege.attacking = buffer.readBoolean();
            data.sieges.add(siege);
        }

        int fobCount = buffer.readShort();
        for (int i = 0; i < fobCount; i++) {
            OperationsGuiData.FobEntry fob = new OperationsGuiData.FobEntry();
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(buffer.readUtf()));
            int x = buffer.readInt();
            int y = buffer.readInt();
            int z = buffer.readInt();
            fob.pos = new DimBlockPos(dim, x, y, z);
            fob.name = buffer.readUtf();
            fob.tickets = buffer.readInt();
            fob.maxTickets = buffer.readInt();
            fob.occupied = buffer.readBoolean();
            fob.canWarp = buffer.readBoolean();
            data.fobs.add(fob);
        }
        return data;
    }

    private OperationsGuiData createServerData(ServerPlayer player) {
        OperationsGuiData data = new OperationsGuiData(player);
        Faction faction = WarForgeMod.FACTIONS.getFactionOfPlayer(player.getUUID());
        if (faction == null) {
            return data;
        }
        data.hasFaction = true;
        data.factionId = faction.uuid;
        data.factionName = faction.name;
        data.factionColor = faction.colour;
        data.warpTicketCost = 1 + FobWarpQueue.vehicleExtraCost(player.getVehicle());

        boolean ownerInSiege = WarForgeMod.FACTIONS.isFactionInActiveSiege(faction.uuid);

        for (Map.Entry<DimChunkPos, Siege> entry : WarForgeMod.FACTIONS.getSieges().entrySet()) {
            Siege siege = entry.getValue();
            boolean attacking = faction.uuid.equals(siege.attackingFaction);
            boolean defending = faction.uuid.equals(siege.defendingFaction);
            if (!attacking && !defending) {
                continue;
            }
            Faction attacker = WarForgeMod.FACTIONS.getFaction(siege.attackingFaction);
            Faction defender = WarForgeMod.FACTIONS.getFaction(siege.defendingFaction);
            OperationsGuiData.SiegeEntry siegeEntry = new OperationsGuiData.SiegeEntry();
            siegeEntry.attackerName = attacker == null ? "Unknown" : attacker.name;
            siegeEntry.defenderName = defender == null ? "Unknown" : defender.name;
            siegeEntry.attackerColor = attacker == null ? 0xFFFFFF : attacker.colour;
            siegeEntry.defenderColor = defender == null ? 0xFFFFFF : defender.colour;
            siegeEntry.chunkX = entry.getKey().x;
            siegeEntry.chunkZ = entry.getKey().z;
            siegeEntry.dimName = entry.getKey().dim.location().toString();
            siegeEntry.attacking = attacking;
            data.sieges.add(siegeEntry);
        }

        for (Fob fob : WarForgeMod.FOBS.fobsOf(faction)) {
            OperationsGuiData.FobEntry fobEntry = new OperationsGuiData.FobEntry();
            fobEntry.pos = fob.pos;
            fobEntry.name = fob.name;
            fobEntry.tickets = fob.tickets;
            fobEntry.maxTickets = fob.maxTickets;
            fobEntry.occupied = FobPresence.enemyHeld(fob);
            fobEntry.canWarp = ownerInSiege && !fobEntry.occupied && fob.tickets >= data.warpTicketCost;
            data.fobs.add(fobEntry);
        }
        return data;
    }
}
