package com.flansmod.warforge.common.blocks;

import brachy.modularui.factory.BlockEntityUIFactory;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.server.Faction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;

public class BlockIslandCollector extends Block implements EntityBlock {
    public BlockIslandCollector() {
        super(BlockBehaviour.Properties.of()
                .strength(4.0F, 20.0F)
                .sound(SoundType.STONE));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntityIslandCollector(pos, state);
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (world.isClientSide || !(placer instanceof ServerPlayer player)) {
            return;
        }

        DimBlockPos collectorPos = new DimBlockPos(world.dimension(), pos);
        boolean success = WarForgeMod.FACTIONS.registerCollector(player, collectorPos);
        if (!success) {
            world.removeBlock(pos, false);
            if (!player.isCreative()) {
                ItemStack refund = new ItemStack(this);
                if (!player.getInventory().add(refund)) {
                    player.drop(refund, false);
                } else {
                    player.inventoryMenu.broadcastChanges();
                }
            }
        }
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(this));
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity tileentity = world.getBlockEntity(pos);
            if (tileentity instanceof TileEntityIslandCollector collector) {
                for (int i = 0; i < collector.getSlots(); i++) {
                    Containers.dropItemStack(world, pos.getX(), pos.getY(), pos.getZ(), collector.getStackInSlot(i));
                }
            }
            if (!world.isClientSide) {
                WarForgeMod.FACTIONS.unregisterCollector(new DimBlockPos(world.dimension(), pos));
            }
        }
        super.onRemove(state, world, pos, newState, isMoving);
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (world.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (world.getBlockEntity(pos) instanceof TileEntityIslandCollector collector) {
            if (!collector.getFaction().equals(Faction.nullUuid)
                    && !WarForgeMod.FACTIONS.IsPlayerInFaction(player.getUUID(), collector.getFaction())
                    && !WarForgeMod.isOp(player)) {
                return InteractionResult.SUCCESS;
            }
            WarForgeMod.syncClaimToPlayer(player, pos);
            BlockEntityUIFactory.INSTANCE.open(player, pos);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
