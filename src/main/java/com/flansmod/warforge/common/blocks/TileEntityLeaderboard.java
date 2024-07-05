package com.flansmod.warforge.common.blocks;

import java.util.ArrayList;
import java.util.List;

import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.server.Faction;
import com.flansmod.warforge.server.Leaderboard.FactionStat;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

public class TileEntityLeaderboard extends TileEntity
{
	public static final int NUM_ENTRIES = 6;	
	// Client only really
	public String[] topNames = new String[NUM_ENTRIES];
	
	// Does nothing, just for TileEntityRenderer
	
	@Override
	public SPacketUpdateTileEntity getUpdatePacket()
	{
		return new SPacketUpdateTileEntity(getPos(), getBlockMetadata(), getUpdateTag());
	}
	
	@Override
	public void onDataPacket(net.minecraft.network.NetworkManager net, SPacketUpdateTileEntity packet)
	{
		NBTTagCompound tags = packet.getNbtCompound();
		
		for(int i = 0; i < NUM_ENTRIES; i++)
		{
			topNames[i] = tags.getString("#" + i);
		}
	}
	
	@Override
	public NBTTagCompound getUpdateTag()
	{
		// You have to get parent tags so that x, y, z are added.
		NBTTagCompound tags = super.getUpdateTag();

		ArrayList<Faction> tempList = new ArrayList<Faction>();
		WarForgeMod.LEADERBOARD.GetSortedList(((BlockLeaderboard)getBlockType()).mStat, tempList);
		
		for(int i = 0; i < NUM_ENTRIES; i++)
		{
			if(tempList.size() > i)
			{
				tags.setString("#" + i, tempList.get(i).mName);
			}
			else
			{
				tags.setString("#" + i, "");
			}
		}
		
		return tags;
	}
	
	@Override
	public void handleUpdateTag(NBTTagCompound tags)
	{
		for(int i = 0; i < NUM_ENTRIES; i++)
		{
			topNames[i] = tags.getString("#" + i);
		}
	}
}
