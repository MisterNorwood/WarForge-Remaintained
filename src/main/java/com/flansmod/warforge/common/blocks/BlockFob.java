package com.flansmod.warforge.common.blocks;

import com.flansmod.warforge.common.Content;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.blocks.structure.StructureStamper;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import net.minecraft.network.chat.Component;
import com.flansmod.warforge.server.Faction;
import com.flansmod.warforge.server.fob.Fob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.UUID;

public class BlockFob extends Block implements EntityBlock, BlockUIMenuType.BlockUI {
    private static final float FOB_HARDNESS = 100.0F;

    public BlockFob() {
        super(BlockBehaviour.Properties.of()
                .strength(-1.0F, 3600000.0F)
                .noLootTable()
                .noOcclusion());
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntityFob(pos, state);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if (!world.isClientSide) {
            DimChunkPos chunk = new DimChunkPos(world.dimension(), pos);
            if (!com.flansmod.warforge.server.fob.FobManager.isClaimDimWhitelisted(chunk.dim)) {
                return null;
            }
            if (WarForgeMod.FACTIONS.isChunkContested(chunk)) {
                return null;
            }
            UUID existingClaim = WarForgeMod.FACTIONS.getClaim(chunk);
            if (!existingClaim.equals(Faction.nullUuid)) {
                return null;
            }
            if (WarForgeMod.FOBS.getFobAt(chunk) != null) {
                return null;
            }
        }
        return this.defaultBlockState();
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        return world.getBlockState(pos.below()).isFaceSturdy(world, pos.below(), Direction.UP);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (world.isClientSide) {
            return InteractionResult.CONSUME;
        }

        if (!(world.getBlockEntity(pos) instanceof TileEntityFob fob)) {
            return InteractionResult.SUCCESS;
        }

        Faction playerFaction = WarForgeMod.FACTIONS.getFactionOfPlayer(player.getUUID());
        boolean established = !fob.ownerFaction.equals(Faction.nullUuid);
        boolean isOfficer = playerFaction != null
                && (WarForgeMod.isOp(player) || playerFaction.isPlayerRoleInFaction(player.getUUID(), Faction.Role.OFFICER));

        if (established) {
            if (playerFaction == null || !playerFaction.uuid.equals(fob.ownerFaction)) {
                return InteractionResult.SUCCESS;
            }
        } else if (!isOfficer) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("You are not an officer of your faction"));
            return InteractionResult.SUCCESS;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            ClientboundBlockEntityDataPacket packet = fob.getUpdatePacket();
            if (packet != null) {
                serverPlayer.connection.send(packet);
            }
            BlockUIMenuType.openUI(serverPlayer, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        UIElement root = new UIElement();
        UIElement body = com.flansmod.warforge.api.modularui.WarForgeUiTheme.frame(root, 280);
        body.addChild(com.flansmod.warforge.api.modularui.WarForgeUiTheme.header("Forward Operating Base"));

        BlockEntity be = holder.player.level().getBlockEntity(holder.pos);
        if (be instanceof TileEntityFob fob) {
            boolean established = !fob.ownerFaction.equals(new UUID(0, 0)) && !fob.name.isEmpty();
            if (established) {
                UIElement section = com.flansmod.warforge.api.modularui.WarForgeUiTheme.section();
                section.addChild(com.flansmod.warforge.api.modularui.WarForgeUiTheme.text("FOB: " + fob.name, com.flansmod.warforge.api.modularui.WarForgeUiTheme.TEXT_PRIMARY));
                section.addChild(com.flansmod.warforge.api.modularui.WarForgeUiTheme.text("Tickets: " + fob.tickets, com.flansmod.warforge.api.modularui.WarForgeUiTheme.TEXT_SECONDARY));
                Button warpBtn = new Button().setText("Warp to FOB");
                com.flansmod.warforge.api.modularui.WarForgeUiTheme.styleButton(warpBtn, 100);
                warpBtn.setOnServerClick(event -> {
                    if (holder.player instanceof ServerPlayer sp) {
                        DimBlockPos dpos = new DimBlockPos(sp.level().dimension(), holder.pos);
                        Fob warpTarget = WarForgeMod.FOBS.getFobAt(dpos.toChunkPos());
                        if (warpTarget != null) {
                            WarForgeMod.FOBS.requestFobWarp(sp, warpTarget);
                        }
                    }
                });
                section.addChild(warpBtn);
                body.addChild(section);
            } else {
                body.addChild(com.flansmod.warforge.api.modularui.WarForgeUiTheme.text("This FOB is not established.", com.flansmod.warforge.api.modularui.WarForgeUiTheme.TEXT_MUTED));
            }
        }

        return ModularUI.of(UI.of(root), holder.player);
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, net.minecraft.world.level.BlockGetter world, BlockPos pos) {
        if (!WarForgeConfig.FOB_BLOCK_BREAKABLE) {
            return 0.0F;
        }
        return 1.0F / FOB_HARDNESS / 30.0F;
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!world.isClientSide && !state.is(newState.getBlock())) {
            Fob registered = WarForgeMod.FOBS.getFobAt(new DimChunkPos(world.dimension(), pos));
            if (registered != null && registered.pos.toRegularPos().equals(pos)) {
                StructureStamper.clearStructure(world, pos);
                WarForgeMod.FOBS.removeFob(registered);
            }
        }
        super.onRemove(state, world, pos, newState, isMoving);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != Content.TE_FOB.get()) {
            return null;
        }
        return (lvl, pos, blockState, be) -> {
            if (be instanceof TileEntityFob fob) {
                fob.tick();
            }
        };
    }
}
