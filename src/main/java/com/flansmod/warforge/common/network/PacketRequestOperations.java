package com.flansmod.warforge.common.network;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.factories.OperationsGuiData;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.server.Faction;
import com.flansmod.warforge.server.Siege;
import com.flansmod.warforge.server.fob.Fob;
import com.flansmod.warforge.server.fob.FobPresence;
import com.flansmod.warforge.server.fob.FobWarpQueue;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Map;

public class PacketRequestOperations extends PacketBase {
    public static final CustomPacketPayload.Type<PacketRequestOperations> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetrequestoperations"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketRequestOperations> STREAM_CODEC =
        StreamCodec.ofMember(PacketRequestOperations::encodeInto, buf -> { PacketRequestOperations p = new PacketRequestOperations(); p.decodeInto(buf); return p; });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    @Override
    public void encodeInto(FriendlyByteBuf data) {
    }

    @Override
    public void decodeInto(FriendlyByteBuf data) {
    }

    @Override
    public void handleServerSide(ServerPlayer player) {
        PacketOperationsData resp = new PacketOperationsData();
        Faction faction = WarForgeMod.FACTIONS.getFactionOfPlayer(player.getUUID());
        if (faction == null) {
            WarForgeMod.NETWORK.sendTo(resp, player);
            return;
        }
        resp.hasFaction = true;
        resp.factionId = faction.uuid;
        resp.factionName = faction.name;
        resp.factionColor = faction.colour;
        resp.warpTicketCost = 1 + FobWarpQueue.vehicleExtraCost(player.getVehicle());

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
            resp.sieges.add(siegeEntry);
        }

        for (Fob fob : WarForgeMod.FOBS.fobsOf(faction)) {
            OperationsGuiData.FobEntry fobEntry = new OperationsGuiData.FobEntry();
            fobEntry.pos = fob.pos;
            fobEntry.name = fob.name;
            fobEntry.tickets = fob.tickets;
            fobEntry.maxTickets = fob.maxTickets;
            fobEntry.occupied = FobPresence.enemyHeld(fob);
            fobEntry.canWarp = ownerInSiege && !fobEntry.occupied && fob.tickets >= resp.warpTicketCost;
            resp.fobs.add(fobEntry);
        }

        WarForgeMod.NETWORK.sendTo(resp, player);
    }

    @Override
    public void handleClientSide(Player clientPlayer) {
        WarForgeMod.LOGGER.error("Received a PacketRequestOperations on client side");
    }
}
