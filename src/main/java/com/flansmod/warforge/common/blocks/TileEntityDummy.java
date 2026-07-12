package com.flansmod.warforge.common.blocks;

import com.flansmod.warforge.common.Content;
import com.flansmod.warforge.common.WarForgeMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class TileEntityDummy extends BlockEntity implements IBlockDummy {
    protected String playerFlag; //Owner's flag
    private BlockPos masterPos;
    private boolean canRenderLaser = false;
    private float[] laserRGB = new float[]{0F, 0F, 0F}; //pure white

    public TileEntityDummy(BlockPos pos, BlockState state) {
        super(Content.TE_DUMMY.get(), pos, state);
    }

    public void setMaster(BlockPos pos) {
        this.masterPos = pos;
        setChanged();
    }

    public boolean getLaserRender() {
        return canRenderLaser;
    }

    public void setLaserRender(boolean bool) {
        canRenderLaser = bool;
        BlockState state = level.getBlockState(worldPosition);
        level.sendBlockUpdated(worldPosition, state, state, 3);
        level.scheduleTick(worldPosition, getBlockState().getBlock(), 0);
        setChanged();
    }

    public float[] getLaserRGB() {
        return laserRGB;
    }

    public void setLaserRGB(int colour) {
        float r = ((colour >> 16) & 0xFF) / 255.0f;
        float g = ((colour >> 8) & 0xFF) / 255.0f;
        float b = (colour & 0xFF) / 255.0f;
        this.laserRGB = new float[]{r, g, b};
        BlockState state = level.getBlockState(worldPosition);
        level.sendBlockUpdated(worldPosition, state, state, 3);
        level.scheduleTick(worldPosition, getBlockState().getBlock(), 0);
        setChanged();
    }

    public void setLaserRGB(float[] laserRGB) {
        this.laserRGB = laserRGB;
    }

    public BlockPos getMasterTile() {
        if (level == null || masterPos == null) return null;
        return masterPos;
    }//Primitive but sufficent for the time tbh

    @Override
    public void saveAdditional(CompoundTag compound) {
        super.saveAdditional(compound);
        compound.putBoolean("laser", canRenderLaser);
        compound.putFloat("r", laserRGB[0]);
        compound.putFloat("g", laserRGB[1]);
        compound.putFloat("b", laserRGB[2]);
        if (masterPos != null) {
            compound.putLong("master", masterPos.asLong());
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level.isClientSide) return;
        if (masterPos == null) {
            WarForgeMod.LOGGER.error("TileEntityDummy at " + this.worldPosition + "had null Master pos, attempting to locate it");
            for (int i = 1; i <= 3; i++) {
                Block block = level.getBlockState(worldPosition.below(i)).getBlock();
                if (block instanceof MultiBlockColumn) {
                    setMaster(worldPosition.below(i));
                    WarForgeMod.LOGGER.info("TileEntityDummy at " + this.worldPosition + " found master at " + worldPosition.below(i));
                    setChanged();
                    return;
                }
                WarForgeMod.LOGGER.error("TileEntityDummy at " + this.worldPosition + " cannot find it's master, this is a bug, report it to the developer!");
            }
        }
    }

    @Override
    public void load(CompoundTag compound) {
        super.load(compound);
        canRenderLaser = compound.getBoolean("laser");
        laserRGB[0] = compound.getFloat("r");
        laserRGB[1] = compound.getFloat("g");
        laserRGB[2] = compound.getFloat("b");

        if (compound.contains("master")) {
            masterPos = BlockPos.of(compound.getLong("master"));
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        handleUpdateTag(packet.getTag());
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tags = super.getUpdateTag();
        tags.putBoolean("laser", canRenderLaser);
        tags.putFloat("r", laserRGB[0]);
        tags.putFloat("g", laserRGB[1]);
        tags.putFloat("b", laserRGB[2]);
        if (masterPos != null)
            tags.putLong("master", masterPos.asLong());

        return tags;
    }

    @Override
    public void handleUpdateTag(CompoundTag tags) {
        canRenderLaser = tags.getBoolean("laser");
        laserRGB[0] = tags.getFloat("r");
        laserRGB[1] = tags.getFloat("g");
        laserRGB[2] = tags.getFloat("b");
        masterPos = BlockPos.of(tags.getLong("master"));
    }

    @Override
    public AABB getRenderBoundingBox() {
        if (canRenderLaser) {
            BlockPos pos = getBlockPos();
            double poleTop = pos.getY() + PoleGeometry.BASE_TRANSLATE;
            if (masterPos != null) {
                float poleLength = TileEntityClaim.DEFAULT_POLE_LENGTH;
                if (level != null && level.getBlockEntity(masterPos) instanceof TileEntityClaim claim) {
                    poleLength = claim.getPoleLength();
                }
                poleTop = masterPos.getY() + PoleGeometry.topOffset(poleLength);
            }
            return new AABB(pos.getX(), poleTop, pos.getZ(), pos.getX() + 1, 257, pos.getZ() + 1);
        }
        return super.getRenderBoundingBox();
    }

}
