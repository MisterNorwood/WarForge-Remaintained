package com.flansmod.warforge.server;

import com.flansmod.warforge.common.DimBlockPos;
import com.flansmod.warforge.common.DimChunkPos;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.blocks.IClaim;
import com.flansmod.warforge.common.blocks.TileEntitySiegeCamp;
import com.flansmod.warforge.common.network.SiegeCampProgressInfo;
import com.flansmod.warforge.server.Faction.PlayerData;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class Siege {
	public UUID mAttackingFaction;
	public UUID mDefendingFaction;
	public ArrayList<DimBlockPos> mAttackingSiegeCamps;
	public DimBlockPos mDefendingClaim;

	private int mExtraDifficulty = 0;
	private int mAttackProgress = 0;
	private long mSiegeStartTime;
	public int siegeExpireTime = 15 * 60 * 1000; // 15 minutes in milliseconds
	public int mBaseDifficulty = 5;

	// Add a method to get current game time (replace with actual game time retrieval if available)
	private static long getCurrentGameTime() {
		return WarForgeMod.MC_SERVER.getWorld(0).getTotalWorldTime() * 50; // Assuming 1 tick = 50 ms
	}

	public int GetAttackProgress() { return mAttackProgress; }
	public void setAttackProgress(int progress) { mAttackProgress = progress; }

	public int GetDefenceProgress() { return -mAttackProgress; }
	public int GetAttackSuccessThreshold() { return mBaseDifficulty + mExtraDifficulty; }

	public boolean IsCompleted() {
		return GetAttackProgress() >= GetAttackSuccessThreshold() || GetDefenceProgress() >= 5;
	}

	public boolean WasSuccessful() {
		return GetAttackProgress() >= GetAttackSuccessThreshold();
	}

	public boolean IsAutoSucceeded() {
		return getCurrentGameTime() - mSiegeStartTime >= siegeExpireTime;
	}

	public String timeUntilAutoSucceed() {
		long timeRemaining = siegeExpireTime - (getCurrentGameTime() - mSiegeStartTime);
		long seconds = (timeRemaining / 1000) % 60;
		long minutes = (timeRemaining / (1000 * 60)) % 60;
		long hours = (timeRemaining / (1000 * 60 * 60)) % 24;

		// Return the time remaining as "HH:mm:ss"
		return String.format("%02d:%02d:%02d", hours, minutes, seconds);
	}

	public Siege() {
		mAttackingSiegeCamps = new ArrayList<>(4);
	}

	public Siege(UUID attacker, UUID defender, DimBlockPos defending) {
		mAttackingSiegeCamps = new ArrayList<>(4);
		mAttackingFaction = attacker;
		mDefendingFaction = defender;
		mDefendingClaim = defending;

		TileEntity te = WarForgeMod.MC_SERVER.getWorld(defending.mDim).getTileEntity(defending.ToRegularPos());
		if(te instanceof IClaim) {
			mBaseDifficulty = ((IClaim)te).GetDefenceStrength();
		}
	}

	public SiegeCampProgressInfo GetSiegeInfo() {
		Faction attackers = WarForgeMod.FACTIONS.GetFaction(mAttackingFaction);
		Faction defenders = WarForgeMod.FACTIONS.GetFaction(mDefendingFaction);

		if(attackers == null || defenders == null) {
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

	public boolean Start() {
		Faction attackers = WarForgeMod.FACTIONS.GetFaction(mAttackingFaction);
		Faction defenders = WarForgeMod.FACTIONS.GetFaction(mDefendingFaction);

		if (attackers == null || defenders == null) {
			WarForgeMod.LOGGER.error("Invalid factions in siege. Cannot start");
			return false;
		}

		CalculateBasePower();
		mSiegeStartTime = getCurrentGameTime();
		WarForgeMod.INSTANCE.MessageAll(new TextComponentString(attackers.mName + " started a siege against " + defenders.mName), true);
		WarForgeMod.FACTIONS.SendSiegeInfoToNearby(mDefendingClaim.ToChunkPos());
		return true;
	}




	public void AdvanceDay() {

		Faction attackers = WarForgeMod.FACTIONS.GetFaction(mAttackingFaction);
		Faction defenders = WarForgeMod.FACTIONS.GetFaction(mDefendingFaction);

		if(attackers == null || defenders == null) {
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

		for(HashMap.Entry<UUID, PlayerData> kvp : defenders.mMembers.entrySet()) {
			if(kvp.getValue().mFlagPosition.equals(mDefendingClaim)) {
				totalSwing -= WarForgeConfig.SIEGE_SWING_PER_DEFENDER_FLAG;
			}
		}

		for(HashMap.Entry<UUID, PlayerData> kvp : attackers.mMembers.entrySet()) {
			if(mAttackingSiegeCamps.contains(kvp.getValue().mFlagPosition)) {
				totalSwing += WarForgeConfig.SIEGE_SWING_PER_ATTACKER_FLAG;
			}
		}

		mAttackProgress += totalSwing;

		if(totalSwing > 0) {
			attackers.MessageAll(new TextComponentString("Your siege on " + defenders.mName + " at " + mDefendingClaim.ToFancyString() + " shifted " + totalSwing + " points in your favour. The progress is now at " + GetAttackProgress() + "/" + mBaseDifficulty));
			defenders.MessageAll(new TextComponentString("The siege on " + mDefendingClaim.ToFancyString() + " by " + attackers.mName + " shifted " + totalSwing + " points in their favour. The progress is now at " + GetAttackProgress() + "/" + mBaseDifficulty));
		} else if(totalSwing < 0) {
			defenders.MessageAll(new TextComponentString("The siege on " + mDefendingClaim.ToFancyString() + " by " + attackers.mName + " shifted " + -totalSwing + " points in your favour. The progress is now at " + GetAttackProgress() + "/" + mBaseDifficulty));
			attackers.MessageAll(new TextComponentString("Your siege on " + defenders.mName + " at " + mDefendingClaim.ToFancyString() + " shifted " + -totalSwing + " points in their favour. The progress is now at " + GetAttackProgress() + "/" + mBaseDifficulty));
		} else {
			defenders.MessageAll(new TextComponentString("The siege on " + mDefendingClaim.ToFancyString() + " by " + attackers.mName + " did not shift today. The progress is at " + GetAttackProgress() + "/" + mBaseDifficulty));
			attackers.MessageAll(new TextComponentString("Your siege on " + defenders.mName + " at " + mDefendingClaim.ToFancyString() + " did not shift today. The progress is at " + GetAttackProgress() + "/" + mBaseDifficulty));
		}

		WarForgeMod.FACTIONS.SendSiegeInfoToNearby(mDefendingClaim.ToChunkPos());
	}

	public void CalculateBasePower() {
		Faction attackers = WarForgeMod.FACTIONS.GetFaction(mAttackingFaction);
		Faction defenders = WarForgeMod.FACTIONS.GetFaction(mDefendingFaction);

		if(attackers == null || defenders == null || WarForgeMod.MC_SERVER == null) {
			WarForgeMod.LOGGER.error("Invalid factions in siege.");
			return;
		}

		mExtraDifficulty = 0;

		for(HashMap.Entry<UUID, PlayerData> kvp : defenders.mMembers.entrySet()) {
			if(kvp.getValue().mFlagPosition.equals(mDefendingClaim)) {
				mExtraDifficulty += WarForgeConfig.SIEGE_DIFFICULTY_PER_DEFENDER_FLAG;
			}
		}

		DimChunkPos defendingChunk = mDefendingClaim.ToChunkPos();
		for(EnumFacing direction : EnumFacing.HORIZONTALS) {
			DimChunkPos checkChunk = defendingChunk.Offset(direction, 1);
			UUID factionInChunk = WarForgeMod.FACTIONS.GetClaim(checkChunk);
			if(factionInChunk.equals(mAttackingFaction)) {
				DimBlockPos claimBlockPos = attackers.GetSpecificPosForClaim(checkChunk);
				if(claimBlockPos != null) {
					TileEntity te = WarForgeMod.MC_SERVER.getWorld(claimBlockPos.mDim).getTileEntity(claimBlockPos.ToRegularPos());
					if(te instanceof IClaim) {
						mExtraDifficulty += ((IClaim) te).GetAttackStrength();
					}
				}
			}
			if(factionInChunk.equals(mDefendingFaction)) {
				DimBlockPos claimBlockPos = defenders.GetSpecificPosForClaim(checkChunk);
				if(claimBlockPos != null) {
					TileEntity te = WarForgeMod.MC_SERVER.getWorld(claimBlockPos.mDim).getTileEntity(claimBlockPos.ToRegularPos());
					if(te instanceof IClaim) {
						mExtraDifficulty -= ((IClaim) te).GetSupportStrength();
					}
				}
			}
		}
	}

	public void OnCancelled() {
		// no action needed
	}

	public void OnCompleted(boolean successful) {
		for (DimBlockPos siegeCampPos : mAttackingSiegeCamps) {
			TileEntity siegeCamp = WarForgeMod.MC_SERVER.getWorld(siegeCampPos.mDim).getTileEntity(siegeCampPos.ToRegularPos());
			if (siegeCamp != null) {
				if (siegeCamp instanceof TileEntitySiegeCamp) {
					if (successful) ((TileEntitySiegeCamp) siegeCamp).cleanupPassedSiege();
					else ((TileEntitySiegeCamp) siegeCamp).cleanupFailedSiege();
				}
			}
		}
	}

	public void Update() {
		if (IsAutoSucceeded()) {
			if (!WasSuccessful()) {
				OnCompleted(true);
			}
		}
	}

	private boolean isPlayerInWarzone(DimBlockPos siegeCampPos, EntityPlayerMP player) {
		DimChunkPos siegeCampChunkPos = siegeCampPos.ToChunkPos();
		DimChunkPos playerChunkPos = new DimChunkPos(player.dimension, player.getPosition());

		return isPlayerInRadius(siegeCampChunkPos, playerChunkPos);
	}

	public static boolean isPlayerInRadius(DimChunkPos centerChunkPos, DimChunkPos playerChunkPos) {
		if (playerChunkPos.mDim != centerChunkPos.mDim) return false;

		int minChunkX = centerChunkPos.x - 1;
		int maxChunkX = centerChunkPos.x + 1;
		int minChunkZ = centerChunkPos.z - 1;
		int maxChunkZ = centerChunkPos.z + 1;

		return (playerChunkPos.x >= minChunkX && playerChunkPos.x <= maxChunkX)
				&& (playerChunkPos.z >= minChunkZ && playerChunkPos.z <= maxChunkZ);
	}

	public void OnPVPKill(EntityPlayerMP killer, EntityPlayerMP killed) {
		Faction attackers = WarForgeMod.FACTIONS.GetFaction(mAttackingFaction);
		Faction defenders = WarForgeMod.FACTIONS.GetFaction(mDefendingFaction);
		Faction killerFaction = WarForgeMod.FACTIONS.GetFactionOfPlayer(killer.getUniqueID());
		Faction killedFaction = WarForgeMod.FACTIONS.GetFactionOfPlayer(killed.getUniqueID());

		if (attackers == null || defenders == null || WarForgeMod.MC_SERVER == null) {
			WarForgeMod.LOGGER.error("Invalid factions in siege.");
			return;
		}

		boolean attackValid = false;
		boolean defendValid = false;

		for (DimBlockPos siegeCamp : mAttackingSiegeCamps) {
			if (isPlayerInWarzone(siegeCamp, killer)) {
				if (killerFaction == attackers && killedFaction == defenders) {
					attackValid = true;
				} else if (killerFaction == defenders && killedFaction == attackers) {
					defendValid = true;
				}
			}
		}

		if (!attackValid && !defendValid) return;

		mAttackProgress += attackValid ? WarForgeConfig.SIEGE_SWING_PER_DEFENDER_DEATH : -WarForgeConfig.SIEGE_SWING_PER_ATTACKER_DEATH;
		WarForgeMod.FACTIONS.SendSiegeInfoToNearby(mDefendingClaim.ToChunkPos());

		ITextComponent notification = new TextComponentTranslation("warforge.notification.siege_death",
				killed.getName(), WarForgeConfig.SIEGE_SWING_PER_ATTACKER_DEATH,
				GetAttackProgress(), GetAttackSuccessThreshold(), GetDefenceProgress());

		attackers.MessageAll(notification);
		defenders.MessageAll(notification);
	}

	public void ReadFromNBT(NBTTagCompound tags) {
		mAttackingSiegeCamps.clear();

		mAttackingFaction = tags.getUniqueId("attacker");
		mDefendingFaction = tags.getUniqueId("defender");

		NBTTagList claimList = tags.getTagList("attackLocations", 11);
		if (claimList != null) {
			for (NBTBase base : claimList) {
				NBTTagIntArray claimInfo = (NBTTagIntArray) base;
				DimBlockPos pos = DimBlockPos.ReadFromNBT(claimInfo);
				mAttackingSiegeCamps.add(pos);
			}
		}

		mDefendingClaim = DimBlockPos.ReadFromNBT(tags, "defendLocation");
		mAttackProgress = tags.getInteger("progress");
		mBaseDifficulty = tags.getInteger("baseDifficulty");
		mExtraDifficulty = tags.getInteger("extraDifficulty");
		mSiegeStartTime = tags.getLong("siegeStartTime"); // Read the siege start time
	}

	public void WriteToNBT(NBTTagCompound tags) {
		tags.setUniqueId("attacker", mAttackingFaction);
		tags.setUniqueId("defender", mDefendingFaction);

		NBTTagList claimsList = new NBTTagList();
		for (DimBlockPos pos : mAttackingSiegeCamps) {
			claimsList.appendTag(pos.WriteToNBT());
		}

		tags.setTag("attackLocations", claimsList);
		tags.setTag("defendLocation", mDefendingClaim.WriteToNBT());
		tags.setInteger("progress", mAttackProgress);
		tags.setInteger("baseDifficulty", mBaseDifficulty);
		tags.setInteger("extraDifficulty", mExtraDifficulty);
		tags.setLong("siegeStartTime", mSiegeStartTime); // Write the siege start time
	}
}