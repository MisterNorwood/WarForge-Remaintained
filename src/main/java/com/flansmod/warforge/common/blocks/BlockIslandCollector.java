package com.flansmod.warforge.common.blocks;

import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.server.Faction;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
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
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.SlotItemHandler;

import javax.annotation.Nullable;
import java.util.List;

public class BlockIslandCollector extends Block implements EntityBlock, BlockUIMenuType.BlockUI {
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
    public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
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
            if (player instanceof ServerPlayer serverPlayer) {
                BlockUIMenuType.openUI(serverPlayer, pos);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        UIElement root = new UIElement();
        UIElement body = com.flansmod.warforge.api.modularui.WarForgeUiTheme.frame(root, 200);

        BlockEntity be = holder.player.level().getBlockEntity(holder.pos);
        if (be instanceof TileEntityIslandCollector collector) {
            boolean hasFaction = !collector.getFaction().equals(Faction.nullUuid);
            String factionLabel = hasFaction ? collector.factionName : "Unclaimed";
            int colour = hasFaction ? collector.colour : 0xC7CCD1;

            Button close = com.flansmod.warforge.api.modularui.WarForgeUiTheme.closeButton();
            close.setOnServerClick(event -> {
                if (holder.player instanceof ServerPlayer sp) {
                    sp.closeContainer();
                }
            });
            body.addChild(com.flansmod.warforge.api.modularui.WarForgeUiTheme.header("Faction Yield Storage", factionLabel, colour, close));

            IItemHandlerModifiable storage = collector.getStorageHandler();
            int slots = storage.getSlots();
            UIElement grid = new UIElement();
            grid.layout(l -> l.flexDirection(FlexDirection.ROW).flexWrap(FlexWrap.WRAP).width(180));
            for (int i = 0; i < slots; i++) {
                grid.addChild(new ItemSlot()
                        .bind(new SlotItemHandler(storage, i, 0, 0) {
                            @Override
                            public boolean mayPlace(ItemStack stack) {
                                return false;
                            }
                        })
                        .layout(l -> l.width(18).height(18)));
            }
            UIElement section = com.flansmod.warforge.api.modularui.WarForgeUiTheme.section();
            section.addChild(grid);
            body.addChild(section);
        }

        body.addChild(com.flansmod.warforge.api.modularui.WarForgeUiTheme.inventoryPanel(new InventorySlots()));
        return ModularUI.of(UI.of(root), holder.player);
    }
}
