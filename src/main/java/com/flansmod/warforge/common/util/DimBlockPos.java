package com.flansmod.warforge.common.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public class DimBlockPos extends BlockPos
{
	public static final DimBlockPos ZERO = new DimBlockPos(Level.OVERWORLD, 0, 0, 0);

	public ResourceKey<Level> dim;

	public DimBlockPos(ResourceKey<Level> dim, int x, int y, int z)
    {
        super(x, y, z);
        this.dim = dim;
    }

    public DimBlockPos(ResourceKey<Level> dim, double x, double y, double z)
    {
        super((int)x, (int)y, (int)z);
        this.dim = dim;
    }

    public DimBlockPos(Entity source)
    {
        super(source.blockPosition().getX(), source.blockPosition().getY(), source.blockPosition().getZ());
        dim = source.level().dimension();
    }

    public DimBlockPos(BlockEntity source)
    {
        super(source.getBlockPos().getX(), source.getBlockPos().getY(), source.getBlockPos().getZ());
        dim = source.getLevel().dimension();
    }

    public DimBlockPos(ResourceKey<Level> dim, Vec3 vec)
    {
        super((int)vec.x, (int)vec.y, (int)vec.z);
        this.dim = dim;
    }

    public DimBlockPos(ResourceKey<Level> dim, Vec3i source)
    {
    	super(source.getX(), source.getY(), source.getZ());
    	this.dim = dim;
    }

    public DimChunkPos toChunkPos()
    {
    	return new DimChunkPos(dim, getX() >> 4, getZ() >> 4);
    }

    public BlockPos toRegularPos()
    {
    	return new BlockPos(getX(), getY(), getZ());
    }

    @Override
    public BlockPos relative(Direction facing, int n)
    {
        return n == 0 ? this : new DimBlockPos(this.dim, this.getX() + facing.getStepX() * n, this.getY() + facing.getStepY() * n, this.getZ() + facing.getStepZ() * n);
    }

	// HASHING INTO A MAP DEPENDENT ON BLOCKPOS (VANILLA METHODS) WILL RETURN NULL DUE TO THIS CUSTOM IMPL HAVING A DIFFERENT VALUE
	// (dimBlockPos -> func(BlockPos) -> hashMap<BlockPos>.get(blockPos.hashCode() [dimBlockPos.hashCode != blockPos.hashCode] -> diff value -> null
	@Override
	public int hashCode()
    {
		return super.hashCode() ^ (155225 * this.dim.hashCode() + 140501023);
    }

	@Override
    public boolean equals(Object other)
    {
        if (this == other)
            return true;

        if (!(other instanceof DimBlockPos dcpos))
            return false;

        return this.dim.equals(dcpos.dim)
        		&& this.getX() == dcpos.getX()
        		&& this.getY() == dcpos.getY()
        		&& this.getZ() == dcpos.getZ();
    }

	@Override
    public String toString()
    {
        return "[" + this.dim.location() + ": " + this.getX() + ", " + this.getY() + ", " + this.getZ() + "]";
    }

	public String toFancyString()
	{
		return "[" + getX() + ", " + getY() + ", " + getZ() + "] in " + getDimensionName();
	}

	public String getDimensionName()
	{
		if (dim.equals(Level.NETHER))
			return "The Nether";
		if (dim.equals(Level.OVERWORLD))
			return "The Overworld";
		if (dim.equals(Level.END))
			return "The End";
		return "Dimension " + dim.location();
	}

	public CompoundTag writeToNBT()
	{
		CompoundTag tag = new CompoundTag();
		tag.putString("dim", dim.location().toString());
		tag.putIntArray("pos", new int[] { getX(), getY(), getZ() });
		return tag;
	}

	public void writeToNBT(CompoundTag tags, String prefix)
	{
		tags.put(prefix, writeToNBT());
	}

	public static DimBlockPos readFromNBT(CompoundTag tags, String prefix)
	{
		if (tags.contains(prefix))
			return readFromNBT(tags.getCompound(prefix));
		return DimBlockPos.ZERO;
	}

	public static DimBlockPos readFromNBT(CompoundTag tag)
	{
		if (tag != null && tag.contains("dim") && tag.contains("pos"))
		{
			int[] data = tag.getIntArray("pos");
			if (data.length == 3)
			{
				ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("dim")));
				return new DimBlockPos(dim, data[0], data[1], data[2]);
			}
		}
		return DimBlockPos.ZERO;
	}
}
