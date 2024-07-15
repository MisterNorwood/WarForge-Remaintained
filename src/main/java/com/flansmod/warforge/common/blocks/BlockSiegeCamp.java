package com.flansmod.warforge.common.blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.flansmod.warforge.common.CommonProxy;
import com.flansmod.warforge.common.DimBlockPos;
import com.flansmod.warforge.common.DimChunkPos;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.network.PacketSiegeCampInfo;
import com.flansmod.warforge.common.network.SiegeCampAttackInfo;
import com.flansmod.warforge.server.Faction;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFurnace;
import net.minecraft.block.BlockWorkbench;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class BlockSiegeCamp extends Block implements ITileEntityProvider
{
	public BlockSiegeCamp(Material materialIn) 
	{
		super(materialIn);
		this.setCreativeTab(CreativeTabs.COMBAT);
		this.setBlockUnbreakable();
		this.setResistance(30000000f);
	}
	
	@Override
    public boolean isOpaqueCube(IBlockState state) { return false; }
	@Override
    public boolean isFullCube(IBlockState state) { return false; }
	@Override
    public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
	@Override
	public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
		return layer == BlockRenderLayer.TRANSLUCENT;
	}


	/* Unused code that errors #5
	@SideOnly(Side.CLIENT)
    @Override
    public BlockRenderLayer getBlockLayer() { return BlockRenderLayer.CUTOUT; }
	*/

	@Override
	public TileEntity createNewTileEntity(World worldIn, int meta)
	{
		return new TileEntitySiegeCamp();
	}

	@Override
	public boolean canPlaceBlockAt(World world, BlockPos pos)
	{
		// Can't claim a chunk claimed by another faction
		if(!world.isRemote)
		{
			UUID existingClaim = WarForgeMod.FACTIONS.GetClaim(new DimChunkPos(world.provider.getDimension(), pos));
			if(!existingClaim.equals(Faction.NULL))
				return false;
		}
	
		// Can only place on a solid surface
		if(!world.getBlockState(pos.add(0, -1, 0)).isSideSolid(world, pos.add(0, -1, 0), EnumFacing.UP))
			return false;
		
		return true;
	}
	
	@Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack)
    {
		if(!world.isRemote)
		{
			TileEntity te = world.getTileEntity(pos);
			if(te != null)
			{
				TileEntitySiegeCamp siegeCamp = (TileEntitySiegeCamp)te;
				WarForgeMod.FACTIONS.OnNonCitadelClaimPlaced(siegeCamp, placer);
				if(placer instanceof EntityPlayerMP)
				{
					WarForgeMod.FACTIONS.RequestPlaceFlag((EntityPlayerMP)placer, new DimBlockPos(world.provider.getDimension(), pos));
				}
			}
		}
    }
	
	private List<SiegeCampAttackInfo> CalculatePossibleAttackDirections(World world, BlockPos pos)
	{
		List<SiegeCampAttackInfo> list = new ArrayList<SiegeCampAttackInfo>();
		
		DimChunkPos siegeCampPos = new DimChunkPos(world.provider.getDimension(), pos);
		TileEntitySiegeCamp siegeCamp = (TileEntitySiegeCamp)world.getTileEntity(pos);
		
		if(siegeCamp != null)
		{
			for(TileEntity te : world.loadedTileEntityList)
			{
				if(te instanceof IClaim)
				{
					IClaim claim = (IClaim)te;
					if(claim.CanBeSieged() && !claim.GetFaction().equals(siegeCamp.GetFaction()))
					{
						DimChunkPos claimPos = claim.GetPos().ToChunkPos();
						for(EnumFacing facing : EnumFacing.HORIZONTALS)
						{
							if(claimPos.equals(siegeCampPos.Offset(facing, 1)))
							{
								Faction faction = WarForgeMod.FACTIONS.GetFaction(claim.GetFaction());
								if(faction != null)
								{
									SiegeCampAttackInfo info = new SiegeCampAttackInfo();
									
									info.mDirection = facing;
									info.mFactionUUID = claim.GetFaction();
									info.mFactionName = faction.mName;
									info.mFactionColour = faction.mColour;
									
									list.add(info);
								}
								else
								{
									WarForgeMod.LOGGER.error("Could not find faction with UUID " + claim.GetFaction());
								}
							}
						}
					}
				}
			}
		}
		return list;
	}
	
	@Override
	public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float par7, float par8, float par9)
	{
		if(player.isSneaking())
			return false;
		if(!world.isRemote)
		{
			DimChunkPos chunkPos = new DimChunkPos(world.provider.getDimension(), pos);
			if(WarForgeMod.FACTIONS.IsSiegeInProgress(chunkPos))
			{
				WarForgeMod.FACTIONS.SendAllSiegeInfoToNearby();
			}

			PacketSiegeCampInfo info = new PacketSiegeCampInfo();
			info.mPossibleAttacks = CalculatePossibleAttackDirections(world, pos);
			info.mSiegeCampPos = new DimBlockPos(world.provider.getDimension(), pos);
			WarForgeMod.INSTANCE.NETWORK.sendTo(info, (EntityPlayerMP)player);
		}
		//player.openGui(WarForgeMod.INSTANCE, CommonProxy.GUI_TYPE_SIEGE_CAMP, world, pos.getX(), pos.getY(), pos.getZ());
		return true;
	}
	
	@Override
    public boolean canEntityDestroy(IBlockState state, IBlockAccess world, BlockPos pos, Entity entity)
    {
        return false;
    }
}
