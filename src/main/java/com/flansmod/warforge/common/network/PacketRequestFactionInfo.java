package com.flansmod.warforge.common.network;

import java.util.UUID;

import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.server.Faction;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

public class PacketRequestFactionInfo extends PacketBase 
{
	public UUID mFactionIDRequest = Faction.NULL;
	public String mFactionNameRequest = "";
	
	@Override
	public void encodeInto(ChannelHandlerContext ctx, ByteBuf data) 
	{
		writeUUID(data, mFactionIDRequest);
		writeUTF(data, mFactionNameRequest);
	}

	@Override
	public void decodeInto(ChannelHandlerContext ctx, ByteBuf data) 
	{
		mFactionIDRequest = readUUID(data);
		mFactionNameRequest = readUTF(data);
	}

	@Override
	public void handleServerSide(EntityPlayerMP playerEntity) 
	{
		Faction faction = null;
		if(!mFactionIDRequest.equals(Faction.NULL))
		{
			faction = WarForgeMod.FACTIONS.GetFaction(mFactionIDRequest);
		}
		else if(!mFactionNameRequest.isEmpty())
		{
			faction = WarForgeMod.FACTIONS.GetFaction(mFactionNameRequest);
		}
		else
		{
			WarForgeMod.LOGGER.error("Player " + playerEntity.getName() + " made a request for faction info with no valid key");
		}
		
		if(faction != null)
		{
			PacketFactionInfo packet = new PacketFactionInfo();
			packet.mInfo = faction.CreateInfo();
			WarForgeMod.INSTANCE.NETWORK.sendTo(packet, playerEntity);
		}
		else
		{
			WarForgeMod.LOGGER.error("Could not find faction for info");
		}
	}

	@Override
	public void handleClientSide(EntityPlayer clientPlayer) 
	{
		WarForgeMod.LOGGER.error("Received a faction info request client side");
	}
}
