package com.flansmod.warforge.common.blocks;

import java.util.Objects;
import java.util.UUID;

import com.flansmod.warforge.common.DimBlockPos;
import com.flansmod.warforge.common.DimChunkPos;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.network.PacketSiegeCampProgressUpdate;
import com.flansmod.warforge.common.network.SiegeCampProgressInfo;
import com.flansmod.warforge.server.Faction;

import com.flansmod.warforge.server.Siege;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;

public class TileEntitySiegeCamp extends TileEntityClaim implements ITickable
{
	private UUID mPlacer = Faction.NULL;
	private DimBlockPos mSiegeTarget = null;
	private final boolean doCheckPerTick;
	private int tickTimer = 0;
	private byte siegeStatus; // -1 = invalidated, 0 = hasnt started, 1 = in progress, 2 = failed, 3 = succeeded
	private int abandonedSiegeTickTimer = 0;

	public TileEntitySiegeCamp() {
		doCheckPerTick = WarForgeConfig.ATTACKER_DESERTION_TIMER == 0;
		siegeStatus = 0;
	}

	public void OnPlacedBy(EntityLivingBase placer) {
		WarForgeMod.LOGGER.always().log("OnPlaced by with placer: " + placer + " and faction of " + mFactionUUID);
		mPlacer = placer.getUniqueID();

        /*
		// check for claim in every direction
		EnumFacing dir = null;
		while (dir != placer.getHorizontalFacing()) {
			dir = placer.getHorizontalFacing();
			UUID existingClaim = WarForgeMod.FACTIONS.GetClaim(new DimChunkPos(world.provider.getDimension(), pos).Offset(dir, 1));
			WarForgeMod.LOGGER.always().log("onPlacedBy check with placer: " + placer + ", currDir: " + dir + ", UUID of: " + existingClaim);
			if (existingClaim != null) {
				mSiegeTarget = WarForgeMod.FACTIONS.GetFaction(existingClaim).mCitadelPos;
				break;
			}

			// wrapping around is done automatically
			dir = EnumFacing.byHorizontalIndex(EnumFacing.valueOf(dir.getName()).getHorizontalIndex() + 1);
		}
		 */

	}

	// called whenever block should be destroyed
	public void onDestroyed() {
		// will fail the siege if it is on going, or just do the destruction actions if finished or never started
		if (siegeStatus == 1) failSiege();
		else destroy();
	}

	public void destroy() {
		WarForgeMod.LOGGER.always().log("Destroying siege block at " + getPos() + " with chunkPos: " + GetPos().ToChunkPos());
		world.destroyBlock(getPos(), true); // destroy block of failed siege
		siegeStatus = -1; // invalidate before destruction do that this code is not run in an infinite loop in getUpdateTag
		world.markBlockRangeForRenderUpdate(pos, pos);
		world.notifyBlockUpdate(getPos(), world.getBlockState(getPos()), world.getBlockState(getPos()), 3); // 2 is bit mask apparently indicating send to client
		//world.scheduleBlockUpdate(pos, this.getBlockType(), 0, 0);
		markDirty();

		WarForgeMod.FACTIONS.getClaims().remove(GetPos().ToChunkPos());
		WarForgeMod.FACTIONS.GetFaction(mFactionUUID).OnClaimLost(GetPos());
		WarForgeMod.LOGGER.always().log("mClaims: " + WarForgeMod.FACTIONS.getClaims());
		world.removeTileEntity(getPos());
	}

	@Override
	public int GetDefenceStrength() { return 0; }

	@Override
	public int GetSupportStrength() { return 0; }

	@Override
	public int GetAttackStrength() { return WarForgeConfig.ATTACK_STRENGTH_SIEGE_CAMP; }

	public void setSiegeTarget(DimBlockPos siegeTarget) {
		mSiegeTarget = siegeTarget;
		siegeStatus = 1;
	}

	@Override
	public boolean CanBeSieged() { return false; }

	// returns true to invalidate forcefull; run when chunk wants to replace dat
	@Override
	public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
		WarForgeMod.LOGGER.always().log("shouldRefresh has been called in " + world + " at pos " + pos + " with oldSate of " + oldState + " and newState of " + newState);
		return super.shouldRefresh(world, pos, oldState, newState);
	}

	// called when player flag is removed, but not necessarily when siege ends?
	@Override
	public void OnServerRemovePlayerFlag(String playerName) {
		super.OnServerRemovePlayerFlag(playerName);

		if(mPlayerFlags.size() == 0) endSiegePrepped(); // if siege block runs out of player flags, siege fails
	}

	// separated from overridden method to allow for testing and to make functionality apparent
	public void endSiegePrepped() {
		WarForgeMod.LOGGER.always().log("ending siege from prep");
		siegeStatus = 2; // siege is failed as all player flags from placing group are lost
		concludeSiege();
	}

	// forces siege to end
	public void failSiege() {
		WarForgeMod.LOGGER.always().log("ending siege as failure");
		siegeStatus = 2;
		concludeSiege();
	}

	public void concludeSiege(byte siegeStatus) {
		this.siegeStatus = siegeStatus;
		concludeSiege();
	}

	// kills siege block and tile entity
	private void concludeSiege() {
		// do any client side logic, then return
		if (world.isRemote) {
			return;
		}

		WarForgeMod.LOGGER.always().log("Concluding Siege with siegeStatus: " + siegeStatus);

		// update siege info and notify all nearby
		Siege siege = WarForgeMod.FACTIONS.getSieges().get(mSiegeTarget.ToChunkPos());
		if(siege != null) {
			SiegeCampProgressInfo info = siege.GetSiegeInfo();
			info.mProgress = siegeStatus == 2 ? -5 : info.mCompletionPoint;
			PacketSiegeCampProgressUpdate packet = new PacketSiegeCampProgressUpdate();
			packet.mInfo = info;
			WarForgeMod.LOGGER.always().log("Sending siege packet update with siege: " + siege + ", progress: " + info.mProgress + ", packetInfo progress of: " + packet.mInfo.mProgress + ", getAttacking: " + getAttacking() + ", getDefending: " + getDefending());
			for (EntityPlayer attacker : getAttacking().getPlayers(entityPlayer -> true)) {
				if (attacker != null) WarForgeMod.NETWORK.sendTo(packet, (EntityPlayerMP) attacker);
			}
			for (EntityPlayer defender : getDefending().getPlayers(entityPlayer -> true)) {
				if (defender != null) WarForgeMod.NETWORK.sendTo(packet, (EntityPlayerMP) defender);
			}
		}

		// siege may not exist in server's record, leading to crash loop. This prevents the loop. Removes siege after info indicating update is done
		try {
			WarForgeMod.FACTIONS.getSieges().get(mSiegeTarget.ToChunkPos()).setAttackProgress(siegeStatus == 2 ? -5 : WarForgeMod.FACTIONS.getSieges().get(mSiegeTarget.ToChunkPos()).GetAttackSuccessThreshold()); // ends siege
			WarForgeMod.FACTIONS.handleCompletedSiege(mSiegeTarget.ToChunkPos()); // performs check on completed sieges without invoking checks on unrelated sieges
			WarForgeMod.FACTIONS.EndSiege(GetPos());
			WarForgeMod.LOGGER.always().log("mClaims: " + WarForgeMod.FACTIONS.getClaims());
		} catch (Exception e) {
			WarForgeMod.LOGGER.atError().log("Got exception when attempting to force end siege of: " + e + " with siegeTarget of: " + mSiegeTarget + " and pos of: " + getPos());
		}

		mSiegeTarget = null;
		if (siegeStatus == 2) destroy();
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		nbt.setUniqueId("placer", mPlacer);
		nbt.setBoolean("started", mSiegeTarget != null);
		nbt.setByte("siegeStatus", siegeStatus);
		nbt.setInteger("tickTimer", tickTimer);
		if(mSiegeTarget != null) {
			nbt.setInteger("attackDim", mSiegeTarget.mDim);
			nbt.setInteger("attackX", mSiegeTarget.getX());
			nbt.setInteger("attackY", mSiegeTarget.getY());
			nbt.setInteger("attackZ", mSiegeTarget.getZ());
		}

		return nbt;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		mPlacer = nbt.getUniqueId("placer");

		boolean started = nbt.getBoolean("started");

		siegeStatus = nbt.getByte("siegeStatus");
		tickTimer = nbt.getInteger("tickTimer");
		if(started) {
			mSiegeTarget = new DimBlockPos(
					nbt.getInteger("attackDim"),
					nbt.getInteger("attackX"),
					nbt.getInteger("attackY"),
					nbt.getInteger("attackZ"));
		} else mSiegeTarget = null;

		if(FMLCommonHandler.instance().getEffectiveSide() == Side.SERVER) {
			Faction faction = WarForgeMod.FACTIONS.GetFaction(mFactionUUID);
			if(!mFactionUUID.equals(Faction.NULL) && faction == null) {
				WarForgeMod.LOGGER.error("Faction " + mFactionUUID + " could not be found for citadel at " + pos);
				//world.setBlockState(getPos(), Blocks.AIR.getDefaultState());
			}
			if(faction != null) {
				mColour = faction.mColour;
				mFactionName = faction.mName;
			}
		} else {
			WarForgeMod.LOGGER.error("Loaded TileEntity from NBT on client?");
		}
	}

	// allows client side to also receive block events (not used currently)
	@Override
	public boolean receiveClientEvent(int id, int type) {
		return true;
	}

	// called to create update data packet nbt info
	/*
	@Override
	public NBTTagCompound getUpdateTag() {
		// You have to get parent tags so that x, y, z are added.
		NBTTagCompound tags = super.getUpdateTag();

		// Custom partial nbt write method
		WarForgeMod.LOGGER.always().log("setting siegeStatus with siegeStatus: " + siegeStatus + ", blockstate at pos: " +  world.getBlockState(getPos()));
		tags.setByte("siegeStatus", siegeStatus);

		// do destruction only after relevant info has been sent
		if (siegeStatus == 2)  {
			destroy();
		}

		return tags;
	}

	@Override
	public void onDataPacket(net.minecraft.network.NetworkManager net, SPacketUpdateTileEntity packet) {
		super.onDataPacket(net, packet);
		WarForgeMod.LOGGER.always().log("Got data packet with passed status of: " + packet.getNbtCompound().getByte("siegeStatus") + ", curr status: " + siegeStatus);
		byte prevSiegeStatus = siegeStatus;
		siegeStatus = packet.getNbtCompound().getByte("siegeStatus");

		// because 2 and 3 encode failure and success, respectively, we only check for > 1 and equality to 2, assuming that if it is > 1 and not 2, then 3 is the status
		if (world.isRemote && prevSiegeStatus < 2 && siegeStatus > 1) {
			WarForgeMod.LOGGER.always().log("Notified siege is ending; closing gui");
			SiegeCampProgressInfo info = ClientProxy.sSiegeInfo.get(GetPos());
			WarForgeMod.LOGGER.always().log("Info is: " + info + " from map: " + ClientProxy.sSiegeInfo.toString());
			WarForgeMod.LOGGER.always().log("doSiegeFail of: " + siegeStatus + ", with INFO - attacking: " + info.mAttackingName + ", defending: " + info.mDefendingName + ", attack pos: " + info.mAttackingPos + ", defend pos: " + info.mDefendingPos + ", completionPoint: " + info.mCompletionPoint);
			info.mProgress = siegeStatus == 2 ? -5 : info.mCompletionPoint;
			ClientProxy.sSiegeInfo.get(GetPos()).mProgress = siegeStatus == 2 ? -5 : ClientProxy.sSiegeInfo.get(GetPos()).mCompletionPoint; // forcefully indicate to client that siege is over
		}
	}
	 */

	@Override
	public void update() {
		// do not do logic on client
		if (world.isRemote) return;

		// do not do logic with invalid values
		if (mPlacer == Faction.NULL || mSiegeTarget == null) {
			if (System.currentTimeMillis() % 5000L < 100) WarForgeMod.LOGGER.always().log("Doing Update Failed due to null values with mPlacer: " + mPlacer + " mSiegeTarget: " + mSiegeTarget);
			return;
		}

		tickTimer &= 0b01111111_11111111_11111111_11111111; // ensure positive

		// only perform the check every second if the timer is greater than one second, or every tick if an attacker must always be present
		if (doCheckPerTick || tickTimer % 20 == 0) {
			WarForgeMod.LOGGER.always().log("Doing update with mPlacer as " + mPlacer + ", mSiegeTarget: " + mSiegeTarget + ", tickTimer: " + tickTimer + ", doCheckPerTick: " + doCheckPerTick +  ", abandondedSiegeTickTimer: " + abandonedSiegeTickTimer + ", desertionTimer: " + WarForgeConfig.ATTACKER_DESERTION_TIMER);

			// send message to all players on defending team with necessary information to defend every 5 minutes
			if (tickTimer % 6000 == 0) {
				WarForgeMod.LOGGER.always().log("Sending information to player with mSiegeTarget: " + mSiegeTarget);
				messageAllDefenders("warforge.info.siege_defense_info", getPos().toString());
			}

			// logging
			for (EntityPlayer player : world.getPlayers(EntityPlayer.class, Objects::nonNull)) {
				if (player == null) {
					WarForgeMod.LOGGER.always().log("this predicate doesn't work and a null was found");
					continue;
				}
				WarForgeMod.LOGGER.always().log("Player <" + player + ">, isPosInRad of: " + isPlayerInRad(player) + ", isInAttackerFaction of: " + (getFac(player) == null ? "null player faction" : getFac(player).equals(getPlayerFac(mPlacer))));
			}

			// if there are no players in the radius
			if (WarForgeMod.FACTIONS.GetFaction(mFactionUUID).getPlayers(entityPlayer -> isPlayerInRad(entityPlayer)).size() < 1) {
				// end siege if idle timer reaches desertion timer
				if (abandonedSiegeTickTimer >= WarForgeConfig.ATTACKER_DESERTION_TIMER * 20) {
					messageAllAttackers("warforge.info.siege_idle_exceeded_attacker");
					messageAllDefenders("warforge.info.siege_idle_exceeded_defender");
					failSiege();
				} else {
					switch(WarForgeConfig.ATTACKER_DESERTION_TIMER * 20 - abandonedSiegeTickTimer) {
						case 1200:
							messageAllAttackers("warforge.info.siege_abandon_approaching_attacker", 60);
							messageAllDefenders("warforge.info.siege_abandon_approaching_defender", 60);
							break;
						case 200:
							messageAllAttackers("warforge.info.siege_abandon_approaching_attacker", 10);
							messageAllDefenders("warforge.info.siege_abandon_approaching_defender", 10);
							break;
						default:
							break;
					}

					abandonedSiegeTickTimer += doCheckPerTick ? 1 : 20; // increment timer
				}
			} else {
				abandonedSiegeTickTimer = 0; // reset timer if attacker is found
			}
		}

		++tickTimer;
	}

	private boolean isPlayerInRad(EntityPlayer player) {
		DimChunkPos playerChunk = new DimChunkPos(player.dimension, player.getPosition());
		DimChunkPos blockChunk = new DimChunkPos(world.provider.getDimension(), getPos());

		if (playerChunk.mDim == blockChunk.mDim && abs(blockChunk.x - playerChunk.x) < 2 && abs(blockChunk.z - playerChunk.z) < 2) return true;
		return false;
	}

	private void messageAllAttackers(String translateKey, Object... args) {
		WarForgeMod.FACTIONS.GetFaction(mFactionUUID).MessageAll(new TextComponentTranslation(translateKey, args));
	}

	private void messageAllDefenders(String translateKey, Object... args) {
		WarForgeMod.FACTIONS.GetFaction(WarForgeMod.FACTIONS.GetClaim(mSiegeTarget)).MessageAll(new TextComponentTranslation(translateKey, args));
	}

	private Faction getAttacking() {
		return WarForgeMod.FACTIONS.GetFaction(mFactionUUID);
	}

	private Faction getDefending() {
		return WarForgeMod.FACTIONS.GetFaction(WarForgeMod.FACTIONS.getSieges().get(mSiegeTarget.ToChunkPos()).mDefendingFaction);
	}

	private <T extends EntityLivingBase> Faction getFac(T player) {
		return WarForgeMod.FACTIONS.GetFactionOfPlayer(player.getUniqueID());
	}

	private Faction getPlayerFac(UUID playerID) {
		return WarForgeMod.FACTIONS.GetFactionOfPlayer(playerID);
	}

	private int abs(int n) {
		if (n < 0) return n * -1;
		return n;
	}
}
