package com.flansmod.warforge.common.blocks;

import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.server.Faction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;

import static com.flansmod.warforge.common.blocks.BlockCitadel.FACING;

public abstract class TileEntityClaim extends BlockEntity implements IClaim {
    public int colour = 0xFF_FF_FF;
    public String factionName = "";
    public String factionFlagId = "";
    protected UUID factionUUID = Faction.nullUuid;
    public byte rotation;

    public static final float DEFAULT_POLE_LENGTH = 8.75F;
    public float poleLength = DEFAULT_POLE_LENGTH;

    // This is so weird
    private Level worldCreate;

    public TileEntityClaim(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // IClaim
    @Override
    public UUID getFaction() {
        return factionUUID;
    }

    public void increaseRotation() {
        rotation += 1;
        rotation  = (byte) (rotation % 8);
        updateTileEntity();
    }

    public float getPoleLength() {
        return poleLength;
    }

    public void setPoleLength(float length) {
        poleLength = length;
        updateTileEntity();
    }

    //TODO: This needs to be handled with enums and not array indexes
    public void onPlacedBy(LivingEntity placer) {
        // This locks in the placer as the only person who can create a faction using the interface on this citadel
        rotation = 0;
        Direction blockRotation = level.getBlockState(worldPosition).getValue(FACING);
        switch (blockRotation) {
            case NORTH: //WORKING
                rotation = 0;
                break;
            case EAST: //WORKING
                rotation = 6;
                break;
            case SOUTH: //WORKING
                rotation = 4;
                break;
            case WEST: //WORKING
                rotation = 2;
        }

    }



    @Override
    public void updateColour(int colour) {
        this.colour = colour;
        BlockEntity te = level.getBlockEntity(worldPosition.above(2));
        if (te instanceof TileEntityDummy) {
            ((TileEntityDummy) te).setLaserRGB(colour);
        }
        updateTileEntity();
    }

    @Override
    public int getColour() {
        return colour;
    }

    @Override
    public BlockEntity getAsTileEntity() {
        return this;
    }

    @Override
    public DimBlockPos getClaimPos() {
        if (level == null) {
            if (worldCreate == null)
                return DimBlockPos.ZERO;
            else
                return new DimBlockPos(worldCreate.dimension(), getBlockPos());
        }
        return new DimBlockPos(level.dimension(), getBlockPos());
    }

    @Override
    public boolean canBeSieged() {
        return true;
    }

    @Override
    public String getClaimDisplayName() {
        return factionName;
    }

    @Override
    public void onServerSetFaction(Faction faction) {
        if (faction == null) {
            factionUUID = Faction.nullUuid;
            factionFlagId = "";
        } else {
            factionUUID = faction.uuid;
            updateColour(faction.colour);
            factionName = faction.name;
            factionFlagId = faction.flagId;
        }

        if (level.getBlockState(worldPosition).getBlock() instanceof MultiBlockColumn b) {
            b.setUpMultiblock(level, worldPosition, b.defaultBlockState());
        }

        updateTileEntity();
    }

    public void updateFactionName(String newName) {
        factionName = newName == null ? "" : newName;
        updateTileEntity();
    }

    public void updateFactionFlag(String newFlagId) {
        factionFlagId = newFlagId == null ? "" : newFlagId;
        updateTileEntity();
    }

    private void updateTileEntity() {
        if (level.isClientSide) return;
        BlockState state = level.getBlockState(worldPosition);
        level.sendBlockUpdated(worldPosition, state, state, 3);
        setChanged();
    }

    public void setWorldCreate(Level world) {
        worldCreate = world;
    }

    @Override
    public void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);

        nbt.putUUID("faction", factionUUID);
        nbt.putByte("rotation", rotation);
        nbt.putFloat("poleLength", poleLength);
        nbt.putString("flagId", factionFlagId);
        nbt.putString("name", factionName);
        nbt.putInt("colour", colour);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);

        factionUUID = nbt.getUUID("faction");
        rotation = nbt.getByte("rotation");
        poleLength = nbt.contains("poleLength") ? nbt.getFloat("poleLength") : DEFAULT_POLE_LENGTH;
        factionFlagId = nbt.getString("flagId");
        factionName = nbt.getString("name");
        if (nbt.contains("colour")) {
            colour = nbt.getInt("colour");
        }

        // Verifications
        if (level != null && !level.isClientSide) {
            Faction faction = WarForgeMod.FACTIONS.getFaction(factionUUID);
            if (!factionUUID.equals(Faction.nullUuid) && faction == null) {
                WarForgeMod.LOGGER.error("Faction " + factionUUID + " could not be found for citadel at " + worldPosition);
            }
            if (faction != null) {
                colour = faction.colour;
                factionName = faction.name;
                factionFlagId = faction.flagId;
            }
        } else {
            WarForgeMod.LOGGER.error("Loaded TileEntity from NBT on logical client?");
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        handleUpdateTag(packet.getTag(), registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tags = super.getUpdateTag(registries);

        // Custom partial nbt write method
        tags.putUUID("faction", factionUUID);
        tags.putInt("colour", colour);
        tags.putString("name", factionName);
        tags.putString("flagId", factionFlagId);
        tags.putByte("rotation", rotation);
        tags.putFloat("poleLength", poleLength);
        return tags;
    }

    @Override
    public void handleUpdateTag(CompoundTag tags, HolderLookup.Provider registries) {
        factionUUID = tags.getUUID("faction");
        colour = tags.getInt("colour");
        factionName = tags.getString("name");
        factionFlagId = tags.getString("flagId");
        rotation = tags.getByte("rotation");
        poleLength = tags.contains("poleLength") ? tags.getFloat("poleLength") : DEFAULT_POLE_LENGTH;
    }

}
