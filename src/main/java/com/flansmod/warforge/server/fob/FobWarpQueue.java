package com.flansmod.warforge.server.fob;

import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.server.TeleportUtil;
import com.flansmod.warforge.server.WfTeleporter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

public class FobWarpQueue {
    private static final int TICKS_PER_SECOND = 20;
    private final List<PendingWarp> mPendingWarps = new ArrayList<PendingWarp>();

    public static void forceTouchChunk(DimBlockPos target) {
        ServerLevel level = WarForgeMod.MC_SERVER.getLevel(target.dim);
        if (level != null) {
            level.getChunk(target.getX() >> 4, target.getZ() >> 4);
        }
    }

    public static int vehicleExtraCost(Entity vehicle) {
        if (vehicle == null) {
            return 0;
        }
        String id = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(vehicle.getType()).toString();
        Integer extra = WarForgeConfig.FOB_VEHICLE_TICKET_COST.get(id);
        return extra == null ? 0 : Math.max(0, extra);
    }

    private static void warpVehicle(ServerPlayer player, Entity vehicle, DimBlockPos target) {
        if (vehicle == null || player.getVehicle() != vehicle) {
            return;
        }

        ServerLevel targetLevel = WarForgeMod.MC_SERVER.getLevel(target.dim);
        if (targetLevel == null) {
            return;
        }

        List<Entity> passengers = new ArrayList<Entity>(vehicle.getPassengers());
        vehicle.ejectPassengers();

        double x = target.getX() + 0.5D;
        double y = target.getY() + 1.5D;
        double z = target.getZ() + 0.5D;

        Entity movedVehicle = vehicle;
        if (!movedVehicle.level().dimension().equals(target.dim)) {
            movedVehicle = movedVehicle.changeDimension(targetLevel, new WfTeleporter());
        }
        if (movedVehicle == null) {
            return;
        }
        movedVehicle.moveTo(x, y, z, movedVehicle.getYRot(), movedVehicle.getXRot());

        for (Entity passenger : passengers) {
            if (passenger == player) {
                continue;
            }
            Entity moved = passenger;
            if (!moved.level().dimension().equals(target.dim)) {
                moved = moved.changeDimension(targetLevel, new WfTeleporter());
            }
            if (moved != null) {
                moved.moveTo(x, y, z, moved.getYRot(), moved.getXRot());
                moved.startRiding(movedVehicle, true);
            }
        }
    }

    public boolean hasPendingWarp(java.util.UUID playerId) {
        for (PendingWarp warp : mPendingWarps) {
            if (warp.player != null && warp.player.getUUID().equals(playerId)) return true;
        }
        return false;
    }

    public void requestFobWarp(ServerPlayer player, Fob fob, int cost) {
        if (player == null || fob == null) {
            return;
        }

        PendingWarp warp = new PendingWarp();
        warp.player = player;
        warp.vehicle = player.getVehicle();
        warp.pos = player.blockPosition();
        warp.target = new DimBlockPos(fob.pos.dim, fob.pos.getX(), fob.pos.getY(), fob.pos.getZ());
        warp.fob = fob;
        warp.cost = cost;
        warp.ticksRemaining = WarForgeConfig.FOB_WARP_TICKS;
        mPendingWarps.add(warp);

        player.sendSystemMessage(Component.literal("Warp started. Stand still."));
    }

    public void update() {
        for (int i = mPendingWarps.size() - 1; i >= 0; i--) {
            PendingWarp warp = mPendingWarps.get(i);

            if (!warp.player.blockPosition().equals(warp.pos)) {
                warp.fob.tickets = Math.min(warp.fob.maxTickets, warp.fob.tickets + warp.cost);
                warp.player.sendSystemMessage(Component.literal("Warp cancelled."));
                mPendingWarps.remove(i);
                continue;
            }

            if (warp.ticksRemaining % TICKS_PER_SECOND == 0) {
                warp.player.sendSystemMessage(Component.literal("Warping in " + (warp.ticksRemaining / TICKS_PER_SECOND)));
            }

            warp.ticksRemaining--;

            if (warp.ticksRemaining <= 0) {
                fireWarp(warp);
                mPendingWarps.remove(i);
            }
        }
    }

    private void fireWarp(PendingWarp warp) {
        forceTouchChunk(warp.target);
        warpVehicle(warp.player, warp.vehicle, warp.target);
        TeleportUtil.teleportPlayer(warp.player, warp.target.dim, warp.target.toRegularPos());
    }

    private static class PendingWarp {
        public ServerPlayer player;
        public Entity vehicle;
        public int ticksRemaining;
        public BlockPos pos;

        public DimBlockPos target;
        public Fob fob;
        public int cost;
    }
}
