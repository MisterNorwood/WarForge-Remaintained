package com.flansmod.warforge.api;

import net.minecraft.item.ItemStack;

// Implement this interface on a Block to have it show up as a faction yield
public interface IItemYieldProvider 
{
	public ItemStack GetYieldToProvide();
	public float GetMultiplier();
}

// TODO: IFluidYieldProvider
