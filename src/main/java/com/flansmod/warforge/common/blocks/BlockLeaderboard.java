package com.flansmod.warforge.common.blocks;

import java.util.UUID;

import com.flansmod.warforge.common.CommonProxy;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.network.PacketFactionInfo;
import com.flansmod.warforge.common.network.PacketLeaderboardInfo;
import com.flansmod.warforge.server.Faction;
import com.flansmod.warforge.server.Leaderboard.FactionStat;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class BlockLeaderboard extends Block implements ITileEntityProvider
{
	public FactionStat mStat;
	
	public BlockLeaderboard(Material materialIn, FactionStat stat) 
	{
		super(materialIn);
		
		this.setCreativeTab(CreativeTabs.COMBAT);
		
		this.setBlockUnbreakable();
		this.setHardness(300000000F);
		
		mStat = stat;
	}
	
	@Override
    public boolean isOpaqueCube(IBlockState state) { return false; }
	@Override
    public boolean isFullCube(IBlockState state) { return false; }
	@Override
    public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }

	/* Unused code that errors #4
	@SideOnly(Side.CLIENT)
    @Override
    public BlockRenderLayer getBlockLayer() { return BlockRenderLayer.CUTOUT; }
	*/
    @Override
	public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float par7, float par8, float par9)
	{
		if(player.isSneaking())
			return false;
		if(!world.isRemote)
		{
			UUID uuid = player.getUniqueID();
			PacketLeaderboardInfo packet = new PacketLeaderboardInfo();
			packet.mInfo = WarForgeMod.LEADERBOARD.CreateInfo(0, mStat, uuid);
			WarForgeMod.NETWORK.sendTo(packet, (EntityPlayerMP)player);
		}
		return true;
	}

	@Override
	public TileEntity createNewTileEntity(World worldIn, int meta) 
	{
		return new TileEntityLeaderboard();
	}
	
	@Override
    public boolean canEntityDestroy(IBlockState state, IBlockAccess world, BlockPos pos, Entity entity)
    {
        return false;
    }
}
