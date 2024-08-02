package com.flansmod.warforge.server;

import java.awt.Color;
import java.util.*;

import com.flansmod.warforge.api.ObjectIntPair;
import com.flansmod.warforge.common.DimBlockPos;
import com.flansmod.warforge.common.DimChunkPos;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.blocks.IClaim;
import com.flansmod.warforge.common.blocks.TileEntityCitadel;
import com.flansmod.warforge.common.blocks.TileEntityClaim;
import com.flansmod.warforge.common.blocks.TileEntitySiegeCamp;
import com.flansmod.warforge.common.network.PacketSiegeCampProgressUpdate;
import com.flansmod.warforge.common.network.SiegeCampProgressInfo;
import com.flansmod.warforge.server.Faction.PlayerData;
import com.flansmod.warforge.server.Faction.Role;
import com.mojang.authlib.GameProfile;

import it.unimi.dsi.fastutil.Hash;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

public class FactionStorage {
    private HashMap<UUID, Faction> mFactions = new HashMap<UUID, Faction>();
    // This map contains every single claim, including siege camps.
    // So if you take one of these and try to look it up in the faction, check their active sieges too
    private HashMap<DimChunkPos, UUID> mClaims = new HashMap<DimChunkPos, UUID>();
    
    // This is all the currently active sieges, keyed by the defending position
    private HashMap<DimChunkPos, Siege> mSieges = new HashMap<DimChunkPos, Siege>();

	//This is all chunks that are under the "Grace" period
	public HashMap<DimChunkPos, ObjectIntPair<UUID>> conqueredChunks = new HashMap<>();
    
    // SafeZone and WarZone
    public static UUID SAFE_ZONE_ID = Faction.CreateUUID("safezone");
    public static UUID WAR_ZONE_ID = Faction.CreateUUID("warzone");
    public static Faction SAFE_ZONE = null;
    public static Faction WAR_ZONE = null;
    public static boolean IsNeutralZone(UUID factionID) { return factionID.equals(SAFE_ZONE_ID) || factionID.equals(WAR_ZONE_ID); }
    
    public FactionStorage()
    {
		InitNeutralZones();
    }
    
    private void InitNeutralZones()
    {
		SAFE_ZONE = new Faction();
		SAFE_ZONE.mCitadelPos = new DimBlockPos(0,0,0,0); // Overworld origin
		SAFE_ZONE.mColour = 0x00ff00;
		SAFE_ZONE.mName = "SafeZone";
		SAFE_ZONE.mUUID = SAFE_ZONE_ID;
		mFactions.put(SAFE_ZONE_ID, SAFE_ZONE);

		WAR_ZONE = new Faction();
		WAR_ZONE.mCitadelPos = new DimBlockPos(0,0,0,0); // Overworld origin
		WAR_ZONE.mColour = 0xff0000;
		WAR_ZONE.mName = "WarZone";
		WAR_ZONE.mUUID = WAR_ZONE_ID;
		mFactions.put(WAR_ZONE_ID, WAR_ZONE);
		// Note: We specifically do not put this data in the leaderboard
    }
    
    public boolean IsPlayerInFaction(UUID playerID, UUID factionID)
    {
		if(mFactions.containsKey(factionID))
			return mFactions.get(factionID).IsPlayerInFaction(playerID);
		return false;
    }

	public boolean isPlayerDefending(UUID playerID){
		Faction faction = GetFactionOfPlayer(playerID);
		List<Siege> Sieges = new ArrayList<>(mSieges.values());
		for(Siege siege : Sieges){
			if(siege.mDefendingFaction == faction.mUUID){
				return true;
			}
		}

		return false;
	}

	public long getPlayerCooldown(UUID playerID){
		return mFactions.get(playerID).mMembers.get(playerID).mMoveFlagCooldown;
	}

	public HashMap<DimChunkPos, Siege> getSieges() { return mSieges; }

	public HashMap<DimChunkPos, UUID> getClaims() { return mClaims; }

    public boolean IsPlayerRoleInFaction(UUID playerID, UUID factionID, Faction.Role role)
    {
		if(mFactions.containsKey(factionID))
			return mFactions.get(factionID).IsPlayerRoleInFaction(playerID, role);
		return false;
    }
    
    public Faction GetFaction(UUID factionID)
    {
		if(factionID.equals(Faction.NULL))
			return null;

		if(mFactions.containsKey(factionID))
			return mFactions.get(factionID);

		WarForgeMod.LOGGER.error("Could not find a faction with UUID " + factionID);
		return null;
    }
    
    public Faction GetFaction(String name)
    {
		for(HashMap.Entry<UUID, Faction> entry : mFactions.entrySet()) {
			if(entry.getValue().mName.equals(name))
				return entry.getValue();
		}
		return null;
    }
    

    public Faction GetFactionWithOpenInviteTo(UUID playerID)
    {
		for(HashMap.Entry<UUID, Faction> entry : mFactions.entrySet()) {
			if(entry.getValue().IsInvitingPlayer(playerID))
				return entry.getValue();
		}
		return null;
    }

	public String[] GetFactionNames() {
		String[] names = new String[mFactions.size()];
		int i = 0;
		for(HashMap.Entry<UUID, Faction> entry : mFactions.entrySet()) {
			names[i] = entry.getValue().mName;
			i++;
		}
		return names;
	}

	// This is called for any non-citadel claim. Citadels can be factionless, so this makes no sense
	public void OnNonCitadelClaimPlaced(IClaim claim, EntityLivingBase placer) {
		OnNonCitadelClaimPlaced(claim, GetFactionOfPlayer(placer.getUniqueID()));
	}

	public void OnNonCitadelClaimPlaced(IClaim claim, Faction faction) {
		if(faction != null) {
			TileEntity tileEntity = claim.GetAsTileEntity();
			mClaims.put(claim.GetPos().ToChunkPos(), faction.mUUID);

			faction.MessageAll(new TextComponentString("Claimed the chunk [" + claim.GetPos().ToChunkPos().x + ", " + claim.GetPos().ToChunkPos().z + "] around " + claim.GetPos().ToFancyString()));

			claim.OnServerSetFaction(faction);
			faction.OnClaimPlaced(claim);
		} else
			WarForgeMod.LOGGER.error("Invalid placer placed a claim at " + claim.GetPos());
	}

	public UUID GetClaim(DimBlockPos pos)
    {
		return GetClaim(pos.ToChunkPos());
    }
    
    public UUID GetClaim(DimChunkPos pos)
    {
		if(mClaims.containsKey(pos))
			return mClaims.get(pos);
		return Faction.NULL;
    }
    
    public Faction GetFactionOfPlayer(UUID playerID)
    {
		for(HashMap.Entry<UUID, Faction> entry : mFactions.entrySet()) {
			if(entry.getValue().IsPlayerInFaction(playerID))
				return entry.getValue();
		}
		return null;
    }
    
    public void Update()
    {
		for(HashMap.Entry<UUID, Faction> entry : mFactions.entrySet()) {
			entry.getValue().Update();
		}
    }

	public void updateConqueredChunks(long msUpdateTime) {
		int msPassed = (int) (msUpdateTime - WarForgeMod.previousUpdateTimestamp); // the difference is likely less than 596h (max time storage of int using ms)

		for (DimChunkPos chunkPosKey : conqueredChunks.keySet()) {
			ObjectIntPair<UUID> chunkEntry = conqueredChunks.get(chunkPosKey);

			try {
				if (chunkEntry.getRight() < msPassed) conqueredChunks.remove(chunkPosKey);
				else chunkEntry.setRight(chunkEntry.getRight() - msPassed);
			} catch (Exception e) {
				WarForgeMod.LOGGER.atError().log("Error when updating conquered chunk at position " + chunkPosKey.toString() + " of type " + e + " with stacktrace: ");
				e.printStackTrace();
				break;
			}

		}

		WarForgeMod.previousUpdateTimestamp = msUpdateTime; // current update is now previous, as update has been performed
	}
    
    public void AdvanceSiegeDay()
    {
		for(HashMap.Entry<DimChunkPos, Siege> kvp : mSieges.entrySet()) {
			kvp.getValue().AdvanceDay();
		}

		CheckForCompleteSieges();

		if(!WarForgeConfig.LEGACY_USES_YIELD_TIMER) {
			for(HashMap.Entry<UUID, Faction> entry : mFactions.entrySet()) {
				entry.getValue().IncreaseLegacy();
			}
		}
    }
    
    public void AdvanceYieldDay()
    {
		for(HashMap.Entry<UUID, Faction> entry : mFactions.entrySet()) {
			entry.getValue().AwardYields();
		}

		if(WarForgeConfig.LEGACY_USES_YIELD_TIMER) {
			for(HashMap.Entry<UUID, Faction> entry : mFactions.entrySet()) {
				entry.getValue().IncreaseLegacy();
			}
		}
    }
    
    public void PlayerDied(EntityPlayerMP playerWhoDied, DamageSource source)
    {
		if(source.getTrueSource() instanceof EntityPlayerMP) {
			EntityPlayerMP killer = (EntityPlayerMP)source.getTrueSource();
			Faction killedFac = GetFactionOfPlayer(playerWhoDied.getUniqueID());
			Faction killerFac = GetFactionOfPlayer(killer.getUniqueID());

			if(killedFac != null && killerFac != null) {
				for(HashMap.Entry<DimChunkPos, Siege> kvp : mSieges.entrySet()) {
					kvp.getValue().OnPVPKill(killer, playerWhoDied);
				}

				CheckForCompleteSieges();
			}

			if(killerFac != null) {
				int numTimesKilled = 0;
				if(killerFac.mKillCounter.containsKey(playerWhoDied.getUniqueID())) {
					numTimesKilled = killerFac.mKillCounter.get(playerWhoDied.getUniqueID()) + 1;
					killerFac.mKillCounter.replace(playerWhoDied.getUniqueID(), numTimesKilled);
				} else {
					numTimesKilled = 1;
					killerFac.mKillCounter.put(playerWhoDied.getUniqueID(), numTimesKilled);
				}

				if(numTimesKilled <= WarForgeConfig.NOTORIETY_KILL_CAP_PER_PLAYER) {
					if(killerFac != killedFac) {
						((EntityPlayer)source.getTrueSource()).sendMessage(new TextComponentString("Killing " + playerWhoDied.getName() + " earned your faction " + WarForgeConfig.NOTORIETY_PER_PLAYER_KILL + " notoriety"));
						killerFac.mNotoriety += WarForgeConfig.NOTORIETY_PER_PLAYER_KILL;
					}
				} else {
					((EntityPlayer)source.getTrueSource()).sendMessage(new TextComponentString("Your faction has already killed " + playerWhoDied.getName() + " " + numTimesKilled + " times. You will not become more notorious."));
				}
			}
		}
    }
    
    public void ClearNotoriety()
    {
		for(HashMap.Entry<UUID, Faction> entry : mFactions.entrySet()) {
			entry.getValue().mNotoriety = 0;
		}
    }
    
    public void ClearLegacy()
    {
		for(HashMap.Entry<UUID, Faction> entry : mFactions.entrySet()) {
			entry.getValue().mLegacy = 0;
		}
    }

	public void CheckForCompleteSieges() {
		// Cache in a list so we can remove from the siege HashMap
		ArrayList<DimChunkPos> completedSieges = new ArrayList<DimChunkPos>();
		for(HashMap.Entry<DimChunkPos, Siege> kvp : mSieges.entrySet()) {
			if(kvp.getValue().IsCompleted())
				completedSieges.add(kvp.getKey());
		}

		// Now process the results
		for(DimChunkPos chunkPos : completedSieges)
			handleCompletedSiege(chunkPos);
	}

	// cleaner separation between action to be done on completed sieges and the determination of these sieges
	public void handleCompletedSiege(DimChunkPos chunkPos) {
		handleCompletedSiege(chunkPos, true);
	}

	// cleanup is done by failing, passing, or cancelling siege through the TE class. If called without boolean, it is assumed to not be from inside TE
	public void handleCompletedSiege(DimChunkPos chunkPos, boolean doCleanup) {
		Siege siege = mSieges.get(chunkPos);

		Faction attackers = GetFaction(siege.mAttackingFaction);
		Faction defenders = GetFaction(siege.mDefendingFaction);
		if(attackers == null || defenders == null) {
			WarForgeMod.LOGGER.error("Invalid factions in completed siege. Nothing will happen.");
			return;
		}

		DimBlockPos blockPos = defenders.GetSpecificPosForClaim(chunkPos);
		boolean successful = siege.WasSuccessful();
		if(successful) {
			if (WarForgeConfig.ATTACKER_CONQUERED_CHUNK_PERIOD > 0) {
				conqueredChunks.put(chunkPos, new ObjectIntPair<>(copyUUID(attackers.mUUID), WarForgeConfig.ATTACKER_CONQUERED_CHUNK_PERIOD));
				for (DimBlockPos siegeCampPos : siege.mAttackingSiegeCamps)
					if (siegeCampPos != null)
						conqueredChunks.put(siegeCampPos.ToChunkPos(), new ObjectIntPair<>(copyUUID(attackers.mUUID), WarForgeConfig.ATTACKER_CONQUERED_CHUNK_PERIOD));
			}
			defenders.OnClaimLost(blockPos); // drops block if SIEGE_CAPTURE is off, or drops nothing if it is on
			mClaims.remove(blockPos.ToChunkPos());
			attackers.MessageAll(new TextComponentTranslation("warforge.info.siege_won_attackers", defenders.mName, blockPos.ToFancyString()));
			defenders.MessageAll(new TextComponentTranslation("warforge.info.siege_lost_defenders", defenders.mName, blockPos.ToFancyString()));
			attackers.mNotoriety += WarForgeConfig.NOTORIETY_PER_SIEGE_ATTACK_SUCCESS;
			if(WarForgeConfig.SIEGE_CAPTURE) {
				WarForgeMod.MC_SERVER.getWorld(blockPos.mDim).setBlockState(blockPos.ToRegularPos(), WarForgeMod.CONTENT.basicClaimBlock.getDefaultState());
				TileEntity te = WarForgeMod.MC_SERVER.getWorld(blockPos.mDim).getTileEntity(blockPos.ToRegularPos());
				OnNonCitadelClaimPlaced((IClaim)te, attackers);
			}
		} else {
			if (WarForgeConfig.DEFENDER_CONQUERED_CHUNK_PERIOD > 0) {
				conqueredChunks.put(chunkPos, new ObjectIntPair<>(copyUUID(defenders.mUUID), WarForgeConfig.DEFENDER_CONQUERED_CHUNK_PERIOD)); // defenders get won claims defended
				for (DimBlockPos siegeCampPos : siege.mAttackingSiegeCamps)
					if (siegeCampPos != null)
						conqueredChunks.put(siegeCampPos.ToChunkPos(), new ObjectIntPair<>(copyUUID(defenders.mUUID), WarForgeConfig.DEFENDER_CONQUERED_CHUNK_PERIOD));
			}
			attackers.MessageAll(new TextComponentTranslation("warforge.info.siege_lost_attackers", attackers.mName, blockPos.ToFancyString()));
			defenders.MessageAll(new TextComponentTranslation("warforge.info.siege_won_defenders", defenders.mName, blockPos.ToFancyString()));
			defenders.mNotoriety += WarForgeConfig.NOTORIETY_PER_SIEGE_DEFEND_SUCCESS;
		}

		if (doCleanup) siege.OnCompleted(successful);

		// Then remove the siege
		mSieges.remove(chunkPos);
	}

	// copy UUID's are used in the case that the UUID referred to are nulled at some point, which is not ideal for chunk protection
	public static UUID copyUUID(final UUID source) {
		return new UUID(source.getMostSignificantBits(), source.getLeastSignificantBits());
	}
    
    public boolean RequestCreateFaction(TileEntityCitadel citadel, EntityPlayer player, String factionName, int colour)
    {
		if(citadel == null) {
			player.sendMessage(new TextComponentString("You can't create a faction without a citadel"));
			return false;
		}

		if(factionName == null || factionName.isEmpty()) {
			player.sendMessage(new TextComponentString("You can't create a faction with no name"));
			return false;
		}

		if(factionName.length() > WarForgeConfig.FACTION_NAME_LENGTH_MAX) {
			player.sendMessage(new TextComponentString("Name is too long, must be at most " + WarForgeConfig.FACTION_NAME_LENGTH_MAX + " characters"));
			return false;
		}

		for(int i = 0; i < factionName.length(); i++) {
			char c = factionName.charAt(i);
			if('0' <= c && c <= '9')
				continue;
			if('a' <= c && c <= 'z')
				continue;
			if('A' <= c && c <= 'Z')
				continue;

			player.sendMessage(new TextComponentString("Invalid character [" + c + "] in faction name"));
			return false;
		}

		Faction existingFaction = GetFactionOfPlayer(player.getUniqueID());
		if(existingFaction != null) {
			player.sendMessage(new TextComponentString("You are already in a faction"));
			return false;
		}

		UUID proposedID = Faction.CreateUUID(factionName);
		if(mFactions.containsKey(proposedID)) {
			player.sendMessage(new TextComponentString("A faction with the name " + factionName + " already exists"));
			return false;
		}

		if(colour == 0xffffff) {
			colour = Color.HSBtoRGB(WarForgeMod.rand.nextFloat(), WarForgeMod.rand.nextFloat() * 0.5f + 0.5f, 1.0f);
		}

		// All checks passed, create a faction
		Faction faction = new Faction();
		faction.mUUID = proposedID;
		faction.mName = factionName;
		faction.mCitadelPos = new DimBlockPos(citadel);
		faction.mClaims.put(faction.mCitadelPos, 0);
		faction.mColour = colour;
		faction.mNotoriety = 0;
		faction.mLegacy = 0;
		faction.mWealth = 0;

		mFactions.put(proposedID, faction);
		citadel.OnServerSetFaction(faction);
		mClaims.put(citadel.GetPos().ToChunkPos(), proposedID);
		WarForgeMod.LEADERBOARD.RegisterFaction(faction);

		WarForgeMod.INSTANCE.MessageAll(new TextComponentString(player.getName() + " created the faction " + factionName), true);

		faction.AddPlayer(player.getUniqueID());
		faction.SetLeader(player.getUniqueID());

		return true;
    }
    
    public boolean RequestRemovePlayerFromFaction(ICommandSender remover, UUID factionID, UUID toRemove)
    {
		Faction faction = GetFaction(factionID);
		if(faction == null) {
			remover.sendMessage(new TextComponentString("That faction doesn't exist"));
			return false;
		}

		if(!faction.IsPlayerInFaction(toRemove)) {
			remover.sendMessage(new TextComponentString("That player is not in that faction"));
			return false;
		}

		boolean canRemove = WarForgeMod.IsOp(remover);
		boolean removingSelf = false;
		if(remover instanceof EntityPlayer) {
			UUID removerID = ((EntityPlayer)remover).getUniqueID();
			if(removerID.equals(toRemove)) // remove self
			{
				canRemove = true;
				removingSelf = true;
			}

			if(faction.IsPlayerOutrankingOfficerOf(removerID, toRemove))
				canRemove = true;
		}

		if(!canRemove) {
			remover.sendMessage(new TextComponentString("You don't have permission to remove that player"));
			return false;
		}

		GameProfile userProfile = WarForgeMod.MC_SERVER.getPlayerProfileCache().getProfileByUUID(toRemove);
		if(userProfile != null) {
			if(removingSelf)
				faction.MessageAll(new TextComponentString(userProfile.getName() + " left " + faction.mName));
			else
				faction.MessageAll(new TextComponentString(userProfile.getName() + " was kicked from " + faction.mName));
		} else {
			remover.sendMessage(new TextComponentString("Error: Could not get user profile"));
		}

		faction.RemovePlayer(toRemove);

		SendAllSiegeInfoToNearby();

		return true;
    }
        
    public boolean RequestInvitePlayerToMyFaction(EntityPlayer factionOfficer, UUID invitee)
    {
		Faction myFaction = GetFactionOfPlayer(factionOfficer.getUniqueID());
		if(myFaction != null)
			return RequestInvitePlayerToFaction(factionOfficer, myFaction.mUUID, invitee);
		return false;
    }
    
    public boolean RequestInvitePlayerToFaction(ICommandSender factionOfficer, UUID factionID, UUID invitee)
    {
		Faction faction = GetFaction(factionID);
		if(faction == null) {
			factionOfficer.sendMessage(new TextComponentString("That faction doesn't exist"));
			return false;
		}

		if(!WarForgeMod.IsOp(factionOfficer) && !faction.IsPlayerRoleInFaction(WarForgeMod.GetUUID(factionOfficer), Faction.Role.OFFICER)) {
			factionOfficer.sendMessage(new TextComponentString("You are not an officer of this faction"));
			return false;
		}

		Faction existingFaction = GetFactionOfPlayer(invitee);
		if(existingFaction != null) {
			factionOfficer.sendMessage(new TextComponentString("That player is already in a faction"));
			return false;
		}

		// TODO: Faction player limit - grows with claims?

		faction.InvitePlayer(invitee);
		WarForgeMod.MC_SERVER.getPlayerList().getPlayerByUUID(invitee).sendMessage(new TextComponentString("You have received an invite to " + faction.mName + ". Type /f accept to join"));

		return true;
    }
    
    public void RequestAcceptInvite(EntityPlayer player)
    {
		Faction inviter = GetFactionWithOpenInviteTo(player.getUniqueID());
		if(inviter != null) {
			inviter.AddPlayer(player.getUniqueID());
		} else
			player.sendMessage(new TextComponentString("You have no open invite to accept"));
    }
    
    public boolean RequestTransferLeadership(EntityPlayer factionLeader, UUID factionID, UUID newLeaderID)
    {
		Faction faction = GetFaction(factionID);
		if(faction == null) {
			factionLeader.sendMessage(new TextComponentString("That faction does not exist"));
			return false;
		}

		if(!WarForgeMod.IsOp(factionLeader) && !faction.IsPlayerRoleInFaction(factionLeader.getUniqueID(), Faction.Role.LEADER)) {
			factionLeader.sendMessage(new TextComponentString("You are not the leader of this faction"));
			return false;
		}

		if(!faction.IsPlayerInFaction(newLeaderID)) {
			factionLeader.sendMessage(new TextComponentString("That player is not in your faction"));
			return false;
		}

		// Do the set
		if(!faction.SetLeader(newLeaderID)) {
			factionLeader.sendMessage(new TextComponentString("Failed to set leader"));
			return false;
		}

		factionLeader.sendMessage(new TextComponentString("Successfully set leader"));
		return true;
    }

	public boolean RequestPromote(EntityPlayer factionLeader, EntityPlayerMP target) {
		Faction faction = GetFactionOfPlayer(factionLeader.getUniqueID());
		if(faction == null) {
			factionLeader.sendMessage(new TextComponentString("You are not in a faction"));
			return false;
		}
		if(!faction.IsPlayerRoleInFaction(factionLeader.getUniqueID(), Role.LEADER)) {
			factionLeader.sendMessage(new TextComponentString("You are not the leader of this faction"));
			return false;
		}
		if(!faction.IsPlayerRoleInFaction(target.getUniqueID(), Role.MEMBER)) {
			factionLeader.sendMessage(new TextComponentString("This player cannot be promoted"));
			return false;
		}

		faction.Promote(target.getUniqueID());
		return true;
	}

	public boolean RequestDemote(EntityPlayer factionLeader, EntityPlayerMP target) {
		Faction faction = GetFactionOfPlayer(factionLeader.getUniqueID());
		if(faction == null) {
			factionLeader.sendMessage(new TextComponentString("You are not in a faction"));
			return false;
		}
		if(!faction.IsPlayerRoleInFaction(factionLeader.getUniqueID(), Role.LEADER)) {
			factionLeader.sendMessage(new TextComponentString("You are not the leader of this faction"));
			return false;
		}
		if(!faction.IsPlayerRoleInFaction(target.getUniqueID(), Role.OFFICER)) {
			factionLeader.sendMessage(new TextComponentString("This player cannot be demoted"));
			return false;
		}

		faction.Demote(target.getUniqueID());
		return true;
	}


	public boolean RequestDisbandFaction(EntityPlayer factionLeader, UUID factionID)
    {
		if(factionID.equals(Faction.NULL)) {
			Faction faction = GetFactionOfPlayer(factionLeader.getUniqueID());
			if(faction != null)
				factionID = faction.mUUID;
		}

		if(!IsPlayerRoleInFaction(factionLeader.getUniqueID(), factionID, Faction.Role.LEADER)) {
			factionLeader.sendMessage(new TextComponentString("You are not the leader of this faction"));
			return false;
		}

		Faction faction = mFactions.get(factionID);
		for(Map.Entry<DimBlockPos, Integer> kvp : faction.mClaims.entrySet()) {
			mClaims.remove(kvp.getKey().ToChunkPos());
		}
		faction.Disband();
		mFactions.remove(factionID);
		WarForgeMod.LEADERBOARD.UnregisterFaction(faction);

		return true;
    }
    
    public void FactionDefeated(Faction faction)
    {
		for(Map.Entry<DimBlockPos, Integer> kvp : faction.mClaims.entrySet()) {
			mClaims.remove(kvp.getKey().ToChunkPos());
		}
		faction.Disband();
		mFactions.remove(faction.mUUID);
		WarForgeMod.LEADERBOARD.UnregisterFaction(faction);
    }

	public boolean IsSiegeInProgress(DimChunkPos chunkPos) {

		for(Map.Entry<DimChunkPos, Siege> kvp : mSieges.entrySet()) {
			if(kvp.getKey().equals(chunkPos))
				return true;

			for(DimBlockPos attackerPos : kvp.getValue().mAttackingSiegeCamps) {
				if(attackerPos.ToChunkPos().equals(chunkPos))
					return true;
			}
		}
		return false;
	}
    
    public boolean RequestStartSiege(EntityPlayer factionOfficer, DimBlockPos siegeCampPos, EnumFacing direction)
    {
		Faction attacking = GetFactionOfPlayer(factionOfficer.getUniqueID());

		if (WarForgeMod.ServerTick - attacking.getLastSiegeTimestamp() < WarForgeConfig.SIEGE_COOLDOWN_FAIL) {
			factionOfficer.sendMessage(new TextComponentString("Your faction is on cooldown on starting a new siege"));

			long s = WarForgeMod.INSTANCE.GetCooldownRemainingSeconds(WarForgeConfig.SIEGE_COOLDOWN_FAIL, attacking.getLastSiegeTimestamp()) % 60;
			int m = WarForgeMod.INSTANCE.GetCooldownRemainingMinutes(WarForgeConfig.SIEGE_COOLDOWN_FAIL, attacking.getLastSiegeTimestamp()) % 60;
			int h = WarForgeMod.INSTANCE.GetCooldownRemainingHours(WarForgeConfig.SIEGE_COOLDOWN_FAIL, attacking.getLastSiegeTimestamp());

			factionOfficer.sendMessage(new TextComponentString(String.format("Cooldown remaining: %dh %dm %ds or %d ticks", h, m, s, s * 20)));
			return false;
		}

		if(attacking == null) {
			factionOfficer.sendMessage(new TextComponentString("You are not in a faction"));
			return false;
		}

		if(!attacking.IsPlayerRoleInFaction(factionOfficer.getUniqueID(), Faction.Role.OFFICER)) {
			factionOfficer.sendMessage(new TextComponentString("You are not an officer of this faction"));
			return false;
		}

		// TODO: Verify there aren't existing alliances

		TileEntitySiegeCamp siegeTE = (TileEntitySiegeCamp) WarForgeMod.proxy.GetTile(siegeCampPos);
		DimChunkPos defendingChunk = siegeCampPos.ToChunkPos().Offset(direction, 1);
		UUID defendingFactionID = mClaims.get(defendingChunk);
		Faction defending = GetFaction(defendingFactionID);

		if(defending == null) {
			factionOfficer.sendMessage(new TextComponentString("Could not find a target faction at that poisition"));
			return false;
		}

		DimBlockPos defendingPos = defending.GetSpecificPosForClaim(defendingChunk);

		if(IsSiegeInProgress(defendingPos.ToChunkPos())) {
			factionOfficer.sendMessage(new TextComponentString("That position is already being sieged"));
			return false;
		}

		if (conqueredChunks.get(defendingPos.ToChunkPos()) != null) {
			factionOfficer.sendMessage(new TextComponentTranslation("warforge.info.chunk_is_conquered",
					defending.mName, WarForgeMod.formatTime(conqueredChunks.get(defendingPos.ToChunkPos()).getRight())));
			return false;
		}

		Siege siege = new Siege(attacking.mUUID, defendingFactionID, defendingPos);
		siege.mAttackingSiegeCamps.add(siegeCampPos);

		RequestPlaceFlag((EntityPlayerMP)factionOfficer, siegeCampPos);
		mSieges.put(defendingChunk, siege);
		siegeTE.setSiegeTarget(defendingPos);
		siege.Start();

		attacking.setLastSiegeTimestamp(WarForgeMod.ServerTick);

		return true;
    }

	public void EndSiege(DimBlockPos getPos) {
		Siege siege = mSieges.get(getPos.ToChunkPos());
		if(siege != null) {
			siege.OnCancelled();
			mSieges.remove(getPos.ToChunkPos());
		}
	}

	public boolean RequestOpClaim(EntityPlayer op, DimChunkPos pos, UUID factionID) {
		Faction zone = GetFaction(factionID);
		if(zone == null) {
			op.sendMessage(new TextComponentString("Could not find that faction"));
			return false;
		}

		UUID existingClaim = GetClaim(pos);
		if(!existingClaim.equals(Faction.NULL)) {
			op.sendMessage(new TextComponentString("There is already a claim here"));
			return false;
		}

		// Place a bedrock tile entity at 0,0,0 chunk coords
		// This might look a bit dodge in End. It's only for admin claims though
		DimBlockPos tePos = new DimBlockPos(pos.mDim, pos.getXStart(), 0, pos.getZStart());
		op.world.setBlockState(tePos.ToRegularPos(), WarForgeMod.CONTENT.adminClaimBlock.getDefaultState());
		TileEntity te = op.world.getTileEntity(tePos.ToRegularPos());
		if(te == null || !(te instanceof IClaim)) {
			op.sendMessage(new TextComponentString("Placing admin claim block failed"));
			return false;
		}

		OnNonCitadelClaimPlaced((IClaim)te, zone);

		op.sendMessage(new TextComponentString("Claimed " + pos + " for faction " + zone.mName));

		return true;
	}
    
    public void SendSiegeInfoToNearby(DimChunkPos siegePos)
    {
		Siege siege = mSieges.get(siegePos);
		if(siege != null) {
			SiegeCampProgressInfo info = siege.GetSiegeInfo();
			if(info != null) {
				PacketSiegeCampProgressUpdate packet = new PacketSiegeCampProgressUpdate();
				packet.mInfo = info;
				WarForgeMod.NETWORK.sendToAllAround(packet, siegePos.x * 16, 128d, siegePos.z * 16, WarForgeConfig.SIEGE_INFO_RADIUS + 128f, siegePos.mDim);
			}
		}
    }

	public void SendAllSiegeInfoToNearby() {
		for(HashMap.Entry<DimChunkPos, Siege> kvp : WarForgeMod.FACTIONS.mSieges.entrySet()) {
			kvp.getValue().CalculateBasePower();

			SendSiegeInfoToNearby(kvp.getKey());
		}

	}

	// returns arraylist with nulls for invalid claim directions, with positions in their horizontal ordering
	public int GetAdjacentClaims(UUID excludingFaction, DimBlockPos pos, ArrayList<DimChunkPos> positions) {
		// allows setting in horizontal index order, which is useful in some cases
		if (positions.size() < 4) positions = new ArrayList<>(Arrays.asList(new DimChunkPos[4]));
		int numValidTargets = 0;

		// checks all horizontal directions
		for (EnumFacing facing : EnumFacing.HORIZONTALS) {
			DimChunkPos targetChunkPos = pos.ToChunkPos().Offset(facing, 1);
			UUID targetID = GetClaim(targetChunkPos);
			if (!IsClaimed(excludingFaction, targetChunkPos)) continue; // also screens for dimension
			int targetY = GetFaction(targetID).GetSpecificPosForClaim(targetChunkPos).getY();
			if (pos.getY() < targetY - WarForgeConfig.VERTICAL_SIEGE_DIST || pos.getY() > targetY + WarForgeConfig.VERTICAL_SIEGE_DIST) continue;
			positions.set(facing.getHorizontalIndex(), targetChunkPos);
			++numValidTargets;
		}

		return numValidTargets;
	}

	public boolean IsClaimed(UUID excludingFaction, DimChunkPos pos) {
		UUID factionID = GetClaim(pos);
		return factionID != null && !factionID.equals(excludingFaction) && !factionID.equals(Faction.NULL);
	}

	public boolean RequestRemoveClaim(EntityPlayerMP player, DimBlockPos pos) {
		UUID factionID = GetClaim(pos);
		Faction faction = GetFaction(factionID);
		if(factionID.equals(Faction.NULL) || faction == null) {
			player.sendMessage(new TextComponentString("Could not find a claim in that location"));
			return false;
		}

		if(pos.equals(faction.mCitadelPos)) {
			player.sendMessage(new TextComponentString("Can't remove the citadel without disbanding the faction"));
			return false;
		}

		if(!WarForgeMod.IsOp(player) && !faction.IsPlayerRoleInFaction(player.getUniqueID(), Role.OFFICER)) {
			player.sendMessage(new TextComponentString("You are not an officer of the faction"));
			return false;
		}

		for(HashMap.Entry<DimChunkPos, Siege> siege : mSieges.entrySet()) {
			if(siege.getKey().equals(pos.ToChunkPos())) {
				player.sendMessage(new TextComponentString("This claim is currently under siege"));
				return false;
			}

			if(siege.getValue().mAttackingSiegeCamps.contains(pos)) {
				player.sendMessage(new TextComponentString("This siege camp is currently in a siege"));
				return false;
			}
		}

		faction.OnClaimLost(pos);
		mClaims.remove(pos.ToChunkPos());
		faction.MessageAll(new TextComponentString(player.getName() + " unclaimed " + pos.ToFancyString()));


		return true;
	}

	public boolean RequestPlaceFlag(EntityPlayerMP player, DimBlockPos pos) {
		Faction faction = GetFactionOfPlayer(player.getUniqueID());
		if(faction == null) {
			player.sendMessage(new TextComponentString("You are not in a faction"));
			return false;
		}

		SendAllSiegeInfoToNearby();

		return faction.PlaceFlag(player, pos);
	}

	public boolean RequestSetFactionColour(EntityPlayerMP player, int colour) {
		Faction faction = GetFactionOfPlayer(player.getUniqueID());
		if(faction == null) {
			player.sendMessage(new TextComponentString("You are not in a faction"));
			return false;
		}

		if(!faction.IsPlayerRoleInFaction(player.getUniqueID(), Role.LEADER)) {
			player.sendMessage(new TextComponentString("You are not the faction leader"));
			return false;
		}

		faction.SetColour(colour);
		for(World world : WarForgeMod.MC_SERVER.worlds) {
			for(TileEntity te : world.loadedTileEntityList) {
				if(te instanceof IClaim) {
					if(((IClaim) te).GetFaction().equals(faction.mUUID)) {
						((IClaim) te).UpdateColour(colour);
					}
				}
			}
		}

		return true;

	}


	public boolean RequestMoveCitadel(EntityPlayerMP player, DimBlockPos pos) {
		Faction faction = GetFactionOfPlayer(player.getUniqueID());
		if(faction == null) {
			player.sendMessage(new TextComponentString("You are not in a faction"));
			return false;
		}

		if(!faction.IsPlayerRoleInFaction(player.getUniqueID(), Role.LEADER)) {
			player.sendMessage(new TextComponentString("You are not the faction leader"));
			return false;
		}

		for(HashMap.Entry<DimChunkPos, Siege> kvp : mSieges.entrySet()) {
			if(kvp.getValue().mDefendingFaction.equals(faction.mUUID)) {
				player.sendMessage(new TextComponentString("There is an ongoing siege against your faction"));
				return false;
			}
		}


		if(faction.mDaysUntilCitadelMoveAvailable > 0) {
			player.sendMessage(new TextComponentString("You must wait an additional " + faction.mDaysUntilCitadelMoveAvailable + " days until you can move your citadel"));
			return false;
		}

		// Set new citadel
		WarForgeMod.MC_SERVER.getWorld(pos.mDim).setBlockState(pos.ToRegularPos(), WarForgeMod.CONTENT.citadelBlock.getDefaultState());
		TileEntityCitadel newCitadel = (TileEntityCitadel)WarForgeMod.MC_SERVER.getWorld(pos.mDim).getTileEntity(pos.ToRegularPos());

		// Convert old citadel to basic claim
		WarForgeMod.MC_SERVER.getWorld(faction.mCitadelPos.mDim).setBlockState(faction.mCitadelPos.ToRegularPos(), WarForgeMod.CONTENT.basicClaimBlock.getDefaultState());
		TileEntityClaim newClaim = (TileEntityClaim)WarForgeMod.MC_SERVER.getWorld(faction.mCitadelPos.mDim).getTileEntity(faction.mCitadelPos.ToRegularPos());

		// Update pos
		faction.mCitadelPos = pos;
		newCitadel.OnServerSetFaction(faction);
		newClaim.OnServerSetFaction(faction);

		WarForgeMod.INSTANCE.MessageAll(new TextComponentString(faction.mName + " moved their citadel"), true);

		faction.mDaysUntilCitadelMoveAvailable = WarForgeConfig.CITADEL_MOVE_NUM_DAYS;

		return true;
	}
    
    public void ReadFromNBT(NBTTagCompound tags) {
		mFactions.clear();
		mClaims.clear();
		mSieges.clear();

		InitNeutralZones();

		NBTTagList list = tags.getTagList("factions", 10); // Compound Tag
		for(NBTBase baseTag : list) {
			NBTTagCompound factionTags = ((NBTTagCompound)baseTag);
			UUID uuid = factionTags.getUniqueId("id");
			Faction faction;


			if(uuid.equals(SAFE_ZONE_ID)) {
				faction = SAFE_ZONE;
			} else if(uuid.equals(WAR_ZONE_ID)) {
				faction = WAR_ZONE;
			} else {
				faction = new Faction();
				faction.mUUID = uuid;
				mFactions.put(uuid, faction);
				WarForgeMod.LEADERBOARD.RegisterFaction(faction);
			}

			faction.ReadFromNBT(factionTags);

			// Also populate the DimChunkPos lookup table
			for(DimBlockPos blockPos : faction.mClaims.keySet()) {
				mClaims.put(blockPos.ToChunkPos(), uuid);
			}
		}

		list = tags.getTagList("sieges", 10); // Compound Tag
		for(NBTBase baseTag : list) {
			NBTTagCompound siegeTags = ((NBTTagCompound)baseTag);
			int dim = siegeTags.getInteger("dim");
			int x = siegeTags.getInteger("x");
			int z = siegeTags.getInteger("z");

			Siege siege = new Siege();
			siege.ReadFromNBT(siegeTags);

			mSieges.put(new DimChunkPos(dim, x, z), siege);
		}

		readConqueredChunks(tags);
	}

	private void readConqueredChunks(NBTTagCompound tags) {
		conqueredChunks = new HashMap<>();
		NBTTagCompound conqueredChunksDataList = tags.getCompoundTag("conqueredChunks");

		// 11 is type id for int array
		int index = 0;
		while (true) {
			NBTTagList keyValPair = conqueredChunksDataList.getTagList(new StringBuilder("conqueredChunk_").append(index).toString(), 11);
			if (keyValPair.isEmpty()) break; // exit once invalid (empty, since getTagList never returns null) is found, as it is assumed this is the first non-existent/ invalid index
			int[] dimInfo = keyValPair.getIntArrayAt(0);
			DimChunkPos chunkPosKey = new DimChunkPos(dimInfo[0], dimInfo[1], dimInfo[2]);
			UUID factionID = BEIntArrayToUUID(keyValPair.getIntArrayAt(1));

			conqueredChunks.put(chunkPosKey, new ObjectIntPair<>(factionID, keyValPair.getIntArrayAt(2)[0]));
			++index;
		}
	}

	public void WriteToNBT(NBTTagCompound tags) {
		NBTTagList factionList = new NBTTagList();
		for(HashMap.Entry<UUID, Faction> kvp : mFactions.entrySet()) {
			NBTTagCompound factionTags = new NBTTagCompound();
			factionTags.setUniqueId("id", kvp.getKey());
			kvp.getValue().WriteToNBT(factionTags);
			factionList.appendTag(factionTags);
		}

		tags.setTag("factions", factionList);

		NBTTagList siegeList = new NBTTagList();
		for(HashMap.Entry<DimChunkPos, Siege> kvp : mSieges.entrySet()) {
			NBTTagCompound siegeTags = new NBTTagCompound();
			siegeTags.setInteger("dim", kvp.getKey().mDim);
			siegeTags.setInteger("x", kvp.getKey().x);
			siegeTags.setInteger("z", kvp.getKey().z);
			kvp.getValue().WriteToNBT(siegeTags);
			siegeList.appendTag(siegeTags);
		}

		tags.setTag("sieges", siegeList);

		writeConqueredChunks(tags);
	}

	private void writeConqueredChunks(NBTTagCompound tags) {
		NBTTagCompound conqueredChunksDataList = new NBTTagCompound();
		int index = 0;
		for (DimChunkPos chunkPosKey : conqueredChunks.keySet()) {
			// values in tag list must all be same, so all types are changed to use int arrays
			NBTTagList keyValPair = new NBTTagList();
			keyValPair.appendTag(new NBTTagIntArray(new int[] {chunkPosKey.mDim, chunkPosKey.x, chunkPosKey.z}));
			keyValPair.appendTag(new NBTTagIntArray(UUIDToBEIntArray(conqueredChunks.get(chunkPosKey).getLeft())));
			keyValPair.appendTag(new NBTTagIntArray(new int[] {conqueredChunks.get(chunkPosKey).getRight()}));

			conqueredChunksDataList.setTag(new StringBuilder("conqueredChunk_").append(index).toString(), keyValPair);

			++index;
		}

		tags.setTag("conqueredChunks", conqueredChunksDataList);
	}

	// returns big endian (decreasing sig/ biggest sig first) array
	public static int[] UUIDToBEIntArray(UUID uniqueID) {
		return new int[] {
				(int) (uniqueID.getMostSignificantBits() >>> 32),
				(int) uniqueID.getMostSignificantBits(),
				(int) (uniqueID.getLeastSignificantBits() >>> 32),
				(int) uniqueID.getLeastSignificantBits()
		};
	}

	public static UUID BEIntArrayToUUID(int[] bigEndianArray) {
		// FUCKING SIGN EXTENSION
		return new UUID(
				((bigEndianArray[0] & 0xFFFF_FFFFL) << 32) | (bigEndianArray[1] & 0xFFFF_FFFFL),
				((bigEndianArray[2] & 0xFFFF_FFFFL) << 32) | (bigEndianArray[3] & 0xFFFF_FFFFL)
		);
	}

	public void OpResetFlagCooldowns() {
		for(HashMap.Entry<UUID, Faction> kvp : mFactions.entrySet()) {
			for(HashMap.Entry<UUID, PlayerData> pDataKVP : kvp.getValue().mMembers.entrySet()) {
				//pDataKVP.getValue().mHasMovedFlagToday = false;
				pDataKVP.getValue().mMoveFlagCooldown = 0;
			}
		}
	}
}
