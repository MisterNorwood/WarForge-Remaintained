package com.flansmod.warforge.common.world;

import java.util.Random;

import com.flansmod.warforge.common.ModuloHelper;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;

public class WorldGenBedrockOre extends WorldGenerator 
{
	private IBlockState mBlock, mOuter;
	private int mCellSize = 64;
	private int mDepositRadius = 4;
	private int mOuterShellRadius = 8;
	private float mOuterShellProbability = 0.1f;
	private int mMinInstancesPerCell = 1;
	private int mMaxInstancesPerCell = 3;
	private int mMinHeight = 0;
	private int mMaxHeight = 5;
	
	public WorldGenBedrockOre(IBlockState block, IBlockState outer, int cellSize, int depositRadius, int outerShellRadius, float outerShellChance,
			int minInstancesPerCell, int maxInstancesPerCell, int minHeight, int maxHeight)
	{
		mBlock = block;
		mOuter = outer;
		mCellSize = cellSize;
		mDepositRadius = depositRadius;
		mOuterShellRadius = outerShellRadius;
		mOuterShellProbability = outerShellChance;
		mMinInstancesPerCell = minInstancesPerCell;
		mMaxInstancesPerCell = maxInstancesPerCell;
		mMinHeight = minHeight;
		mMaxHeight = maxHeight;
		
		if(mDepositRadius * 2 >= mCellSize)
		{
			mCellSize = mDepositRadius * 4;
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
			int depositX = xCell * mCellSize + 8 + cellRNG.nextInt(mCellSize - 4 * mDepositRadius) + mDepositRadius * 2;
			int depositZ = zCell * mCellSize + 8 + cellRNG.nextInt(mCellSize - 4 * mDepositRadius) + mDepositRadius * 2;
			BlockPos depositPosA = new BlockPos(depositX, mMinHeight + cellRNG.nextInt(mMaxHeight - mMinHeight), depositZ);
						
			int minY = depositPosA.getY() - mDepositRadius;
			int maxY = depositPosA.getY() + mDepositRadius;			
			
			for(int i = 8; i < 24; i++)
			{
				for(int k = 8; k < 24; k++)
				{
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
					
					// And create surface indicators, but with high rarity
					if(rand.nextFloat() < mOuterShellProbability * 0.25f) 
					{
						BlockPos y0Pos = new BlockPos(cornerPos.getX() + i, 0, cornerPos.getZ() + k);
						if(y0Pos.distanceSq(depositPosA.getX(), 0, depositPosA.getZ()) <= mDepositRadius * mDepositRadius)
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
