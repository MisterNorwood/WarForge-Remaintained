package com.flansmod.warforge.server;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

public class TeleportUtil {

    public static void teleportPlayer(EntityPlayerMP player, int targetDimension, BlockPos targetPosition) {
        // Ensure the target dimension is different from the current dimension
        if (player.dimension != targetDimension) {
            // Create a new instance of the WorldServer for the target dimension
            WorldServer targetWorld = player.getServerWorld().getMinecraftServer().getWorld(targetDimension);

            // Change the player's dimension and teleport them
            player.changeDimension(targetDimension, new WfTeleporter(targetWorld));
        }

        // Set the player's location in the target dimension
        player.connection.setPlayerLocation(
                targetPosition.getX() + 0.5D,
                targetPosition.getY() + 1.5D,
                targetPosition.getZ() + 0.5D,
                player.rotationYaw,
                player.rotationPitch
        );
    }
}
