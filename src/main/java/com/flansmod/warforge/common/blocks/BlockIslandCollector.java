package com.flansmod.warforge.common.blocks;

import com.cleanroommc.modularui.factory.TileEntityGuiFactory;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.server.Faction;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BlockIslandCollector extends Block implements ITileEntityProvider {
    public BlockIslandCollector(Material materialIn) {
        super(materialIn);
        setHardness(4.0f);
        setResistance(20.0f);
        setCreativeTab(CreativeTabs.COMBAT);
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileEntityIslandCollector();
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        if (world.isRemote || !(placer instanceof EntityPlayerMP)) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) placer;
        DimBlockPos collectorPos = new DimBlockPos(world.provider.getDimension(), pos);
        boolean success = WarForgeMod.FACTIONS.registerCollector(player, collectorPos);
        if (!success) {
            world.destroyBlock(pos, true);
        }
    }

    @Override
    public void breakBlock(World worldIn, BlockPos pos, IBlockState state) {
        TileEntity tileentity = worldIn.getTileEntity(pos);
        if (tileentity instanceof IInventory) {
            InventoryHelper.dropInventoryItems(worldIn, pos, (IInventory) tileentity);
        }
        if (!worldIn.isRemote) {
            WarForgeMod.FACTIONS.unregisterCollector(new DimBlockPos(worldIn.provider.getDimension(), pos));
        }
        super.breakBlock(worldIn, pos, state);
    }

    @Override
    public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (worldIn.isRemote) {
            return true;
        }

        TileEntity te = worldIn.getTileEntity(pos);
        if (te instanceof TileEntityIslandCollector collector) {
            if (!collector.getFaction().equals(Faction.nullUuid)
                    && !WarForgeMod.FACTIONS.IsPlayerInFaction(playerIn.getUniqueID(), collector.getFaction())
                    && !WarForgeMod.isOp(playerIn)) {
                return true;
            }
            WarForgeMod.syncClaimToPlayer(playerIn, pos);
            TileEntityGuiFactory.INSTANCE.open(playerIn, pos);
            return true;
        }
        return false;
    }
}
