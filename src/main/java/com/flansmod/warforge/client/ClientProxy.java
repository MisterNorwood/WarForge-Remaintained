package com.flansmod.warforge.client;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.flansmod.warforge.api.vein.Vein;
import com.flansmod.warforge.common.CommonProxy;
import com.flansmod.warforge.common.Content;
import com.flansmod.warforge.common.blocks.models.ClaimModels;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.blocks.*;
import com.flansmod.warforge.common.effect.AnimatedEffectHandler;
import com.flansmod.warforge.common.network.PacketRequestFactionInfo;
import com.flansmod.warforge.common.factories.FactionStatsGuiFactory;
import com.flansmod.warforge.common.network.SiegeCampProgressInfo;
import com.flansmod.warforge.server.Faction;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import static com.flansmod.warforge.common.WarForgeMod.FACTIONS;

public class ClientProxy extends CommonProxy
{
	public static HashMap<DimBlockPos, SiegeCampProgressInfo> sSiegeInfo = new HashMap<DimBlockPos, SiegeCampProgressInfo>();

	public static Short2ObjectOpenHashMap<Vein> VEIN_ENTRIES = new Short2ObjectOpenHashMap<>();

	public static ChunkVeinCache CHUNK_VEIN_CACHE = new ChunkVeinCache();

	public static short megachunkLength = -1;

	public static KeyBinding factionChatKey = new KeyBinding("key.factionchat.desc",
			KeyConflictContext.IN_GAME,
			Keyboard.KEY_Y,
			"key.warforge.factionchat");

	@Override
	public void preInit(FMLPreInitializationEvent event) {
		MinecraftForge.EVENT_BUS.register(this);
		MinecraftForge.EVENT_BUS.register(new ClientTickHandler());
		MinecraftForge.EVENT_BUS.register(new AnimatedEffectHandler());
		MinecraftForge.EVENT_BUS.register(new ClaimFlagRenderer());
		MinecraftForge.EVENT_BUS.register(new ClientPlacementPredictor());
		MinecraftForge.EVENT_BUS.register(new ClientMineTimePredictor());
		ClientRegistry.registerKeyBinding(factionChatKey);
	}

	@Override
	public void Init(FMLInitializationEvent event) {
//		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityCitadel.class, new TileEntityClaimRenderer());
//		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityBasicClaim.class, new TileEntityClaimRenderer());
//		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityReinforcedClaim.class, new TileEntityClaimRenderer());
//		ClientRegistry.bindTileEntitySpecialRenderer(TileEntitySiegeCamp.class, new TileEntityClaimRenderer());
//
		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityDummy.class, new RenderTileEntityDummy());

		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityLeaderboard.class, new TileEntityLeaderboardRenderer());
		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityCitadel.class, new RenderTileEntityClaim(ClaimModels.ModelType.CITADEL));
		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityBasicClaim.class, new RenderTileEntityClaim(ClaimModels.ModelType.BASIC_CLAIM));
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntitySiegeCamp.class, new RenderTileEntityClaim(ClaimModels.ModelType.SIEGE));
	}

	@Override
	public void TickClient() {
		if(factionChatKey.isPressed()) {
			GuiChat gui = new GuiChat("/f chat ");
			Minecraft.getMinecraft().displayGuiScreen(gui);
		}
	}

	@Override
	public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        return switch (ID) {
            case GUI_TYPE_CREATE_FACTION ->
                    new GuiCreateFaction((TileEntityCitadel) world.getTileEntity(new BlockPos(x, y, z)), false);
            case GUI_TYPE_RECOLOUR_FACTION ->
                    new GuiCreateFaction((TileEntityCitadel) world.getTileEntity(new BlockPos(x, y, z)), true);
            case GUI_TYPE_BASIC_CLAIM -> new GuiBasicClaim(getServerGuiElement(ID, player, world, x, y, z));
            case GUI_TYPE_FACTION_INFO -> new GuiFactionInfo();
            //case GUI_TYPE_SIEGE_CAMP: return new GuiSiegeCamp();
            case GUI_TYPE_LEADERBOARD -> new GuiLeaderboard();
            default -> null;
        };
    }

	@Override
	public TileEntity GetTile(DimBlockPos pos) {
		if(Minecraft.getMinecraft().world.provider.getDimension() == pos.dim)
			return Minecraft.getMinecraft().world.getTileEntity(pos.toRegularPos());

		WarForgeMod.LOGGER.error("Can't get info about a tile entity in a different dimension on client");
		return null;
	}

	@Override
	public ModularPanel buildCitadelUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings settings, TileEntityCitadel citadel) {
		return ModularCitadelGui.buildUI(guiData, syncManager, settings, citadel);
	}

	@SubscribeEvent
	public void registerModels(ModelRegistryEvent event) {
		RegisterModel(Content.citadelBlockItem);
		RegisterModel(Content.basicClaimBlockItem);
		RegisterModel(Content.reinforcedClaimBlockItem);
		RegisterModel(Content.siegeCampBlockItem);
        RegisterModel(Content.islandCollectorItem);

		RegisterModel(Content.topLeaderboardItem);
		RegisterModel(Content.legacyLeaderboardItem);
		RegisterModel(Content.wealthLeaderboardItem);
		RegisterModel(Content.notorietyLeaderboardItem);
	}

	private void RegisterModel(Item item) {
		ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(item.getRegistryName(), "inventory"));
	}

	public void UpdateSiegeInfo(SiegeCampProgressInfo info) {
		// sent to client on server stop to avoid de-sync
		if (info.attackingName.equals("c") && info.defendingName.equals("c")) {
			sSiegeInfo.clear();
			return;
		}

		// add warzone chunks manually only when the siege is first recognized
		if (!sSiegeInfo.containsKey(info.attackingPos)) {
			// initialize data about this client
			Faction playerFaction = FACTIONS.getFactionOfPlayer(Minecraft.getMinecraft().player.getUniqueID());
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
