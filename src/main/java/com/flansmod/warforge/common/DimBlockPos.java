package com.flansmod.warforge.common;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraftforge.common.DimensionManager;

public class DimBlockPos extends BlockPos
{
	public static final DimBlockPos ZERO = new DimBlockPos(0, 0, 0, 0);
	
	public int mDim;
	
	public DimBlockPos(int dim, int x, int y, int z)
    {
        super(x, y, z);
        mDim = dim;
    }

    public DimBlockPos(int dim, double x, double y, double z)
    {
        super(x, y, z);
        mDim = dim;
    }

    public DimBlockPos(Entity source)
    {
        super(source);
        mDim = source.dimension;
    }
    
    public DimBlockPos(TileEntity source)
    {
        super(source.getPos().getX(), source.getPos().getY(), source.getPos().getZ());
        mDim = source.getWorld().provider.getDimension();
    }

    public DimBlockPos(int dim, Vec3d vec)
    {
        super(vec);
        mDim = dim;       
    }

    public DimBlockPos(int dim, Vec3i source)
    {
    	super(source);
    	mDim = dim;
    }
    
    public DimChunkPos ToChunkPos()
    {
    	return new DimChunkPos(mDim, getX() >> 4, getZ() >> 4);
    }
    
    public BlockPos ToRegularPos()
    {
    	return new BlockPos(getX(), getY(), getZ());
    }
    
    @Override
    public BlockPos offset(EnumFacing facing, int n)
    {
        return n == 0 ? this : new DimBlockPos(this.mDim, this.getX() + facing.getXOffset() * n, this.getY() + facing.getYOffset() * n, this.getZ() + facing.getZOffset() * n);
    }

	// HASHING INTO A MAP DEPENDENT ON BLOCKPOS (VANILLA METHODS) WILL RETURN NULL DUE TO THIS CUSTOM IMPL HAVING A DIFFERENT VALUE
	// (dimBlockPos -> func(BlockPos) -> hashMap<BlockPos>.get(blockPos.hashCode() [dimBlockPos.hashCode != blockPos.hashCode] -> diff value -> null
	@Override
	public int hashCode()
    {
		return super.hashCode() ^ (155225 * this.mDim + 140501023);
    }

	@Override
    public boolean equals(Object other)
    {
        if (this == other)
            return true;

        if (!(other instanceof DimBlockPos))
            return false;

        DimBlockPos dcpos = (DimBlockPos)other;
        return this.mDim == dcpos.mDim 
        		&& this.getX() == dcpos.getX()
        		&& this.getY() == dcpos.getY()
        		&& this.getZ() == dcpos.getZ();
    }
    
	@Override
    public String toString()
    {
        return "[" + this.mDim + ": " + this.getX() + ", " + this.getY() + ", " + this.getZ() + "]";
    }
	
	public String ToFancyString()
	{
		return "[" + getX() + ", " + getY() + ", " + getZ() + "] in " + GetDimensionName();
	}
	
	public String GetDimensionName()
	{
		switch(mDim)
		{
			case -1: return "The Nether";
			case 0: return "The Overworld";
			case 1: return "The End";
			
			default: return "Dimension #" + mDim;
		}
	}
	
	public NBTTagIntArray WriteToNBT()
	{
		return new NBTTagIntArray(new int[] {mDim, getX(), getY(), getZ()});
	}
	
	public void WriteToNBT(NBTTagCompound tags, String prefix)
	{
		tags.setIntArray(prefix, new int[] { mDim, getX(), getY(), getZ() });
	}
	
	public static DimBlockPos ReadFromNBT(NBTTagCompound tags, String prefix)
	{
		int[] data = tags.getIntArray(prefix);
		if(data.length == 4)
			return new DimBlockPos(data[0], data[1], data[2], data[3]);
		else
			return DimBlockPos.ZERO;
	}
	
	public static DimBlockPos ReadFromNBT(NBTTagIntArray tag)
	{
		if(tag != null)
		{
			int[] data = tag.getIntArray();
			if(data.length == 4)
				return new DimBlockPos(data[0], data[1], data[2], data[3]);
		}
		return DimBlockPos.ZERO;
	}
}
