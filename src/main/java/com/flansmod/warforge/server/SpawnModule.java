package com.flansmod.warforge.server;

import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.util.DimBlockPos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class SpawnModule {
    private static BlockPos findCitadelSpawn(ServerLevel level, DimBlockPos citadel) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(citadel.getX(), citadel.getY(), citadel.getZ());
        int maxY = level.getMaxBuildHeight() - 1;
        while (cursor.getY() < maxY && !level.getBlockState(cursor).isAir()) {
            cursor.move(Direction.UP);
        }
        return cursor.immutable();
    }

    @SubscribeEvent
    public void onSetSpawn(PlayerSetSpawnEvent event) {
        if (!WarForgeConfig.SPAWN_AT_CITADEL || event.isForced()) {
            return;
        }

        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.literal("Bed spawn points are disabled on this server."));
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!WarForgeConfig.SPAWN_AT_CITADEL || event.isEndConquered()) {
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer player)) {
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
            player.changeDimension(level, new WfTeleporter());
        }

        player.connection.teleport(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, player.getYRot(), player.getXRot());
    }
}
