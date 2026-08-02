package com.flansmod.warforge.common.blocks;

import com.flansmod.warforge.common.Content;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.server.Faction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static com.flansmod.warforge.common.blocks.BlockDummy.MODEL;

public class TileEntityCitadel extends TileEntityYieldCollector implements IClaim {
    public static final int BANNER_SLOT_INDEX = NUM_BASE_SLOTS;
    public static final int NUM_SLOTS = NUM_BASE_SLOTS + 1;

    public UUID placer = Faction.nullUuid;

    // The banner stack is an optional slot that sets all banners in owned chunks to copy the design
    protected ItemStack bannerStack;

    public TileEntityCitadel(BlockPos pos, BlockState state) {
        super(Content.TE_CITADEL.get(), pos, state);
        bannerStack = ItemStack.EMPTY;
    }

    //TODO: This needs to be handled with enums and not array indexes
    @Override
    public void onPlacedBy(LivingEntity placer) {
        // This locks in the placer as the only person who can create a faction using the interface on this citadel
        this.placer = placer.getUUID();
        super.onPlacedBy(placer);
    }

    // IClaim
    @Override
    public int getDefenceStrength() {
        return WarForgeConfig.CLAIM_STRENGTH_CITADEL;
    }

    @Override
    public int getSupportStrength() {
        return WarForgeConfig.SUPPORT_STRENGTH_CITADEL;
    }

    @Override
    public int getAttackStrength() {
        return 0;
    }

    @Override
    protected float getYieldMultiplier() {
        return 2.0f;
    }

    public void processFactionYields(Faction faction) {
        if (level == null || level.isClientSide || faction == null) {
            return;
        }

        Set<DimChunkPos> collectorCovered = null;
        if (!faction.islandCollectors.isEmpty()) {
            collectorCovered = new HashSet<>();
            for (DimBlockPos collectorPos : faction.islandCollectors) {
                collectorCovered.addAll(WarForgeMod.FACTIONS.collectFactionIsland(faction.uuid, collectorPos.toChunkPos()));
            }
        }

        for (DimBlockPos claimPos : faction.claims.keySet()) {
            if (collectorCovered != null && collectorCovered.contains(claimPos.toChunkPos())) {
                continue;
            }
            processYieldForClaim(faction.claims, claimPos, false);
        }
    }

    @Override
    public void onLoad() {
        if (level != null && !level.isClientSide) {
            Faction faction = WarForgeMod.FACTIONS.getFaction(factionUUID);
            if (faction != null) {
                processFactionYields(faction);
            } else if (!factionUUID.equals(Faction.nullUuid)) {
                WarForgeMod.LOGGER.error("Loaded Citadel with invalid faction");
            }
        }
    }

    @Override
    public String getClaimDisplayName() { //FIXME
        if (factionName == null || factionName.isEmpty()) {
            return "Unclaimed Citadel";
        }
        return "Citadel of " + factionName;
    }
    //-----------



    // Inventory overrides for banner stack (IItemHandlerModifiable surface from TileEntityYieldCollector)
    @Override
    public int getSlots() {
        return NUM_SLOTS;
    }

    @Override
    public boolean isEmpty() {
        return super.isEmpty() && bannerStack.isEmpty();
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        if (index == BANNER_SLOT_INDEX)
            return bannerStack;
        return super.getStackInSlot(index);
    }

    @Override
    public void setStackInSlot(int index, ItemStack stack) {
        if (index == BANNER_SLOT_INDEX) {
            bannerStack = stack;
            setChanged();
        } else
            super.setStackInSlot(index, stack);
    }

    @Override
    public ItemStack insertItem(int index, ItemStack stack, boolean simulate) {
        if (index == BANNER_SLOT_INDEX) {
            if (stack.isEmpty() || !bannerStack.isEmpty() || !isItemValid(index, stack))
                return stack;
            if (!simulate) {
                ItemStack inserted = stack.copy();
                inserted.setCount(1);
                bannerStack = inserted;
                setChanged();
            }
            if (stack.getCount() == 1)
                return ItemStack.EMPTY;
            ItemStack remainder = stack.copy();
            remainder.shrink(1);
            return remainder;
        }
        return super.insertItem(index, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int index, int count, boolean simulate) {
        if (index == BANNER_SLOT_INDEX) {
            if (count <= 0 || bannerStack.isEmpty())
                return ItemStack.EMPTY;
            int toExtract = Math.min(count, bannerStack.getCount());
            ItemStack result = bannerStack.copy();
            result.setCount(toExtract);
            if (!simulate) {
                if (toExtract >= bannerStack.getCount())
                    bannerStack = ItemStack.EMPTY;
                else
                    bannerStack.shrink(toExtract);
                setChanged();
            }
            return result;
        }
        return super.extractItem(index, count, simulate);
    }

    @Override
    public int getSlotLimit(int index) {
        if (index == BANNER_SLOT_INDEX)
            return 1;
        return super.getSlotLimit(index);
    }

    @Override
    public boolean isItemValid(int index, ItemStack stack) {
        if (index == BANNER_SLOT_INDEX) {
            return stack.getItem() instanceof BannerItem || stack.getItem() instanceof ShieldItem;
        }
        return super.isItemValid(index, stack);
    }

    @Override
    public void clear() {
        super.clear();
        bannerStack = ItemStack.EMPTY;
    }

    public void copyStorageFrom(TileEntityCitadel other) {
        for (int i = 0; i < NUM_YIELD_STACKS; i++) {
            yieldStacks[i] = other.yieldStacks[i].copy();
        }
        bannerStack = other.bannerStack.copy();
        setChanged();
    }

    /**
     * Builds the statue and assigns rotation.
     *
     * @param faction faction to build with.
     */
    @Override
    public void onServerSetFaction(Faction faction) {

        //To do: save rotation to NBT
        BlockState state = level.getBlockState(worldPosition);
        level.setBlock(worldPosition.above(), Content.statue.defaultBlockState().setValue(MODEL, BlockDummy.modelEnum.KNIGHT), 3);
        level.sendBlockUpdated(worldPosition.above(), state, state, 3);


        BlockEntity teMiddle = level.getBlockEntity(worldPosition.above());
        if (teMiddle instanceof TileEntityDummy) {
            ((TileEntityDummy) teMiddle).setMaster(worldPosition);
        }

        level.setBlock(worldPosition.above(2), Content.dummyTranslusent.defaultBlockState().setValue(MODEL, BlockDummy.modelEnum.TRANSLUCENT), 3);
        level.sendBlockUpdated(worldPosition.above(2), state, state, 3);

        BlockEntity teTop = level.getBlockEntity(worldPosition.above(2));
        if (teTop instanceof TileEntityDummy) {
            ((TileEntityDummy) teTop).setMaster(worldPosition);
        }

        super.onServerSetFaction(faction);
        BlockEntity te = level.getBlockEntity(worldPosition.above(2));
        if (te instanceof TileEntityDummy)
            ((TileEntityDummy) te).setLaserRender(true);


    }


    public void onServerCreateFaction(Faction faction) {

        level.playSound(null, worldPosition, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.playSound(null, worldPosition, SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 1.0F, 1.2F);
        ((ServerLevel) level).sendParticles(
                ParticleTypes.EXPLOSION_EMITTER,
                worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                32,
                0.0D, 2.5D, 0.0D,
                1.0D
        );
        onServerSetFaction(faction);
    }

    @Override
    public void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);

        nbt.putUUID("placer", placer);

        CompoundTag bannerStackTags = new CompoundTag();
        bannerStack.save(bannerStackTags);
        nbt.put("banner", bannerStackTags);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);

        bannerStack = ItemStack.of(nbt.getCompound("banner"));
        placer = nbt.getUUID("placer");
    }
}
