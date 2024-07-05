package com.flansmod.warforge.common.network;

import java.util.ArrayList;
import java.util.UUID;

import com.flansmod.warforge.common.DimBlockPos;
import com.flansmod.warforge.server.Faction;

import net.minecraft.item.ItemStack;

// What gets sent over network to display faction information on client
public class FactionDisplayInfo 
{	
	public UUID mFactionID = Faction.NULL;
	public String mFactionName = "";
	public UUID mLeaderID = Faction.NULL;
	public ArrayList<PlayerDisplayInfo> mMembers = new ArrayList<PlayerDisplayInfo>();
	
	
	public int mNotoriety = 0;
	public int mWealth = 0;
	public int mLegacy = 0;
	
	public int mNotorietyRank = 0;
	public int mWealthRank = 0;
	public int mLegacyRank = 0;
	public int mTotalRank = 0;
	
	
	public int mNumClaims = 0;
	public int mNumActiveSiegeCamps = 0;
	public int mNumActiveLeeches = 0;
	public DimBlockPos mCitadelPos = DimBlockPos.ZERO;	
	
	public PlayerDisplayInfo GetPlayerInfo(UUID playerID)
	{
		for(PlayerDisplayInfo info : mMembers)
		{
			if(info.mPlayerUUID.equals(playerID))
				return info;
		}
		return null;
	}
}
