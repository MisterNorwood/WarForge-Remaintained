package com.flansmod.warforge.common.network;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.client.ClientPacketHandler;

import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Flan's Mod packet handler class. Directs packet data to packet classes.
 *
 * @author Flan
 */
public class PacketHandler
{
	private static final String PROTOCOL = "1";

	public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
			new ResourceLocation(Tags.MODID, "main"),
			() -> PROTOCOL,
			PROTOCOL::equals,
			PROTOCOL::equals);

	/**
	 * Initialisation method called from FMLCommonSetupEvent in WarForgeMod
	 */
	public void initialise()
	{
		int id = 0;

		register(id++, PacketCreateFaction.class, PacketCreateFaction::new);
		register(id++, PacketRequestFactionInfo.class, PacketRequestFactionInfo::new);
		register(id++, PacketFactionInfo.class, PacketFactionInfo::new);
		register(id++, PacketSiegeCampInfo.class, PacketSiegeCampInfo::new);
		register(id++, PacketSiegeCampProgressUpdate.class, PacketSiegeCampProgressUpdate::new);
		register(id++, PacketStartSiege.class, PacketStartSiege::new);
		register(id++, PacketLeaderboardInfo.class, PacketLeaderboardInfo::new);
		register(id++, PacketRequestLeaderboardInfo.class, PacketRequestLeaderboardInfo::new);
		register(id++, PacketDisbandFaction.class, PacketDisbandFaction::new);
		register(id++, PacketRemoveClaim.class, PacketRemoveClaim::new);
		register(id++, PacketTimeUpdates.class, PacketTimeUpdates::new);
		register(id++, PacketSetFactionColour.class, PacketSetFactionColour::new);
		register(id++, PacketClientNotification.class, PacketClientNotification::new);
		register(id++, PacketMoveCitadel.class, PacketMoveCitadel::new);
		register(id++, PacketCitadelUpgradeRequirement.class, PacketCitadelUpgradeRequirement::new);
		register(id++, PacketRequestUpgradeUI.class, PacketRequestUpgradeUI::new);
		register(id++, PacketUpgradeUI.class, PacketUpgradeUI::new);
		register(id++, PacketRequestUpgrade.class, PacketRequestUpgrade::new);
		register(id++, PacketChooseFactionFlag.class, PacketChooseFactionFlag::new);
		register(id++, PacketFlagManifest.class, PacketFlagManifest::new);
		register(id++, PacketFlagChunk.class, PacketFlagChunk::new);
		register(id++, PacketEffect.class, PacketEffect::new);
		register(id++, PacketNamePlateChange.class, PacketNamePlateChange::new);
		register(id++, PacketRequestNamePlate.class, PacketRequestNamePlate::new);
		register(id++, PacketChunkPosVeinID.class, PacketChunkPosVeinID::new);
		register(id++, PacketVeinEntries.class, PacketVeinEntries::new);
		register(id++, PacketSyncConfig.class, PacketSyncConfig::new);
		register(id++, PacketRequestClaimChunks.class, PacketRequestClaimChunks::new);
		register(id++, PacketClaimChunksData.class, PacketClaimChunksData::new);
		register(id++, PacketClaimChunkAction.class, PacketClaimChunkAction::new);
		register(id++, PacketFactionMemberManagerAction.class, PacketFactionMemberManagerAction::new);
		register(id++, PacketFactionInsuranceAction.class, PacketFactionInsuranceAction::new);
		register(id++, PacketJourneyMapClaims.class, PacketJourneyMapClaims::new);
		register(id++, PacketJourneyMapVeins.class, PacketJourneyMapVeins::new);
		register(id++, PacketDeclareSiege.class, PacketDeclareSiege::new);
		register(id++, PacketRequestTerrainColors.class, PacketRequestTerrainColors::new);
		register(id++, PacketTerrainColors.class, PacketTerrainColors::new);
		register(id++, PacketFactionAllianceAction.class, PacketFactionAllianceAction::new);
		register(id++, PacketEstablishFob.class, PacketEstablishFob::new);
		register(id++, PacketRequestFobWarp.class, PacketRequestFobWarp::new);
		register(id++, PacketFobManagerAction.class, PacketFobManagerAction::new);
	}

	private static <T extends PacketBase> void register(int id, Class<T> clazz, java.util.function.Supplier<T> factory)
	{
		CHANNEL.registerMessage(id, clazz,
				PacketBase::encodeInto,
				buf -> {
					T p = factory.get();
					p.decodeInto(buf);
					return p;
				},
				(msg, ctxSup) -> {
					var ctx = ctxSup.get();
					ctx.enqueueWork(() -> {
						if (ctx.getDirection().getReceptionSide().isServer())
							msg.handleServerSide(ctx.getSender());
						else
							DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
									() -> () -> ClientPacketHandler.handle(msg));
					});
					ctx.setPacketHandled(true);
				});
	}

	/**
	 * Post-Initialisation method. Threading is handled by ctx.enqueueWork, so this is a no-op.
	 */
	public void postInitialise()
	{
	}

	/**
	 * Packets are dispatched on the main thread via ctx.enqueueWork, so this is a no-op.
	 */
	public void handleClientPackets()
	{
	}

	/**
	 * Packets are dispatched on the main thread via ctx.enqueueWork, so this is a no-op.
	 */
	public void handleServerPackets()
	{
	}

	/**
	 * Send a packet to all players
	 */
	public void sendToAll(PacketBase packet)
	{
		CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
	}

	/**
	 * Send a packet to a player
	 */
	public void sendTo(PacketBase packet, ServerPlayer player)
	{
		if (player == null) return;
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
	}

	/**
	 * Send a packet to all in a dimension
	 */
	public void sendToDimension(PacketBase packet, ResourceKey<Level> dimension)
	{
		CHANNEL.send(PacketDistributor.DIMENSION.with(() -> dimension), packet);
	}

	/**
	 * Send a packet to all around a point
	 */
	public void sendToAllAround(PacketBase packet, double x, double y, double z, float range, ResourceKey<Level> dimension)
	{
		CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(x, y, z, (double) range * range, dimension)), packet);
	}

	/**
	 * Send a packet to the server
	 */
	public void sendToServer(PacketBase packet)
	{
		CHANNEL.sendToServer(packet);
	}

	//Vanilla packets follow

	/**
	 * Send a packet to all players
	 */
	public void sendToAll(Packet<?> packet)
	{
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		server.getPlayerList().broadcastAll(packet);
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
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
				() -> () -> ClientPacketHandler.sendToServer(packet));
	}
}
