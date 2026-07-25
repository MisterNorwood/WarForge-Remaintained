package com.flansmod.warforge.common.network;

import com.flansmod.warforge.api.vein.Quality;
import com.flansmod.warforge.api.vein.Vein;
import com.flansmod.warforge.server.Faction;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import net.minecraft.core.Vec3i;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
public class SiegeCampAttackInfo
{
	public boolean canAttack;
	public Vec3i mOffset;  //more flexible than DirectionFacing, Y value is ignored
	public UUID mFactionUUID;
	public String mFactionName;
	public int mFactionColour;
	public Vein mWarforgeVein;
	public Quality mOreQuality;
    public Faction.ClaimType claimType = Faction.ClaimType.NONE;
    public byte momentum;
    public boolean conquered;

   	//Bruh
    public SiegeCampAttackInfo(SiegeCampAttackInfo info) {
        this(
                info.canAttack,
                info.mOffset,
                info.mFactionUUID,
                info.mFactionName,
                info.mFactionColour,
                info.mWarforgeVein,
                info.mOreQuality,
                info.claimType,
                info.momentum,
                info.conquered
        );
    }
}
