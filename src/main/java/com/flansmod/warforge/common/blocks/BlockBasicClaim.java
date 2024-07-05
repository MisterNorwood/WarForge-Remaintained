package com.flansmod.warforge.common.blocks;

import java.util.UUID;

import com.flansmod.warforge.common.CommonProxy;
import com.flansmod.warforge.common.DimChunkPos;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.network.PacketFactionInfo;
import com.flansmod.warforge.server.Faction;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBeacon;
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
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class BlockBasicClaim extends Block implements ITileEntityProvider
{
	public BlockBasicClaim(Material materialIn) 
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

	/* No usages but it errors sooooooooo
	@SideOnly(Side.CLIENT)
    @Override
    public BlockRenderLayer getBlockLayer() { return BlockRenderLayer.CUTOUT; }
    */

	@Override
	public TileEntity createNewTileEntity(World worldIn, int meta)
	{
		if(this == WarForgeMod.CONTENT.basicClaimBlock)
			return new TileEntityBasicClaim();
		else
			return new TileEntityReinforcedClaim();
	}
	
	@Override
	public boolean canPlaceBlockAt(World world, BlockPos pos)
	{
		if(!world.isRemote)
		{
			// Can't claim a chunk claimed by another faction
			UUID existingClaim = WarForgeMod.FACTIONS.GetClaim(new DimChunkPos(world.provider.getDimension(), pos));
			if(!existingClaim.equals(Faction.NULL))
				return false;
					
			// Can only place on a solid surface
			if(!world.getBlockState(pos.add(0, -1, 0)).isSideSolid(world, pos.add(0, -1, 0), EnumFacing.UP))
				return false;
		}
		
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
				TileEntityBasicClaim claim = (TileEntityBasicClaim)te;

				WarForgeMod.FACTIONS.OnNonCitadelClaimPlaced(claim, placer);
			}
		}
    }
	
	@Override
	public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float par7, float par8, float par9)
	{
		if(player.isSneaking())
			return false;
		if(!world.isRemote)
		{
			Faction playerFaction = WarForgeMod.FACTIONS.GetFactionOfPlayer(player.getUniqueID());
			TileEntityBasicClaim claimTE = (TileEntityBasicClaim)world.getTileEntity(pos);
			
			// Any factionless players, and players who aren't in this faction get an info panel			
			if(playerFaction == null || !playerFaction.mUUID.equals(claimTE.mFactionUUID))
			{
				Faction citadelFaction = WarForgeMod.FACTIONS.GetFaction(claimTE.mFactionUUID);
				if(citadelFaction != null)
				{
					PacketFactionInfo packet = new PacketFactionInfo();
					packet.mInfo = citadelFaction.CreateInfo();
					WarForgeMod.INSTANCE.NETWORK.sendTo(packet, (EntityPlayerMP) player);
				}
				else
				{
					player.sendMessage(new TextComponentString("This claim has no faction."));
				}
			}
			// So anyone else will be from the target faction
			else
			{
				player.openGui(WarForgeMod.INSTANCE, CommonProxy.GUI_TYPE_BASIC_CLAIM, world, pos.getX(), pos.getY(), pos.getZ());
			}
		}
		return true;
	}
	
	@Override
    public boolean canEntityDestroy(IBlockState state, IBlockAccess world, BlockPos pos, Entity entity)
    {
        return false;
    }
	
	@Override
	public void breakBlock(World worldIn, BlockPos pos, IBlockState state)
	{
        TileEntity tileentity = worldIn.getTileEntity(pos);

        if (tileentity instanceof TileEntityYieldCollector)
        {
            InventoryHelper.dropInventoryItems(worldIn, pos, (TileEntityYieldCollector)tileentity);
            worldIn.updateComparatorOutputLevel(pos, this);
        }

        super.breakBlock(worldIn, pos, state);
    }
	
}
