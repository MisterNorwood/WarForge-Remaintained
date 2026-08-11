package com.flansmod.warforge.common.blocks;

import com.flansmod.warforge.common.Content;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.common.util.PullOnlyItemHandler;
import com.flansmod.warforge.server.Faction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.Set;

public class TileEntityIslandCollector extends TileEntityYieldCollector {
    /** Slot count for the faction yield collector, configurable via {@link WarForgeConfig#ISLAND_COLLECTOR_SLOTS}. */
    private static int slotCount() {
        return Math.max(1, WarForgeConfig.ISLAND_COLLECTOR_SLOTS);
    }

    // The real backing store. Yields are deposited here by the mod; the GUI binds directly to it.
    private final ItemStackHandler storage = new ItemStackHandler(slotCount()) {
        @Override
        protected void onContentsChanged(int slot) {
            TileEntityIslandCollector.this.setChanged();
        }
    };

    // The view exposed to automation: pull-only, available from every side.
    private final IItemHandler pullOnlyView = new PullOnlyItemHandler(storage);

    public IItemHandler getPullOnlyView() {
        return pullOnlyView;
    }

    public TileEntityIslandCollector(BlockPos pos, BlockState state) {
        super(Content.TE_ISLAND_COLLECTOR.get(), pos, state);
    }

    @Override
    public int getDefenceStrength() {
        return 0;
    }

    @Override
    public int getSupportStrength() {
        return 0;
    }

    @Override
    public int getAttackStrength() {
        return 0;
    }

    @Override
    public boolean canBeSieged() {
        return false;
    }

    @Override
    protected float getYieldMultiplier() {
        return 1.0f;
    }

    @Override
    public String getClaimDisplayName() {
        return factionName.isEmpty() ? "Faction Yield Storage" : factionName + " Yield Storage";
    }

    @Override
    public String getName() {
        return getClaimDisplayName();
    }

    public IItemHandlerModifiable getStorageHandler() {
        return storage;
    }

    public void processIslandYields(Faction faction) {
        if (level == null || level.isClientSide || faction == null) {
            return;
        }

        DimChunkPos collectorChunk = new DimChunkPos(level.dimension(), worldPosition);
        Set<DimChunkPos> island = WarForgeMod.FACTIONS.collectFactionIsland(faction.uuid, collectorChunk);
        if (island.isEmpty()) {
            return;
        }

        for (DimBlockPos claimPos : faction.claims.keySet()) {
            if (island.contains(claimPos.toChunkPos())) {
                processYieldForClaim(faction.claims, claimPos);
            }
        }
    }

    @Override
    public void onLoad() {
        if (level == null || level.isClientSide) {
            return;
        }

        Faction faction = WarForgeMod.FACTIONS.getFaction(factionUUID);
        if (faction != null) {
            processIslandYields(faction);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        invalidateCapabilities();
    }

    // ----------------------------------------------------------
    // IItemHandler delegates to the config-sized storage so yield deposits, break-drops and the GUI
    // all share one backing store. Insertion is refused (isItemValid) so nothing can be put in.
    @Override
    public int getSlots() {
        return storage.getSlots();
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < storage.getSlots(); i++) {
            if (!storage.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        return (index >= 0 && index < storage.getSlots()) ? storage.getStackInSlot(index) : ItemStack.EMPTY;
    }

    @Override
    public void setStackInSlot(int index, ItemStack stack) {
        if (index >= 0 && index < storage.getSlots()) {
            storage.setStackInSlot(index, stack);
        }
    }

    @Override
    public ItemStack insertItem(int index, ItemStack stack, boolean simulate) {
        return storage.insertItem(index, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int index, int count, boolean simulate) {
        return storage.extractItem(index, count, simulate);
    }

    @Override
    public int getSlotLimit(int index) {
        return storage.getSlotLimit(index);
    }

    @Override
    public boolean isItemValid(int index, ItemStack stack) {
        return false;
    }

    @Override
    public void clear() {
        for (int i = 0; i < storage.getSlots(); i++) {
            storage.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    // ----------------------------------------------------------
    @Override
    public void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);
        nbt.put("storage", storage.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);

        // Load the saved items into the (config-sized) handler manually, rather than via
        // deserializeNBT, so that the configured slot count always wins. Items whose saved slot no
        // longer exists are relocated into any remaining free space instead of being dropped.
        if (nbt.contains("storage")) {
            ListTag items = nbt.getCompound("storage").getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < items.size(); i++) {
                CompoundTag itemTags = items.getCompound(i);
                int slot = itemTags.getInt("Slot");
                ItemStack stack = ItemStack.parseOptional(registries, itemTags);
                if (stack.isEmpty()) {
                    continue;
                }
                if (slot >= 0 && slot < storage.getSlots()) {
                    storage.setStackInSlot(slot, stack);
                } else {
                    ItemHandlerHelper.insertItem(storage, stack, false);
                }
            }
        }

        // Migrate any items saved by the old 9-slot layout into the new storage.
        for (int i = 0; i < NUM_YIELD_STACKS; i++) {
            ItemStack legacy = yieldStacks[i];
            if (legacy != null && !legacy.isEmpty()) {
                ItemHandlerHelper.insertItem(storage, legacy, false);
                yieldStacks[i] = ItemStack.EMPTY;
            }
        }
    }

}
