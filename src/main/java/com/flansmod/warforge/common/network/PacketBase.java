package com.flansmod.warforge.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * Base class for all packets in Flan's Mod.
 */
public abstract class PacketBase implements CustomPacketPayload
{
	/**
	 * Encode the packet into a FriendlyByteBuf stream.
	 */
	public abstract void encodeInto(FriendlyByteBuf data);

	/**
	 * Decode the packet from a FriendlyByteBuf stream.
	 */
	public abstract void decodeInto(FriendlyByteBuf data);

	/**
	 * Handle the packet on server side, post-decoding
	 */
	public abstract void handleServerSide(ServerPlayer playerEntity);

	/**
	 * Handle the packet on client side, post-decoding
	 */
	public abstract void handleClientSide(Player clientPlayer);

	/**
	 * Util method for quickly writing strings
	 */
	public static void writeUTF(FriendlyByteBuf data, String s)
	{
		data.writeUtf(s);
	}

	/**
	 * Util method for quickly reading strings
	 */
	public static String readUTF(FriendlyByteBuf data)
	{
		return data.readUtf();
	}

	public static String readUTF(FriendlyByteBuf data, int maxLength)
	{
		return data.readUtf(maxLength);
	}

	public static void writeUUID(FriendlyByteBuf data, UUID id)
	{
		data.writeUUID(id);
	}

	public static UUID readUUID(FriendlyByteBuf data)
	{
		return data.readUUID();
	}
}
