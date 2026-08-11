package com.flansmod.warforge.common.blocks;

import java.util.Objects;
import java.util.UUID;

import com.flansmod.warforge.common.Content;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.network.PacketSiegeCampProgressUpdate;
import com.flansmod.warforge.common.network.SiegeCampProgressInfo;
import com.flansmod.warforge.common.util.TimeHelper;
import com.flansmod.warforge.server.Faction;

import com.flansmod.warforge.server.Siege;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import static com.flansmod.warforge.common.WarForgeConfig.SIEGE_DEFENDER_RADIUS;

public class TileEntitySiegeCamp extends TileEntityClaim
{
	private UUID placer = Faction.nullUuid;
	private DimBlockPos siegeTarget = null;
	private Faction defenders = null;
	private boolean doCheckPerTick = WarForgeConfig.ATTACKER_DESERTION_TIMER == 0 || WarForgeConfig.DEFENDER_DESERTION_TIMER == 0;
	private int tickTimer = 0;
	private SiegeStatus siegeStatus = SiegeStatus.IDLING;
	private int attackerAbandonTickTimer = 0;
	private int defenderAbandonTickTimer = 0;
	private int lastSyncedAbandonTimer = -1;

	private long defenderOfflineTimerMs = 0;
	private long previousTimestamp = WarForgeMod.currTickTimestamp;
	private int largestSeenDefenderCount;
	private int lastSeenDefenderCount;

	public TileEntitySiegeCamp(BlockPos pos, BlockState state) {
		super(Content.TE_SIEGE_CAMP.get(), pos, state);
	}

	public void onPlacedBy(LivingEntity placer) {
		this.placer = placer.getUUID();
        super.onPlacedBy(placer);
	}

	@Override
	public int getDefenceStrength() { return 0; }

	@Override
	public int getSupportStrength() { return 0; }

	@Override
	public int getAttackStrength() { return WarForgeConfig.ATTACK_STRENGTH_SIEGE_CAMP; }

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
		this.siegeTarget = siegeTarget;
		defenders = getDefenders(this.siegeTarget);
		if (defenders == null) {
			WarForgeMod.LOGGER.error("Siege camp at " + getClaimPos() + " could not bind to target " + siegeTarget + " (no defending faction); aborting bind.");
			this.siegeTarget = null;
			return;
		}
		largestSeenDefenderCount = defenders.onlinePlayerCount;
		siegeStatus = SiegeStatus.ACTIVE;
		setChanged();
	}

    public DimBlockPos getSiegeTarget() {
        return siegeTarget;
    }

	private Faction getDefenders(DimBlockPos siegeTarget) {
		return WarForgeMod.FACTIONS.getFaction(WarForgeMod.FACTIONS.getClaim(siegeTarget));
	}

	@Override
	public boolean canBeSieged() { return false; }

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

	// forces siege to end as failure
	public void failSiege() {
		siegeStatus = SiegeStatus.FAILED;
		concludeSiege();
	}

	// cleanup function used for multiple camp siege
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
		if (level.isClientSide) {
			return;
		}

		// only modify external information if not performing cleanup on this tile entity
		if (siegeTarget == null)
		{

			WarForgeMod.LOGGER.error("Siege conclusion error! siegeTarget is null!");
			return;
		}
		Siege siege = WarForgeMod.FACTIONS.getSieges().get(siegeTarget.toChunkPos());
		if (!siegeStatus.isCleanup() && siege != null) {
			// update siege info and notify all nearby
			SiegeCampProgressInfo info = siege.GetSiegeInfo();
			info.progress = siegeStatus.isFailed() ? -5 : info.completionPoint;
			PacketSiegeCampProgressUpdate packet = new PacketSiegeCampProgressUpdate();
			packet.info = info;

			for (Player attacker : getAttacking().getOnlinePlayers(Objects::nonNull))
				WarForgeMod.NETWORK.sendTo(packet, (ServerPlayer) attacker);
			for (Player defender : defenders.getOnlinePlayers(Objects::nonNull))
				WarForgeMod.NETWORK.sendTo(packet, (ServerPlayer) defender);

			// attempt to actually modify siege information, now that all nearby have been updated
			try {
				siege.setAttackProgress(siegeStatus.isFailed() ? -5 : siege.GetAttackSuccessThreshold()); // ends siege
				WarForgeMod.FACTIONS.handleCompletedSiege(siegeTarget.toChunkPos(), false); // performs check on completed sieges without invoking checks on unrelated sieges
			} catch (Exception e) {
				WarForgeMod.LOGGER.atError().log("Got exception when attempting to force end siege of: " + e + " with siegeTarget of: " + siegeTarget + " and pos of: " + getClaimPos());
				e.printStackTrace();
			}

			for (DimBlockPos siegeCampPos : siege.attackingCamps) {
				if (siegeCampPos == null || getClaimPos().equals(siegeCampPos.toRegularPos())) continue;

				BlockEntity siegeCamp = level.getBlockEntity(siegeCampPos.toRegularPos());
				if (!(siegeCamp instanceof TileEntitySiegeCamp)) continue;

				if (siegeStatus.isFailed()) ((TileEntitySiegeCamp) siegeCamp).cleanupFailedSiege();
				else if (siegeStatus.isPassed()) ((TileEntitySiegeCamp) siegeCamp).cleanupPassedSiege();
			}
		}

		siegeTarget = null;
		defenders = null;
		destroy();
	}

	// Sided annotations and checks try to be useful challenge
	public void destroy() {
        WarForgeMod.FACTIONS.requestRemoveClaimServer(getClaimPos());
	}

	public void tick() {
		// do not do logic on client (somehow this got accessed by the client)
		if (level.isClientSide) return;

		// clear out ghost sieges for debugging
		if (!(level.getBlockState(worldPosition).getBlock() instanceof BlockSiegeCamp)) {
			destroy();
			return;
		}

		// do not do logic with invalid values
		if (placer == Faction.nullUuid || siegeTarget == null || defenders == null) return;

		tickTimer &= 0b01111111_11111111_11111111_11111111; // ensure positive

		// only perform the check every second if the timer is greater than one second, or every tick if an attacker must always be present
		if (doCheckPerTick || tickTimer % 20 == 0) {
			// send message to all players on defending team with necessary information to defend every 5 minutes
			if (tickTimer % 6000 == 0) {
				messageAllDefenders("warforge.info.siege_defense_info", new DimBlockPos(level.dimension(), getClaimPos()).toFancyString());
			}

			// --- ATTACKER HANDLING ---

			// if there are no players in the presence zone
			Siege activeSiege = WarForgeMod.FACTIONS.getSieges().get(siegeTarget.toChunkPos());
			if (activeSiege == null || !activeSiege.hasPresentAttacker()) {
				if (handleDesertion(true)) return; // cancel update if siege concludes
			} else {
				// stops at 0 and decrements gradually to stop attackers from popping into and out of warzone
				int decrementedAbandonedTimer = attackerAbandonTickTimer - (doCheckPerTick ? 1 : 20);
				if (decrementedAbandonedTimer > 0) {
					attackerAbandonTickTimer -= doCheckPerTick ? 1 : 20; // decrement timer if attacker is found
				} else {
					attackerAbandonTickTimer = 0;
				}
			}

			// Push the live abandon countdown to the attackers' siege HUD whenever the timer changes
			// (counting up while deserted, gradually back down, or hitting 0), without per-tick spam.
			if (tickTimer % 20 == 0 && attackerAbandonTickTimer != lastSyncedAbandonTimer && siegeTarget != null) {
				lastSyncedAbandonTimer = attackerAbandonTickTimer;
				WarForgeMod.FACTIONS.sendSiegeInfoToNearby(siegeTarget.toChunkPos());
			}

			// --- DEFENDER HANDLING ---
			if (lastSeenDefenderCount == 0 && defenders.onlinePlayerCount > 0 && defenderOfflineTimerMs > 0) {
				defenders.messageAll(Component.literal("Your faction [" + defenders.name + "] has an offline timer of " + TimeHelper.formatTime(defenderOfflineTimerMs) + " for the siege camp at " + getClaimPos()));
			}

			lastSeenDefenderCount = defenders.onlinePlayerCount;

			if (defenders.onlinePlayerCount > largestSeenDefenderCount) largestSeenDefenderCount = defenders.onlinePlayerCount; // update largest number of defenders seen
			int numActiveDefenders = defenders.getOnlinePlayers(this::isDefenderInWarzone).size();

			// check if the defenders have quit, and if not check if they are actively defending
			boolean haveDefendersQuit = haveDefendersLiveQuit();
			if (haveDefendersQuit) {
				incrementOfflineTimer(WarForgeMod.currTickTimestamp - previousTimestamp); // if defenders have quit, tick up the offline timer
				if (defenderOfflineTimerMs >= WarForgeConfig.LIVE_QUIT_TIMER) {
					getAttacking().messageAll(Component.literal("The defenders have fled from their posts for " + TimeHelper.formatTime(defenderOfflineTimerMs)));
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
							// abandon timer reset; the countdown is shown on the siege HUD instead of chat
							defenderAbandonTickTimer = 0;
						}
					}

				}
			}

			previousTimestamp = WarForgeMod.currTickTimestamp; // now that update is done, mark time as previous
			// end processing
		}

		++tickTimer;
		setChanged(); // notifies chunk of changes in value
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
        /* useful if the offline timer needs to be manually capped, though currently it doesn't
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
			// offline timer reset (no chat spam)
			return;
		}

		defenderOfflineTimerMs = newTimer; // if not going to overshoot, do decrement
	}

	private boolean hasLiveQuitSiege() {
		return defenderOfflineTimerMs < 0;
	}

	// returns whether update has been cancelled
	private boolean handleDesertion(boolean isAttackingSide) {
		// end siege if idle timer reaches desertion timer
		int abandonTimer = isAttackingSide ? WarForgeConfig.ATTACKER_DESERTION_TIMER : WarForgeConfig.DEFENDER_DESERTION_TIMER;
		int currentTickTimer = isAttackingSide ? attackerAbandonTickTimer : defenderAbandonTickTimer;

		if (currentTickTimer >= abandonTimer * 20) {
			// The siege is now abandoned: surface it as a WarForge notification (toast) + a global
			// announcement, instead of the previous chat lines / per-tick approach warnings.
			notifyAbandoned(isAttackingSide);

			// should cancel update; return boolean indicating continuation of update
			if (isAttackingSide) failSiege();
			else passSiege();
			return true;
		} else {
			// Abandon-approach feedback is now the live countdown on the attackers' siege HUD.

			if (isAttackingSide) attackerAbandonTickTimer += doCheckPerTick ? 1 : 20; // increment timer
			else defenderAbandonTickTimer += doCheckPerTick ? 1 : 20;
		}

		return false;
	}

	private void notifyAbandoned(boolean attackersDeserted) {
		Siege.notifyAbandoned(getAttacking(), defenders, attackersDeserted);
	}

	private boolean isDefenderInWarzone(Player player) {
		return isPlayerInRadius(player, SIEGE_DEFENDER_RADIUS);
	}

	private boolean isPlayerInRadius(Player player, int radius) {
		if(player == null) return false;
		DimChunkPos playerChunk = new DimChunkPos(player.level().dimension(), player.blockPosition());
		DimChunkPos blockChunk = new DimChunkPos(level.dimension(), getClaimPos());
		return !player.isRemoved() && Siege.isPlayerInRadius(blockChunk, playerChunk, radius);
	}

	private void messageAllDefenders(String translateKey, Object... args) {
		if (defenders == null) return;
		defenders.messageAll(Component.translatable(translateKey, args));
	}

	private Faction getAttacking() {
		return WarForgeMod.FACTIONS.getFaction(factionUUID);
	}

	@Override
	public void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);

		nbt.putUUID("placer", placer);
		nbt.putBoolean("started", siegeTarget != null);
		nbt.putBoolean("doCheckPerTick", doCheckPerTick);
		nbt.putInt("siegeStatus", siegeStatus.ordinal());
		nbt.putInt("tickTimer", tickTimer);
		if(siegeTarget != null) {
			siegeTarget.writeToNBT(nbt, "siegeTarget");
		}
	}

	@Override
	protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);

		placer = nbt.getUUID("placer");

		boolean started = nbt.getBoolean("started");
		doCheckPerTick = nbt.getBoolean("doCheckPerTick");

		siegeStatus = SiegeStatus.values()[nbt.getInt("siegeStatus")];
		tickTimer = nbt.getInt("tickTimer");
		previousTimestamp = WarForgeMod.currTickTimestamp;
		if(started) {
			siegeTarget = DimBlockPos.readFromNBT(nbt, "siegeTarget");

			defenders = getDefenders(siegeTarget);
			if (defenders != null) {
				largestSeenDefenderCount = defenders.onlinePlayerCount;
				lastSeenDefenderCount = defenders.onlinePlayerCount;
			} else {
				WarForgeMod.LOGGER.warn("Siege camp at " + worldPosition + " loaded with target " + siegeTarget + " but no defending faction; clearing target.");
				siegeTarget = null;
			}
		} else siegeTarget = null;

		Faction faction = WarForgeMod.FACTIONS.getFaction(factionUUID);
		if(!factionUUID.equals(Faction.nullUuid) && faction == null) {
			WarForgeMod.LOGGER.error("Faction " + factionUUID + " could not be found for citadel at " + worldPosition);
		}
		if(faction != null) {
			colour = faction.colour;
			factionName = faction.name;
		}
	}
}
