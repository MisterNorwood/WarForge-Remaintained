package com.flansmod.warforge.server;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.util.DimChunkPos;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import net.neoforged.neoforge.common.world.chunk.TicketSet;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FactionChunkLoadingManager {
    private final HashMap<UUID, HashMap<ResourceKey<Level>, HashSet<DimChunkPos>>> forcedByFaction = new HashMap<UUID, HashMap<ResourceKey<Level>, HashSet<DimChunkPos>>>();
    private TicketController ticketController;

    public void initialize(IEventBus modBus) {
        ticketController = new TicketController(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "faction_chunks"), this::validateTickets);
        modBus.addListener((RegisterTicketControllersEvent event) -> event.register(ticketController));
    }

    private void validateTickets(ServerLevel level, TicketHelper ticketHelper) {
        ResourceKey<Level> dim = level.dimension();
        for (Map.Entry<UUID, TicketSet> entry : ticketHelper.getEntityTickets().entrySet()) {
            UUID factionId = entry.getKey();
            Faction faction = WarForgeMod.FACTIONS.getFaction(factionId);
            if (faction == null || !FactionStorage.isValidFaction(faction) || FactionStorage.IsNeutralZone(factionId)) {
                ticketHelper.removeAllTickets(factionId);
                continue;
            }

            for (long packed : entry.getValue().nonTicking().toLongArray()) {
                ticketHelper.removeTicket(factionId, packed, false);
            }
            HashSet<DimChunkPos> tracked = forcedByFaction
                    .computeIfAbsent(factionId, id -> new HashMap<>())
                    .computeIfAbsent(dim, d -> new HashSet<>());
            trackPackedChunks(tracked, dim, entry.getValue().ticking());
        }
    }

    private static void trackPackedChunks(HashSet<DimChunkPos> out, ResourceKey<Level> dim, LongSet packedChunks) {
        for (long packed : packedChunks) {
            out.add(new DimChunkPos(dim, ChunkPos.getX(packed), ChunkPos.getZ(packed)));
        }
    }

    public void shutdown() {
        for (UUID factionId : new HashSet<UUID>(forcedByFaction.keySet())) {
            releaseFaction(factionId);
        }
        forcedByFaction.clear();
    }

    public void releaseFaction(UUID factionId) {
        Map<ResourceKey<Level>, HashSet<DimChunkPos>> byDim = forcedByFaction.remove(factionId);
        if (byDim == null) {
            return;
        }
        for (Map.Entry<ResourceKey<Level>, HashSet<DimChunkPos>> entry : byDim.entrySet()) {
            ServerLevel level = WarForgeMod.MC_SERVER.getLevel(entry.getKey());
            if (level == null) {
                continue;
            }
            for (DimChunkPos chunk : entry.getValue()) {
                ticketController.forceChunk(level, factionId, chunk.x, chunk.z, false, true);
            }
        }
    }

    public void refreshAllFactions(Collection<Faction> factions) {
        for (Faction faction : factions) {
            if (faction != null && FactionStorage.isValidFaction(faction) && !FactionStorage.IsNeutralZone(faction.uuid)) {
                refreshFactionChunks(faction);
            }
        }
    }

    public void refreshFactionChunks(Faction faction) {
        if (faction == null || !FactionStorage.isValidFaction(faction)) {
            return;
        }

        HashMap<ResourceKey<Level>, HashSet<DimChunkPos>> desiredByDim = new HashMap<>();

        for (DimChunkPos forced : faction.forcedChunks) {
            desiredByDim.computeIfAbsent(forced.dim, dim -> new HashSet<>()).add(forced);
        }
        for (DimBlockPos collectorPos : faction.islandCollectors) {
            DimChunkPos collectorChunk = collectorPos.toChunkPos();
            desiredByDim.computeIfAbsent(collectorChunk.dim, dim -> new HashSet<>()).add(collectorChunk);
        }

        HashMap<ResourceKey<Level>, HashSet<DimChunkPos>> currentByDim = forcedByFaction.computeIfAbsent(faction.uuid, id -> new HashMap<ResourceKey<Level>, HashSet<DimChunkPos>>());

        for (Map.Entry<ResourceKey<Level>, HashSet<DimChunkPos>> entry : currentByDim.entrySet()) {
            ResourceKey<Level> dim = entry.getKey();
            HashSet<DimChunkPos> desired = desiredByDim.get(dim);
            ServerLevel level = WarForgeMod.MC_SERVER.getLevel(dim);
            if (level == null) {
                continue;
            }
            for (DimChunkPos chunk : new HashSet<>(entry.getValue())) {
                if (desired == null || !desired.contains(chunk)) {
                    ticketController.forceChunk(level, faction.uuid, chunk.x, chunk.z, false, true);
                    entry.getValue().remove(chunk);
                }
            }
        }

        for (Map.Entry<ResourceKey<Level>, HashSet<DimChunkPos>> entry : desiredByDim.entrySet()) {
            ResourceKey<Level> dim = entry.getKey();
            Set<DimChunkPos> chunks = entry.getValue();
            if (chunks.isEmpty()) {
                continue;
            }

            ServerLevel level = WarForgeMod.MC_SERVER.getLevel(dim);
            if (level == null) {
                continue;
            }

            HashSet<DimChunkPos> current = currentByDim.computeIfAbsent(dim, id -> new HashSet<DimChunkPos>());
            for (DimChunkPos chunk : chunks) {
                if (current.add(chunk)) {
                    ticketController.forceChunk(level, faction.uuid, chunk.x, chunk.z, true, true);
                }
            }
        }

        currentByDim.values().removeIf(Set::isEmpty);
        if (currentByDim.isEmpty()) {
            forcedByFaction.remove(faction.uuid);
        }
    }
}
