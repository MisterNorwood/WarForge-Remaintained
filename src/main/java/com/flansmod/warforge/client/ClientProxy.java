package com.flansmod.warforge.client;

import com.flansmod.warforge.api.vein.Vein;
import com.flansmod.warforge.common.CommonProxy;
import com.flansmod.warforge.common.Content;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.effect.AnimatedEffectHandler;
import com.flansmod.warforge.common.factories.FactionStatsGuiFactory;
import com.flansmod.warforge.common.network.SiegeCampProgressInfo;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.server.Faction;
import com.mojang.blaze3d.platform.InputConstants;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import static com.flansmod.warforge.common.WarForgeMod.FACTIONS;

public class ClientProxy extends CommonProxy
{
	public static HashMap<DimBlockPos, SiegeCampProgressInfo> sSiegeInfo = new HashMap<>();

	public static Short2ObjectOpenHashMap<Vein> VEIN_ENTRIES = new Short2ObjectOpenHashMap<>();

	public static ChunkVeinCache CHUNK_VEIN_CACHE = new ChunkVeinCache();

	public static short megachunkLength = -1;

	public static KeyMapping factionChatKey = new KeyMapping("key.factionchat.desc",
			KeyConflictContext.IN_GAME,
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_Y,
			"key.warforge.factionchat");

	// Invoked from the WarForgeMod constructor with the client mod event bus.
	public void clientSetup(FMLClientSetupEvent event)
	{
		NeoForge.EVENT_BUS.register(new ClientTickHandler());
		NeoForge.EVENT_BUS.register(new SiegeUiDebugCommand());
		NeoForge.EVENT_BUS.register(new AnimatedEffectHandler());
		NeoForge.EVENT_BUS.register(new ClaimFlagRenderer());
		NeoForge.EVENT_BUS.register(new ClientMineTimePredictor());
		NeoForge.EVENT_BUS.register(new ClientPlacementPredictor());
	}

	public void registerRenderers(EntityRenderersEvent.RegisterRenderers event)
	{
		event.registerBlockEntityRenderer(Content.TE_CITADEL.get(), ctx -> new RenderTileEntityClaim());
		event.registerBlockEntityRenderer(Content.TE_BASIC_CLAIM.get(), ctx -> new RenderTileEntityClaim());
		event.registerBlockEntityRenderer(Content.TE_SIEGE_CAMP.get(), ctx -> new RenderTileEntityClaim());
		event.registerBlockEntityRenderer(Content.TE_FOB.get(), ctx -> new RenderTileEntityFob());
		event.registerBlockEntityRenderer(Content.TE_DUMMY.get(), RenderTileEntityDummy::new);
		event.registerBlockEntityRenderer(Content.TE_LEADERBOARD.get(), TileEntityLeaderboardRenderer::new);
	}

	public void registerKeyMappings(RegisterKeyMappingsEvent event)
	{
		event.register(factionChatKey);
		event.register(ClientTickHandler.toggleBordersKey);
		event.register(ClientTickHandler.claimManagerKey);
		event.register(ClientTickHandler.toggleVeinOverlayKey);
	}

	@Override
	public void TickClient()
	{
		if (factionChatKey.consumeClick()) {
			Minecraft.getInstance().setScreen(new ChatScreen("/f chat "));
		}
	}

	@Override
	public BlockEntity GetTile(DimBlockPos pos)
	{
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null && mc.level.dimension().equals(pos.dim))
			return mc.level.getBlockEntity(pos.toRegularPos());

		WarForgeMod.LOGGER.error("Can't get info about a tile entity in a different dimension on client");
		return null;
	}

	@Override
	public void UpdateSiegeInfo(SiegeCampProgressInfo info)
	{
		// sent to client on server stop to avoid de-sync
		if (info.attackingName.equals("c") && info.defendingName.equals("c")) {
			sSiegeInfo.clear();
			return;
		}

		// add warzone chunks manually only when the siege is first recognized
		if (!sSiegeInfo.containsKey(info.attackingPos)) {
			// initialize data about this client
			Faction playerFaction = FACTIONS.getFactionOfPlayer(Minecraft.getInstance().player.getUUID());
			String facName = null;
			if (playerFaction != null && !playerFaction.uuid.equals(Faction.nullUuid)) { facName = playerFaction.name; }

			// assign the synced battle radius when the player is directly involved in the siege
			int sqrRad = -1;
			if (facName != null && (facName.equals(info.defendingName) || facName.equals(info.attackingName))) {
				sqrRad = info.battleRadius;
			}

			// if the player is on either side of the siege, fill the warzone for them
			if (sqrRad != -1) {
				// setup the warzone data
				ChunkPos siegeChunk = info.attackingPos.toChunkPos();
				int warzoneLength = 2 * sqrRad + 1;
				int zEnd = siegeChunk.z + sqrRad;
				int xEnd = siegeChunk.x + sqrRad;
				info.warzoneChunks = new ArrayList<>(warzoneLength * warzoneLength);

				// add the warzone chunks
				for (int z = siegeChunk.z - sqrRad; z <= zEnd; ++z) {
					for (int x = siegeChunk.x - sqrRad; x <= xEnd; ++x) {
						info.warzoneChunks.add(new ChunkPos(x, z));  // don't use dim chunk as dim is redundant info
					}
				}
			}
		} else {
			// copy the warzone chunks
			info.warzoneChunks = sSiegeInfo.get(info.attackingPos).warzoneChunks;
		}

		sSiegeInfo.remove(info.attackingPos);

		sSiegeInfo.put(info.attackingPos, info);
	}

	public static void requestFactionInfo(UUID factionID) {
		FactionStatsGuiFactory.INSTANCE.openClient(factionID);
	}
}
