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
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;

public class TileEntitySiegeCamp extends TileEntityClaim implements ITickable
{
	private UUID mPlacer = Faction.NULL;
	private DimBlockPos mSiegeTarget = null;
	private Faction defenders = null;
	private boolean doCheckPerTick = WarForgeConfig.ATTACKER_DESERTION_TIMER == 0 || WarForgeConfig.DEFENDER_DESERTION_TIMER == 0;
	private int tickTimer = 0;
	private SiegeStatus siegeStatus = SiegeStatus.IDLING;
	private int attackerAbandonTickTimer = 0;
	private int defenderAbandonTickTimer = 0;

	private long defenderOfflineTimerMs = 0;
	private long previousTimestamp = WarForgeMod.currTickTimestamp;
	private int largestSeenDefenderCount;
	private int lastSeenDefenderCount;

	// tile entity constructor should be default

	public void OnPlacedBy(EntityLivingBase placer) {
		mPlacer = placer.getUniqueID();
	}

	@Override
	public int GetDefenceStrength() { return 0; }

	@Override
	public int GetSupportStrength() { return 0; }

	@Override
	public int GetAttackStrength() { return WarForgeConfig.ATTACK_STRENGTH_SIEGE_CAMP; }

	public int getAttackerAbandonTickTimer() {
		return attackerAbandonTickTimer;
	}

	public int getDefenderAbandonTickTimer() {
		return defenderAbandonTickTimer;
	}

	public long getDefenderOfflineTimer() {
		return defenderOfflineTimerMs;
	}

	public void setSiegeTarget(DimBlockPos siegeTarget) {
		mSiegeTarget = siegeTarget;
		defenders = getDefenders(mSiegeTarget);
		largestSeenDefenderCount = defenders.onlinePlayerCount;
		siegeStatus = SiegeStatus.ACTIVE;
	}

	private Faction getDefenders(DimBlockPos siegeTarget) {
		return WarForgeMod.FACTIONS.GetFaction(WarForgeMod.FACTIONS.GetClaim(siegeTarget));
	}

	@Override
	public boolean CanBeSieged() { return false; }

	// returns true to invalidate forceful; run when chunk wants to replace dat; Chunk shouldnt want to invalidate te until after normal procedure has been run
	@Override
	public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
		onDestroyed(); // in case block is somehow destroyed outside of normal procedures
		return super.shouldRefresh(world, pos, oldState, newState);
	}

	// called when player flag is removed, but not necessarily when siege ends?
	@Override
	public void OnServerRemovePlayerFlag(String playerName) {
		super.OnServerRemovePlayerFlag(playerName);

        // can cause crash as placing a siege block, not selecting a target, and then placing your flag at another siege block will call this
		if(mPlayerFlags.size() == 0 && siegeStatus == SiegeStatus.ACTIVE && mSiegeTarget != null) {
            endSiegePrepped(); // if siege block runs out of player flags, siege fails
        }
	}

	private enum SiegeStatus {
		IDLING,
		ACTIVE,
		FAILED,
		PASSED,
		FAILED_CLEANUP,
		PASSED_CLEANUP;

		public boolean isCleanup() { return this.ordinal() >= FAILED_CLEANUP.ordinal(); }

		public boolean isPassed() { return this.ordinal() == 3 || this.ordinal() == 5; }
		public boolean isFailed() { return this.ordinal() == 2 || this.ordinal() == 4; }
	}

	// separated from overridden method to allow for testing and to make functionality apparent
	public void endSiegePrepped() {
		siegeStatus = SiegeStatus.FAILED; // siege is failed as all player flags from placing group are lost
		concludeSiege();
	}

	// forces siege to end as failure
	public void failSiege() {
		siegeStatus = SiegeStatus.FAILED;
		concludeSiege();
	}

	public void cleanupFailedSiege() {
		siegeStatus = SiegeStatus.FAILED_CLEANUP;
		concludeSiege();
	}

	// sets siege to be successful
	public void passSiege() {
		siegeStatus = SiegeStatus.PASSED;
		concludeSiege();
	}

	public void cleanupPassedSiege() {
		siegeStatus = SiegeStatus.PASSED_CLEANUP;
		concludeSiege();
	}

	// kills siege block and tile entity
	private void concludeSiege() {
		// do any client side logic, then return
		if (world.isRemote) {
			return;
		}

		// only modify external information if not performing cleanup on this tile entity
		if (!siegeStatus.isCleanup()) {
			// update siege info and notify all nearby
			Siege siege = WarForgeMod.FACTIONS.getSieges().get(mSiegeTarget.ToChunkPos());
			if(siege != null) {
				SiegeCampProgressInfo info = siege.GetSiegeInfo();
				info.mProgress = siegeStatus.isFailed() ? -5 : info.mCompletionPoint;
				PacketSiegeCampProgressUpdate packet = new PacketSiegeCampProgressUpdate();
				packet.mInfo = info;

				for (EntityPlayer attacker : getAttacking().getOnlinePlayers(Objects::nonNull)) WarForgeMod.NETWORK.sendTo(packet, (EntityPlayerMP) attacker);
				for (EntityPlayer defender : defenders.getOnlinePlayers(Objects::nonNull)) WarForgeMod.NETWORK.sendTo(packet, (EntityPlayerMP) defender);
			}

			// attempt to actually modify siege information, now that all nearby have been updated
			try {
				WarForgeMod.FACTIONS.getSieges().get(mSiegeTarget.ToChunkPos()).setAttackProgress(siegeStatus.isFailed() ? -5 : WarForgeMod.FACTIONS.getSieges().get(mSiegeTarget.ToChunkPos()).GetAttackSuccessThreshold()); // ends siege
				WarForgeMod.FACTIONS.handleCompletedSiege(mSiegeTarget.ToChunkPos(), false); // performs check on completed sieges without invoking checks on unrelated sieges
			} catch (Exception e) {
				WarForgeMod.LOGGER.atError().log("Got exception when attempting to force end siege of: " + e + " with siegeTarget of: " + mSiegeTarget + " and pos of: " + getPos());
				e.printStackTrace();
			}

			for (DimBlockPos siegeCampPos : siege.mAttackingSiegeCamps) {
				if (siegeCampPos == null || getPos().equals(siegeCampPos.ToRegularPos())) continue;

				TileEntity siegeCamp = world.getTileEntity(siegeCampPos);
				if (!(siegeCamp instanceof TileEntitySiegeCamp)) continue;

				if (siegeStatus.isFailed()) ((TileEntitySiegeCamp) siegeCamp).cleanupFailedSiege();
				else if (siegeStatus.isPassed()) ((TileEntitySiegeCamp) siegeCamp).cleanupPassedSiege();
			}
		}

		mSiegeTarget = null;
		defenders = null;
		destroy();
	}

	// called whenever block should be destroyed
	public void onDestroyed() {
		// will fail the siege if it is on going, or just do the destruction actions if finished or never started
		if (siegeStatus == SiegeStatus.ACTIVE) failSiege();
		else destroy();
	}

	public void destroy() {
		IBlockState oldState = world.getBlockState(getPos());
		world.markBlockRangeForRenderUpdate(pos, pos);
		world.notifyBlockUpdate(getPos(), oldState, Blocks.AIR.getDefaultState(), 3); // 2 is bit mask apparently indicating send to client
		world.scheduleBlockUpdate(pos, this.getBlockType(), 0, 0);
		markDirty();

		world.destroyBlock(getPos(), true); // destroy block of failed siege (gets rid of tile-entity
		world.removeTileEntity(getPos());
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
		if (mPlacer == Faction.NULL || mSiegeTarget == null || defenders == null) return;

		tickTimer &= 0b01111111_11111111_11111111_11111111; // ensure positive

		// only perform the check every second if the timer is greater than one second, or every tick if an attacker must always be present
		if (doCheckPerTick || tickTimer % 20 == 0) {
			// send message to all players on defending team with necessary information to defend every 5 minutes
			if (tickTimer % 6000 == 0) {
				messageAllDefenders("warforge.info.siege_defense_info", new DimBlockPos(world.provider.getDimension(), getPos()).ToFancyString());
			}

			// --- ATTACKER HANDLING ---

			// if there are no players in the radius
			if (WarForgeMod.FACTIONS.GetFaction(mFactionUUID).getOnlinePlayers(this::isPlayerInWarzone).size() < 1) {
				if (handleDesertion(true)) return; // cancel update if siege concludes
			} else {
				// stops at 0 and decrements gradually to stop attackers from popping into and out of warzone
				int decrementedAbandonedTimer = attackerAbandonTickTimer - (doCheckPerTick ? 1 : 20);
				if (decrementedAbandonedTimer > 0) {
					attackerAbandonTickTimer -= doCheckPerTick ? 1 : 20; // decrement timer if attacker is found
				} else {
					// if going to overshoot/ hit zero and not already 0
					if (attackerAbandonTickTimer != 0) {
						getAttacking().MessageAll(new TextComponentString("Your faction's [" + getAttacking().mName + "] siege abandon timer is now 0."));
						attackerAbandonTickTimer = 0;
					}
				}

			}

			// --- DEFENDER HANDLING ---

			if (lastSeenDefenderCount == 0 && defenders.onlinePlayerCount > 0 && defenderOfflineTimerMs > 0) {
				defenders.MessageAll(new TextComponentString("Your faction [" + defenders.mName + "] has an offline timer of " + WarForgeMod.formatTime(defenderOfflineTimerMs) + " for the siege camp at " + getPos()));
			}

			lastSeenDefenderCount = defenders.onlinePlayerCount;

			if (defenders.onlinePlayerCount > largestSeenDefenderCount) largestSeenDefenderCount = defenders.onlinePlayerCount; // update largest number of defenders seen
			int numActiveDefenders = defenders.getOnlinePlayers(entityPlayer -> isPlayerInRadius(entityPlayer, WarForgeConfig.SIEGE_DEFENDER_RADIUS)).size();

			// check if the defenders have quit, and if not check if they are actively defending
			boolean haveDefendersQuit = haveDefendersLiveQuit();
			if (haveDefendersQuit) {
				incrementOfflineTimer(WarForgeMod.currTickTimestamp - previousTimestamp); // if defenders have quit, tick up the offline timer
				if (defenderOfflineTimerMs >= WarForgeConfig.LIVE_QUIT_TIMER) {
					getAttacking().MessageAll(new TextComponentString("The defenders have fled from their posts for " + WarForgeMod.formatTime(defenderOfflineTimerMs)));
					defenderOfflineTimerMs = -1; // mark as having live quit for any future increments of this timer and reset if already have quit
					passSiege(); // end siege as attacker success
					return; // do not update a concluded siege
				}
			} else {
				// decrement offline timer
				decrementOfflineTimer(WarForgeMod.currTickTimestamp - previousTimestamp);
				if (numActiveDefenders < 1) {
					// if no active defenders, handle desertion status and increment timer accordingly
					if (handleDesertion(false)) return; // calls appropriate siege end method
				} else {
					// handle defenders in radius
					// stops at 0 w/ gradual decrement
					int decrementedAbandonedTimer = defenderAbandonTickTimer - (doCheckPerTick ? 1 : 20);
					if (decrementedAbandonedTimer > 0) {
						defenderAbandonTickTimer -= doCheckPerTick ? 1 : 20; // decrement timer if attacker is found
					} else {
						// if going to overshoot/ hit zero and not already 0
						if (defenderAbandonTickTimer != 0) {
							defenders.MessageAll(new TextComponentString("Your faction's [" + getAttacking().mName + "] siege abandon timer is now 0."));
							defenderAbandonTickTimer = 0;
						}
					}

				}
			}

			previousTimestamp = WarForgeMod.currTickTimestamp; // now that update is done, mark time as previous
			// end processing
		}

		++tickTimer;
		markDirty(); // notifies chunk of changes in value
	}

	private boolean haveDefendersLiveQuit() {
		final int MAX_PLAYERS_BEFORE_LIVE_QUIT = WarForgeConfig.MAX_OFFLINE_PLAYER_COUNT_MINIMUM < 0 ?
				-WarForgeConfig.MAX_OFFLINE_PLAYER_COUNT_MINIMUM :
				Math.max((int) (defenders.getMemberCount() * WarForgeConfig.MAX_OFFLINE_PLAYER_PERCENT), WarForgeConfig.MAX_OFFLINE_PLAYER_COUNT_MINIMUM);
		return largestSeenDefenderCount > MAX_PLAYERS_BEFORE_LIVE_QUIT && defenders.onlinePlayerCount < 1;
	}

	// once this exceeds the offline time, it is automatically set to -1, so passing certain amounts need not be considered
	private void incrementOfflineTimer(long msPassed) {
		defenderOfflineTimerMs += hasLiveQuitSiege() ? -msPassed : msPassed;
        /* useful if the offline timer needs to be manually capped, though currentlyw it doesn't
		boolean haveDefendersLiveQuit = hasLiveQuitSiege();
		if (haveDefendersLiveQuit) defenderOfflineTimerMs = Math.max(defenderOfflineTimerMs - msPassed, -WarForgeConfig.QUITTER_FAIL_TIMER);
		else defenderOfflineTimerMs = Math.min(defenderOfflineTimerMs + msPassed, WarForgeConfig.LIVE_QUIT_TIMER);
		 */
	}

	// doesn't overshoot 0
	private void decrementOfflineTimer(long msPassed) {
		// cannot decrease to zero if already 0
		if (defenderOfflineTimerMs == 0) return;

		// check if zero is going to be overshot and round to it
		long newTimer = defenderOfflineTimerMs + (defenderOfflineTimerMs < 0 ? msPassed : -msPassed);
		if (defenderOfflineTimerMs < 0 && newTimer >= 0 || defenderOfflineTimerMs > 0 && newTimer <= 0) {
			defenderOfflineTimerMs = 0;
			defenders.MessageAll(new TextComponentString("Your faction's [" + defenders.mName + "] offline timer is now 0."));
			return;
		}

		defenderOfflineTimerMs = newTimer; // if not going to overshoot, do decrement
	}

	private boolean hasLiveQuitSiege() {
		return defenderOfflineTimerMs < 0;
	}

	private long calcAbsoluteOfflineTimer() {
		return hasLiveQuitSiege() ? -defenderOfflineTimerMs : defenderOfflineTimerMs;
	}

	// returns whether update has been cancelled
	private boolean handleDesertion(boolean isAttackingSide) {
		// end siege if idle timer reaches desertion timer
		int abandonTimer = isAttackingSide ? WarForgeConfig.ATTACKER_DESERTION_TIMER : WarForgeConfig.DEFENDER_DESERTION_TIMER;
		int abandonRadius = isAttackingSide ? WarForgeConfig.SIEGE_ATTACKER_RADIUS : WarForgeConfig.SIEGE_DEFENDER_RADIUS;
		int currentTickTimer = isAttackingSide ? attackerAbandonTickTimer : defenderAbandonTickTimer;

		if (currentTickTimer >= abandonTimer * 20) {
			messageAllAttackers("warforge.info.siege_idle_exceeded_" + (isAttackingSide ? "current" : "opposing"));
			messageAllDefenders("warforge.info.siege_idle_exceeded_" + (isAttackingSide ? "opposing" : "current"));

			// should cancel update; return boolean indicating continuation of update
			if (isAttackingSide) failSiege();
			else passSiege();
			return true;
		} else {
			if (currentTickTimer / 20 == WarForgeConfig.ATTACKER_DESERTION_TIMER >>> 4) {
				if (isAttackingSide) messageAllAttackers("warforge.notification.siege_abandon_" + (isAttackingSide ? "current" : "opposing"), abandonRadius, currentTickTimer / 20, abandonTimer);
				else messageAllDefenders("warforge.notification.siege_abandon_" + (isAttackingSide ? "current" : "opposing"), abandonRadius, currentTickTimer / 20, abandonTimer);
			}

			switch (abandonTimer * 20 - currentTickTimer) {
				case 1200:
					messageAllAttackers("warforge.info.siege_abandon_approaching_" + (isAttackingSide ? "current" : "opposing"), 60, abandonRadius);
					messageAllDefenders("warforge.info.siege_abandon_approaching_" + (isAttackingSide ? "opposing" : "current"), 60, abandonRadius);
					break;
				case 200:
					messageAllAttackers("warforge.info.siege_abandon_approaching_" + (isAttackingSide ? "current" : "opposing"), 10, abandonRadius);
					messageAllDefenders("warforge.info.siege_abandon_approaching_" + (isAttackingSide ? "opposing" : "current"), 10, abandonRadius);
					break;
				default:
					break;
			}

			if (isAttackingSide) attackerAbandonTickTimer += doCheckPerTick ? 1 : 20; // increment timer
			else defenderAbandonTickTimer += doCheckPerTick ? 1 : 20;
		}

		return false;
	}

	private boolean isPlayerInWarzone(EntityPlayer player) {
		DimChunkPos playerChunk = new DimChunkPos(player.dimension, player.getPosition());
		DimChunkPos blockChunk = new DimChunkPos(world.provider.getDimension(), getPos());
		return !player.isDead && Siege.isPlayerInRadius(blockChunk, playerChunk);
	}

	private boolean isPlayerInRadius(EntityPlayer player, int radius) {
		DimChunkPos playerChunk = new DimChunkPos(player.dimension, player.getPosition());
		DimChunkPos blockChunk = new DimChunkPos(world.provider.getDimension(), getPos());
		return !player.isDead && Siege.isPlayerInRadius(blockChunk, playerChunk, radius);
	}

	private void messageAllAttackers(String translateKey, Object... args) {
		Faction attackerFaction = WarForgeMod.FACTIONS.GetFaction(mFactionUUID);
		if (attackerFaction == null) return;
		attackerFaction.MessageAll(new TextComponentTranslation(translateKey, args));
	}

	private void messageAllDefenders(String translateKey, Object... args) {
		if (defenders == null) return;
		defenders.MessageAll(new TextComponentTranslation(translateKey, args));
	}

	private Faction getAttacking() {
		return WarForgeMod.FACTIONS.GetFaction(mFactionUUID);
	}

	private <T extends EntityLivingBase> Faction getFac(T player) {
		return WarForgeMod.FACTIONS.GetFactionOfPlayer(player.getUniqueID());
	}

	private Faction getPlayerFac(UUID playerID) {
		return WarForgeMod.FACTIONS.GetFactionOfPlayer(playerID);
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		nbt.setUniqueId("placer", mPlacer);
		nbt.setBoolean("started", mSiegeTarget != null);
		nbt.setBoolean("doCheckPerTick", doCheckPerTick);
		nbt.setInteger("siegeStatus", siegeStatus.ordinal());
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
		doCheckPerTick = nbt.getBoolean("doCheckPerTick");

		siegeStatus = SiegeStatus.values()[nbt.getInteger("siegeStatus")];
		tickTimer = nbt.getInteger("tickTimer");
		previousTimestamp = WarForgeMod.currTickTimestamp;
		if(started) {
			mSiegeTarget = new DimBlockPos(
					nbt.getInteger("attackDim"),
					nbt.getInteger("attackX"),
					nbt.getInteger("attackY"),
					nbt.getInteger("attackZ"));

			defenders = getDefenders(mSiegeTarget);
			largestSeenDefenderCount = defenders.onlinePlayerCount;
			lastSeenDefenderCount = defenders.onlinePlayerCount;
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
}
