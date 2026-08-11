package com.flansmod.warforge.common.util;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * An {@link IItemHandler} view that forbids insertion but permits extraction.
 * <p>
 * Used to expose a yield collector's storage to automation (hoppers, pipes) so that items can be
 * pulled out from any side, while nothing can ever be pushed in. The mod still deposits yields by
 * writing to the backing handler directly; this wrapper only guards the externally exposed capability.
 */
public class PullOnlyItemHandler implements IItemHandler {
    private final IItemHandler delegate;

    public PullOnlyItemHandler(IItemHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public int getSlots() {
        return delegate.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return delegate.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        // Pull-only: reject everything by returning the stack untouched.
        return stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return delegate.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return delegate.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return false;
    }
}
