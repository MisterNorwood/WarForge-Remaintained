package com.flansmod.warforge.common;

import com.flansmod.warforge.common.blocks.TileEntityBasicClaim;
import com.flansmod.warforge.common.blocks.TileEntityCitadel;
import com.flansmod.warforge.server.Faction;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemBanner;
import net.minecraft.item.ItemShield;
import net.minecraft.item.ItemStack;

public class ContainerBasicClaim extends Container
{
	public TileEntityBasicClaim claim;
	
	public ContainerBasicClaim(InventoryPlayer inventory, TileEntityBasicClaim te)
	{
		claim = te;
		
		for(int i = 0; i < 3; i++)
		{
			for(int j = 0; j < 3; j++)
			{
				int index = i * 3 + j;
				addSlotToContainer(new Slot(claim, index, 8 + 18 * i, 32 + 18 * j));
			}
		}		
		
		//Main inventory slots
		for(int row = 0; row < 3; row++)
		{
			for(int col = 0; col < 9; col++)
			{
				addSlotToContainer(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 100 + row * 18));
			}
			
		}
		//Quickbar slots
		for(int col = 0; col < 9; col++)
		{
			addSlotToContainer(new Slot(inventory, col, 8 + col * 18, 158));
		}
	}
	
	@Override
	public boolean canInteractWith(EntityPlayer player) 
	{
		return player.world.isRemote || claim.GetFaction().equals(Faction.NULL) || WarForgeMod.FACTIONS.IsPlayerInFaction(player.getUniqueID(), claim.GetFaction());
	}
	
	@Override
	public ItemStack transferStackInSlot(EntityPlayer player, int slotID)
	{
		ItemStack stack = ItemStack.EMPTY;
		Slot currentSlot = inventorySlots.get(slotID);
		
		if(currentSlot != null && currentSlot.getHasStack())
		{
			ItemStack slotStack = currentSlot.getStack();
			stack = slotStack.copy();
			
			if(slotID < TileEntityBasicClaim.NUM_SLOTS)
			{
				if(!mergeItemStack(slotStack, TileEntityBasicClaim.NUM_SLOTS, inventorySlots.size(), true))
				{
					return ItemStack.EMPTY;
				}
			}
			else
			{
				// Merge into the container
				if(!mergeItemStack(slotStack, 0, TileEntityBasicClaim.NUM_YIELD_STACKS, false))
				{
					return ItemStack.EMPTY;
				}
			}
			
			if(slotStack.getCount() == 0)
			{
				currentSlot.putStack(ItemStack.EMPTY);
			}
			else
			{
				currentSlot.onSlotChanged();
			}
			
			if(slotStack.getCount() == stack.getCount())
			{
				return ItemStack.EMPTY;
			}
			
			currentSlot.onTake(player, slotStack);
		}
		
		return stack;
	}
}
