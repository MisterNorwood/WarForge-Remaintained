package com.flansmod.warforge.server;

import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.WarForgeConfig;
import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class CombatLogHandler {
    private class PlayerInfo {
        public DimBlockPos logoffPos;
        public UUID playerID;
        public long logoffTimestamp;

        public PlayerInfo(DimBlockPos logoffPos, UUID playerID, long logoffTimestamp) {
            this.logoffPos = logoffPos;
            this.playerID = playerID;
            this.logoffTimestamp = logoffTimestamp;
        }
    }

    private List<PlayerInfo> enforcementList;

    public CombatLogHandler() {
        enforcementList = new ArrayList<>();
    }

    public void add(DimBlockPos logoffPos, UUID playerID, long logoffTimestamp) {
        if (playerID == null)
            return;
        enforcementList.add(new PlayerInfo(logoffPos, playerID, logoffTimestamp));
    }

    private void enforce(PlayerInfo player) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        // Retrieve player profile and data file
        UUID playerUUID = player.playerID;
        GameProfile profile = server.getProfileCache().get(playerUUID).orElse(null);
        if (profile == null) return;

        Path worldDir = server.getWorldPath(LevelResource.ROOT);
        Path playerDataFile = worldDir.resolve("playerdata/" + playerUUID + ".dat");

        if (playerDataFile.toFile().exists() && playerDataFile.toFile().isFile()) {
            try {
                CompoundTag playerData = NbtIo.readCompressed(playerDataFile, NbtAccounter.unlimitedHeap());
                if (playerData != null) {
                    ListTag inventoryList = playerData.getList("Inventory", Tag.TAG_COMPOUND).copy();
                    DimBlockPos logoffPos = player.logoffPos;
                    ServerLevel world = server.getLevel(logoffPos.dim);

                    playerData.put("Inventory", new ListTag());

                    NbtIo.writeCompressed(playerData, playerDataFile);

                    for (int i = 0; i < inventoryList.size(); i++) {
                        CompoundTag itemCompound = inventoryList.getCompound(i);
                        ItemStack stack = ItemStack.parseOptional(server.registryAccess(), itemCompound);

                        ItemEntity entityItem = new ItemEntity(world, logoffPos.getX(), logoffPos.getY(), logoffPos.getZ(), stack);
                        world.addFreshEntity(entityItem);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void doEnforcements(long timestamp) {
        for (PlayerInfo info : enforcementList) {
            // if the difference in the timestamp between when the player logged out and when this check is done exceeds the threshold, then enforcement is done
            if (((int) (timestamp - info.logoffTimestamp)) > WarForgeConfig.COMBAT_LOG_THRESHOLD) enforce(info);
        }
    }

    public boolean isEmpty() {
        return enforcementList.isEmpty();
    }
}
