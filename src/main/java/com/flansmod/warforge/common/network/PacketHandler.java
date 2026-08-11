package com.flansmod.warforge.common.network;

import com.flansmod.warforge.client.ClientPacketHandler;

import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Flan's Mod packet handler class. Directs packet data to packet classes.
 *
 * @author Flan
 */
public class PacketHandler
{
	/**
	 * Packet type + handler registration now happens in WFNetwork via RegisterPayloadHandlersEvent.
	 */
	public void initialise()
	{
	}

	public void postInitialise()
	{
	}

	public void handleClientPackets()
	{
	}

	public void handleServerPackets()
	{
	}

	/**
	 * Send a packet to all players
	 */
	public void sendToAll(PacketBase packet)
	{
		PacketDistributor.sendToAllPlayers(packet);
	}

	/**
	 * Send a packet to a player
	 */
	public void sendTo(PacketBase packet, ServerPlayer player)
	{
		if (player == null) return;
		PacketDistributor.sendToPlayer(player, packet);
	}

	/**
	 * Send a packet to all in a dimension
	 */
	public void sendToDimension(PacketBase packet, ResourceKey<Level> dimension)
	{
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server == null) return;
		ServerLevel level = server.getLevel(dimension);
		if (level != null) PacketDistributor.sendToPlayersInDimension(level, packet);
	}

	/**
	 * Send a packet to all around a point
	 */
	public void sendToAllAround(PacketBase packet, double x, double y, double z, float range, ResourceKey<Level> dimension)
	{
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server == null) return;
		ServerLevel level = server.getLevel(dimension);
		if (level != null) PacketDistributor.sendToPlayersNear(level, null, x, y, z, range, packet);
	}

	/**
	 * Send a packet to the server
	 */
	public void sendToServer(PacketBase packet)
	{
		PacketDistributor.sendToServer(packet);
	}

	//Vanilla packets follow

	/**
	 * Send a packet to all players
	 */
	public void sendToAll(Packet<?> packet)
	{
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server != null) server.getPlayerList().broadcastAll(packet);
	}

	/**
	 * Send a packet to a player
	 */
	public void sendTo(Packet<?> packet, ServerPlayer player)
	{
		if (player == null) return;
		player.connection.send(packet);
	}

	/**
	 * Send a packet to the server
	 */
	public void sendToServer(Packet<?> packet)
	{
		if (FMLEnvironment.dist == Dist.CLIENT)
			ClientPacketHandler.sendToServer(packet);
	}
}
