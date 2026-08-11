package com.flansmod.warforge.common;

import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.server.Faction;
import com.flansmod.warforge.server.FactionStorage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.UUID;

// Diagnostic only. When WarForgeConfig.DEBUG_TRACE_SETBLOCK is enabled, every server-side
// Level#setBlockAndUpdate that lands inside a claimed chunk is logged together with the owning
// claim and the calling code, so operators can pin down which mod/object is mutating protected
// terrain outside the explosion-event path and decide what targeted protection to add.
public final class SetBlockTracer {

    private SetBlockTracer() {
    }

    public static void trace(Level level, BlockPos pos, BlockState newState) {
        if (!WarForgeConfig.DEBUG_TRACE_SETBLOCK) {
            return;
        }
        if (level == null || level.isClientSide || pos == null) {
            return;
        }

        FactionStorage factions = WarForgeMod.FACTIONS;
        if (factions == null) {
            return;
        }

        DimChunkPos chunkPos;
        UUID owner;
        try {
            chunkPos = new DimChunkPos(level.dimension(), pos);
            owner = factions.getClaim(chunkPos);
        } catch (Throwable ignored) {
            return;
        }

        // Only claimed chunks are of interest; unclaimed terrain is left alone.
        if (owner == null || owner.equals(Faction.nullUuid)) {
            return;
        }

        WarForgeMod.LOGGER.info("[WarForge SetBlock Trace] dim={} pos=[{}, {}, {}] chunk=[{}, {}] claim={} block={} caller={}",
                chunkPos.dim, pos.getX(), pos.getY(), pos.getZ(), chunkPos.x, chunkPos.z,
                describeOwner(factions, owner), describeBlock(newState), findCaller());
    }

    private static String describeOwner(FactionStorage factions, UUID owner) {
        if (owner.equals(FactionStorage.SAFE_ZONE_ID)) {
            return "SafeZone";
        }
        if (owner.equals(FactionStorage.WAR_ZONE_ID)) {
            return "WarZone";
        }
        Faction faction = factions.getFaction(owner);
        if (faction != null && faction.name != null) {
            return "'" + faction.name + "' (" + owner + ")";
        }
        return owner.toString();
    }

    private static String describeBlock(BlockState state) {
        if (state == null) {
            return "<null>";
        }
        try {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            return id == null ? state.getBlock().toString() : id.toString();
        } catch (Throwable ignored) {
            return "<unknown>";
        }
    }

    // Walk the live stack and collect the first few frames that belong neither to this tracer,
    // the mixin handler, nor Level's own setBlockAndUpdate delegation - i.e. the actual caller chain.
    private static String findCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (StackTraceElement el : stack) {
            String cls = el.getClassName();
            if (cls.startsWith("java.lang.Thread")) {
                continue;
            }
            if (cls.equals(SetBlockTracer.class.getName())) {
                continue;
            }
            // The mixin handler is merged into net.minecraft.world.level.Level; skip that class entirely
            // so we step past both the injected method and Level's own setBlock overloads.
            if (cls.equals("net.minecraft.world.level.Level")) {
                continue;
            }
            if (shown > 0) {
                sb.append(" <- ");
            }
            sb.append(el.toString());
            if (++shown >= 6) {
                break;
            }
        }
        return sb.length() == 0 ? "<unknown>" : sb.toString();
    }
}
