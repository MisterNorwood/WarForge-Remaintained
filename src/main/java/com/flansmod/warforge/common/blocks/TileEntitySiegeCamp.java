package com.flansmod.warforge.common.blocks;

import java.util.UUID;

import com.flansmod.warforge.common.DimBlockPos;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.server.Faction;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;

public class TileEntitySiegeCamp extends TileEntityClaim
{
	private UUID mPlacer = Faction.NULL;
	private BlockPos mSiegeTarget = null;
	
	public TileEntitySiegeCamp()
	{
		
	}
	
	public void OnPlacedBy(EntityLivingBase placer) 
	{
		mPlacer = placer.getUniqueID();
	}
	
	@Override
	public int GetDefenceStrength() { return 0; }
	@Override
	public int GetSupportStrength() { return 0; }
	@Override
	public int GetAttackStrength() { return WarForgeConfig.ATTACK_STRENGTH_SIEGE_CAMP; }
	@Override 
	public boolean CanBeSieged() { return false; }

	@Override
	public void OnServerRemovePlayerFlag(String playerName)
	{
		super.OnServerRemovePlayerFlag(playerName);
		
		if(mPlayerFlags.size() == 0)
		{
			Faction faction = WarForgeMod.FACTIONS.GetFaction(mFactionUUID);
			if(faction != null)
			{
				faction.MessageAll(new TextComponentString("The siege at " + GetPos().ToFancyString() + " ended when all player flags were removed"));
				
				WarForgeMod.FACTIONS.EndSiege(GetPos());
				faction.OnClaimLost(GetPos());
			}
		}
	}
	
	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound nbt)
	{
		super.writeToNBT(nbt);
		
		nbt.setUniqueId("placer", mPlacer);
		nbt.setBoolean("started", mSiegeTarget != null);
		if(mSiegeTarget != null)
		{
			nbt.setInteger("attackX", mSiegeTarget.getX());
			nbt.setInteger("attackY", mSiegeTarget.getY());
			nbt.setInteger("attackZ", mSiegeTarget.getZ());
		}
		
		return nbt;
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt)
	{
		super.readFromNBT(nbt);
		
		mPlacer = nbt.getUniqueId("placer");
		
		boolean started = nbt.getBoolean("started");
		if(started)
		{
			mSiegeTarget = new BlockPos(
					nbt.getInteger("attackX"),
					nbt.getInteger("attackY"),
					nbt.getInteger("attackZ"));
		}
		else mSiegeTarget = null;
		
		if(FMLCommonHandler.instance().getEffectiveSide() == Side.SERVER)
		{
			Faction faction = WarForgeMod.FACTIONS.GetFaction(mFactionUUID);
			if(!mFactionUUID.equals(Faction.NULL) && faction == null)
			{
				WarForgeMod.LOGGER.error("Faction " + mFactionUUID + " could not be found for citadel at " + pos);
				//world.setBlockState(getPos(), Blocks.AIR.getDefaultState());
			}
			if(faction != null)
			{
				mColour = faction.mColour;
				mFactionName = faction.mName;
			}
		}
		else
		{
			WarForgeMod.LOGGER.error("Loaded TileEntity from NBT on client?");
		}
	}	
}
