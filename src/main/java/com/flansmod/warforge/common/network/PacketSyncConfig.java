package com.flansmod.warforge.common.network;

import com.flansmod.warforge.client.ClientProxy;
import com.flansmod.warforge.client.JourneyMapClaimCache;
import com.flansmod.warforge.client.JourneyMapVeinCache;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

public class PacketSyncConfig extends PacketBase {
    public String configNBT;

    @Override
    public void encodeInto(FriendlyByteBuf data) {
        writeUTF(data, configNBT);
    }

    @Override
    public void decodeInto(FriendlyByteBuf data) {
        configNBT = readUTF(data);
    }

    @Override
    public void handleServerSide(ServerPlayer playerEntity) {
        //noop
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void handleClientSide(Player clientPlayer) {
        CompoundTag compound;
        try {
            compound = TagParser.parseTag(configNBT);
        } catch (Exception e) {
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
        int defenceThreshold = compound.getInt("defenceThreshold");
        WarForgeConfig.SIEGE_DEFENCE_THRESHOLD = defenceThreshold > 0 ? defenceThreshold : WarForgeConfig.SIEGE_DEFENCE_THRESHOLD;
        WarForgeConfig.SIEGE_MOMENTUM_MAX = (byte) compound.getInt("maxMomentum");
        WarForgeConfig.SIEGE_MOMENTUM_DURATION = compound.getInt("timeMomentum");


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
        WarForgeConfig.SIEGE_BATTLE_RADIUS = compound.contains("battleSiegeRadius")
                ? compound.getInt("battleSiegeRadius")
                : WarForgeConfig.SIEGE_BATTLE_RADIUS;
        WarForgeConfig.SIEGE_ATTACKER_RADIUS = compound.getInt("atkSiegeRadius");
        WarForgeConfig.SIEGE_DEFENDER_RADIUS = compound.getInt("defSiegeRadius");
        WarForgeConfig.ENABLE_OFFLINE_RAID_PROTECTION = compound.getBoolean("offlineRaidProtection");
        WarForgeConfig.OFFLINE_RAID_PROTECTION_HOURS = compound.getInt("offlineRaidProtectionHours");
        String insuranceBlacklist = compound.getString("insuranceBlacklist");
        WarForgeConfig.INSURANCE_BLACKLIST_IDS = insuranceBlacklist.isEmpty() ? new String[0] : insuranceBlacklist.split("\n");

        WarForgeConfig.FACTION_PREFIX_IN_CHAT = compound.getBoolean("factionPrefixChat");
        WarForgeConfig.FACTION_PREFIX_IN_TABLIST = compound.getBoolean("factionPrefixTab");
        if (compound.contains("islandCollectorSlots")) {
            WarForgeConfig.ISLAND_COLLECTOR_SLOTS = compound.getInt("islandCollectorSlots");
        }
        WarForgeConfig.JOURNEYMAP_CLAIM_MODE = compound.getInt("jmClaimMode");
        WarForgeConfig.JOURNEYMAP_VEIN_MODE = compound.getInt("jmVeinMode");

        // Per-zone break/place rules + MineTime settings (server-authoritative values for prediction).
        if (compound.contains("protectionZones")) {
            CompoundTag zones = compound.getCompound("protectionZones");
            WarForgeConfig.UNCLAIMED.readProtectionSync(zones.getCompound("unclaimed"));
            WarForgeConfig.SAFE_ZONE.readProtectionSync(zones.getCompound("safe"));
            WarForgeConfig.WAR_ZONE.readProtectionSync(zones.getCompound("war"));
            WarForgeConfig.CITADEL_FRIEND.readProtectionSync(zones.getCompound("citadelFriend"));
            WarForgeConfig.CITADEL_FOE.readProtectionSync(zones.getCompound("citadelFoe"));
            WarForgeConfig.CLAIM_FRIEND.readProtectionSync(zones.getCompound("claimFriend"));
            WarForgeConfig.CLAIM_FOE.readProtectionSync(zones.getCompound("claimFoe"));
            WarForgeConfig.SIEGED_FRIEND.readProtectionSync(zones.getCompound("siegedFriend"));
            WarForgeConfig.SIEGED_FOE.readProtectionSync(zones.getCompound("siegedFoe"));
            WarForgeConfig.WAR_FRIEND.readProtectionSync(zones.getCompound("warFriend"));
            WarForgeConfig.WAR_FOE.readProtectionSync(zones.getCompound("warFoe"));
        }
        // Each connection starts from a clean slate; the server re-sends whatever this client is allowed to see.
        JourneyMapClaimCache.applyClear();
        JourneyMapVeinCache.applyClear();

        WarForgeConfig.SIEGE_MOMENTUM_TIME.clear();
        WarForgeConfig.SIEGE_MOMENTUM_TIME.putAll(parsedMap);

        WarForgeMod.LOGGER.info("Synced siege config:"
                + " maxMomentum=" + WarForgeConfig.SIEGE_MOMENTUM_MAX
                + ", duration=" + WarForgeConfig.SIEGE_MOMENTUM_DURATION
                + ", multipliers=" + WarForgeConfig.SIEGE_MOMENTUM_TIME);
    }
}
