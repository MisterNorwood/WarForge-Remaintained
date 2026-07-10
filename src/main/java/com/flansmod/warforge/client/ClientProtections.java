package com.flansmod.warforge.client;

import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeConfig.ProtectionConfig;
import com.flansmod.warforge.common.network.ClaimChunkInfo;
import com.flansmod.warforge.common.network.SiegeCampProgressInfo;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.server.Faction;
import com.flansmod.warforge.server.FactionStorage;
import com.flansmod.warforge.server.Siege;

import java.util.UUID;

public final class ClientProtections {
    private ClientProtections() {}

    public static ProtectionConfig configFor(DimChunkPos chunkPos) {
        ProtectionConfig config = siegeZoneFor(chunkPos);
        if (config != null)
            return config;
        ClaimChunkInfo info = ClientClaimChunkCache.get(chunkPos);
        if (info == null)
            info = ClientBorderCache.get(chunkPos);
        if (info == null)
            return null;
        return zoneFor(info);
    }

    private static ProtectionConfig siegeZoneFor(DimChunkPos chunkPos) {
        for (SiegeCampProgressInfo si : ClientProxy.sSiegeInfo.values()) {
            if (si == null || si.attackingPos == null)
                continue;
            DimChunkPos camp = si.attackingPos.toChunkPos();
            if (Siege.isPlayerInRadius(camp, chunkPos, si.battleRadius))
                return WarForgeConfig.SIEGECAMP_SIEGER;
        }
        return null;
    }

    private static ProtectionConfig zoneFor(ClaimChunkInfo info) {
        UUID owner = info.factionId;
        if (owner == null || owner.equals(Faction.nullUuid))
            owner = info.outlineFactionId;
        if (owner == null || owner.equals(Faction.nullUuid))
            return WarForgeConfig.UNCLAIMED;
        if (owner.equals(FactionStorage.SAFE_ZONE_ID))
            return WarForgeConfig.SAFE_ZONE;
        if (owner.equals(FactionStorage.WAR_ZONE_ID))
            return WarForgeConfig.WAR_ZONE;

        boolean friend = !ClientClaimChunkCache.playerFactionId.equals(Faction.nullUuid)
                && owner.equals(ClientClaimChunkCache.playerFactionId);
        if (info.claimType == Faction.ClaimType.CITADEL)
            return friend ? WarForgeConfig.CITADEL_FRIEND : WarForgeConfig.CITADEL_FOE;
        return friend ? WarForgeConfig.CLAIM_FRIEND : WarForgeConfig.CLAIM_FOE;
    }
}
