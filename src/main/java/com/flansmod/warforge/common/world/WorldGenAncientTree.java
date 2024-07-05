package com.flansmod.warforge.common.world;

import java.util.Random;

import com.flansmod.warforge.common.ModuloHelper;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.BiomeManager.BiomeType;

public class WorldGenAncientTree extends WorldGenerator  
{
	private IBlockState mBlock, mOuter, mLeaf;
	private int mCellSize = 64;
	private float mChance = 0.1f;
	private float mHoleRadius = 10;
	private float mTreeRadius = 5;
	private float mCoreRadius = 2;
	private float mBranchRadius = 3;
	private float mMaxHeight = 128;
	private int mMinDepth = 16;
	
	public WorldGenAncientTree(IBlockState block, IBlockState outer, IBlockState leaf, int cellSize, 
			float chance, float holeRadius, float coreRadius, float trunkRadius, float height)
	{
		mBlock = block;
		mOuter = outer;
		mLeaf = leaf;
		mCellSize = cellSize;
		mChance = chance;
		mCoreRadius = coreRadius;
		mTreeRadius = trunkRadius;
		mHoleRadius = holeRadius;
		mMaxHeight = height;
		
		if(mHoleRadius * 4 + 2 > mCellSize)
		{
			mCellSize = MathHelper.ceil(mHoleRadius * 4 + 2);
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
		
		if(cellRNG.nextFloat() < mChance) // Only place in some cells, keep it rare
		{
			int depositX = xCell * mCellSize + 8 + cellRNG.nextInt(mCellSize - 4 * (int)mHoleRadius) + (int)mHoleRadius * 2;
			int depositZ = zCell * mCellSize + 8 + cellRNG.nextInt(mCellSize - 4 * (int)mHoleRadius) + (int)mHoleRadius * 2;
			
			if(BiomeDictionary.hasType(world.getBiome(new BlockPos(depositX, 0, depositZ)), BiomeDictionary.Type.FOREST))
			{
				
				final int NUM_BRANCHES = 12;
				float[] branchHeights = new float[NUM_BRANCHES];
				float[] branchAngles = new float[NUM_BRANCHES];
				for(int i = 0; i < NUM_BRANCHES; i++)
				{
					branchHeights[i] = mMinDepth + (cellRNG.nextFloat() + i) * (float)(mMaxHeight - mMinDepth) / (float)NUM_BRANCHES;
					if(Math.abs(branchHeights[i] - 64) < 8)
						branchHeights[i] += 12;
					if(Math.abs(branchHeights[i] - 56) < 8)
						branchHeights[i] -= 12;
					branchAngles[i] = cellRNG.nextFloat() * 360f;
				}
				
				for(int i = 8; i < 24; i++)
				{
					for(int k = 8; k < 24; k++)
					{
						BlockPos p = new BlockPos(cornerPos.getX() + i, 0, cornerPos.getZ() + k);
						
						int deltaX = p.getX() - depositX;
						int deltaZ = p.getZ() - depositZ;
						
						double distanceFromCenter = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ );
						
						// Dig a hole
						if(distanceFromCenter < mHoleRadius)
						{
							double edgeToTreeBlend = (distanceFromCenter - mTreeRadius) / (mHoleRadius - mTreeRadius);
							edgeToTreeBlend = MathHelper.clamp(edgeToTreeBlend, 0, 1);
							int minHeight = MathHelper.floor(mMinDepth + (64 - mMinDepth) * edgeToTreeBlend);
							minHeight += rand.nextInt(3);
							
							for(int j = minHeight; j < mMaxHeight; j++)
							{
								BlockPos pos = p.add(0, j, 0);
								
								double heightParam = (double)(j - minHeight) / (double)(mMaxHeight - minHeight);
								double trunkRadius = mCoreRadius + (1.0d - heightParam) * (mTreeRadius - mCoreRadius);
								double coreRadius = mCoreRadius * (1.0d - heightParam);
								
								if(distanceFromCenter < coreRadius)
								{
									world.setBlockState(pos, mBlock);
								}
								else if(distanceFromCenter < trunkRadius)
								{
									world.setBlockState(pos, mOuter);
								}
								else 
								{
									double branchExtents = 
											j > 64 ? mHoleRadius * (0.5d + 0.5d * ((float)(j - mMinDepth) / (float)(mMaxHeight - mMinDepth)))
											: mHoleRadius;
									boolean isBranch = false;
									
									if(distanceFromCenter < branchExtents)
									{
										// Test to see if this is part of a branch
										for(int n = 0; n < NUM_BRANCHES; n++)
										{
											int testHeight = j < 64 ? MathHelper.ceil(j + distanceFromCenter * 0.5d) : MathHelper.ceil(j - distanceFromCenter * 0.5d);
											// Quick test to see if we are at the right height
											if(branchHeights[n] - mBranchRadius <= testHeight && testHeight <= branchHeights[n] + mBranchRadius)
											{
												double cos = Math.cos((branchAngles[n] + distanceFromCenter * (n % 2== 0 ? 2 : -2)) * Math.PI / 180d);
												double sin = Math.sin((branchAngles[n] + distanceFromCenter * (n % 2== 0 ? 2 : -2)) * Math.PI / 180d);
												double rotX = deltaX * cos + deltaZ * sin;
												double rotZ = -deltaX * sin + deltaZ * cos;
												double distanceToBranchSpline = Math.min(Math.abs(rotX), Math.abs(rotZ));
												double deltaY = testHeight - branchHeights[n];
												distanceToBranchSpline = Math.sqrt(distanceToBranchSpline * distanceToBranchSpline + deltaY * deltaY);
												
												if(distanceToBranchSpline < mBranchRadius * ((1.0d - heightParam) * (1.0d - distanceFromCenter / mHoleRadius) + 0.5d))
												{
													world.setBlockState(pos, mOuter);
													isBranch = true;
												}
											}
											// Or leaf height
											if(j > 64 && distanceFromCenter > mTreeRadius)
											{
												if(branchHeights[n] - mBranchRadius + 3 <= testHeight && testHeight <= branchHeights[n] + mBranchRadius + 3)
												{
													double cos = Math.cos((branchAngles[n] + distanceFromCenter * (n % 2== 0 ? 2 : -2)) * Math.PI / 180d);
													double sin = Math.sin((branchAngles[n] + distanceFromCenter * (n % 2== 0 ? 2 : -2)) * Math.PI / 180d);
													double rotX = deltaX * cos + deltaZ * sin;
													double rotZ = -deltaX * sin + deltaZ * cos;
													double distanceToBranchSpline = Math.min(Math.abs(rotX), Math.abs(rotZ));
													double deltaY = testHeight - branchHeights[n];
													
													distanceToBranchSpline = Math.sqrt(distanceToBranchSpline * distanceToBranchSpline + deltaY * deltaY);
													
													if(distanceToBranchSpline < mBranchRadius * 2d * ((1.0d - heightParam) * (1.0d - Math.min(0.5d * mHoleRadius, distanceFromCenter) / mHoleRadius) + 0.5d))
													{
														world.setBlockState(pos, mLeaf);
														isBranch = true;
													}
												}
											}
										}
									}
									
									if(!isBranch)
									{
										world.setBlockToAir(pos);
									}
								}
							}
							
						
						}
					}
				}
			}
		}
		
		return false;
	}

}
