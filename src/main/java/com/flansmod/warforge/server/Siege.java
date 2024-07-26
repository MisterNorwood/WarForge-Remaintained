package com.flansmod.warforge.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import com.flansmod.warforge.common.DimBlockPos;
import com.flansmod.warforge.common.DimChunkPos;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.blocks.IClaim;
import com.flansmod.warforge.common.blocks.TileEntitySiegeCamp;
import com.flansmod.warforge.common.network.SiegeCampProgressInfo;
import com.flansmod.warforge.server.Faction.PlayerData;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.TextComponentString;

import javax.xml.crypto.dsig.dom.DOMSignContext;

public class Siege 
{
	public UUID mAttackingFaction;
	public UUID mDefendingFaction;
	public ArrayList<DimBlockPos> mAttackingSiegeCamps;
	public DimBlockPos mDefendingClaim;
	
	/**
	 * The base progress comes from passive sources and must be recalculated whenever checking progress.
	 * Sources for the attackers are:
	 * 		- Additional siege camps
	 * Sources of the defenders are:
	 * 		- Adjacent claims with differing support strengths
	 * 		- Defender's flags on the defended claim
	 *  */
	private int mExtraDifficulty = 0;
	/**
	 * The attack progress is accumulated over time based on active actions in the area of the siege
	 * Sources for the attackers are:
	 * 		- Defender deaths in or around the siege
	 * 		- Elapsed days with no defender logins
	 * 		- Elapsed days (there is a constant pressure from the attacker that will eventually wear down the defenders unless they push back)
	 * Sources for the defenders are:
	 * 		- Attacker deaths in or around the siege
	 * 		- Elapsed days with no attacker logins
	 */
	private int mAttackProgress = 0;


	
	
	// This is defined by the chunk we are attacking and what type it is
	public int mBaseDifficulty = 5;
	
	// Attack progress starts at 0 and can be moved to -5 or mAttackSuccessThreshold
	public int GetAttackProgress() { return mAttackProgress; }
	public void setAttackProgress(int progress) { mAttackProgress = progress; }

	public int GetDefenceProgress() { return -mAttackProgress; }
	public int GetAttackSuccessThreshold() { return mBaseDifficulty + mExtraDifficulty; }
	
	public boolean IsCompleted()
	{
		return GetAttackProgress() >= GetAttackSuccessThreshold() || GetDefenceProgress() >= 5;
	}
	
	public boolean WasSuccessful()
	{
		return GetAttackProgress() >= GetAttackSuccessThreshold();
	}
	
	public Siege()
	{
		mAttackingSiegeCamps = new ArrayList<DimBlockPos>(4);
	}
	
	public Siege(UUID attacker, UUID defender, DimBlockPos defending)
	{
		mAttackingSiegeCamps = new ArrayList<DimBlockPos>(4);
		mAttackingFaction = attacker;
		mDefendingFaction = defender;
		mDefendingClaim = defending;

		TileEntity te = WarForgeMod.MC_SERVER.getWorld(defending.mDim).getTileEntity(defending.ToRegularPos());
		if(te instanceof IClaim)
		{
			mBaseDifficulty = ((IClaim)te).GetDefenceStrength();
		}
	}
	
	public SiegeCampProgressInfo GetSiegeInfo()
	{
		Faction attackers = WarForgeMod.FACTIONS.GetFaction(mAttackingFaction);
		Faction defenders = WarForgeMod.FACTIONS.GetFaction(mDefendingFaction);
		
		if(attackers == null || defenders == null)
		{
			WarForgeMod.LOGGER.error("Invalid factions in siege. Can't display info");
			return null;
		}
		
		SiegeCampProgressInfo info = new SiegeCampProgressInfo();
		info.mAttackingPos = mAttackingSiegeCamps.get(0);
		info.mAttackingName = attackers.mName;
		info.mAttackingColour = attackers.mColour;
		info.mDefendingPos = mDefendingClaim;
		info.mDefendingName = defenders.mName;
		info.mDefendingColour = defenders.mColour;
		info.mProgress = GetAttackProgress();
		info.mCompletionPoint = GetAttackSuccessThreshold();
		
		return info;
	}
	
	public boolean Start() 
	{
		Faction attackers = WarForgeMod.FACTIONS.GetFaction(mAttackingFaction);
		Faction defenders = WarForgeMod.FACTIONS.GetFaction(mDefendingFaction);
		
		if (attackers == null || defenders == null) {
			WarForgeMod.LOGGER.error("Invalid factions in siege. Cannot start");
			return false;
		}
		
		CalculateBasePower();
		WarForgeMod.INSTANCE.MessageAll(new TextComponentString(attackers.mName + " started a siege against " + defenders.mName + " at " + mDefendingClaim.ToFancyString()), true);
		WarForgeMod.FACTIONS.SendSiegeInfoToNearby(mDefendingClaim.ToChunkPos());
		return true;
	}
	
	public void AdvanceDay()
	{
		Faction attackers = WarForgeMod.FACTIONS.GetFaction(mAttackingFaction);
		Faction defenders = WarForgeMod.FACTIONS.GetFaction(mDefendingFaction);
		
		if(attackers == null || defenders == null)
		{
			WarForgeMod.LOGGER.error("Invalid factions in siege.");
			return;
		}
		
		CalculateBasePower();
		float totalSwing = 0.0f;
		totalSwing += WarForgeConfig.SIEGE_SWING_PER_DAY_ELAPSED_BASE;
		if(!defenders.mHasHadAnyLoginsToday)
			totalSwing += WarForgeConfig.SIEGE_SWING_PER_DAY_ELAPSED_NO_DEFENDER_LOGINS;
		if(!attackers.mHasHadAnyLoginsToday)
			totalSwing -= WarForgeConfig.SIEGE_SWING_PER_DAY_ELAPSED_NO_ATTACKER_LOGINS;
		
		
		for(HashMap.Entry<UUID, PlayerData> kvp : defenders.mMembers.entrySet())
		{
			if(kvp.getValue().mFlagPosition.equals(mDefendingClaim))
			{
				totalSwing -= WarForgeConfig.SIEGE_SWING_PER_DEFENDER_FLAG;
			}
		}
		
		for(HashMap.Entry<UUID, PlayerData> kvp : attackers.mMembers.entrySet())
		{
			if(mAttackingSiegeCamps.contains(kvp.getValue().mFlagPosition))
			{
				totalSwing += WarForgeConfig.SIEGE_SWING_PER_ATTACKER_FLAG;
			}
		}
		
		mAttackProgress += totalSwing;
		
		if(totalSwing > 0)
		{
			attackers.MessageAll(new TextComponentString("Your siege on " + defenders.mName + " at " + mDefendingClaim.ToFancyString() + " shifted " + totalSwing + " points in your favour. The progress is now at " + GetAttackProgress() + "/" + mBaseDifficulty));
			defenders.MessageAll(new TextComponentString("The siege on " + mDefendingClaim.ToFancyString() + " by " + attackers.mName + " shifted " + totalSwing + " points in their favour. The progress is now at " + GetAttackProgress() + "/" + mBaseDifficulty));
		}
		else if(totalSwing < 0)
		{
			defenders.MessageAll(new TextComponentString("The siege on " + mDefendingClaim.ToFancyString() + " by " + attackers.mName + " shifted " + -totalSwing + " points in your favour. The progress is now at " + GetAttackProgress() + "/" + mBaseDifficulty));
			attackers.MessageAll(new TextComponentString("Your siege on " + defenders.mName + " at " + mDefendingClaim.ToFancyString() + " shifted " + -totalSwing + " points in their favour. The progress is now at " + GetAttackProgress() + "/" + mBaseDifficulty));
		}
		else
		{
			defenders.MessageAll(new TextComponentString("The siege on " + mDefendingClaim.ToFancyString() + " by " + attackers.mName + " did not shift today. The progress is at " + GetAttackProgress() + "/" + mBaseDifficulty));
			attackers.MessageAll(new TextComponentString("Your siege on " + defenders.mName + " at " + mDefendingClaim.ToFancyString() + " did not shift today. The progress is at " + GetAttackProgress() + "/" + mBaseDifficulty));
		}
		
		WarForgeMod.FACTIONS.SendSiegeInfoToNearby(mDefendingClaim.ToChunkPos());
	}
	
	public void CalculateBasePower()
	{
		Faction attackers = WarForgeMod.FACTIONS.GetFaction(mAttackingFaction);
		Faction defenders = WarForgeMod.FACTIONS.GetFaction(mDefendingFaction);
		
		if(attackers == null || defenders == null || WarForgeMod.MC_SERVER == null)
		{
			WarForgeMod.LOGGER.error("Invalid factions in siege.");
			return;
		}
		
		mExtraDifficulty = 0;
		
		// Add a point for each defender flag in place
		for(HashMap.Entry<UUID, PlayerData> kvp : defenders.mMembers.entrySet())
		{
			// 
			if(kvp.getValue().mFlagPosition.equals(mDefendingClaim))
			{
				mExtraDifficulty += WarForgeConfig.SIEGE_DIFFICULTY_PER_DEFENDER_FLAG;
			}
		}
		
		DimChunkPos defendingChunk = mDefendingClaim.ToChunkPos();
		for(EnumFacing direction : EnumFacing.HORIZONTALS)
		{
			DimChunkPos checkChunk = defendingChunk.Offset(direction, 1);
			UUID factionInChunk = WarForgeMod.FACTIONS.GetClaim(checkChunk);
			// Sum up all additional attack claims
			if(factionInChunk.equals(mAttackingFaction))
			{
				DimBlockPos claimBlockPos = attackers.GetSpecificPosForClaim(checkChunk);
				if(claimBlockPos != null)
				{
					TileEntity te = WarForgeMod.MC_SERVER.getWorld(claimBlockPos.mDim).getTileEntity(claimBlockPos.ToRegularPos());
					if(te instanceof IClaim)
					{
						mExtraDifficulty += ((IClaim) te).GetAttackStrength();
					}
				}
			}
			// Sum up all defending support claims
			if(factionInChunk.equals(mDefendingFaction))
			{
				DimBlockPos claimBlockPos = defenders.GetSpecificPosForClaim(checkChunk);
				if(claimBlockPos != null)
				{
					TileEntity te = WarForgeMod.MC_SERVER.getWorld(claimBlockPos.mDim).getTileEntity(claimBlockPos.ToRegularPos());
					if(te instanceof IClaim)
					{
						mExtraDifficulty -= ((IClaim) te).GetSupportStrength();
					}
				}
			}
		}
	}

	// called when siege is ended for any reason and not detected as completed normally
	public void OnCancelled()
	{
		// canceling is only run inside EndSiege, which is only run in TE, so no need for this to do anything
	}

	// called when natural conclusion of siege occurs, not called from TE itself
	public void OnCompleted(boolean successful)
	{
		// for every attacking siege camp attempt to locate it, and if an actual siege camp handle appropriately
		for (DimBlockPos siegeCampPos : mAttackingSiegeCamps) {
			TileEntity siegeCamp = WarForgeMod.MC_SERVER.getWorld(siegeCampPos.mDim).getTileEntity(siegeCampPos.ToRegularPos());
			if (siegeCamp != null) {
				if (siegeCamp instanceof TileEntitySiegeCamp) {
					if (successful) ((TileEntitySiegeCamp) siegeCamp).passSiege();
					else ((TileEntitySiegeCamp) siegeCamp).failSiege();
				}
			}
		}
	}

	private boolean isPlayerInWarzone(DimBlockPos siegeCampPos, EntityPlayerMP player){
			if(player.dimension != siegeCampPos.mDim){
				return false;
			}
		// Get the chunk coordinates of the dimBlockPos using toChunkPos
		ChunkPos chunkSiegeCampPos = new ChunkPos(siegeCampPos);

		// Get the chunk coordinates of the player
		ChunkPos playerChunkPos = new ChunkPos(player.getPosition());

		// Check if the player's chunk coordinates are within a 3x3 chunk area
		int minChunkX = chunkSiegeCampPos.x - 1;
		int maxChunkX = chunkSiegeCampPos.x + 1;
		int minChunkZ = chunkSiegeCampPos.z - 1;
		int maxChunkZ = chunkSiegeCampPos.z + 1;

		// Check if the player's chunk coordinates are within the 3x3 area
		return (playerChunkPos.x >= minChunkX && playerChunkPos.x <= maxChunkX) &&
				(playerChunkPos.z >= minChunkZ && playerChunkPos.z <= maxChunkZ);
	}

	
	public void OnPVPKill(EntityPlayerMP killer, EntityPlayerMP killed) {
		Faction attackers = WarForgeMod.FACTIONS.GetFaction(mAttackingFaction);
		Faction defenders = WarForgeMod.FACTIONS.GetFaction(mDefendingFaction);

		if (attackers == null || defenders == null || WarForgeMod.MC_SERVER == null) {
			WarForgeMod.LOGGER.error("Invalid factions in siege.");
			return;
		}

		// First case, an attacker killed a defender
		if (attackers.IsPlayerInFaction(killer.getUniqueID()) && defenders.IsPlayerInFaction(killed.getUniqueID())) {
//			DimBlockPos attackerFlagPos = attackers.GetFlagPosition(killer.getUniqueID());
//			DimBlockPos defenderFlagPos = defenders.GetFlagPosition(killed.getUniqueID());

			// Only valid if the attacker has their flag on one of the siege camps
/*
			boolean attackerFlagged = false;
			for(DimBlockPos siegeCamp : mAttackingSiegeCamps)
			{
				if(siegeCamp.equals(attackerFlagPos))
					attackerFlagged = true;
			}
			
			boolean defenderFlagged = defenderFlagPos.equals(mDefendingClaim);
*/

//			if(attackerFlagged && defenderFlagged)
			boolean attackValid = false;
			for (DimBlockPos siegeCamp : mAttackingSiegeCamps) {
				if (isPlayerInWarzone(siegeCamp, killer) && isPlayerInWarzone(siegeCamp, killed)) {
					attackValid = true;
				}

				if (attackValid) {
					mAttackProgress += WarForgeConfig.SIEGE_SWING_PER_DEFENDER_DEATH;
					killed.sendMessage(new TextComponentString("Your death has shifted the siege progress by " + WarForgeConfig.SIEGE_SWING_PER_ATTACKER_DEATH));
				}
			}

			// Other case, a defender killed an attacker
			if (defenders.IsPlayerInFaction(killer.getUniqueID()) && attackers.IsPlayerInFaction(killed.getUniqueID())) {
//				DimBlockPos attackerFlagPos = attackers.GetFlagPosition(killed.getUniqueID());
//				DimBlockPos defenderFlagPos = defenders.GetFlagPosition(killer.getUniqueID());

				// Only valid if the attacker has their flag on one of the siege camps
//				boolean attackerFlagged = false;
//				for (DimBlockPos siegeCamp : mAttackingSiegeCamps) {
//					if (siegeCamp.equals(attackerFlagPos))
//						attackerFlagged = true;
//				}
//
//				boolean defenderFlagged = defenderFlagPos.equals(mDefendingClaim);
//
//				if (attackerFlagged && defenderFlagged) {

				boolean defendValid = false;
				for (DimBlockPos siegeCamp : mAttackingSiegeCamps) {
					if (isPlayerInWarzone(siegeCamp, killer) && isPlayerInWarzone(siegeCamp, killed)) {
						defendValid = true;
					}

					if (defendValid) {
						mAttackProgress -= WarForgeConfig.SIEGE_SWING_PER_ATTACKER_DEATH;
						killed.sendMessage(new TextComponentString("Your death has shifted the siege progress by " + WarForgeConfig.SIEGE_SWING_PER_ATTACKER_DEATH));
					}
				}

				WarForgeMod.FACTIONS.SendSiegeInfoToNearby(mDefendingClaim.ToChunkPos());
			}
		}
	}
	
	public void ReadFromNBT(NBTTagCompound tags)
	{
		mAttackingSiegeCamps.clear();
		
		// Get the attacker and defender
		mAttackingFaction = tags.getUniqueId("attacker");
		mDefendingFaction = tags.getUniqueId("defender");
		
		// Get the important locations
		NBTTagList claimList = tags.getTagList("attackLocations", 11); // IntArray (see NBTBase.class)
		if (claimList != null) {
			for(NBTBase base : claimList) {
				NBTTagIntArray claimInfo = (NBTTagIntArray)base;
				DimBlockPos pos = DimBlockPos.ReadFromNBT(claimInfo);
				mAttackingSiegeCamps.add(pos);
			}
		}
				
		mDefendingClaim = DimBlockPos.ReadFromNBT(tags, "defendLocation");
		mAttackProgress = tags.getInteger("progress");
		mBaseDifficulty = tags.getInteger("baseDifficulty");
		mExtraDifficulty = tags.getInteger("extraDifficulty");
	}
	
	public void WriteToNBT(NBTTagCompound tags)
	{
		// Set attacker / defender
		tags.setUniqueId("attacker", mAttackingFaction);
		tags.setUniqueId("defender", mDefendingFaction);
		
		// Set important locations
		NBTTagList claimsList = new NBTTagList();
		for(DimBlockPos pos : mAttackingSiegeCamps)
		{
			claimsList.appendTag(pos.WriteToNBT());
		}
		tags.setTag("attackLocations", claimsList);
				
		tags.setTag("defendLocation", mDefendingClaim.WriteToNBT());
		tags.setInteger("progress", mAttackProgress);
		tags.setInteger("baseDifficulty", mBaseDifficulty);
		tags.setInteger("extraDifficulty", mExtraDifficulty);
	}
}
