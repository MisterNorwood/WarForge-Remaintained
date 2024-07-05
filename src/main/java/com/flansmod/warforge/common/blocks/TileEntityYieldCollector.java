package com.flansmod.warforge.common.blocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import com.flansmod.warforge.api.IItemYieldProvider;
import com.flansmod.warforge.common.DimBlockPos;
import com.flansmod.warforge.common.InventoryHelper;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.server.Faction;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemBanner;
import net.minecraft.item.ItemShield;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;

public abstract class TileEntityYieldCollector extends TileEntityClaim implements IInventory
{
	public static final int NUM_YIELD_STACKS = 9;
	public static final int NUM_BASE_SLOTS = NUM_YIELD_STACKS;
			
	protected abstract float GetYieldMultiplier();

	// The yield stacks are where items arrive when your faction is above a deposit
	protected ItemStack[] mYieldStacks = new ItemStack[NUM_YIELD_STACKS];

	public TileEntityYieldCollector()
	{
		for(int i = 0; i < NUM_YIELD_STACKS; i++)
		{
			mYieldStacks[i] = ItemStack.EMPTY;
		}
	}
			
	public void ProcessYield(int numYields) 
	{
		if(world.isRemote)
			return;
		
		HashMap<IItemYieldProvider, Integer> count = new HashMap<IItemYieldProvider, Integer>();
		
		ChunkPos chunk = new ChunkPos(getPos());
		
		for(int x = chunk.x * 16; x < (chunk.x + 1) * 16; x++)
		{
			for(int z = chunk.z * 16; z < (chunk.z + 1) * 16; z++)
			{
				for(int y = 0; y < WarForgeConfig.HIGHEST_YIELD_ASSUMPTION; y++)
				{
					Block block = world.getBlockState(new BlockPos(x, y, z)).getBlock();
					if(block instanceof IItemYieldProvider)
					{
						IItemYieldProvider yieldProv = (IItemYieldProvider)block;
						if(count.containsKey(yieldProv))
							count.replace(yieldProv, count.get(yieldProv) + 1);
						else
							count.put(yieldProv, 1);
						
					}
				}
			}
		}
		
		for(HashMap.Entry<IItemYieldProvider, Integer> kvp : count.entrySet())
		{
			if(kvp.getKey().GetMultiplier() > 0.0f)
			{
				ItemStack stack = kvp.getKey().GetYieldToProvide().copy();
				stack.setCount(MathHelper.ceil(kvp.getValue() * numYields * kvp.getKey().GetMultiplier() * GetYieldMultiplier()));
				if(!InventoryHelper.addItemStackToInventory(this, stack, false))
				{
					
				}
			}
		}
		
		markDirty();
	}
	
	@Override
	public void onLoad()
	{
		if(!world.isRemote)
		{
			Faction faction = WarForgeMod.FACTIONS.GetFaction(mFactionUUID);
			if(faction != null)
			{
				int pendingYields = faction.mClaims.get(GetPos());
				if(pendingYields > 0)
				{
					ProcessYield(pendingYields);
				}
				faction.mClaims.replace(GetPos(), 0);
			}
			else if(!mFactionUUID.equals(Faction.NULL))
			{
				WarForgeMod.LOGGER.error("Loaded YieldCollector with invalid faction");
			}
		}
		
	}
	
	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound nbt)
	{
		super.writeToNBT(nbt);
		
		// Write all our stacks out		
		for(int i = 0; i < NUM_YIELD_STACKS; i++)
		{
			NBTTagCompound yieldStackTags = new NBTTagCompound();
			mYieldStacks[i].writeToNBT(yieldStackTags);
			nbt.setTag("yield_" + i, yieldStackTags);
		}
		
		return nbt;
	}

	
	@Override
	public void readFromNBT(NBTTagCompound nbt)
	{
		super.readFromNBT(nbt);
	
		// Read inventory, or as much as we can find
		for(int i = 0; i < NUM_YIELD_STACKS; i++)
		{
			if(nbt.hasKey("yield_" + i))
				mYieldStacks[i] = new ItemStack(nbt.getCompoundTag("yield_" + i));
			else 
				mYieldStacks[i] = ItemStack.EMPTY;
		}
	}
	
	// ----------------------------------------------------------
	// The GIGANTIC amount of IInventory methods...
	@Override
	public String getName() { return mFactionName; }
	@Override
	public boolean hasCustomName() { return false; }
	@Override
	public int getSizeInventory() { return NUM_BASE_SLOTS; }
	@Override
	public boolean isEmpty() 
	{
		for(int i = 0; i < NUM_YIELD_STACKS; i++)
			if(!mYieldStacks[i].isEmpty())
				return false;
		return true;
	}
	// In terms of indexing, the yield stacks are 0 - 8
	@Override
	public ItemStack getStackInSlot(int index) 
	{
		if(index < NUM_YIELD_STACKS)
			return mYieldStacks[index];
		return ItemStack.EMPTY;
	}
	@Override
	public ItemStack decrStackSize(int index, int count) 
	{
		if(index < NUM_YIELD_STACKS)
		{
			int numToTake = Math.max(count, mYieldStacks[index].getCount());
			ItemStack result = mYieldStacks[index].copy();
			result.setCount(numToTake);
			mYieldStacks[index].setCount(mYieldStacks[index].getCount() - numToTake);
			return result;
		}
		return ItemStack.EMPTY;
	}
	@Override
	public ItemStack removeStackFromSlot(int index) 
	{
		ItemStack result = ItemStack.EMPTY;
		if(index < NUM_YIELD_STACKS)
		{
			result = mYieldStacks[index];
			mYieldStacks[index] = ItemStack.EMPTY;			
		}
		return result;
	}
	@Override
	public void setInventorySlotContents(int index, ItemStack stack) 
	{
		if(index < NUM_YIELD_STACKS)
		{
			mYieldStacks[index] = stack;
		}
	}
	@Override
	public int getInventoryStackLimit() 
	{
		return 64;
	}
	@Override
	public boolean isUsableByPlayer(EntityPlayer player) 
	{
		return mFactionUUID.equals(Faction.NULL) || WarForgeMod.FACTIONS.IsPlayerInFaction(player.getUniqueID(), mFactionUUID);
	}
	@Override
	public void openInventory(EntityPlayer player) { }
	@Override
	public void closeInventory(EntityPlayer player) { }
	@Override
	public boolean isItemValidForSlot(int index, ItemStack stack) 
	{
		if(index < NUM_YIELD_STACKS)
		{
			return true;
		}
		return false;
	}
	@Override
	public int getField(int id)  { return 0; }
	@Override
	public void setField(int id, int value) { }
	@Override
	public int getFieldCount() { return 0; }
	@Override
	public void clear() 
	{ 
		for(int i = 0; i < NUM_YIELD_STACKS; i++)
			mYieldStacks[i] = ItemStack.EMPTY;
	}
	// ----------------------------------------------------------
}
