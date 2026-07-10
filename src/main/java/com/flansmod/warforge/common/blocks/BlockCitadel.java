package com.flansmod.warforge.common.blocks;

import com.cleanroommc.modularui.factory.TileEntityGuiFactory;
import com.flansmod.warforge.common.CommonProxy;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.blocks.models.RotatableStateMapper;
import com.flansmod.warforge.common.factories.FactionStatsGuiFactory;
import com.flansmod.warforge.common.network.PacketFactionInfo;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.common.util.IDynamicModels;
import com.flansmod.warforge.server.Faction;
import com.flansmod.warforge.server.FactionStorage;
import lombok.SneakyThrows;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Collections;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

import static com.flansmod.warforge.client.models.BakingUtil.registerFacingModels;
import static com.flansmod.warforge.common.Content.dummyTranslusent;
import static com.flansmod.warforge.common.Content.statue;
import static com.flansmod.warforge.common.blocks.BlockDummy.MODEL;
import static com.flansmod.warforge.common.blocks.BlockDummy.modelEnum.KING;
import static com.flansmod.warforge.common.blocks.BlockDummy.modelEnum.TRANSLUCENT;

public class BlockCitadel extends MultiBlockColumn implements ITileEntityProvider, IMultiBlock, IDynamicModels {
    public static final PropertyDirection FACING = BlockHorizontal.FACING;

    public BlockCitadel(Material materialIn) {
        super(materialIn);
        this.setCreativeTab(CreativeTabs.COMBAT);
        this.setBlockUnbreakable();
        this.setResistance(30000000f); //Makes sense. We probably don't wanna let people bomb it
        this.setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    public void initMap() {
        multiBlockMap = Collections.unmodifiableMap(new HashMap<>() {{
            put(statue.getDefaultState().withProperty(MODEL, KING), new Vec3i(0, 1, 0));
            put(dummyTranslusent.getDefaultState().withProperty(MODEL, TRANSLUCENT), new Vec3i(0, 2, 0));
        }});

    }


    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return true;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return true;
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileEntityCitadel();
    }

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        if (!world.isRemote) {
            if (WarForgeMod.FACTIONS.isChunkContested(new DimChunkPos(world.provider.getDimension(), pos)))
                return false;

            // Can't claim a chunk claimed by another faction
            UUID existingClaim = WarForgeMod.FACTIONS.getClaim(new DimChunkPos(world.provider.getDimension(), pos));
            if (!existingClaim.equals(Faction.nullUuid))
                return false;
        }

        // Can only place on a solid surface
        if (!world.getBlockState(pos.add(0, -1, 0)).isSideSolid(world, pos.add(0, -1, 0), EnumFacing.UP))
            return false;
        return super.canPlaceBlockAt(world, pos);
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        EnumFacing facing = placer.getHorizontalFacing().getOpposite();
        world.setBlockState(pos, state.withProperty(FACING, facing), 2);
        TileEntity te = world.getTileEntity(pos);
        if (te != null) {
            TileEntityCitadel citadel = (TileEntityCitadel) te;
            citadel.onPlacedBy(placer);
//            if (world.isRemote) return;
//            super.onBlockPlacedBy(world, pos, state, placer, stack);
        }
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return this.getDefaultState().withProperty(FACING, EnumFacing.HORIZONTALS[meta]);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
    }


    @Override
    public boolean eventReceived(IBlockState state, World worldIn, BlockPos pos, int id, int param) {
        return true;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float par7, float par8, float par9) {
        if (player.isSneaking()) {
            TileEntityClaim citadel = (TileEntityClaim) world.getTileEntity(pos);
            assert citadel != null;
            if (!citadel.getFaction().equals(Faction.nullUuid))
                citadel.increaseRotation();
            else if (!world.isRemote) {
                breakBlock(world, pos, state);
                world.destroyBlock(pos, true);
            }
            return true;
        }
        if (!world.isRemote) {
            Faction playerFaction = WarForgeMod.FACTIONS.getFactionOfPlayer(player.getUniqueID());
            TileEntityCitadel citadel = (TileEntityCitadel) world.getTileEntity(pos);

            // If the player has no faction and is the placer, they can open the UI
            if (playerFaction == null && player.getUniqueID().equals(citadel.placer)) {
                WarForgeMod.syncClaimToPlayer(player, pos);
                TileEntityGuiFactory.INSTANCE.open(player, pos);
            }
            // Any other factionless players, and players who aren't in this faction get an info panel
            else if (playerFaction == null || !playerFaction.uuid.equals(citadel.factionUUID)) {
                Faction citadelFaction = WarForgeMod.FACTIONS.getFaction(citadel.factionUUID);
                if (citadelFaction != null) {
                    FactionStatsGuiFactory.INSTANCE.open(player, citadelFaction.uuid);
                } else {
                    DimBlockPos citadelPos = new DimBlockPos(world.provider.getDimension(), pos);
                    Faction chunkFaction = WarForgeMod.FACTIONS.getFaction(WarForgeMod.FACTIONS.getClaim(citadelPos.toChunkPos()));
                    // if ghost citadel exists in chunk claimed by faction, delete it
                    if (FactionStorage.isValidFaction(chunkFaction)) {
                        world.destroyBlock(pos, false);
                        player.sendMessage(new TextComponentString("Overlapping citadel placement found; deleting current."));
                    } else {
                        player.sendMessage(new TextComponentString("This citadel is not home to a faction, and was not placed by you."));
                    }
                }
            }
            // So anyone else will be from the target faction
            else {
                WarForgeMod.syncClaimToPlayer(player, pos);
                TileEntityGuiFactory.INSTANCE.open(player, pos);
            }
        }
        return true;
    }

    @Override
    public boolean canEntityDestroy(IBlockState state, IBlockAccess world, BlockPos pos, Entity entity) {
        if (world.getTileEntity(pos) instanceof TileEntityClaim te && te.getFaction().equals(Faction.nullUuid))
            return true;
        return false;
    }

    @Override
    public void breakBlock(World worldIn, BlockPos pos, IBlockState state) {
        super.breakBlock(worldIn, pos, state);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public StateMapperBase getStateMapper(ResourceLocation loc) {
        return new RotatableStateMapper(getRegistryName());
    }

    @Override
    @SneakyThrows
    public void bakeModel(ModelBakeEvent event) {
        IModel medieval = ModelLoaderRegistry.getModelOrMissing(
                new ResourceLocation(Tags.MODID, "block/citadelblock"));
        IModel modern = ModelLoaderRegistry.getModelOrMissing(
                new ResourceLocation(Tags.MODID, "block/statues/modern/flag_pole"));
        registerFacingModels(medieval, modern, event.getModelRegistry(), getRegistryName());
    }

    @Override
    public void registerModel() {
        ModelLoader.setCustomModelResourceLocation(
                Item.getItemFromBlock(this),
                0,
                new ModelResourceLocation(Objects.requireNonNull(getRegistryName()), "inventory")
        );
    }

    @Override
    public void registerSprite(TextureMap map) {
        //Already registered via ClaimModels's recursive register
    }

}
