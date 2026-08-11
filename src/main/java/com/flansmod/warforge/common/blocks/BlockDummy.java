package com.flansmod.warforge.common.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

public class BlockDummy extends Block implements EntityBlock {
    public static final EnumProperty<modelEnum> MODEL = EnumProperty.create("model", modelEnum.class);

    public BlockDummy() {
        super(Properties.of()
                .strength(-1.0F, 30000000.0F)
                .sound(SoundType.STONE)
                .noLootTable()
                // Invisible structural placeholder above the citadel/claim base: it must not occlude
                // the base block's adjacent (top) face and must not block skylight, otherwise the
                // statue position renders dark and the base's top face is culled into a black hole.
                .noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(MODEL, modelEnum.TRANSLUCENT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MODEL);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(MODEL, modelEnum.TRANSLUCENT);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return false;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockEntity tileEntity = level.getBlockEntity(pos);
        if (!(tileEntity instanceof IBlockDummy dummy)) return InteractionResult.PASS;

        BlockPos masterPos = dummy.getMasterTile();
        if (masterPos == null) return InteractionResult.PASS;

        if (player.isShiftKeyDown()) {
            BlockEntity masterTile = level.getBlockEntity(masterPos);
            if (masterTile instanceof TileEntityClaim claim) {
                claim.increaseRotation();
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        if (!level.isEmptyBlock(masterPos)) {
            return level.getBlockState(masterPos).useWithoutItem(level, player, new BlockHitResult(
                    hit.getLocation(), hit.getDirection(), masterPos, hit.isInside()));
        }

        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntityDummy(pos, state);
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    // Invisible placeholder: present no occlusion shape so it never culls the neighbouring claim
    // base's top face (the dark "hole" symptom).
    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    // Let skylight pass straight through so the statue rendered above the base is lit.
    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    @Deprecated
    public enum modelEnum implements StringRepresentable {
        TRANSLUCENT,
        KING,
        KNIGHT,
        BERSERKER,
        ;

        @Override
        public String getSerializedName() {
            return name().toLowerCase();
        }
    }
}
