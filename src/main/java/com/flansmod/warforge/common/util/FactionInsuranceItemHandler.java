package com.flansmod.warforge.common.util;

import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.server.Faction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

public class FactionInsuranceItemHandler implements IItemHandlerModifiable {
    private final Faction faction;

    public FactionInsuranceItemHandler(Faction faction) {
        this.faction = faction;
    }

    @Override
    public int getSlots() {
        return faction.getInsuranceSlotCount();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= getSlots()) {
            return ItemStack.EMPTY;
        }
        return faction.getInsuranceStack(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (slot < 0 || slot >= getSlots() || stack.isEmpty() || !isItemValid(slot, stack)) {
            return stack;
        }

        ItemStack existing = getStackInSlot(slot);
        int limit = getSlotLimit(slot);
        if (existing.isEmpty()) {
            int toInsert = Math.min(Math.min(limit, stack.getMaxStackSize()), stack.getCount());
            if (!simulate) {
                ItemStack inserted = stack.copy();
                inserted.setCount(toInsert);
                setStackInSlot(slot, inserted);
            }
            if (stack.getCount() == toInsert) {
                return ItemStack.EMPTY;
            }
            ItemStack remainder = stack.copy();
            remainder.shrink(toInsert);
            return remainder;
        }

        if (!ItemStack.isSameItemSameComponents(existing, stack)) {
            return stack;
        }

        int space = Math.min(limit, existing.getMaxStackSize()) - existing.getCount();
        if (space <= 0) {
            return stack;
        }

        int toInsert = Math.min(space, stack.getCount());
        if (!simulate) {
            ItemStack merged = existing.copy();
            merged.grow(toInsert);
            setStackInSlot(slot, merged);
        }
        if (stack.getCount() == toInsert) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = stack.copy();
        remainder.shrink(toInsert);
        return remainder;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (slot < 0) {
            return;
        }
        faction.setInsuranceStack(slot, stack);
    }

    public boolean isItemValid(int slot, ItemStack stack) {
        return slot >= 0 && slot < getSlots() && !WarForgeConfig.isInsuranceBlacklisted(stack);
    }
}
