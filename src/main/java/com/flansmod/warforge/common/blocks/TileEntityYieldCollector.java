package com.flansmod.warforge.common.blocks;

import org.apache.commons.lang3.tuple.Pair;
import com.flansmod.warforge.api.vein.Quality;
import com.flansmod.warforge.api.vein.Vein;
import com.flansmod.warforge.api.vein.init.VeinUtils;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.common.util.InventoryHelper;
import com.flansmod.warforge.server.Faction;
import com.flansmod.warforge.server.ItemMatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

import static com.flansmod.warforge.common.WarForgeMod.VEIN_HANDLER;

public abstract class TileEntityYieldCollector extends TileEntityClaim implements IItemHandlerModifiable
{
	public static final int NUM_YIELD_STACKS = 9;
	public static final int NUM_BASE_SLOTS = NUM_YIELD_STACKS;

	protected abstract float getYieldMultiplier();

	// The yield stacks are where items arrive when your faction is above a deposit
	protected ItemStack[] yieldStacks = new ItemStack[NUM_YIELD_STACKS];

	public TileEntityYieldCollector(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		Arrays.fill(yieldStacks, ItemStack.EMPTY);
	}

	public void processYield(HashMap<DimBlockPos, Integer> claims) {
		processYieldForClaim(claims, getClaimPos());
	}

	protected void processYieldForClaim(HashMap<DimBlockPos, Integer> claims, DimBlockPos claimPos) {
		processYieldForClaim(claims, claimPos, true);
	}

	protected void processYieldForClaim(HashMap<DimBlockPos, Integer> claims, DimBlockPos claimPos, boolean warnOnFull) {
		if(level.isClientSide) { return; }  // we don't process on the client
		if (VEIN_HANDLER == null || !VEIN_HANDLER.hasFinishedInit) { return; }

		// check the number of yields to award and handle appropriately
		Integer pending = claims.get(claimPos);
		if (pending == null) { return; }
		int numYields = pending;
		if (numYields == 0) { return; }
		claims.replace(claimPos, 0);

		DimChunkPos currPos = claimPos.toChunkPos();
		if (!VEIN_HANDLER.dimHasVeins(currPos.dim)) { return; }

		long seed = ((ServerLevel) level).getSeed();

		// get vein data
		Pair<Vein, Quality> veinInfo = VEIN_HANDLER.getVein(level.dimension(), currPos.x, currPos.z, seed);
		if (veinInfo == null) { return; }  // ignore null veins

		Vein currVein = veinInfo.getLeft();

		Random rand = new Random((WarForgeMod.currTickTimestamp * seed) * 2654435761L);
		ArrayList<ItemStack> yieldComps = new ArrayList<>(currVein.compIds.size());
		float yieldMult = getYieldMultiplier();  // collector-specific scaling (e.g. citadels yield more)

		// for each component in the vein, attempt to yield it numYields many times
		for (ItemMatcher currComp : currVein.compIds) {
			int numItems = 0;  // figure out how many items of this component are needed

			// determine yield amount based on quality and component base yield
			ArrayList<short[]> subCompYieldInfos = VeinUtils.getYieldInfo(currComp, veinInfo, currPos.dim);

			// yield this component the appropriate number of times
			for (int j = 0; j < numYields; ++j) {
				// for each subcomponent, apply the yielding logic
				for (short[] subCompInfo : subCompYieldInfos) {
					// apply a chance to yield this subcomponent
					if (rand.nextInt(VeinUtils.WEIGHT_FRACTION_TENS_POW) + 1 > subCompInfo[0]) { continue; }

					// apply a chance to yield an extra item of this sub component
					numItems += subCompInfo[1];  // add guaranteed yield
					if (rand.nextInt(VeinUtils.WEIGHT_FRACTION_TENS_POW) + 1 > subCompInfo[2]) { continue; }

					++numItems;  // add extra yield
				}
			}

			// scale the rolled amount by this collector's yield multiplier
			numItems = Math.round(numItems * yieldMult);

			if (numItems == 0) { continue; } // adding an itemstack with 0 of the item will result in an air itemstack

			// attempt to locate the item and append the new item stack representing yield amounts
			ItemStack compStack = currComp.toStack(numItems);
			if (compStack.isEmpty()) {
				// item does not exist for some reason
				WarForgeMod.LOGGER.atError().log("Got null item component for vein w/ key: " + currVein.translationKey);
				continue;
			}

			yieldComps.add(compStack);
		}

		if (yieldComps.size() == 0) { return; }  // don't go through the process of marking dirty if we won't do anything

		// try to add the items (THIS WILL CONSUME THE ITEMSTACKS, SO MAKE SURE THEY ARE COPIES)
		for (ItemStack currCompStack : yieldComps) {
			if(!InventoryHelper.addItemStackToInventory(this, currCompStack, false) && warnOnFull) {
				WarForgeMod.LOGGER.atError().log("Failed to add <" + currCompStack.toString() + "> to yield " +
						"collector at " + this.getBlockPos());
			}
		}

		setChanged();
	}

	@Override
	public void onLoad() {
		if(!level.isClientSide) {
			Faction faction = WarForgeMod.FACTIONS.getFaction(factionUUID);
			if(faction != null) {
				processYield(faction.claims);
			} else if(!factionUUID.equals(Faction.nullUuid)) {
				WarForgeMod.LOGGER.error("Loaded YieldCollector with invalid faction");
			}
		}

	}

	@Override
	public void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);

		// Write all our stacks out
		for(int i = 0; i < NUM_YIELD_STACKS; i++) {
			nbt.put("yield_" + i, yieldStacks[i].saveOptional(registries));
		}
	}


	@Override
	protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);

		for(int i = 0; i < NUM_YIELD_STACKS; i++) {
			if(nbt.contains("yield_" + i))
				yieldStacks[i] = ItemStack.parseOptional(registries, nbt.getCompound("yield_" + i));
			else
				yieldStacks[i] = ItemStack.EMPTY;
		}
	}

	public String getName() {
		if (factionName == null || factionName.isEmpty()) {
			return "WarForge Storage";
		}
		return factionName;
	}

	// ----------------------------------------------------------
	// IItemHandlerModifiable: the yield stacks are exposed directly so deposits, break-drops and the
	// GUI all share one backing store. The yield stacks occupy slots 0 - 8.
	@Override
	public int getSlots() { return NUM_BASE_SLOTS; }

	@Override
	public ItemStack getStackInSlot(int index) {
		if(index < NUM_YIELD_STACKS)
			return yieldStacks[index];
		return ItemStack.EMPTY;
	}

	@Override
	public void setStackInSlot(int index, ItemStack stack) {
		if(index < NUM_YIELD_STACKS) {
			yieldStacks[index] = stack;
		}
	}

	@Override
	public ItemStack insertItem(int index, ItemStack stack, boolean simulate) {
		if(stack.isEmpty() || index < 0 || index >= NUM_YIELD_STACKS || !isItemValid(index, stack)) {
			return stack;
		}

		ItemStack existing = yieldStacks[index];
		int limit = Math.min(getSlotLimit(index), stack.getMaxStackSize());

		if(!existing.isEmpty()) {
			if(!ItemStack.isSameItemSameComponents(existing, stack)) {
				return stack;
			}
			limit -= existing.getCount();
		}

		if(limit <= 0) {
			return stack;
		}

		int toInsert = Math.min(limit, stack.getCount());
		if(!simulate) {
			if(existing.isEmpty()) {
				ItemStack inserted = stack.copy();
				inserted.setCount(toInsert);
				yieldStacks[index] = inserted;
			} else {
				existing.grow(toInsert);
			}
			setChanged();
		}

		if(stack.getCount() == toInsert) {
			return ItemStack.EMPTY;
		}
		ItemStack remainder = stack.copy();
		remainder.shrink(toInsert);
		return remainder;
	}

	@Override
	public ItemStack extractItem(int index, int count, boolean simulate) {
		if(count <= 0 || index < 0 || index >= NUM_YIELD_STACKS) {
			return ItemStack.EMPTY;
		}
		ItemStack existing = yieldStacks[index];
		if(existing.isEmpty()) {
			return ItemStack.EMPTY;
		}

		int toExtract = Math.min(count, existing.getCount());
		ItemStack result = existing.copy();
		result.setCount(toExtract);
		if(!simulate) {
			if(toExtract >= existing.getCount()) {
				yieldStacks[index] = ItemStack.EMPTY;
			} else {
				existing.shrink(toExtract);
			}
			setChanged();
		}
		return result;
	}

	@Override
	public int getSlotLimit(int index) {
		return 64;
	}

	@Override
	public boolean isItemValid(int index, ItemStack stack) {
		return index < NUM_YIELD_STACKS;
	}

	public boolean isEmpty() {
		for(int i = 0; i < NUM_YIELD_STACKS; i++)
			if(!yieldStacks[i].isEmpty())
				return false;
		return true;
	}

	public void clear() {
		Arrays.fill(yieldStacks, ItemStack.EMPTY);
	}
	// ----------------------------------------------------------
}
