package com.flansmod.warforge.api;

import com.flansmod.warforge.api.interfaces.IChunkReinforcer;
import com.flansmod.warforge.api.interfaces.IClaimStrengthModifier;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.server.Faction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Public, addon-facing entry points for WarForge (2.1.0+). Lets other mods reason about a faction's
 * claimed and currently-loaded territory and the siege reinforcement around a besieged chunk.
 */
public final class WarforgeAPI {

    private WarforgeAPI() {}

    /** Claimed chunks of a faction that are currently loaded on the server. */
    public static Set<DimChunkPos> getLoadedClaimedChunks(UUID factionId) {
        Set<DimChunkPos> out = new LinkedHashSet<>();
        Faction faction = WarForgeMod.FACTIONS.getFaction(factionId);
        MinecraftServer server = WarForgeMod.MC_SERVER;
        if (faction == null || server == null) return out;
        for (DimBlockPos claim : faction.claims.keySet()) {
            DimChunkPos cpos = claim.toChunkPos();
            if (out.contains(cpos)) continue;
            ServerLevel level = server.getLevel(cpos.dim);
            if (level != null && level.getChunkSource().getChunkNow(cpos.x, cpos.z) != null) {
                out.add(cpos);
            }
        }
        return out;
    }

    /**
     * Tests {@code predicate} against every {@link BlockEntity} in the faction's loaded, claimed chunks,
     * short-circuiting on the first match. Useful for "does this faction have X somewhere in its territory".
     */
    public static boolean anyLoadedClaimedTile(UUID factionId, Predicate<BlockEntity> predicate) {
        Faction faction = WarForgeMod.FACTIONS.getFaction(factionId);
        MinecraftServer server = WarForgeMod.MC_SERVER;
        if (faction == null || server == null) return false;
        Set<DimChunkPos> seen = new HashSet<>();
        for (DimBlockPos claim : faction.claims.keySet()) {
            DimChunkPos cpos = claim.toChunkPos();
            if (!seen.add(cpos)) continue;
            ServerLevel level = server.getLevel(cpos.dim);
            if (level == null) continue;
            LevelChunk chunk = level.getChunkSource().getChunkNow(cpos.x, cpos.z);
            if (chunk == null) continue;
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                if (be != null && !be.isRemoved() && predicate.test(be)) return true;
            }
        }
        return false;
    }

    /**
     * Total extra siege difficulty reinforcing {@code besieged} from active reinforcers placed in the
     * faction's loaded claimed chunks. Non-stacking reinforcers (the default) contribute only their single
     * strongest bonus; stacking ones add on top. Honours both the {@link IChunkReinforcer} capability and the
     * legacy {@link IClaimStrengthModifier} interface.
     */
    public static int getReinforcementBonus(UUID factionId, DimChunkPos besieged) {
        Faction faction = WarForgeMod.FACTIONS.getFaction(factionId);
        MinecraftServer server = WarForgeMod.MC_SERVER;
        if (faction == null || server == null || besieged == null) return 0;

        int stackingSum = 0;
        int bestNonStacking = 0;
        Set<DimChunkPos> seen = new HashSet<>();
        for (DimBlockPos claim : faction.claims.keySet()) {
            DimChunkPos cpos = claim.toChunkPos();
            if (!cpos.dim.equals(besieged.dim) || !seen.add(cpos)) continue;
            ServerLevel level = server.getLevel(cpos.dim);
            if (level == null) continue;
            LevelChunk chunk = level.getChunkSource().getChunkNow(cpos.x, cpos.z);
            if (chunk == null) continue;

            for (BlockEntity be : chunk.getBlockEntities().values()) {
                if (be == null || be.isRemoved()) continue;
                int bonus = 0;
                boolean stacks = false;

                IChunkReinforcer reinforcer = level.getCapability(WarForgeCapabilities.CHUNK_REINFORCER, be.getBlockPos(), null);
                if (reinforcer != null) {
                    if (!reinforcer.isReinforcementActive()) continue;
                    ChunkPos rc = new ChunkPos(be.getBlockPos());
                    int dist = Math.max(Math.abs(rc.x - besieged.x), Math.abs(rc.z - besieged.z));
                    if (dist > reinforcer.getReinforcementRadius()) continue;
                    bonus = reinforcer.getReinforcementBonus();
                    stacks = reinforcer.stacksWithOthers();
                } else if (be instanceof IClaimStrengthModifier legacy) {
                    if (!legacy.isActive()) continue;
                    ChunkPos rc = new ChunkPos(be.getBlockPos());
                    boolean covers = false;
                    for (Vec3i vec : legacy.getEffectArea()) {
                        if (rc.x + vec.getX() == besieged.x && rc.z + vec.getZ() == besieged.z) {
                            covers = true;
                            break;
                        }
                    }
                    if (!covers) continue;
                    bonus = legacy.getClaimContribution();
                    stacks = legacy.canStack();
                } else {
                    continue;
                }

                if (bonus == 0) continue;
                if (stacks) stackingSum += bonus;
                else bestNonStacking = Math.max(bestNonStacking, bonus);
            }
        }
        return stackingSum + bestNonStacking;
    }
}
