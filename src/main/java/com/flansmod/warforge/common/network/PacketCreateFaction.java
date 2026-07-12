package com.flansmod.warforge.common.network;

import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.blocks.TileEntityCitadel;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class PacketCreateFaction extends PacketBase
{
	public DimBlockPos mCitadelPos;
	public String mFactionName = "";
	public int mColour = 0xffffff;


	@Override
	public void encodeInto(FriendlyByteBuf data)
	{
		writeUTF(data, mCitadelPos.dim.location().toString());
		data.writeInt(mCitadelPos.getX());
		data.writeInt(mCitadelPos.getY());
		data.writeInt(mCitadelPos.getZ());
		data.writeInt(mColour);
		writeUTF(data, mFactionName);
	}

	@Override
	public void decodeInto(FriendlyByteBuf data)
	{
		ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(readUTF(data)));
		int x = data.readInt();
		int y = data.readInt();
		int z = data.readInt();
		mCitadelPos = new DimBlockPos(dim, x, y, z);
		mColour = data.readInt();
		mFactionName = readUTF(data);
	}

	@Override
	public void handleServerSide(ServerPlayer playerEntity)
	{
		if(!playerEntity.level().dimension().equals(mCitadelPos.dim))
		{
			WarForgeMod.LOGGER.warn("Player requested creating a faction in the wrong dim");
			playerEntity.sendSystemMessage(Component.literal("You must be in the same dimension as the citadel to create a faction."));
		}
		else
		{
			BlockEntity te = playerEntity.level().getBlockEntity(mCitadelPos.toRegularPos());
			if(te instanceof TileEntityCitadel)
			{
				WarForgeMod.FACTIONS.requestCreateFaction((TileEntityCitadel)te, playerEntity, mFactionName, mColour);
			}
		}
	}

	@Override
	public void handleClientSide(Player clientPlayer)
	{
		WarForgeMod.LOGGER.error("Recieved create faction message on client");
	}

}
