package com.flansmod.warforge.common.network;

import net.minecraft.resources.ResourceLocation;
import com.flansmod.warforge.server.Faction;

public class ClaimChunkRenderInfo extends SiegeCampAttackInfoRender {
    public final Faction.ClaimType claimType;
    public final boolean forceLoaded;
    public final boolean battleZone;

    public ClaimChunkRenderInfo(SiegeCampAttackInfo info, Faction.ClaimType claimType, boolean forceLoaded, boolean conquered, boolean battleZone, ResourceLocation centerIcon, CenterMarkType centerIconType) {
        super(info);
        this.claimType = claimType;
        this.forceLoaded = forceLoaded;
        this.conquered = conquered;
        this.battleZone = battleZone;
        if (info instanceof SiegeCampAttackInfoRender renderInfo) {
            setCenterMarkType(renderInfo.getCenterMarkType());
            setCenterIcon(renderInfo.getCenterIcon());
            setVeinIcon(renderInfo.getVeinIcon());
        }
        if (claimType == Faction.ClaimType.SIEGE && getCenterMarkType() == CenterMarkType.NONE) {
            setCenterMarkType(CenterMarkType.SIEGE_CAMP);
        }
        if (centerIcon != null) {
            // PLAYER_FACE crops the head out of a full skin sheet; CUSTOM_TEXTURE draws the icon whole.
            setCenterMarkType(centerIconType);
            setCenterIcon(centerIcon);
        }
    }
}
