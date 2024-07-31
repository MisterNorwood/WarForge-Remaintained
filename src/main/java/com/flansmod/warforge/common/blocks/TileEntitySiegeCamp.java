package com.flansmod.warforge.common.blocks;

import java.util.Objects;
import java.util.UUID;

import com.flansmod.warforge.api.ObjectIntPair;
import com.flansmod.warforge.common.DimBlockPos;
import com.flansmod.warforge.common.DimChunkPos;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.network.PacketSiegeCampProgressUpdate;
import com.flansmod.warforge.common.network.SiegeCampProgressInfo;
import com.flansmod.warforge.server.Faction;

import com.flansmod.warforge.server.FactionStorage;
import com.flansmod.warforge.server.Siege;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ITickable;
import net.minecraft.util.text.TextComponentTranslation;
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
		mPlacer = placer.getUniqueID();
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
    /*
	@Override
	public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
		WarForgeMod.LOGGER.always().log("shouldRefresh has been called in " + world + " at pos " + pos + " with oldSate of " + oldState + " and newState of " + newState);
		return super.shouldRefresh(world, pos, oldState, newState);
	}
	 */

	// called when player flag is removed, but not necessarily when siege ends?
	@Override
	public void OnServerRemovePlayerFlag(String playerName) {
		super.OnServerRemovePlayerFlag(playerName);

		if(mPlayerFlags.size() == 0) endSiegePrepped(); // if siege block runs out of player flags, siege fails
	}

	// separated from overridden method to allow for testing and to make functionality apparent
	public void endSiegePrepped() {
		siegeStatus = 2; // siege is failed as all player flags from placing group are lost
		concludeSiege();
	}

	// stops siege without changing anything
	public void cancelSiege() {
		siegeStatus = 0;
		concludeSiege();
	}

	// sets siege to be successful
	public void passSiege() {
		siegeStatus = 3;
		concludeSiege();
	}

	// forces siege to end as failure
	public void failSiege() {
		siegeStatus = 2;
		concludeSiege();
	}

	// kills siege block and tile entity
	private void concludeSiege() {
		// do any client side logic, then return
		if (world.isRemote) {
			return;
		}

		// update siege info and notify all nearby
		Siege siege = WarForgeMod.FACTIONS.getSieges().get(mSiegeTarget.ToChunkPos());
		if(siege != null) {
			SiegeCampProgressInfo info = siege.GetSiegeInfo();
			info.mProgress = siegeStatus == 2 ? -5 : info.mCompletionPoint;
			PacketSiegeCampProgressUpdate packet = new PacketSiegeCampProgressUpdate();
			packet.mInfo = info;

			for (EntityPlayer attacker : getAttacking().getPlayers(Objects::nonNull)) WarForgeMod.NETWORK.sendTo(packet, (EntityPlayerMP) attacker);
			for (EntityPlayer defender : getDefending().getPlayers(Objects::nonNull)) WarForgeMod.NETWORK.sendTo(packet, (EntityPlayerMP) defender);
		}

		// siege may not exist in server's record, leading to crash loop. This prevents the loop. Removes siege after info indicating update is done
		try {
			WarForgeMod.FACTIONS.getSieges().get(mSiegeTarget.ToChunkPos()).setAttackProgress(siegeStatus == 2 ? -5 : WarForgeMod.FACTIONS.getSieges().get(mSiegeTarget.ToChunkPos()).GetAttackSuccessThreshold()); // ends siege
			WarForgeMod.FACTIONS.handleCompletedSiege(mSiegeTarget.ToChunkPos(), false); // performs check on completed sieges without invoking checks on unrelated sieges
			WarForgeMod.FACTIONS.EndSiege(GetPos());
		} catch (Exception e) {
            WarForgeMod.LOGGER.error("Got exception when attempting to force end siege of: {} with siegeTarget of: {} and pos of: {}", e, mSiegeTarget, getPos());
		}

		// on success, mark conquered chunk as conquered so that attackers may start siege again without enemy claiming over them.
		if (siegeStatus == 3 && WarForgeConfig.ATTACKER_CONQUERED_CHUNK_PERIOD > 0) {
			// claim both the siege block chunk and the conquered claim chunk
			WarForgeMod.FACTIONS.conqueredChunks.put(mSiegeTarget.ToChunkPos(), new ObjectIntPair<>(FactionStorage.copyUUID(mFactionUUID), WarForgeConfig.ATTACKER_CONQUERED_CHUNK_PERIOD));
			WarForgeMod.FACTIONS.conqueredChunks.put(new DimChunkPos(world.provider.getDimension(), getPos()), new ObjectIntPair<>(FactionStorage.copyUUID(mFactionUUID), WarForgeConfig.ATTACKER_CONQUERED_CHUNK_PERIOD));
		}

		mSiegeTarget = null;
		destroy();
	}

	// called whenever block should be destroyed
	public void onDestroyed() {
		// will fail the siege if it is on going, or just do the destruction actions if finished or never started
		if (siegeStatus == 1) failSiege();
		else destroy();
	}

	public void destroy() {
		world.destroyBlock(getPos(), true); // destroy block of failed siege
		siegeStatus = -1; // invalidate before destruction do that this code is not run in an infinite loop in getUpdateTag
		world.markBlockRangeForRenderUpdate(pos, pos);
		world.notifyBlockUpdate(getPos(), world.getBlockState(getPos()), world.getBlockState(getPos()), 3); // 2 is bit mask apparently indicating send to client
		//world.scheduleBlockUpdate(pos, this.getBlockType(), 0, 0);
		markDirty();

		WarForgeMod.FACTIONS.getClaims().remove(GetPos().ToChunkPos());
		WarForgeMod.FACTIONS.GetFaction(mFactionUUID).OnClaimLost(GetPos());
		world.removeTileEntity(getPos());
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
		if (mPlacer == Faction.NULL || mSiegeTarget == null) return;

		tickTimer &= 0b01111111_11111111_11111111_11111111; // ensure positive

		// only perform the check every second if the timer is greater than one second, or every tick if an attacker must always be present
		if (doCheckPerTick || tickTimer % 20 == 0) {
			// send message to all players on defending team with necessary information to defend every 5 minutes
			if (tickTimer % 6000 == 0) {
				messageAllDefenders("warforge.info.siege_defense_info", new DimBlockPos(world.provider.getDimension(), getPos()).ToFancyString());
			}

			// if there are no players in the radius
			if (WarForgeMod.FACTIONS.GetFaction(mFactionUUID).getPlayers(entityPlayer -> isPlayerInWarzone(entityPlayer)).size() < 1) {
				// end siege if idle timer reaches desertion timer
				if (abandonedSiegeTickTimer >= WarForgeConfig.ATTACKER_DESERTION_TIMER * 20) {
					messageAllAttackers("warforge.info.siege_idle_exceeded_attacker");
					messageAllDefenders("warforge.info.siege_idle_exceeded_defender");
					failSiege();
				} else {
					if (abandonedSiegeTickTimer / 20 == WarForgeConfig.ATTACKER_DESERTION_TIMER >>> 4) {
						messageAllAttackers("warforge.notification.siege_abandon", abandonedSiegeTickTimer / 20, WarForgeConfig.ATTACKER_DESERTION_TIMER);
					}
					int ticksLeft = WarForgeConfig.ATTACKER_DESERTION_TIMER * 20 - abandonedSiegeTickTimer;
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

	private boolean isPlayerInWarzone(EntityPlayer player) {
		DimChunkPos playerChunk = new DimChunkPos(player.dimension, player.getPosition());
		DimChunkPos blockChunk = new DimChunkPos(world.provider.getDimension(), getPos());
		return Siege.isPlayerInRadius(blockChunk, playerChunk);
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
}
