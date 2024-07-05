package com.flansmod.warforge.common.world;

import java.util.Random;

import com.flansmod.warforge.common.ModuloHelper;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;

public class WorldGenShulkerFossil extends WorldGenerator
{
	private IBlockState mBase, mFossil;
	
	private int mCellSize = 256;
	private int mMinInstancesPerCell = 1;
	private int mMaxInstancesPerCell = 2;
	private double mMinRotations = 2.0d;
	private double mMaxRotations = 5.0d;
	private double mDiscThickness = 3.0d;
	private int mMinHeight = 12;
	private int mMaxHeight = 106;
	private double mRadiusPerRotation = 3.0d;
	
	public WorldGenShulkerFossil(IBlockState base, IBlockState fossil,
			int cellSize, int minInst, int maxInst, float minRot, float maxRot, float radiusPer, float discThick,
			int minHeight, int maxHeight)
	{
		mBase = base;
		mFossil = fossil;
		mCellSize = cellSize;
		mMinInstancesPerCell = minInst;
		mMaxInstancesPerCell = maxInst;
		mMinRotations = minRot;
		mMaxRotations = maxRot;
		mDiscThickness = discThick;
		mMinHeight = minHeight;
		mMaxHeight = maxHeight;
		mRadiusPerRotation = radiusPer;
	}
	
	@Override
	public boolean generate(World world, Random rand, BlockPos cornerPos) 
	{
		// We generate the 8-24 area offset from pos. So +16 is our centerpoint
		int x = cornerPos.getX() + 8;
		int z = cornerPos.getZ() + 8;
		
		// Don't place in the inner ring
		if(x * x + z * z < 1000d * 1000d)
			return true;
		
		int xCell = ModuloHelper.divide(x, mCellSize);
		int zCell = ModuloHelper.divide(z, mCellSize);
		
		Random cellRNG = new Random();
		cellRNG.setSeed(world.getSeed());
		long seedA = cellRNG.nextLong() / 2L * 2L + 1L;
		long seedB = cellRNG.nextLong() / 2L * 2L + 1L;
		cellRNG.setSeed((long)xCell * seedA + (long)zCell * seedB ^ world.getSeed());
		
		int numDepositsInCell = cellRNG.nextInt(mMaxInstancesPerCell - mMinInstancesPerCell + 1) + mMinInstancesPerCell;
		for(int cellIndex = 0; cellIndex < numDepositsInCell; cellIndex++)
		{
			double numRotations = cellRNG.nextDouble() * (mMaxRotations - mMinRotations) + mMinRotations;
			double radiusD = numRotations * mRadiusPerRotation + 2.0d;
			int radius = MathHelper.ceil(radiusD);
			Vec3d axis = new Vec3d(cellRNG.nextGaussian(), cellRNG.nextGaussian(), cellRNG.nextGaussian());
			axis = axis.normalize();
			Vec3d spanX = axis.crossProduct(new Vec3d(0, 1, 0)).normalize();
			Vec3d spanZ = axis.crossProduct(spanX).normalize();
			
			
			// Choose a random starting point, at least one radius from the edge of the cell
			int depositX = xCell * mCellSize + 8 + cellRNG.nextInt(mCellSize - 4 * radius) + radius * 2;
			int depositZ = zCell * mCellSize + 8 + cellRNG.nextInt(mCellSize - 4 * radius) + radius * 2;
			BlockPos depositPosA = new BlockPos(depositX, mMinHeight + cellRNG.nextInt(mMaxHeight - mMinHeight), depositZ);
						
			int minY = 0; //depositPosA.getY() - radius;
			int maxY = 128; //depositPosA.getY() + radius;			
			
			for(int i = 8; i < 24; i++)
			{
				for(int k = 8; k < 24; k++)
				{
					for(int j = minY; j < maxY; j++)
					{
						BlockPos p = new BlockPos(cornerPos.getX() + i, j, cornerPos.getZ() + k);
						Vec3d delta = new Vec3d(p.getX() - depositX, p.getY() - depositPosA.getY(), p.getZ() - depositZ);
						
						double dotA = delta.dotProduct(axis);
						if(Math.abs(dotA) > mDiscThickness)
							continue;
						
						// Get into the plane of the disc
						double dotX = delta.dotProduct(spanX);
						double dotZ = delta.dotProduct(spanZ);
						
						//if(dotX >= radiusD)
						//	continue;
						//if(dotZ >= radiusD)
						//	continue;
						
						if(dotX * dotX + dotZ * dotZ <= radiusD * radiusD)
						{
							double r = Math.sqrt(dotX * dotX + dotZ * dotZ);
							
							// Set the core as a disc that thins away at the edges
							if(Math.abs(dotA) <= mDiscThickness / (r / 3d + 0.01d))
								world.setBlockState(p, mBase);
							
							// Then apply sticky out fossil bits
							double theta = Math.atan2(dotZ, dotX);
							// curve we are testing against is 
							// r = A * theta, theta in [0,numRotations]
							
							if(Math.abs(dotA) <= mDiscThickness * 1.5d / (r / 3d + 0.01d))
							{
								for(int n = 0; n < MathHelper.ceil(numRotations); n++)
								{
									double curveR = (n + theta / (Math.PI * 2f)) * mRadiusPerRotation;
									
									if(Math.abs(r - curveR) < 0.5d)
										world.setBlockState(p, mFossil);
								}
							}
							
							//if(dotX > 0d && dotZ > 0d)
							//	world.setBlockState(p, mFossil);
							//else
							//	world.setBlockState(p, mBase);
						}
					}
				}
			}
		}
		
		return false;
	}

}
