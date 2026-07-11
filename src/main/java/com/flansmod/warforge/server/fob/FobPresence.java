package com.flansmod.warforge.server.fob;

import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.server.Faction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FobPresence {

    public static List<ServerPlayer> enemiesAbove(Fob fob) {
        List<ServerPlayer> enemies = new ArrayList<>();
        if (fob == null || WarForgeMod.MC_SERVER == null) {
            return enemies;
        }

        Faction owner = WarForgeMod.FACTIONS.getFaction(fob.ownerFaction);
        if (owner == null) {
            return enemies;
        }

        int beY = fob.pos.getY();
        ResourceKey<Level> dim = fob.pos.dim;
        DimChunkPos fobChunk = fob.pos.toChunkPos();
        UUID ownerId = fob.ownerFaction;

        for (ServerPlayer player : WarForgeMod.MC_SERVER.getPlayerList().getPlayers()) {
            if (!player.level().dimension().equals(dim)) {
                continue;
            }
            ChunkPos playerChunk = new ChunkPos(player.blockPosition());
            if (playerChunk.x != fobChunk.x || playerChunk.z != fobChunk.z) {
                continue;
            }
            if (player.getY() <= beY) {
                continue;
            }
            Faction playerFaction = WarForgeMod.FACTIONS.getFactionOfPlayer(player.getUUID());
            boolean enemy = playerFaction == null
                    || (!playerFaction.uuid.equals(ownerId)
                    && !owner.isAllyOf(playerFaction.uuid)
                    && !owner.isInTruceWith(playerFaction.uuid));
            if (enemy) {
                enemies.add(player);
            }
        }

        return enemies;
    }

    public static boolean enemyHeld(Fob fob) {
        return !enemiesAbove(fob).isEmpty();
    }

    public static boolean ownerOrAllyAbove(Fob fob) {
        if (fob == null || WarForgeMod.MC_SERVER == null) {
            return false;
        }

        Faction owner = WarForgeMod.FACTIONS.getFaction(fob.ownerFaction);
        if (owner == null) {
            return false;
        }

        int beY = fob.pos.getY();
        ResourceKey<Level> dim = fob.pos.dim;
        DimChunkPos fobChunk = fob.pos.toChunkPos();
        UUID ownerId = fob.ownerFaction;

        for (ServerPlayer player : WarForgeMod.MC_SERVER.getPlayerList().getPlayers()) {
            if (!player.level().dimension().equals(dim)) {
                continue;
            }
            ChunkPos playerChunk = new ChunkPos(player.blockPosition());
            if (playerChunk.x != fobChunk.x || playerChunk.z != fobChunk.z) {
                continue;
            }
            if (player.getY() <= beY) {
                continue;
            }
            Faction playerFaction = WarForgeMod.FACTIONS.getFactionOfPlayer(player.getUUID());
            if (playerFaction == null) {
                continue;
            }
            if (playerFaction.uuid.equals(ownerId)
                    || owner.isAllyOf(playerFaction.uuid)) {
                return true;
            }
        }

        return false;
    }
}
