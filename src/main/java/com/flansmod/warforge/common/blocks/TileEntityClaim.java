package com.flansmod.warforge.common.blocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import com.flansmod.warforge.common.DimBlockPos;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.server.Faction;
import com.flansmod.warforge.server.Faction.PlayerData;
import com.mojang.authlib.GameProfile;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public abstract class TileEntityClaim extends TileEntity implements IClaim
{
	protected UUID mFactionUUID = Faction.NULL;
	public int mColour = 0xffffff;
	public String mFactionName = "";
	
	public ArrayList<String> mPlayerFlags = new ArrayList<String>();
	
	// IClaim
	@Override
	public UUID GetFaction() { return mFactionUUID; }
	@Override 
	public void UpdateColour(int colour) { mColour = colour; }
	@Override
	public int GetColour() { return mColour; }
	@Override
	public TileEntity GetAsTileEntity() { return this; }
	@Override
	public DimBlockPos GetPos() 
	{ 
		if(world == null)
		{
			if(worldCreate == null)
				return DimBlockPos.ZERO;
			else
				return new DimBlockPos(worldCreate.provider.getDimension(), getPos()); 
		}
		return new DimBlockPos(world.provider.getDimension(), getPos()); 
	}
	@Override 
	public boolean CanBeSieged() { return true; }
	@Override
	public String GetDisplayName() { return mFactionName; }
	@Override
	public List<String> GetPlayerFlags() { return mPlayerFlags; }
	//-----------
	
	
	@Override
	public void OnServerSetPlayerFlag(String playerName)
	{
		mPlayerFlags.add(playerName);
		
		world.markBlockRangeForRenderUpdate(pos, pos);
		world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
		world.scheduleBlockUpdate(pos, this.getBlockType(), 0, 0);
		markDirty();
	}
	
	@Override
	public void OnServerRemovePlayerFlag(String playerName)
	{
		mPlayerFlags.remove(playerName);
		
		world.markBlockRangeForRenderUpdate(pos, pos);
		world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
		world.scheduleBlockUpdate(pos, this.getBlockType(), 0, 0);
		markDirty();
	}
	
	@Override
	public void OnServerSetFaction(Faction faction)
	{
		if(faction == null)
		{
			mFactionUUID = Faction.NULL;
		}
		else
		{
			mFactionUUID = faction.mUUID;
			mColour = faction.mColour;
			mFactionName = faction.mName;
		}
		
		world.markBlockRangeForRenderUpdate(pos, pos);
		world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
		world.scheduleBlockUpdate(pos, this.getBlockType(), 0, 0);
		markDirty();
	}
	
	
	// This is so weird
	private World worldCreate;
	@Override
	public void setWorldCreate(World world)
	{
		worldCreate = world;
	}
	
	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound nbt)
	{
		super.writeToNBT(nbt);
		
		nbt.setUniqueId("faction", mFactionUUID);
				
		/*
		NBTTagList list = new NBTTagList();
		for(int i = 0; i < mPlayerFlags.size(); i++)
		{
			list.appendTag(new NBTTagString(mPlayerFlags.get(i)));
		}
		nbt.setTag("flags", list);
		*/
		return nbt;
	}

	
	@Override
	public void readFromNBT(NBTTagCompound nbt)
	{
		super.readFromNBT(nbt);
	
		mFactionUUID = nbt.getUniqueId("faction");

		// Read player flags
		mPlayerFlags.clear();
		/*
		NBTTagList list = nbt.getTagList("flags", 8); // String
		if(list != null)
		{
			for(NBTBase base : list)
			{
				mPlayerFlags.add(((NBTTagString)base).getString());
			}
		}			*/
		
		// Verifications
		if(FMLCommonHandler.instance().getEffectiveSide() == Side.SERVER)
		{
			Faction faction = WarForgeMod.FACTIONS.GetFaction(mFactionUUID);
			if(!mFactionUUID.equals(Faction.NULL) && faction == null)
			{
				WarForgeMod.LOGGER.error("Faction " + mFactionUUID + " could not be found for citadel at " + pos);
				//world.setBlockState(getPos(), Blocks.AIR.getDefaultState());
			}
			if(faction != null)
			{
				mColour = faction.mColour;
				mFactionName = faction.mName;
				for(HashMap.Entry<UUID, PlayerData> kvp : faction.mMembers.entrySet())
				{
					if(kvp.getValue().mFlagPosition.equals(GetPos()))
					{
						GameProfile profile = WarForgeMod.MC_SERVER.getPlayerProfileCache().getProfileByUUID(kvp.getKey());
						if(profile != null)
							mPlayerFlags.add(profile.getName());
					}
				}
			}
		}
		else
		{
			WarForgeMod.LOGGER.error("Loaded TileEntity from NBT on client?");
		}
		
	}
	
	@Override
	public SPacketUpdateTileEntity getUpdatePacket()
	{
		return new SPacketUpdateTileEntity(getPos(), getBlockMetadata(), getUpdateTag());
	}
	
	@Override
	public void onDataPacket(net.minecraft.network.NetworkManager net, SPacketUpdateTileEntity packet)
	{
		NBTTagCompound tags = packet.getNbtCompound();
		
		handleUpdateTag(tags);
	}
	
	@Override
	public NBTTagCompound getUpdateTag()
	{
		// You have to get parent tags so that x, y, z are added.
		NBTTagCompound tags = super.getUpdateTag();

		// Custom partial nbt write method
		tags.setUniqueId("faction", mFactionUUID);
		tags.setInteger("colour", mColour);
		tags.setString("name", mFactionName);
		
		NBTTagList list = new NBTTagList();
		for(int i = 0; i < mPlayerFlags.size(); i++)
		{
			list.appendTag(new NBTTagString(mPlayerFlags.get(i)));
		}
		tags.setTag("flags", list);
		
		return tags;
	}
	
	@Override
	public void handleUpdateTag(NBTTagCompound tags)
	{
		mFactionUUID = tags.getUniqueId("faction");
		mColour = tags.getInteger("colour");
		mFactionName = tags.getString("name");
		
		
		// Read player flags
		mPlayerFlags.clear();
		NBTTagList list = tags.getTagList("flags", 8); // String
		for(NBTBase base : list)
		{
			mPlayerFlags.add(((NBTTagString)base).getString());
		}
	}
	
	@Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox()
    {
		BlockPos pos = getPos();
		return new AxisAlignedBB(pos.add(-1, 0, -1), pos.add(2, 16, 2));
    	
    }
}
