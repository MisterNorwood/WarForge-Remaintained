package com.flansmod.warforge.common.world;

import java.util.Random;

import com.flansmod.warforge.common.ModuloHelper;
import com.flansmod.warforge.common.WarForgeMod;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;

public class WorldGenSlimeFountain extends WorldGenerator 
{
	private IBlockState mFloor, mLiquid, mWall;
	private int mCellSize = 64;
	private int mLakeRadius = 4;
	private int mLakeCeilingHeight = 8;
	private int mMinInstancesPerCell = 1;
	private int mMaxInstancesPerCell = 2;
	private int mMinHeight = 0;
	private int mMaxHeight = 5;
	
	public WorldGenSlimeFountain(IBlockState floor, IBlockState liquid, IBlockState wall,
			int cellSize,
			int lakeRadius,
			int lakeCeilingHeight,
			int minInstancesPerCell,
			int maxInstancesPerCell,
			int minHeight,
			int maxHeight)
	{
		mFloor = floor;
		mLiquid = liquid;
		mWall = wall;
		
		mCellSize = cellSize;
		mLakeRadius = lakeRadius;
		mLakeCeilingHeight = lakeCeilingHeight;
		mMinInstancesPerCell = minInstancesPerCell;
		mMaxInstancesPerCell = maxInstancesPerCell;
		mMinHeight = minHeight;
		mMaxHeight = maxHeight;
		
		if(mCellSize <= 5 * mLakeRadius)
		{
			WarForgeMod.LOGGER.warn("Lake radius vs cell size ratio too extreme");
			mCellSize = 5 * mLakeRadius;
		}
	}
	
	@Override
	public boolean generate(World world, Random rand, BlockPos cornerPos) 
	{
		// We generate the 8-24 area offset from pos. So +16 is our centerpoint
		int x = cornerPos.getX() + 8;
		int z = cornerPos.getZ() + 8;
		
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
			// Choose a random starting point, at least one radius from the edge of the cell
			int depositX = xCell * mCellSize + 8 + cellRNG.nextInt(mCellSize - 4 * mLakeRadius) + mLakeRadius * 2;
			int depositZ = zCell * mCellSize + 8 + cellRNG.nextInt(mCellSize - 4 * mLakeRadius) + mLakeRadius * 2;
			BlockPos depositPosA = new BlockPos(depositX, mMinHeight + cellRNG.nextInt(mMaxHeight - mMinHeight), depositZ);
						
			int minY = depositPosA.getY() - mLakeCeilingHeight;
			int maxY = depositPosA.getY() + mLakeCeilingHeight;			
			
			int islandPosX = depositX + cellRNG.nextInt(15) - 7;
			int islandPosZ = depositZ + cellRNG.nextInt(15) - 7;
			
			
			for(int i = 8; i < 24; i++)
			{
				for(int k = 8; k < 24; k++)
				{
					// Place liquid and air
					for(int j = minY; j < maxY; j++)
					{
						BlockPos p = new BlockPos(cornerPos.getX() + i, j, cornerPos.getZ() + k);
						
						double deltaX = p.getX() - depositPosA.getX();
						double deltaY = p.getY() - depositPosA.getY();
						double deltaZ = p.getZ() - depositPosA.getZ();
						
						// Ellipsoid
						double distSq = (deltaX * deltaX + deltaZ * deltaZ) / (mLakeRadius * mLakeRadius)
										+ (deltaY * deltaY) / (mLakeCeilingHeight * mLakeCeilingHeight)
										+ rand.nextDouble() * 0.1d;
						
						deltaX = p.getX() - islandPosX;
						deltaZ = p.getZ() - islandPosZ;
						
						double distSqToPillar = (deltaX * deltaX + deltaZ * deltaZ) / (mLakeRadius * mLakeRadius)
								+ rand.nextDouble() * 0.05d;
						
						if(distSq <= 0.8d * 0.8d && distSqToPillar >= 0.25d * 0.25d)
						{
							if(j > depositPosA.getY())
							{
								world.setBlockState(p, Blocks.AIR.getDefaultState());
							}
							else if(j == depositPosA.getY()) 
							{
								if(rand.nextInt(8) == 0)
									world.setBlockState(p, Blocks.WATERLILY.getDefaultState());
								else world.setBlockState(p, Blocks.AIR.getDefaultState());
							}
							else
							{
								world.setBlockState(p, mLiquid);
							}
						}
						else if(distSq <= 0.9d * 0.9d )
						{
							Block existing = world.getBlockState(p).getBlock();
							if(existing != Blocks.BEDROCK && existing != Blocks.END_PORTAL_FRAME && existing != Blocks.WATER && existing != Blocks.FLOWING_WATER)
							{
								if(j > depositPosA.getY() - rand.nextInt(6))
								{
									world.setBlockState(p, mWall);
								}
								else
								{
									world.setBlockState(p, mFloor);
								}
							}
						}
					}
					
					/*
					// Create the vein, cap to y=4 because we are replacing bedrock blocks
					// We don't mind making holes, because we are indestructible too
					for(int j = minY; j < Math.min(maxY, 5); j++)
					{
						BlockPos p = new BlockPos(cornerPos.getX() + i, j, cornerPos.getZ() + k);
						if(world.getBlockState(p).getBlock() == Blocks.BEDROCK)
						{							
							double radius = mDepositRadius + rand.nextGaussian();
							
							if(p.distanceSq(depositPosA) <= (radius * radius))
								world.setBlockState(p, mBlock);
							
							radius = mOuterShellRadius;
							// These could be breakable, so don't swap bottom layer bedrock
							if(radius > 0 && j > 0)
							{
								if(rand.nextFloat() < mOuterShellProbability)
								{
									if(p.distanceSq(depositPosA) <= (radius * radius))
										world.setBlockState(p, mOuter);
								}
							}
						}
					}
					*/
					
					// And create surface indicators, but with high rarity
					if(rand.nextFloat() < 0.05f * 0.25f) 
					{
						BlockPos y0Pos = new BlockPos(cornerPos.getX() + i, 0, cornerPos.getZ() + k);
						if(y0Pos.distanceSq(depositPosA.getX(), 0, depositPosA.getZ()) <= mLakeRadius * mLakeRadius)
						{
							BlockPos topPos = world.getTopSolidOrLiquidBlock(y0Pos);
							world.setBlockState(topPos.down(), Blocks.GRAVEL.getDefaultState());
						}
					}
				}
			}
		}
		
		return false;
	}
	
	
}
