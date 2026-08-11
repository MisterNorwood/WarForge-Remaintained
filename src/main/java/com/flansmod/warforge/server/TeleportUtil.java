package com.flansmod.warforge.server;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

public class TeleportUtil {

    public static void teleportPlayer(ServerPlayer player, ResourceKey<Level> targetDimension, BlockPos targetPosition) {
        if (player.level().dimension() != targetDimension) {
            ServerLevel targetWorld = player.server.getLevel(targetDimension);
            if (targetWorld != null) {
                player.changeDimension(new DimensionTransition(targetWorld,
                        new Vec3(targetPosition.getX() + 0.5D, targetPosition.getY() + 1.5D, targetPosition.getZ() + 0.5D),
                        player.getDeltaMovement(), player.getYRot(), player.getXRot(), DimensionTransition.DO_NOTHING));
            }
        }

        player.connection.teleport(
                targetPosition.getX() + 0.5D,
                targetPosition.getY() + 1.5D,
                targetPosition.getZ() + 0.5D,
                player.getYRot(),
                player.getXRot()
        );
    }
}
