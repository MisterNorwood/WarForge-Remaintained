package com.flansmod.warforge.common.network;

import com.flansmod.warforge.client.ClientProxy;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.Tags;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.HashMap;
import java.util.Map;

public class PacketSyncConfig extends PacketBase {
    public String configNBT;

    @Override
    public void encodeInto(ChannelHandlerContext ctx, ByteBuf data) {
        writeUTF(data, configNBT);
    }

    @Override
    public void decodeInto(ChannelHandlerContext ctx, ByteBuf data) {
        configNBT = readUTF(data);
    }

    @Override
    public void handleServerSide(EntityPlayerMP playerEntity) {
        //noop
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClientSide(EntityPlayer clientPlayer) {
        NBTTagCompound compound;
        try {
            compound = JsonToNBT.getTagFromJson(configNBT);
        } catch (NBTException e) {
            WarForgeMod.LOGGER.error("Malformed config data NBT");
            return;
        }

        // Boolean flags
        WarForgeConfig.SIEGE_ENABLE_NEW_TIMER = compound.getBoolean("newSiegeTimer");
        WarForgeMod.LOGGER.info((WarForgeConfig.SIEGE_ENABLE_NEW_TIMER ? "Enabled " : "Disabled ")
                + "new siege timer system, per server requirements");

        WarForgeConfig.ENABLE_CITADEL_UPGRADES = compound.getBoolean("enableUpgrades");
        WarForgeMod.LOGGER.info((WarForgeConfig.ENABLE_CITADEL_UPGRADES ? "Enabled " : "Disabled ")
                + "citadel upgrade system, per server requirements");

        // Integers
        WarForgeConfig.SIEGE_MOMENTUM_MAX = (byte) compound.getInteger("maxMomentum");
        WarForgeConfig.SIEGE_MOMENTUM_DURATION = compound.getInteger("timeMomentum");


        // Momentum map (stored as String → needs parsing back)
        String mapString = compound.getString("momentumMap");
        Map<Byte, Integer> parsedMap = new HashMap<>();
        try {
            if (mapString.startsWith("{") && mapString.endsWith("}")) {
                mapString = mapString.substring(1, mapString.length() - 1);
            }
            for (String entry : mapString.split(",")) {
                String[] kv = entry.trim().split("=");
                if (kv.length == 2) {
                    byte key = Byte.parseByte(kv[0].trim());
                    int seconds = Integer.parseInt(kv[1].trim());
                    parsedMap.put(key, seconds);
                }
            }
        } catch (Exception e) {
            WarForgeMod.LOGGER.error("Failed to parse momentumMap from config sync: " + mapString, e);
        }

        WarForgeConfig.POOR_QUAL_MULT = compound.getFloat("poorQualMult");
        WarForgeConfig.FAIR_QUAL_MULT = compound.getFloat("fairQualMult");
        WarForgeConfig.RICH_QUAL_MULT = compound.getFloat("richQualMult");

        ClientProxy.megachunkLength = compound.getShort("megachunkLength");
        WarForgeConfig.SIEGE_BATTLE_RADIUS = compound.hasKey("battleSiegeRadius")
                ? compound.getInteger("battleSiegeRadius")
                : WarForgeConfig.SIEGE_BATTLE_RADIUS;
        WarForgeConfig.SIEGE_ATTACKER_RADIUS = compound.getInteger("atkSiegeRadius");
        WarForgeConfig.SIEGE_DEFENDER_RADIUS = compound.getInteger("defSiegeRadius");
        WarForgeConfig.ENABLE_OFFLINE_RAID_PROTECTION = compound.getBoolean("offlineRaidProtection");
        WarForgeConfig.OFFLINE_RAID_PROTECTION_HOURS = compound.getInteger("offlineRaidProtectionHours");
        String insuranceBlacklist = compound.getString("insuranceBlacklist");
        WarForgeConfig.INSURANCE_BLACKLIST_IDS = insuranceBlacklist.isEmpty() ? new String[0] : insuranceBlacklist.split("\n");

        WarForgeConfig.FACTION_PREFIX_IN_CHAT = compound.getBoolean("factionPrefixChat");
        WarForgeConfig.FACTION_PREFIX_IN_TABLIST = compound.getBoolean("factionPrefixTab");
        if (compound.hasKey("islandCollectorSlots")) {
            WarForgeConfig.ISLAND_COLLECTOR_SLOTS = compound.getInteger("islandCollectorSlots");
        }
        WarForgeConfig.JOURNEYMAP_CLAIM_MODE = compound.getInteger("jmClaimMode");
        WarForgeConfig.JOURNEYMAP_VEIN_MODE = compound.getInteger("jmVeinMode");

        if (compound.hasKey("protectionZones")) {
            NBTTagCompound zones = compound.getCompoundTag("protectionZones");
            if (zones.hasKey("unclaimed"))        WarForgeConfig.UNCLAIMED.readProtectionSync(zones.getCompoundTag("unclaimed"));
            if (zones.hasKey("safe"))             WarForgeConfig.SAFE_ZONE.readProtectionSync(zones.getCompoundTag("safe"));
            if (zones.hasKey("war"))              WarForgeConfig.WAR_ZONE.readProtectionSync(zones.getCompoundTag("war"));
            if (zones.hasKey("citadelFriend"))    WarForgeConfig.CITADEL_FRIEND.readProtectionSync(zones.getCompoundTag("citadelFriend"));
            if (zones.hasKey("citadelFoe"))       WarForgeConfig.CITADEL_FOE.readProtectionSync(zones.getCompoundTag("citadelFoe"));
            if (zones.hasKey("claimFriend"))      WarForgeConfig.CLAIM_FRIEND.readProtectionSync(zones.getCompoundTag("claimFriend"));
            if (zones.hasKey("claimAlly"))        WarForgeConfig.CLAIM_ALLY.readProtectionSync(zones.getCompoundTag("claimAlly"));
            if (zones.hasKey("claimFoe"))         WarForgeConfig.CLAIM_FOE.readProtectionSync(zones.getCompoundTag("claimFoe"));
            if (zones.hasKey("sieger"))           WarForgeConfig.SIEGECAMP_SIEGER.readProtectionSync(zones.getCompoundTag("sieger"));
            if (zones.hasKey("siegeOther"))       WarForgeConfig.SIEGECAMP_OTHER.readProtectionSync(zones.getCompoundTag("siegeOther"));
            if (zones.hasKey("claimDefended"))    WarForgeConfig.CLAIM_DEFENDED.readProtectionSync(zones.getCompoundTag("claimDefended"));
            if (zones.hasKey("siegedFriend"))     WarForgeConfig.SIEGED_FRIEND.readProtectionSync(zones.getCompoundTag("siegedFriend"));
            if (zones.hasKey("siegedFoe"))        WarForgeConfig.SIEGED_FOE.readProtectionSync(zones.getCompoundTag("siegedFoe"));
            if (zones.hasKey("warFriend"))        WarForgeConfig.WAR_FRIEND.readProtectionSync(zones.getCompoundTag("warFriend"));
            if (zones.hasKey("warFoe"))           WarForgeConfig.WAR_FOE.readProtectionSync(zones.getCompoundTag("warFoe"));
        }
        if (compound.hasKey("siegeSiegedRadius")) {
            WarForgeConfig.SIEGE_SIEGED_RADIUS = compound.getInteger("siegeSiegedRadius");
        }

        // Each connection starts from a clean slate; the server re-sends whatever this client is allowed to see.
        com.flansmod.warforge.client.JourneyMapClaimCache.applyClear();
        com.flansmod.warforge.client.JourneyMapVeinCache.applyClear();

        WarForgeConfig.SIEGE_MOMENTUM_TIME.clear();
        WarForgeConfig.SIEGE_MOMENTUM_TIME.putAll(parsedMap);

        WarForgeMod.LOGGER.info("Synced siege config:"
                + " maxMomentum=" + WarForgeConfig.SIEGE_MOMENTUM_MAX
                + ", duration=" + WarForgeConfig.SIEGE_MOMENTUM_DURATION
                + ", multipliers=" + WarForgeConfig.SIEGE_MOMENTUM_TIME);
    }

    public boolean canUseCompression() {
        return true;
    }
}
