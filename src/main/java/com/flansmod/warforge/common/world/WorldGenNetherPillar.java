package com.flansmod.warforge.common.world;

import java.util.Random;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;

public class WorldGenNetherPillar extends WorldGenerator 
{
	private IBlockState mDense, mOuter;
	
	public WorldGenNetherPillar(IBlockState dense, IBlockState outer)
	{
		mDense = dense;
		mOuter = outer;
	}
	
	@Override
	public boolean generate(World worldIn, Random rand, BlockPos position) 
	{
		for(int i = 0; i < 16; i++)
		{
			for(int k = 0; k < 16; k++)
			{
				double xzDistSq = (i - 8) * (i - 8) + (k - 8) * (k - 8);
				for(int j = 1; j < 127; j++)
				{
					double targetRadius = 1 + 5 * (Math.abs(j - 64) / (double)64);
					BlockPos pos = position.add(i + 8, j - position.getY(), k + 8);					
					if(xzDistSq <= targetRadius * targetRadius)
					{
						if(xzDistSq <= (targetRadius - 1) * (targetRadius - 1))
						{
							worldIn.setBlockState(pos, mDense);
						}
						else if(rand.nextInt(3) == 0)
						{
							
						}
						else
						{
							worldIn.setBlockState(pos, rand.nextInt(3) == 0 ? mOuter : Blocks.NETHERRACK.getDefaultState());
						}
					}
				}
			}
		}
		
		return false;
	}
	
	
}
