package com.flansmod.warforge.server.fob;

import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.blocks.TileEntityFob;
import com.flansmod.warforge.common.blocks.structure.FobStructureLayout;
import com.flansmod.warforge.common.blocks.structure.StructureStamper;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.server.Faction;
import com.flansmod.warforge.server.Siege;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class FobManager {
    private final HashMap<DimChunkPos, Fob> mFobChunks = new HashMap<>();
    private final FobWarpQueue warpQueue = new FobWarpQueue();
    private final List<DimBlockPos> mPendingCleanup = new ArrayList<>();

    public static boolean isClaimDimWhitelisted(ResourceKey<Level> dim) {
        return java.util.Arrays.asList(WarForgeConfig.CLAIM_DIM_WHITELIST).contains(dim.location().toString());
    }

    public FobWarpQueue getWarpQueue() {
        return warpQueue;
    }

    public void rebuildGlobalIndex() {
        mFobChunks.clear();
        for (Faction faction : WarForgeMod.FACTIONS.getAllFactions()) {
            for (Fob fob : faction.fobs) {
                mFobChunks.put(fob.toChunkPos(), fob);
            }
        }
    }

    public Fob getFobAt(DimChunkPos chunk) {
        return mFobChunks.get(chunk);
    }

    public List<Fob> allFobs() {
        return new ArrayList<>(mFobChunks.values());
    }

    public List<Fob> fobsOf(Faction faction) {
        if (faction == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(faction.fobs);
    }

    public boolean canPlaceFob(Faction owner, DimChunkPos chunk) {
        if (owner == null || chunk == null) {
            return false;
        }
        if (!WarForgeMod.FACTIONS.getClaim(chunk).equals(Faction.nullUuid)) {
            return false;
        }
        if (WarForgeMod.FACTIONS.isChunkContested(chunk)) {
            return false;
        }
        if (!isClaimDimWhitelisted(chunk.dim)) {
            return false;
        }
        if (WarForgeMod.FACTIONS.conqueredChunks.get(chunk) != null) {
            return false;
        }
        if (mFobChunks.get(chunk) != null) {
            return false;
        }
        return WarForgeMod.FACTIONS.findNearbyOpposingFaction(owner, chunk) == null;
    }

    public boolean hasFobCapacity(Faction faction) {
        return faction != null && faction.fobs.size() < faction.getMaxFobs();
    }

    public void registerFob(Faction faction, Fob fob) {
        if (faction == null || fob == null) {
            return;
        }
        if (!faction.fobs.contains(fob)) {
            faction.fobs.add(fob);
        }
        mFobChunks.put(fob.toChunkPos(), fob);
    }

    public void removeFob(Fob fob) {
        if (fob == null) {
            return;
        }
        Faction faction = WarForgeMod.FACTIONS.getFaction(fob.ownerFaction);
        if (faction != null) {
            faction.fobs.remove(fob);
        }
        mFobChunks.remove(fob.toChunkPos());
    }

    public boolean requestCreateFob(TileEntityFob te, ServerPlayer placer, String name) {
        if (te == null || placer == null) {
            return false;
        }
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        Faction owner = WarForgeMod.FACTIONS.getFactionOfPlayer(placer.getUUID());
        if (owner == null) {
            return false;
        }
        if (!owner.isPlayerRoleInFaction(placer.getUUID(), Faction.Role.OFFICER)) {
            return false;
        }
        Level level = te.getLevel();
        if (level == null) {
            return false;
        }
        BlockPos placedPos = te.getBlockPos();
        DimChunkPos chunk = new DimChunkPos(level.dimension(), placedPos);
        if (!canPlaceFob(owner, chunk)) {
            return false;
        }
        if (!hasFobCapacity(owner)) {
            return false;
        }

        BlockPos centerPos = FobStructureLayout.centerBlock(placedPos);
        TileEntityFob centerTe = te;
        if (!centerPos.equals(placedPos)) {
            BlockState fobState = level.getBlockState(placedPos);
            level.removeBlock(placedPos, false);
            level.setBlock(centerPos, fobState, 3);
            if (!(level.getBlockEntity(centerPos) instanceof TileEntityFob relocated)) {
                return false;
            }
            centerTe = relocated;
        }

        Fob fob = new Fob(new DimBlockPos(level.dimension(), centerPos), owner.uuid, name.trim());
        fob.maxTickets = owner.getFobTicketLimit();
        fob.tickets = fob.maxTickets;
        registerFob(owner, fob);

        StructureStamper.stampStructure(level, centerPos);

        centerTe.ownerFaction = owner.uuid;
        centerTe.placer = placer.getUUID();
        centerTe.name = fob.name;
        centerTe.maxTickets = fob.maxTickets;
        centerTe.tickets = fob.tickets;
        centerTe.setChanged();

        return true;
    }

    public boolean requestFobWarp(ServerPlayer player, Fob fob) {
        if (player == null || fob == null) {
            return false;
        }
        if (warpQueue.hasPendingWarp(player.getUUID())) {
            return false;
        }
        Faction owner = WarForgeMod.FACTIONS.getFaction(fob.ownerFaction);
        if (owner == null || !owner.isPlayerInFaction(player.getUUID())) {
            return false;
        }
        if (!WarForgeMod.FACTIONS.isFactionInActiveSiege(owner.uuid)) {
            player.sendSystemMessage(Component.literal("This FOB is only usable while your faction is under siege"));
            return false;
        }
        if (!FobPresence.enemiesAbove(fob).isEmpty()) {
            player.sendSystemMessage(Component.literal("Enemies are holding the FOB"));
            return false;
        }
        int cost = 1 + FobWarpQueue.vehicleExtraCost(player.getVehicle());
        if (fob.tickets < cost) {
            player.sendSystemMessage(Component.literal("Not enough tickets to warp"));
            return false;
        }
        fob.tickets -= cost;
        warpQueue.requestFobWarp(player, fob, cost);
        return true;
    }

    public void onDefendingSiegeEnded(java.util.UUID factionUuid) {
        for (Fob fob : mFobChunks.values()) {
            if (fob.ownerFaction.equals(factionUuid)) {
                fob.enemyHoldTicks = 0;
            }
        }
    }

    public void destroyFob(Fob fob) {
        if (fob == null) {
            return;
        }
        removeFob(fob);
        scheduleOrCleanup(fob.pos);
    }

    private void scheduleOrCleanup(DimBlockPos pos) {
        if (pos == null || WarForgeMod.MC_SERVER == null) {
            return;
        }
        ServerLevel level = WarForgeMod.MC_SERVER.getLevel(pos.dim);
        DimChunkPos chunk = pos.toChunkPos();
        if (level != null && level.hasChunk(chunk.x, chunk.z)) {
            cleanupNow(level, pos);
        } else if (!mPendingCleanup.contains(pos)) {
            mPendingCleanup.add(pos);
        }
    }

    private void cleanupNow(ServerLevel level, DimBlockPos pos) {
        StructureStamper.clearStructure(level, pos.toRegularPos());
        if (level.getBlockState(pos.toRegularPos()).getBlock() instanceof com.flansmod.warforge.common.blocks.BlockFob) {
            level.removeBlock(pos.toRegularPos(), false);
        }
    }

    public void processCleanupQueue() {
        if (mPendingCleanup.isEmpty() || WarForgeMod.MC_SERVER == null) {
            return;
        }
        Iterator<DimBlockPos> it = mPendingCleanup.iterator();
        while (it.hasNext()) {
            DimBlockPos pos = it.next();
            ServerLevel level = WarForgeMod.MC_SERVER.getLevel(pos.dim);
            if (level == null) {
                it.remove();
                continue;
            }
            DimChunkPos chunk = pos.toChunkPos();
            if (level.hasChunk(chunk.x, chunk.z)) {
                cleanupNow(level, pos);
                it.remove();
            }
        }
    }

    public void writeToNBT(CompoundTag tags) {
        ListTag list = new ListTag();
        for (DimBlockPos pos : mPendingCleanup) {
            CompoundTag entry = new CompoundTag();
            pos.writeToNBT(entry, "pos");
            list.add(entry);
        }
        tags.put("fobCleanupQueue", list);
    }

    public void readFromNBT(CompoundTag tags) {
        mPendingCleanup.clear();
        ListTag list = tags.getList("fobCleanupQueue", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            mPendingCleanup.add(DimBlockPos.readFromNBT(list.getCompound(i), "pos"));
        }
    }

    public int getSiegeHoldThresholdTicks(Fob fob) {
        if (fob == null) {
            return -1;
        }
        Siege siege = WarForgeMod.FACTIONS.getActiveSiegeForFaction(fob.ownerFaction);
        if (siege == null) {
            return -1;
        }
        Faction attacker = WarForgeMod.FACTIONS.getFaction(siege.attackingFaction);
        if (attacker == null) {
            return -1;
        }
        Integer seconds = WarForgeConfig.SIEGE_MOMENTUM_TIME.get(attacker.getSiegeMomentum());
        if (seconds == null) {
            WarForgeMod.LOGGER.warn("Missing siege momentum time for FOB hold threshold; hold-to-destroy disabled this cycle");
            return -1;
        }
        long millis = seconds.longValue() * 1000L;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(20L, millis / 50L));
    }

    public void onSiegeTimerReset(Siege siege) {
        if (siege == null) {
            return;
        }
        creditRegen(WarForgeMod.FACTIONS.getFaction(siege.attackingFaction));
        creditRegen(WarForgeMod.FACTIONS.getFaction(siege.defendingFaction));
    }

    private void creditRegen(Faction faction) {
        if (faction == null) {
            return;
        }
        int regen = Math.max(0, faction.getFobRegenPerSiegeTick());
        int limit = faction.getFobTicketLimit();
        for (Fob fob : faction.fobs) {
            fob.maxTickets = limit;
            fob.tickets = Math.min(limit, fob.tickets + regen);
        }
    }
}
