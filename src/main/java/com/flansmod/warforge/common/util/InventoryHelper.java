package com.flansmod.warforge.common.util;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Adds access to the InventoryPlayer stack combination methods for arbitrary inventories
 */
public class InventoryHelper
{
	public static boolean addItemStackToInventory(IItemHandler inventory, ItemStack stack, boolean isCreative)
	{
		if(stack.isEmpty())
			return false;
		else
		{
			try
			{
				int i;

				if(stack.isDamaged())
				{
					i = getFirstEmptyStack(inventory);

					if(i >= 0)
					{
						ItemStack stackToAdd = stack.copy();
						inventory.insertItem(i, stackToAdd, false);
						stack.setCount(0);
						return true;
					}
					else if(isCreative)
					{
						stack.setCount(0);
						return true;
					}
					return false;
				}
				else
				{
					do
					{
						i = stack.getCount();
						stack.setCount(storePartialItemStack(inventory, stack));
					}
					while(stack.getCount() > 0 && stack.getCount() < i);

					if(stack.getCount() == i && isCreative)
					{
						stack.setCount(0);
						return true;
					}
					else
					{
						return stack.getCount() < i;
					}
				}
			}
			catch(Throwable throwable)
			{
				return false;
			}
		}
	}

	public static int storeItemStack(IItemHandler inventory, ItemStack stack)
	{
		for(int i = 0; i < inventory.getSlots(); ++i)
		{
			ItemStack oldStack = inventory.getStackInSlot(i);
			if(!oldStack.isEmpty() && oldStack.getItem() == stack.getItem() &&
					oldStack.isStackable() && oldStack.getCount() < oldStack.getMaxStackSize() &&
					oldStack.getCount() < inventory.getSlotLimit(i) &&
					ItemStack.isSameItemSameComponents(oldStack, stack))
			{
				return i;
			}
		}

		return -1;
	}

	public static int storePartialItemStack(IItemHandler inventory, ItemStack stack)
	{
		int itemCount = stack.getCount();
		int emptySlot;

		//If the item doesn't stack, just find an empty slot for it
		if(stack.getMaxStackSize() == 1)
		{
			emptySlot = getFirstEmptyStack(inventory);
			//If it is impossible, return
			if(emptySlot < 0)
			{
				return itemCount;
			}
			else
			{
				ItemStack oldStack = inventory.getStackInSlot(emptySlot);
				if(oldStack.isEmpty())
				{
					inventory.insertItem(emptySlot, stack.copy(), false);
				}
				return 0;
			}
		}
		else
		{
			emptySlot = storeItemStack(inventory, stack);
			if(emptySlot < 0)
			{
				emptySlot = getFirstEmptyStack(inventory);
			}

			if(emptySlot >= 0)
			{
				ItemStack oldStack = inventory.getStackInSlot(emptySlot);

				int slotLimit = inventory.getSlotLimit(emptySlot);
				int existingCount = oldStack.getCount();
				int maxStack = oldStack.isEmpty() ? stack.getMaxStackSize() : oldStack.getMaxStackSize();
				int l = Math.min(slotLimit - existingCount, Math.min(itemCount, maxStack - existingCount));

				if(l != 0)
				{
					itemCount -= l;
					// Insert the additional amount by inserting a partial stack; track remainder
					ItemStack toInsert = stack.copy();
					toInsert.setCount(l);
					ItemStack remainder = inventory.insertItem(emptySlot, toInsert, false);
					itemCount += remainder.getCount();
				}
			}
			return itemCount;
		}
	}

	/**
	 * Method from InventoryPlayer
	 */
	public static int getFirstEmptyStack(IItemHandler inventory)
	{
		for(int i = 0; i < inventory.getSlots(); ++i)
		{
			if(inventory.getStackInSlot(i).isEmpty())
				return i;
		}

		return -1;
	}

}
