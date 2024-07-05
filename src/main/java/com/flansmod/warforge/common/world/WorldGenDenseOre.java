package com.flansmod.warforge.common.world;

import java.util.Random;

import com.flansmod.warforge.common.ModuloHelper;
import com.flansmod.warforge.common.WarForgeMod;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.gen.NoiseGeneratorOctaves;
import net.minecraft.world.gen.feature.WorldGenerator;

public class WorldGenDenseOre extends WorldGenerator
{
	private IBlockState mBlock, mOuter;
	private int mCellSize = 64;
	private int mDepositRadius = 4;
	private int mOuterShellRadius = 8;
	private float mOuterShellProbability = 0.1f;
	private int mMinInstancesPerCell = 1;
	private int mMaxInstancesPerCell = 3;
	private int mMinHeight = 110;
	private int mMaxHeight = 120;
	
	public WorldGenDenseOre(IBlockState block, IBlockState outer, int cellSize, int depositRadius, int outerShellRadius, float outerShellChance,
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
			
			// Offset by a random amount for the endpoint
			BlockPos depositPosB = new BlockPos(
					depositPosA.getX() + cellRNG.nextInt(9) - 4, 
					depositPosA.getY() + cellRNG.nextInt(7) - 3, 
					depositPosA.getZ() + cellRNG.nextInt(9) - 4);
			Vec3i depositPosDelta = new Vec3i(cellRNG.nextInt(9) - 4, cellRNG.nextInt(7) - 3, cellRNG.nextInt(9) - 4);
			
			double lengthSq = depositPosB.distanceSq(depositPosA);
			int minY = Math.min(depositPosA.getY(), depositPosB.getY()) - mDepositRadius;
			int maxY = Math.max(depositPosA.getY(), depositPosB.getY()) + mDepositRadius;
			
			for(int i = 8; i < 24; i++)
			{
				for(int k = 8; k < 24; k++)
				{
					// Create the vein
					for(int j = minY; j < maxY; j++)
					{
						BlockPos p = new BlockPos(cornerPos.getX() + i, j, cornerPos.getZ() + k);
						BlockPos delta = depositPosA.subtract(p);
						
						if(world.getBlockState(p).getBlock() == Blocks.WATER
						|| world.getBlockState(p).getBlock() == Blocks.FLOWING_WATER
						|| world.getBlockState(p).getBlock() == Blocks.BEDROCK)
						{
							continue;
						}
						
						double distToLineSq = delta.crossProduct(depositPosDelta).distanceSq(Vec3i.NULL_VECTOR) / depositPosDelta.distanceSq(Vec3i.NULL_VECTOR);
						double radius = mDepositRadius + rand.nextGaussian();
						
						if(distToLineSq <= radius * radius
						&& p.distanceSq(depositPosA) <= (radius * radius + lengthSq)
						&& p.distanceSq(depositPosB) <= (radius * radius + lengthSq))
							world.setBlockState(p, mBlock);
						
						radius = mOuterShellRadius;
						if(radius > 0 && rand.nextFloat() < mOuterShellProbability)
						{
							if(distToLineSq <= radius * radius
							&& p.distanceSq(depositPosA) <= (radius * radius + lengthSq)
							&& p.distanceSq(depositPosB) <= (radius * radius + lengthSq))
								world.setBlockState(p, mOuter);
						}
					}
					
					// And create surface indicators, but with high rarity
					if(rand.nextFloat() < mOuterShellProbability * 0.25f) 
					{
						BlockPos y0Pos = new BlockPos(cornerPos.getX() + i, 0, cornerPos.getZ() + k);
						if(y0Pos.distanceSq(depositPosA.getX(), 0, depositPosA.getZ()) <= mDepositRadius * mDepositRadius
						|| y0Pos.distanceSq(depositPosB.getX(), 0, depositPosB.getZ()) <= mDepositRadius * mDepositRadius)
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
