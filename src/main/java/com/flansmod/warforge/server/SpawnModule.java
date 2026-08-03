package com.flansmod.warforge.server;

import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.util.DimChunkPos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class SpawnModule {
    private static BlockPos findCitadelSpawn(ServerLevel level, DimBlockPos citadel) {
        int startY = Math.max(citadel.getY(), level.getMinBuildHeight());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(citadel.getX(), startY, citadel.getZ());
        int maxY = level.getMaxBuildHeight() - 1;
        while (cursor.getY() < maxY && !level.getBlockState(cursor).isAir()) {
            cursor.move(Direction.UP);
        }
        return cursor.immutable();
    }

    private static boolean isOwnedClaim(ServerPlayer player, ResourceKey<Level> dim, BlockPos pos) {
        Faction faction = WarForgeMod.FACTIONS.getFactionOfPlayer(player.getUUID());
        if (faction == null) {
            return false;
        }
        return WarForgeMod.FACTIONS.getClaim(new DimChunkPos(dim, pos)).equals(faction.uuid);
    }

    private static boolean hasSpawnInOwnedClaim(ServerPlayer player) {
        BlockPos spawn = player.getRespawnPosition();
        if (spawn == null) {
            return false;
        }
        ResourceKey<Level> dim = player.getRespawnDimension();
        if (!isOwnedClaim(player, dim, spawn)) {
            return false;
        }
        ServerLevel level = player.server.getLevel(dim);
        if (level == null) {
            return false;
        }
        BlockState state = level.getBlockState(spawn);
        return state.is(BlockTags.BEDS) || state.is(Blocks.RESPAWN_ANCHOR);
    }

    @SubscribeEvent
    public void onSetSpawn(PlayerSetSpawnEvent event) {
        if (!WarForgeConfig.SPAWN_AT_CITADEL || event.isForced()) {
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        BlockPos newSpawn = event.getNewSpawn();
        if (newSpawn == null) {
            return;
        }

        if (WarForgeConfig.ALLOW_BED_SPAWN_IN_CLAIMS) {
            if (isOwnedClaim(player, event.getSpawnLevel(), newSpawn)) {
                return;
            }
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("You can only set a spawn point in chunks claimed by your faction."));
            return;
        }

        event.setCanceled(true);
        player.sendSystemMessage(Component.literal("Bed spawn points are disabled on this server."));
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!WarForgeConfig.SPAWN_AT_CITADEL || event.isEndConquered()) {
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (WarForgeConfig.ALLOW_BED_SPAWN_IN_CLAIMS && hasSpawnInOwnedClaim(player)) {
            return;
        }

        Faction faction = WarForgeMod.FACTIONS.getFactionOfPlayer(player.getUUID());
        if (faction == null) {
            return;
        }

        DimBlockPos citadel = faction.citadelPos;
        if (citadel == null || citadel.equals(DimBlockPos.ZERO)) {
            return;
        }

        ServerLevel level = player.server.getLevel(citadel.dim);
        if (level == null) {
            return;
        }

        BlockPos spawn = findCitadelSpawn(level, citadel);

        if (player.level().dimension() != citadel.dim) {
            Entity result = player.changeDimension(level, new WfTeleporter());
            if (result == null) return;
        }

        player.connection.teleport(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, player.getYRot(), player.getXRot());
    }
}
