package com.flansmod.warforge.common.blocks;

import java.util.ArrayList;

import com.flansmod.warforge.common.Content;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.server.Faction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class TileEntityLeaderboard extends BlockEntity
{
	public static final int NUM_ENTRIES = 6;
	// Client only really
	public String[] topNames = new String[NUM_ENTRIES];

	public TileEntityLeaderboard(BlockPos pos, BlockState state)
	{
		super(Content.TE_LEADERBOARD.get(), pos, state);
	}

	// Does nothing, just for the BlockEntityRenderer

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket()
	{
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries)
	{
		CompoundTag tags = packet.getTag();

		for(int i = 0; i < NUM_ENTRIES; i++)
		{
			topNames[i] = tags.getString("#" + i);
		}
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries)
	{
		CompoundTag tags = super.getUpdateTag(registries);

		ArrayList<Faction> tempList = new ArrayList<Faction>();
		WarForgeMod.LEADERBOARD.GetSortedList(((BlockLeaderboard)getBlockState().getBlock()).stat, tempList);

		for(int i = 0; i < NUM_ENTRIES; i++)
		{
			if(tempList.size() > i)
			{
				tags.putString("#" + i, tempList.get(i).name);
			}
			else
			{
				tags.putString("#" + i, "");
			}
		}

		return tags;
	}

	@Override
	public void handleUpdateTag(CompoundTag tags, HolderLookup.Provider registries)
	{
		for(int i = 0; i < NUM_ENTRIES; i++)
		{
			topNames[i] = tags.getString("#" + i);
		}
	}
}
