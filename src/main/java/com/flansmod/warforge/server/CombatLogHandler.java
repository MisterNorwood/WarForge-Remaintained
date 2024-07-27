package com.flansmod.warforge.server;

import com.flansmod.warforge.common.DimBlockPos;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerProfileCache;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class CombatLogHandler {
    private class PlayerInfo{
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
    public static final long threshold = 60*1000; //60 seconds in MS

    public CombatLogHandler() {
        enforcementList = new ArrayList<>();
    }

    public void add(DimBlockPos logoffPos, UUID playerID, long logoffTimestamp) {
        if(playerID == null)
            return;
        enforcementList.add(new PlayerInfo(logoffPos, playerID, logoffTimestamp));

    }
    public void enforce(PlayerInfo player){
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;

        PlayerProfileCache profileCache = server.getPlayerProfileCache();
        if (profileCache == null) return;

        // Retrieve player profile and data file
        UUID playerUUID = player.playerID;
        GameProfile profile = profileCache.getProfileByUUID(playerUUID);
        if (profile == null) return;

        File worldDir = DimensionManager.getWorld(0).getSaveHandler().getWorldDirectory();
        File playerDataFile = new File(worldDir, "playerdata/" + playerUUID.toString() + ".dat");

        if (playerDataFile.exists() && playerDataFile.isFile()) {
            try {
                NBTTagCompound playerData = CompressedStreamTools.readCompressed(new FileInputStream(playerDataFile));
                if (playerData != null) {
                    NBTTagList inventoryList = playerData.getTagList("Inventory", 10).copy();
                    DimBlockPos logoffPos = player.logoffPos;
                    WorldServer world = DimensionManager.getWorld(logoffPos.mDim);

                    // Clear inventory data
                    playerData.setTag("Inventory", new NBTTagList());

                    // Save modified player data
                    CompressedStreamTools.writeCompressed(playerData, new FileOutputStream(playerDataFile));

                    //Go through every tag and drop it
                    for (int i = 0; i < inventoryList.tagCount(); i++) {
                        NBTTagCompound itemCompound = inventoryList.getCompoundTagAt(i);
                        ItemStack stack = new ItemStack(itemCompound);

                        EntityItem entityItem = new EntityItem(world, logoffPos.getX(), logoffPos.getY(), logoffPos.getZ(), stack);
                        world.spawnEntity(entityItem);
                    }

                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    public void doEnforcements(long timestamp) {
        for (PlayerInfo info : enforcementList) {
            if (info.logoffTimestamp - timestamp > threshold) enforce(info);
        }
    }
    public boolean isEmpty(){
        if(enforcementList.isEmpty())
            return true;
        return false;
    }

}
