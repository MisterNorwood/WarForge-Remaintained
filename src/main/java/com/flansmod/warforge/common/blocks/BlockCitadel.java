package com.flansmod.warforge.common.blocks;

import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.factories.CitadelGuiFactory;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.server.Faction;
import com.flansmod.warforge.server.FactionStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;

import static com.flansmod.warforge.common.Content.dummyTranslusent;
import static com.flansmod.warforge.common.Content.statue;
import static com.flansmod.warforge.common.blocks.BlockDummy.MODEL;
import static com.flansmod.warforge.common.blocks.BlockDummy.modelEnum.KING;
import static com.flansmod.warforge.common.blocks.BlockDummy.modelEnum.TRANSLUCENT;

public class BlockCitadel extends MultiBlockColumn implements EntityBlock, IMultiBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public BlockCitadel() {
        super(Properties.of()
                .strength(-1.0F, 30000000.0F)
                .noLootTable());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public void initMap() {
        multiBlockMap = Collections.unmodifiableMap(new HashMap<>() {{
            put(statue.defaultBlockState().setValue(MODEL, KING), new Vec3i(0, 1, 0));
            put(dummyTranslusent.defaultBlockState().setValue(MODEL, TRANSLUCENT), new Vec3i(0, 2, 0));
        }});
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntityCitadel(pos, state);
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        Direction facing = placer.getDirection().getOpposite();
        world.setBlock(pos, state.setValue(FACING, facing), 2);
        BlockEntity te = world.getBlockEntity(pos);
        if (te != null) {
            TileEntityCitadel citadel = (TileEntityCitadel) te;
            citadel.onPlacedBy(placer);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if (pos.getY() < WarForgeConfig.CITADEL_MIN_Y) {
            if (!world.isClientSide && context.getPlayer() != null) {
                context.getPlayer().sendSystemMessage(Component.literal("Citadels must be placed at or above Y " +
                        WarForgeConfig.CITADEL_MIN_Y));
            }
            return null;
        }

        if (!world.isClientSide) {
            if (WarForgeMod.FACTIONS.isChunkContested(new DimChunkPos(world.dimension(), pos)))
                return null;

            // Can't claim a chunk claimed by another faction
            UUID existingClaim = WarForgeMod.FACTIONS.getClaim(new DimChunkPos(world.dimension(), pos));
            if (!existingClaim.equals(Faction.nullUuid))
                return null;
        }

        // Can only place on a solid surface
        if (!world.getBlockState(pos.below()).isFaceSturdy(world, pos.below(), Direction.UP))
            return null;

        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            TileEntityClaim citadel = (TileEntityClaim) world.getBlockEntity(pos);
            if (!citadel.getFaction().equals(Faction.nullUuid))
                citadel.increaseRotation();
            else if (!world.isClientSide) {
                onRemove(state, world, pos, state, false);
                world.destroyBlock(pos, true);
            }
            return world.isClientSide ? InteractionResult.CONSUME : InteractionResult.SUCCESS;
        }
        if (!world.isClientSide) {
            Faction playerFaction = WarForgeMod.FACTIONS.getFactionOfPlayer(player.getUUID());
            TileEntityCitadel citadel = (TileEntityCitadel) world.getBlockEntity(pos);

            // If the player has no faction and is the placer, they can open the UI
            if (playerFaction == null && player.getUUID().equals(citadel.placer)) {
                CitadelGuiFactory.INSTANCE.open(player, citadel.getBlockPos());
            }
            // Any other factionless players, and players who aren't in this faction get an info panel
            else if (playerFaction == null || !playerFaction.uuid.equals(citadel.factionUUID)) {
                Faction citadelFaction = WarForgeMod.FACTIONS.getFaction(citadel.factionUUID);
                if (citadelFaction != null) {
                    CitadelGuiFactory.INSTANCE.open(player, citadel.getBlockPos());
                } else {
                    DimBlockPos citadelPos = new DimBlockPos(world.dimension(), pos);
                    Faction chunkFaction = WarForgeMod.FACTIONS.getFaction(WarForgeMod.FACTIONS.getClaim(citadelPos.toChunkPos()));
                    // if ghost citadel exists in chunk claimed by faction, delete it
                    if (FactionStorage.isValidFaction(chunkFaction)) {
                        world.destroyBlock(pos, false);
                        player.sendSystemMessage(Component.literal("Overlapping citadel placement found; deleting current."));
                    } else {
                        player.sendSystemMessage(Component.literal("This citadel is not home to a faction, and was not placed by you."));
                    }
                }
            }
            // So anyone else will be from the target faction
            else {
                CitadelGuiFactory.INSTANCE.open(player, citadel.getBlockPos());
            }
        }
        return world.isClientSide ? InteractionResult.CONSUME : InteractionResult.SUCCESS;
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        super.onRemove(state, world, pos, newState, isMoving);
    }
}
