package com.flansmod.warforge.client;

import org.apache.commons.lang3.tuple.Pair;
import com.flansmod.warforge.api.vein.Quality;
import com.flansmod.warforge.api.vein.Vein;
import com.flansmod.warforge.api.vein.init.VeinUtils;
import com.flansmod.warforge.api.modularui.ChunkMapTextureDaemon;
import com.flansmod.warforge.client.util.RenderUtil;
import com.flansmod.warforge.client.util.ScreenSpaceUtil;
import com.flansmod.warforge.common.Content;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.factories.ClaimManagerGuiFactory;
import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.network.ClaimChunkInfo;
import com.flansmod.warforge.common.network.PacketChunkPosVeinID;
import com.flansmod.warforge.common.network.PacketRequestClaimChunks;
import com.flansmod.warforge.common.network.SiegeCampProgressInfo;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.server.Faction;
import com.flansmod.warforge.server.StackComparable;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.model.ModelBanner;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import static com.flansmod.warforge.client.ClientProxy.CHUNK_VEIN_CACHE;
import static com.flansmod.warforge.client.util.RenderUtil.*;

public class ClientTickHandler {
    final static double alignment = 0.25d;
    final static double smaller_alignment = alignment - 0.125d;
    private static final int MAX_BORDER_REBUILDS_PER_FRAME = 8;
    private static final int BORDER_Y_SCAN_MARGIN = 4;
    private static final int BORDER_SYNC_PREFETCH = 4;
    private static final int BORDER_SAFETY_RESYNC_TICKS = 100;
    private static final ResourceLocation texture = new ResourceLocation(Tags.MODID, "world/borders.png");
    private static final ResourceLocation textureConquered = new ResourceLocation(Tags.MODID, "world/borders_restricted.png");
    private static final ResourceLocation fastTexture = new ResourceLocation(Tags.MODID, "world/borders_fast.png");
    private static final ResourceLocation overlayTex = new ResourceLocation(Tags.MODID, "world/overlay.png");
    private static final ResourceLocation siegeprogress = new ResourceLocation(Tags.MODID, "gui/siegeprogressslim.png");
    public static long nextSiegeDayMs = 0L;
    public static long nextYieldDayMs = 0L;
    public static long timerSiegeEndStamp = 0L;
   	public static boolean CLAIMS_DIRTY = false;
    public static boolean UI_DEBUG = false;
    public static boolean TIMER_DEBUG = false;
    public static boolean showVeinOverlay = true;
    private final Tessellator tess;
    private DimChunkPos playerChunkPos = new DimChunkPos(0, 0, 0);
    private DimChunkPos lastClaimSyncChunk = new DimChunkPos(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
    private float newAreaToastTime = 0;
    private String areaMessage = "";
    private int areaMessageColour = 0xFF_FF_FF_FF;
    private String areaFlagId = "";
    private HashMap<DimChunkPos, BorderRenderData> renderData = new HashMap<>();
    private DimChunkPos lastBorderSyncCenter = null;
    private int lastBorderSyncRadius = 0;

	// -1 indicates the chunk wasn't the targeting of previous probe(s)
	private static ArrayList<String> cachedCompStrings = null;
	private static Object2LongOpenHashMap<DimChunkPos> permitChunkReprobeMs = new Object2LongOpenHashMap<>();
	private static long lastRenderStartTimeMs = -1;  // (curr time - this) / (display time (ms)) to get index
	private static Iterator<StackComparable> compIt = null;
	private static StackComparable currComp = null;

    public ClientTickHandler() {
        tess = Tessellator.getInstance();
        toggleBordersKey = new KeyBinding("key.warforge.showborders", Keyboard.KEY_B, "key.warforge.cathegory");
		ClientRegistry.registerKeyBinding(toggleBordersKey);
        claimManagerKey = new KeyBinding("key.warforge.claimmanager", Keyboard.KEY_M, "key.warforge.cathegory");
        ClientRegistry.registerKeyBinding(claimManagerKey);
        toggleVeinOverlayKey = new KeyBinding("key.warforge.toggleveinoverlay", Keyboard.KEY_O, "key.warforge.cathegory");
        ClientRegistry.registerKeyBinding(toggleVeinOverlayKey);

	}
    public static KeyBinding toggleBordersKey;
    public static KeyBinding claimManagerKey;
    public static KeyBinding toggleVeinOverlayKey;

    private void cleanupBorderRenderData() {
        for (BorderRenderData data : renderData.values()) {
            if (data.renderList > 0) {
               Minecraft.getMinecraft().addScheduledTask( () -> GlStateManager.glDeleteLists(data.renderList, 1));
            }
        }
        renderData.clear();
        ClientBorderCache.clear();
        lastBorderSyncCenter = null;
    }

    @SubscribeEvent
    public void onPlayerLogin(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        // init and clear stale data
		permitChunkReprobeMs = new Object2LongOpenHashMap<>();
		permitChunkReprobeMs.defaultReturnValue(-1);
		lastRenderStartTimeMs = -1;
		compIt = null;
		currComp = null;
		cachedCompStrings = null;
        cleanupBorderRenderData();

        // clear stale data
        CHUNK_VEIN_CACHE.purge();
        ClientProxy.VEIN_ENTRIES.clear();
        WarForgeMod.NAMETAG_CACHE.purge(); //Purge to remove possible stale data
        ChunkMapTextureDaemon.releaseAll();
        ServerTerrainCache.clear();
        ClaimManagerGuiFactory.resetSiegeState();
        ClientFlagRegistry.clear();
        ClientClaimChunkCache.replaceAll(0, 0, 0, 0, Faction.nullUuid, 0, 0, 0, 0, new ArrayList<ClaimChunkInfo>());
        CLAIMS_DIRTY = true;
        showVeinOverlay = true;
        areaFlagId = "";
    }

    @SubscribeEvent
    public void onPlayerLogout(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        cleanupBorderRenderData();
        Minecraft.getMinecraft().addScheduledTask(ChunkMapTextureDaemon::releaseAll);
        ServerTerrainCache.clear();
        ClaimManagerGuiFactory.resetSiegeState();
        ClientFlagRegistry.clear();
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        World world = event.getWorld();
        if (!world.isRemote) {
            return;
        }
        net.minecraft.util.math.ChunkPos cp = event.getChunk().getPos();
        DimChunkPos key = new DimChunkPos(world.provider.getDimension(), cp.x, cp.z);
        BorderRenderData data = renderData.get(key);
        if (data != null && data.renderList > 0) {
            GlStateManager.glDeleteLists(data.renderList, 1);
            data.renderList = 0;
        }
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent tick) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) return;

        // Handle client packets and perform the client-side tick
        WarForgeMod.NETWORK.handleClientPackets();
        ChunkMapTextureDaemon.flushTextureQueue();
        WarForgeMod.proxy.TickClient();

        // Use a more efficient approach for expired siege info removal
        ArrayList<DimBlockPos> expired = null;

        // Iterate over entries and mark completed ones for removal
        for (HashMap.Entry<DimBlockPos, SiegeCampProgressInfo> kvp : ClientProxy.sSiegeInfo.entrySet()) {
            SiegeCampProgressInfo siegeInfo = kvp.getValue();
            siegeInfo.ClientTick();
            if (siegeInfo.Completed()) {
                if (expired == null) expired = new ArrayList<>();
                expired.add(kvp.getKey());
            }
        }

        // Remove completed siege camps from the map
        if (expired != null) {
            for (DimBlockPos pos : expired) {
                ClientProxy.sSiegeInfo.remove(pos);
            }
        }

        // Handle new area toast time
        if (newAreaToastTime > 0.0f) {
            newAreaToastTime--;
        }

        // Avoid calling Minecraft.getMinecraft() multiple times
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player != null && player.ticksExisted % 200 == 0) {
            CLAIMS_DIRTY = true;
        }

        if (player != null) {
            DimChunkPos standing = new DimChunkPos(player.dimension, player.getPosition());

            // when we leave a chunk, restart iteration on vein members
            if (!standing.equals(playerChunkPos)) {
				lastRenderStartTimeMs = -1;
            }


            boolean guiOpen = Minecraft.getMinecraft().currentScreen != null;
            if (!guiOpen) {
                int renderDist = WarForgeConfig.BORDER_RENDER_DISTANCE > 0
                        ? WarForgeConfig.BORDER_RENDER_DISTANCE
                        : Minecraft.getMinecraft().gameSettings.renderDistanceChunks;
                int syncRadius = Math.min(renderDist + BORDER_SYNC_PREFETCH, WarForgeConfig.BORDER_SYNC_MAX_RADIUS);
                boolean resync = lastBorderSyncCenter == null
                        || lastBorderSyncCenter.dim != standing.dim
                        || syncRadius != lastBorderSyncRadius
                        || Math.max(Math.abs(standing.x - lastBorderSyncCenter.x), Math.abs(standing.z - lastBorderSyncCenter.z)) > BORDER_SYNC_PREFETCH
                        || player.ticksExisted % BORDER_SAFETY_RESYNC_TICKS == 0;
                if (resync) {
                    requestClaimChunkData(standing, syncRadius, true);
                    lastBorderSyncCenter = standing;
                    lastBorderSyncRadius = syncRadius;
                }
                lastClaimSyncChunk = standing;
            } else if (player.ticksExisted % 40 == 0 && lastClaimSyncChunk.dim == player.dimension
                    && !ClaimManagerGuiFactory.isRemoteSiegeView()) {
                requestClaimChunkData(lastClaimSyncChunk);
                lastBorderSyncCenter = null;
            }

            if (claimManagerKey.isPressed()) {
                ClaimManagerGuiFactory.resetSiegeState();
                ClaimManagerGuiFactory.INSTANCE.openClient(standing, WarForgeConfig.CLAIM_MANAGER_RADIUS, -1, -1);
            }

            if (toggleBordersKey.isPressed()) {
                WarForgeMod.showBorders = !WarForgeMod.showBorders;
                player.sendMessage(new TextComponentString("Borders " + (WarForgeMod.showBorders ? "enabled" : "disabled")));
            }

            if (toggleVeinOverlayKey.isPressed()) {
                toggleVeinOverlay(player);
            }

            // Show new area timer if configured
            if (WarForgeConfig.SHOW_NEW_AREA_TIMER > 0.0f) {

                // Only perform claim checks if the player has moved to a new chunk
                if (!standing.equals(playerChunkPos)) {
                    ClaimChunkInfo preClaim = ClientBorderCache.get(playerChunkPos);
                    ClaimChunkInfo postClaim = ClientBorderCache.get(standing);
                    boolean hadPreClaim = preClaim != null && !preClaim.factionId.equals(Faction.nullUuid);
                    boolean hasPostClaim = postClaim != null && !postClaim.factionId.equals(Faction.nullUuid);

                    // Generate area message only if needed (reduce redundant logic)
                    if (!hadPreClaim) {
                        if (hasPostClaim) {
                            // Entered a new claim
                            areaMessage = "Entering " + postClaim.factionName;
                            areaMessageColour = postClaim.colour;
                            areaFlagId = postClaim.flagId;
                            newAreaToastTime = WarForgeConfig.SHOW_NEW_AREA_TIMER;
                        }
                    } else // Left a claim
                    {
                        if (!hasPostClaim) {
                            // Gone nowhere
                            areaMessage = "Leaving " + preClaim.factionName;
                            areaMessageColour = preClaim.colour;
                            areaFlagId = "";
                            newAreaToastTime = WarForgeConfig.SHOW_NEW_AREA_TIMER;
                        } else {
                            // Entered another claim, possibly different faction
                            if (!preClaim.factionId.equals(postClaim.factionId)) {
                                areaMessage = "Leaving " + preClaim.factionName + ", Entering " + postClaim.factionName;
                                areaMessageColour = postClaim.colour;
                                areaFlagId = postClaim.flagId;
                                newAreaToastTime = WarForgeConfig.SHOW_NEW_AREA_TIMER;
                            }
                        }
                    }

                    playerChunkPos = standing;
                }
            }
        }


    }

    private void requestClaimChunkData(DimChunkPos center) {
        requestClaimChunkData(center, WarForgeConfig.CLAIM_MANAGER_RADIUS, false);
    }

    private void requestClaimChunkData(DimChunkPos center, int radius, boolean outlineOnly) {
        PacketRequestClaimChunks packet = new PacketRequestClaimChunks();
        packet.center = center;
        packet.radius = radius;
        packet.outlineOnly = outlineOnly;
        WarForgeMod.NETWORK.sendToServer(packet);
    }

    @SubscribeEvent
    public void onRenderHUD(RenderGameOverlayEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) return;

        ScreenSpaceUtil.resetOffsets(event);

        if (event.getType() == ElementType.BOSSHEALTH) {
            EntityPlayerSP player = mc.player;

            if (player != null) {

                // Siege camp info
                SiegeCampProgressInfo infoToRender = !UI_DEBUG ? getClosestSiegeCampInfo(player) : SiegeCampProgressInfo.getDebugInfo();

                if (infoToRender != null) {
                    renderSiegeOverlay(mc, infoToRender, event);
                }

                // Timer info
                if (WarForgeConfig.SHOW_YIELD_TIMERS) {
                    renderTimers(mc);
                }

                // New Area Toast
                if (newAreaToastTime > 0.0f) {
                    renderNewAreaToast(mc, event);
                }

				// get the vein info
				if (showVeinOverlay) {
					DimChunkPos currPos = new DimChunkPos(player.dimension, player.getPosition());
					boolean hasPosData = CHUNK_VEIN_CACHE.isReceived(currPos);
					boolean hasValidData = hasPosData && CHUNK_VEIN_CACHE.isRecognized(currPos);
					Pair<Vein, Quality> veinInfo = CHUNK_VEIN_CACHE.get(currPos);

					// probe the server for the data for this chunk
					if (!hasValidData && permitChunkReprobeMs.getLong(currPos) <= System.currentTimeMillis()) {
						WarForgeMod.LOGGER.info("Pinging server for chunk vein info");
						permitChunkReprobeMs.put(currPos, System.currentTimeMillis() + 5000);  // only ping every 5s as needed
						PacketChunkPosVeinID packetChunkVeinRequest = new PacketChunkPosVeinID();
						packetChunkVeinRequest.veinLocation = currPos;
						WarForgeMod.NETWORK.sendToServer(packetChunkVeinRequest);
					}

					renderVeinData(mc, veinInfo, hasPosData, event);
				}

            }
        }
    }

    private void renderTimers(Minecraft mc) {
        int screenWidth = ScreenSpaceUtil.RESOLUTIONX;

        int padding = 4;
        int textHeight = ScreenSpaceUtil.TEXTHEIGHT + padding;

        ScreenSpaceUtil.ScreenPos pos = WarForgeConfig.POS_TIMERS;

        // Siege progress
        if (!WarForgeConfig.SIEGE_ENABLE_NEW_TIMER || UI_DEBUG) {
            String siegeText = "Siege Progress: " + formatPaddedTimer(nextSiegeDayMs - System.currentTimeMillis());
            int textWidth = mc.fontRenderer.getStringWidth(siegeText);
            int x = ScreenSpaceUtil.shouldCenterX(pos) ? ScreenSpaceUtil.centerX(screenWidth, textWidth) : ScreenSpaceUtil.getX(pos, textWidth) + ScreenSpaceUtil.getXOffset(pos, padding);
            int ySiege = pos.getY() + ScreenSpaceUtil.getYOffset(pos, textHeight);

            mc.fontRenderer.drawStringWithShadow(siegeText, x, ySiege, 0xffffff);
            ScreenSpaceUtil.incrementY(pos, textHeight);
        }

        // Next yields
        String yieldText = "Next yields: " + formatPaddedTimer(nextYieldDayMs - System.currentTimeMillis());
        int textWidth = mc.fontRenderer.getStringWidth(yieldText);
        int x = ScreenSpaceUtil.shouldCenterX(pos) ? ScreenSpaceUtil.centerX(screenWidth, textWidth) : ScreenSpaceUtil.getX(pos, textWidth) + ScreenSpaceUtil.getXOffset(pos, padding);
        int yYield = pos.getY() + ScreenSpaceUtil.getYOffset(pos, textHeight);

        mc.fontRenderer.drawStringWithShadow(yieldText, x, yYield, 0xffffff);
        ScreenSpaceUtil.incrementY(pos, textHeight);
    }

    public static String formatPaddedTimer(long msRemaining) {
        long s = msRemaining / 1000;
        long m = s / 60;
        long h = m / 60;
        long d = h / 24;

        return (d > 0 ? (d) + " days, " : "") + String.format("%02d", (h % 24)) + ":" + String.format("%02d", (m % 60)) + ":" + String.format("%02d", (s % 60));
    }

    private SiegeCampProgressInfo getClosestSiegeCampInfo(EntityPlayerSP player) {
        SiegeCampProgressInfo closestInfo = null;
        double bestDistanceSq = Double.MAX_VALUE;

        for (SiegeCampProgressInfo info : ClientProxy.sSiegeInfo.values()) {
            double distSq = info.defendingPos.distanceSq(player.posX, player.posY, player.posZ);
            if (info.defendingPos.dim == player.dimension && distSq < WarForgeConfig.SIEGE_INFO_RADIUS * WarForgeConfig.SIEGE_INFO_RADIUS) {
                if (distSq < bestDistanceSq) {
                    bestDistanceSq = distSq;
                    closestInfo = info;
                }
            }
        }

        return closestInfo;
    }

	private void renderVeinData(Minecraft mc, Pair<Vein, Quality> veinInfo, boolean hasData, RenderGameOverlayEvent event) {
		GlStateManager.enableAlpha();
		GlStateManager.enableBlend();

		// even if we aren't rendering, count up start time to indicate we are within the same chunk as before
		boolean isNewChunk = lastRenderStartTimeMs == -1;
		long currTimeMs = System.currentTimeMillis();

		// even though intelliJ thinks veinInfo is never null, it definitely should be able to be
		// we render either the item, or some waiting icon
		ItemStack currMemberItemStack = null;
		boolean hasItemToRender = veinInfo != null && veinInfo.getLeft() != null && veinInfo.getLeft().compIds.size() > 0;
		if (hasItemToRender)  {
			// initialize render info
			if (lastRenderStartTimeMs == -1) {
				lastRenderStartTimeMs = currTimeMs;
				compIt = veinInfo.getLeft().compIds.iterator();  // LinkedHashSet should give a consistent ordering
				currComp = compIt.next();
			}

			// check if the display time is up
			else if (currTimeMs - lastRenderStartTimeMs > WarForgeConfig.VEIN_MEMBER_DISPLAY_TIME_MS) {
				lastRenderStartTimeMs = currTimeMs;  // we are updating the component that we are rendering
				if (!compIt.hasNext()) { compIt = veinInfo.getLeft().compIds.iterator(); } // restart from beginning
				currComp = compIt.next();
			}

			currMemberItemStack = currComp.toItem();

			if (currMemberItemStack == null) {
				WarForgeMod.LOGGER.atError().log("Got unexpected null stack for vein " + veinInfo.getLeft().toString());
				return;
			}
		}

		// either use the cached string or make a new one if either no cached exists or we are in a new chunk
		ArrayList<String> compInfoStrings = cachedCompStrings;
		if (cachedCompStrings == null || isNewChunk) {
			compInfoStrings = createVeinInfoStrings(veinInfo, hasData);
		}

        ScreenSpaceUtil.ScreenPos veinPos = WarForgeConfig.POS_VEIN_INDICATOR;
		final int imageSize = hasItemToRender ? 24 : 0;

		// draw the vein info
        final int textHeight = ScreenSpaceUtil.TEXTHEIGHT;
        final int veinTitleWidth = mc.fontRenderer.getStringWidth(compInfoStrings.get(0));
        final int titleX = ScreenSpaceUtil.getX(veinPos, veinTitleWidth);
        final int veinInfoHeight = Math.max(textHeight, (textHeight + imageSize) / 2);
        final int titleY = ScreenSpaceUtil.getY(veinPos, veinInfoHeight) + 4 + (veinInfoHeight - textHeight);
        final int imageX = titleX - imageSize / 2;  // offset the image to the left of the title + gap

        if (titleY > WarForgeConfig.HUD_VERT_CUTOFF_PERCENT * ScreenSpaceUtil.RESOLUTIONY) { return; }  // don't overdraw onto main part of screen
		mc.fontRenderer.drawStringWithShadow(compInfoStrings.get(0), titleX, titleY, 0xFFFFFF);
        veinPos.incrementY(textHeight);

		// draw the item
		if (currMemberItemStack != null) {
			// prepare to render
			GlStateManager.pushMatrix();
			RenderHelper.disableStandardItemLighting();
			GlStateManager.enableDepth();

			// render the item
            // y offset is position of dead center of image, so we need to offset down to align with the text
			GlStateManager.translate(imageX - 4, titleY + textHeight / 2f, 0);
			GlStateManager.scale(imageSize, imageSize, 1);
			GlStateManager.rotate(180, 0, 1, 0);
			GlStateManager.rotate(180, 0, 0, 1);

			RenderItem renderItem = Minecraft.getMinecraft().getRenderItem();
			renderItem.renderItem(currMemberItemStack, ItemCameraTransforms.TransformType.GUI);

			// disable the things we just used
			GlStateManager.disableDepth();
			RenderHelper.enableStandardItemLighting();
			GlStateManager.disableLighting();
			GlStateManager.popMatrix();
		}

		// draw the component strings
		for (int i = 1; i < compInfoStrings.size(); ++i) {
            // we want the components to look left aligned and indented
			String currFormattedComp = compInfoStrings.get(i);
			mc.fontRenderer.drawStringWithShadow(currFormattedComp, titleX + 4, veinPos.getY(), 0xFFFFFF);
            veinPos.incrementY(textHeight);
		}
	}

    private void toggleVeinOverlay(EntityPlayerSP player) {
        showVeinOverlay = !showVeinOverlay;
        lastRenderStartTimeMs = -1;
        cachedCompStrings = null;
        compIt = null;
        currComp = null;

        player.sendMessage(new TextComponentString(I18n.format(
                showVeinOverlay ? "warforge.info.vein.toggle.enabled" : "warforge.info.vein.toggle.disabled")));
    }

	private ArrayList<String> createVeinInfoStrings(Pair<Vein, Quality> veinInfo, boolean hasCached) {
		ArrayList<String> result = new ArrayList<>(1);

		// handle no data specially
		if (!hasCached) {
			result.add(I18n.format("warforge.info.vein.waiting"));
			return result;
		}

		// handle null veins specially
		if (veinInfo == null) {
			result.add(I18n.format("warforge.info.vein.null"));
			return result;
		}

		// translate and format the vein name by supplying the localized quality name as an argument
		Vein currVein = veinInfo.getLeft();
		Quality currQual = veinInfo.getRight();

		// handle unrecognized veins specially
		if (currVein == null || currQual == null) {
			result.add(I18n.format("warforge.info.vein.unrecognized"));
			return result;
		}

        // vein is now guaranteed valid and received; prepare formatted data for display
		result.add(I18n.format(currVein.translationKey,
                I18n.format(currQual.getTranslationKey()) + " [" + currQual.getMultString(currVein) + "]"));
		int dim = Minecraft.getMinecraft().player.dimension;

		// turn each component into the item we will be displaying and list them
		for (StackComparable currComp : currVein.compIds) {
			ItemStack currStack = currComp.toItem();
			if (currStack == null) {
				WarForgeMod.LOGGER.atError().log("Couldn't find item with component id " +
						currComp + " in vein " + currVein.translationKey);
				continue;
			}

			// janky work around for weird default minecraft items which sometimes decide to append .name to the key
			// without updating the translation key the item itself returns
			String translationKey = currStack.getItem().getTranslationKey();
			if (!I18n.hasKey(translationKey) && I18n.hasKey(translationKey + ".name")) { translationKey += ".name"; }

			// if we got an item stack, translate it and display information about it
			StringBuilder compInfo = new StringBuilder(I18n.format(translationKey));
			parseCompInfo(compInfo, currComp, veinInfo, dim);
			result.add(compInfo.toString());
		}

		return result;
	}

	private void parseCompInfo(StringBuilder compInfoStr, StackComparable currComp, Pair<Vein, Quality> veinInfo, int dim) {
		compInfoStr.append(":");
		ArrayList<short[]> yieldInfos = VeinUtils.getYieldInfo(currComp, veinInfo, dim);  // weight, guaranteed yield, % extra yield

		// format each subComp in the form <guaranteedYield# - %comp; +%extra> to show yield distribution for item
		for (short[] subCompInfo : yieldInfos) {
			// show the guaranteed yield info and component weight
			compInfoStr.append(" <[");
			compInfoStr.append(subCompInfo[1]);
			compInfoStr.append("]-");
			compInfoStr.append(VeinUtils.shortToPercentStr(subCompInfo[0]));

			// if there is a chance for an extra yield, display as much
			if (subCompInfo[2] > 0) {
				compInfoStr.append("; +");
				compInfoStr.append(VeinUtils.shortToPercentStr(subCompInfo[2]));
			}

			compInfoStr.append(">,");  // setup next sub comp
		}

		// there will be an extra comma at the end
		compInfoStr.deleteCharAt(compInfoStr.length() - 1);
	}

	private void renderSiegeOverlay(Minecraft mc, SiegeCampProgressInfo infoToRender, RenderGameOverlayEvent event) {
		GlStateManager.enableAlpha();
		GlStateManager.enableBlend();

		// Colors for attacking and defending
		float attackR = (float)(infoToRender.attackingColour >> 16 & 255) / 255.0F;
		float attackG = (float)(infoToRender.attackingColour >> 8 & 255) / 255.0F;
		float attackB = (float)(infoToRender.attackingColour & 255) / 255.0F;
		float defendR = (float)(infoToRender.defendingColour >> 16 & 255) / 255.0F;
		float defendG = (float)(infoToRender.defendingColour >> 8 & 255) / 255.0F;
		float defendB = (float)(infoToRender.defendingColour & 255) / 255.0F;

		// Render Background and Bars
        var pos = WarForgeConfig.POS_SIEGE;
		int xText = ScreenSpaceUtil.getX(pos, 256);  // 256 = width of bar
		int yText = ScreenSpaceUtil.getY(pos, 40);   // 40 = total height (bar + text)

		float scroll = (mc.getFrameTimer().getIndex() + event.getPartialTicks()) * 0.25f;
		scroll = scroll % 10;

        mc.renderEngine.bindTexture(siegeprogress);
        GlStateManager.color(1f, 1f, 1f, 1f);
        RenderUtil.drawTexturedModalRect(tess, xText, yText, 0, 0, 256, 30);

        renderSiegeProgressBar(mc, infoToRender, xText, yText, attackR, attackG, attackB, defendR, defendG, defendB, scroll);
        renderSiegeNotches(mc, infoToRender, xText, yText);

        renderSiegeText(mc, infoToRender, xText, yText);
        if(WarForgeConfig.SIEGE_ENABLE_NEW_TIMER)
            renderSiegeTimer(mc, infoToRender, xText, yText+5);
        renderSiegeAbandonTimer(mc, mc.fontRenderer, infoToRender, xText, yText);
    }

    private void renderSiegeAbandonTimer(Minecraft mc, net.minecraft.client.gui.FontRenderer fontRenderer, SiegeCampProgressInfo info, int xText, int yText) {
        if (info.attackerAbandonSeconds <= 0) return;
        if (ClientClaimChunkCache.playerFactionId.equals(Faction.nullUuid)) return;
        if (!ClientClaimChunkCache.playerFactionId.equals(info.attackingFactionId)) return;
        String text = "Siege abandoned in: " + formatPaddedTimer(info.attackerAbandonSeconds * 1000L);
        int textWidth = fontRenderer.getStringWidth(text);
        int color = info.attackerAbandonSeconds <= 10 ? 0xFF5555 : 0xC79A3A;
        fontRenderer.drawStringWithShadow(text, xText + (128 - textWidth / 2), yText + 44, color);
    }

    private void renderSiegeTimer(Minecraft mc, SiegeCampProgressInfo infoToRender, int xText, int yText){
        String siegeText = formatPaddedTimer( infoToRender.endTimestamp - System.currentTimeMillis() );
        int textWidth = mc.fontRenderer.getStringWidth(siegeText);
        int color =  infoToRender.endTimestamp - System.currentTimeMillis() < 60000 ? 0xFF0000 : 0xFFFFFF;

        mc.fontRenderer.drawStringWithShadow(siegeText, xText+(128-textWidth/2), yText + 28, color);
        if(TIMER_DEBUG){
            mc.fontRenderer.drawStringWithShadow( "End timestamp :"+ infoToRender.endTimestamp, xText-textWidth, yText + 30, 0xFFFFFF);
            mc.fontRenderer.drawStringWithShadow("Raw timestamp difference: "+ (infoToRender.endTimestamp - System.currentTimeMillis()), xText-textWidth, yText + 40, 0xFFFFFF);
        }
    }

    private void renderSiegeProgressBar(Minecraft mc, SiegeCampProgressInfo infoToRender, int xText, int yText, float attackR, float attackG, float attackB, float defendR, float defendG, float defendB, float scroll) {
        int xSize = 256;
        float siegeLength = infoToRender.completionPoint + 5;
        float notchDistance = 224 / siegeLength;

        int firstPx = (int) (notchDistance * (infoToRender.progress > 0 ? 5 : 5 + infoToRender.progress));
        int lastPx = (int) (notchDistance * (infoToRender.progress > 0 ? (infoToRender.progress + 5) : 5));

        boolean isIncreasing = infoToRender.progress > infoToRender.mPreviousProgress;

        if (isIncreasing) {
            GlStateManager.color(attackR, attackG, attackB, 1.0F);
            RenderUtil.drawTexturedModalRect(tess, xText + 16 + firstPx, yText + 17, 16 + (10 - scroll), 44, lastPx - firstPx, 8);
        } else {
            GlStateManager.color(defendR, defendG, defendB, 1.0F);
            RenderUtil.drawTexturedModalRect(tess, xText + 16 + firstPx, yText + 17, 16 + scroll, 54, lastPx - firstPx, 8);
        }
    }

    private void renderSiegeNotches(Minecraft mc, SiegeCampProgressInfo infoToRender, int xText, int yText) {
        float notchDistance = (float) 224 / (infoToRender.completionPoint + 5);

        for (int i = -4; i < infoToRender.completionPoint; i++) {
            int x = (int) ((i + 5) * notchDistance + 16);
            if (i == 0) RenderUtil.drawTexturedModalRect(tess, xText + x - 2, yText + 17, 6, 43, 5, 8);
            else RenderUtil.drawTexturedModalRect(tess, xText + x - 2, yText + 17, 1, 43, 4, 8);
        }
    }

    private void renderSiegeText(Minecraft mc, SiegeCampProgressInfo infoToRender, int xText, int yText) {
        mc.fontRenderer.drawStringWithShadow(infoToRender.defendingName, xText + 6, yText + 6, infoToRender.defendingColour);
        mc.fontRenderer.drawStringWithShadow("VS", xText + 128 - (float) mc.fontRenderer.getStringWidth("VS") / 2, yText + 6, 0xffffff);
        mc.fontRenderer.drawStringWithShadow(infoToRender.attackingName, xText + 256 - 6 - mc.fontRenderer.getStringWidth(infoToRender.attackingName), yText + 6, infoToRender.attackingColour);

        String toWin = (infoToRender.progress < infoToRender.completionPoint) ? (infoToRender.completionPoint - infoToRender.progress) + " to win" : "Station siege to win";
        String toDefend = (infoToRender.progress + 5) + " to defend";
        mc.fontRenderer.drawStringWithShadow(toWin, xText + 256 - 8 - mc.fontRenderer.getStringWidth(toWin), yText + 32, infoToRender.attackingColour);
        mc.fontRenderer.drawStringWithShadow(toDefend, xText + 8, yText + 32, infoToRender.attackingColour);
    }

    private void renderNewAreaToast(Minecraft mc, RenderGameOverlayEvent event) {
        final int stringWidth = mc.fontRenderer.getStringWidth(areaMessage);
        final int totalHeight = 24;

        final ScreenSpaceUtil.ScreenPos pos = WarForgeConfig.POS_TOAST_INDICATOR;
        final boolean isTop = ScreenSpaceUtil.isTop(pos);
        final int extraPadding = pos == ScreenSpaceUtil.ScreenPos.TOP ? 24 : 0;

        final int yOffsetFromBar = 14;  // offset below/above the hotbar or title bar
        final int yText = isTop ? pos.getY() + yOffsetFromBar + extraPadding : pos.getY() - totalHeight - yOffsetFromBar;

        final int xText = ScreenSpaceUtil.getX(pos, stringWidth) + ScreenSpaceUtil.getXOffset(pos, 10);

        float fadeOut = 2.0f * newAreaToastTime / WarForgeConfig.SHOW_NEW_AREA_TIMER;
        fadeOut = Math.min(fadeOut, 1.0f);
        int colour = areaMessageColour | ((int) (fadeOut * 255f) << 24);

        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.color(1f, 1f, 1f, fadeOut);
        GlStateManager.disableTexture2D();

        RenderUtil.drawTexturedModalRect(tess, xText - 50, yText, 0, 0, stringWidth + 100, 1);            // top line
        RenderUtil.drawTexturedModalRect(tess, xText - 25, yText + 23, 0, 0, stringWidth + 50, 1);        // bottom line

        GlStateManager.enableTexture2D();
        mc.fontRenderer.drawStringWithShadow(areaMessage, xText, yText + 11, colour);   // vertically centered text

        var flagTexture = ClientFlagRegistry.getFlagTexture(areaFlagId);
        var flagDims = ClientFlagRegistry.getFlagDimensions(areaFlagId);
        if (flagTexture != null && flagDims != null && flagDims[0] > 0 && flagDims[1] > 0) {
            int maxWidth = 64;
            int maxHeight = 24;
            float scale = Math.min(maxWidth / (float) flagDims[0], maxHeight / (float) flagDims[1]);
            int drawWidth = Math.max(1, Math.round(flagDims[0] * scale));
            int drawHeight = Math.max(1, Math.round(flagDims[1] * scale));
            int flagX = ScreenSpaceUtil.getX(pos, drawWidth);
            flagX += ScreenSpaceUtil.getXOffset(pos, 0);
            int flagY = yText + totalHeight + 4;

            mc.getTextureManager().bindTexture(flagTexture);
            GlStateManager.color(1f, 1f, 1f, fadeOut);
            Gui.drawScaledCustomSizeModalRect(flagX, flagY, 0, 0, flagDims[0], flagDims[1], drawWidth, drawHeight, flagDims[0], flagDims[1]);
            ScreenSpaceUtil.incrementY(pos, drawHeight + 4);
        }

        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();
        ScreenSpaceUtil.incrementY(pos, totalHeight + 14 + extraPadding);
    }

    private void updateRenderData() {
        World world = Minecraft.getMinecraft().world;
        if (world == null) return;

        // Update our list from the old one
        HashMap<DimChunkPos, BorderRenderData> tempData = new HashMap<DimChunkPos, BorderRenderData>();

        // Find all synced claim chunks in our current dimension.
        for (HashMap.Entry<DimChunkPos, ClaimChunkInfo> kvp : new HashMap<>(ClientBorderCache.getChunks()).entrySet()) {
            DimChunkPos chunkPos = kvp.getKey();
            if (chunkPos.dim != world.provider.getDimension()) {
                continue;
            }

            ClaimChunkInfo info = kvp.getValue();
            if (info == null || !info.hasVisibleOutline()) {
                continue;
            }

            if (renderData.containsKey(chunkPos)) {
                BorderRenderData existing = renderData.get(chunkPos);
                existing.factionId = info.outlineFactionId;
                existing.colour = info.outlineColour;
                existing.outlineStyle = info.outlineStyle;
                if (existing.renderList > 0) {
                    GlStateManager.glDeleteLists(existing.renderList, 1);
                    existing.renderList = 0;
                }
                tempData.put(chunkPos, existing);
            } else {
                BorderRenderData data = new BorderRenderData();
                data.factionId = info.outlineFactionId;
                data.colour = info.outlineColour;
                data.outlineStyle = info.outlineStyle;
                tempData.put(chunkPos, data);
            }
        }

        for (HashMap.Entry<DimChunkPos, BorderRenderData> oldEntry : renderData.entrySet()) {
            if (!tempData.containsKey(oldEntry.getKey()) && oldEntry.getValue().renderList > 0) {
                GlStateManager.glDeleteLists(oldEntry.getValue().renderList, 1);
            }
        }

        renderData = tempData;

    }

    private void updateRandomMesh() {
        World world = Minecraft.getMinecraft().world;
        if (world == null || renderData.isEmpty()) return;
        int index = world.rand.nextInt(renderData.size());

        // Then construct the mesh for one random entry
        for (HashMap.Entry<DimChunkPos, BorderRenderData> kvp : renderData.entrySet()) {
            if (index > 0) {
                index--;
                continue;
            }

            DimChunkPos pos = kvp.getKey();
            BorderRenderData data = kvp.getValue();

            if (data.renderList != 0) {
                GlStateManager.glDeleteLists(data.renderList, 1);
                data.renderList = 0;
            }
            data.renderList = GLAllocation.generateDisplayLists(1);
            GlStateManager.glNewList(data.renderList, 4864);

            int minY = 0;
            int maxY = world.getActualHeight();
            int scanMin = minY;
            int scanMax = maxY;
            int originX = pos.getXStart();
            int originZ = pos.getZStart();
            int surfMin = Integer.MAX_VALUE;
            int surfMax = Integer.MIN_VALUE;
            for (int i = 0; i < 16; i++) {
                int hN = world.getHeight(originX + i, originZ);
                int hS = world.getHeight(originX + i, originZ + 15);
                int hW = world.getHeight(originX, originZ + i);
                int hE = world.getHeight(originX + 15, originZ + i);
                surfMin = Math.min(surfMin, Math.min(Math.min(hN, hS), Math.min(hW, hE)));
                surfMax = Math.max(surfMax, Math.max(Math.max(hN, hS), Math.max(hW, hE)));
            }
            if (surfMin != Integer.MAX_VALUE) {
                scanMin = Math.max(minY, surfMin - BORDER_Y_SCAN_MARGIN);
                scanMax = Math.min(maxY, surfMax + BORDER_Y_SCAN_MARGIN);
            }
            BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();

            boolean renderNorth = true, renderEast = true, renderWest = true, renderSouth = true, renderNorthWest = true, renderNorthEast = true, renderSouthWest = true, renderSouthEast = true;
            if (renderData.containsKey(pos.north()))
                renderNorth = !sameBorderGroup(renderData.get(pos.north()), data);
            if (renderData.containsKey(pos.east()))
                renderEast = !sameBorderGroup(renderData.get(pos.east()), data);
            if (renderData.containsKey(pos.south()))
                renderSouth = !sameBorderGroup(renderData.get(pos.south()), data);
            if (renderData.containsKey(pos.west()))
                renderWest = !sameBorderGroup(renderData.get(pos.west()), data);

            //for super spesific edge cases
            if (renderData.containsKey(pos.north().west()))
                renderNorthWest = !sameBorderGroup(renderData.get(pos.north().west()), data);
            if (renderData.containsKey(pos.north().east()))
                renderNorthEast = !sameBorderGroup(renderData.get(pos.north().east()), data);
            if (renderData.containsKey(pos.south().west()))
                renderSouthWest = !sameBorderGroup(renderData.get(pos.south().west()), data);
            if (renderData.containsKey(pos.south().east()))
                renderSouthEast = !sameBorderGroup(renderData.get(pos.south().east()), data);

            // North edge, [0,0] -> [16,0] wall
            if (renderNorth) {
                // A smidge of semi-translucent wall from [0,0,0] to [2,256,0] offset by 0.25
                if (renderWest) {
                    tess.getBuffer().begin(7, DefaultVertexFormats.POSITION_TEX);
                    tess.getBuffer().pos(0 + alignment, 0, alignment).tex(64f, 0.5f).endVertex();
                    tess.getBuffer().pos(2 + alignment, 0, alignment).tex(64f, 0f).endVertex();
                    tess.getBuffer().pos(2 + alignment, 128, alignment).tex(0f, 0f).endVertex();
                    tess.getBuffer().pos(0 + alignment, 128, alignment).tex(0f, 0.5f).endVertex();
                    tess.draw();
                }

                // A smidge of semi-translucent wall from [14,0,0] to [16,256,0] offset by 0.25
                if (renderEast) {
                    tess.getBuffer().begin(7, DefaultVertexFormats.POSITION_TEX);
                    tess.getBuffer().pos(16 - alignment, 0, alignment).tex(64f, 0.5f).endVertex();
                    tess.getBuffer().pos(14 - alignment, 0, alignment).tex(64f, 0f).endVertex();
                    tess.getBuffer().pos(14 - alignment, 128, alignment).tex(0f, 0f).endVertex();
                    tess.getBuffer().pos(16 - alignment, 128, alignment).tex(0f, 0.5f).endVertex();
                    tess.draw();
                }
            }

            // South edge
            if (renderSouth) {
                if (renderWest) {
                    tess.getBuffer().begin(7, DefaultVertexFormats.POSITION_TEX);
                    tess.getBuffer().pos(0 + alignment, 0, 16d - alignment).tex(64f, 0.5f).endVertex();
                    tess.getBuffer().pos(2 + alignment, 0, 16d - alignment).tex(64f, 0f).endVertex();
                    tess.getBuffer().pos(2 + alignment, 128, 16d - alignment).tex(0f, 0f).endVertex();
                    tess.getBuffer().pos(0 + alignment, 128, 16d - alignment).tex(0f, 0.5f).endVertex();
                    tess.draw();
                }

                if (renderEast) {
                    tess.getBuffer().begin(7, DefaultVertexFormats.POSITION_TEX);
                    tess.getBuffer().pos(16 - alignment, 0, 16d - alignment).tex(64f, 0.5f).endVertex();
                    tess.getBuffer().pos(14 - alignment, 0, 16d - alignment).tex(64f, 0f).endVertex();
                    tess.getBuffer().pos(14 - alignment, 128, 16d - alignment).tex(0f, 0f).endVertex();
                    tess.getBuffer().pos(16 - alignment, 128, 16d - alignment).tex(0f, 0.5f).endVertex();
                    tess.draw();
                }
            }

            // East edge, [0,0] -> [0,16] wall
            if (renderWest) {
                if (renderNorth) {
                    tess.getBuffer().begin(7, DefaultVertexFormats.POSITION_TEX);
                    tess.getBuffer().pos(alignment, 0, 0 + alignment).tex(64f, 0.5f).endVertex();
                    tess.getBuffer().pos(alignment, 0, 2 + alignment).tex(64f, 0f).endVertex();
                    tess.getBuffer().pos(alignment, 128, 2 + alignment).tex(0f, 0f).endVertex();
                    tess.getBuffer().pos(alignment, 128, 0 + alignment).tex(0f, 0.5f).endVertex();
                    tess.draw();
                }

                if (renderSouth) {
                    tess.getBuffer().begin(7, DefaultVertexFormats.POSITION_TEX);
                    tess.getBuffer().pos(alignment, 0, 16 - alignment).tex(64f, 0.5f).endVertex();
                    tess.getBuffer().pos(alignment, 0, 14 - alignment).tex(64f, 0f).endVertex();
                    tess.getBuffer().pos(alignment, 128, 14 - alignment).tex(0f, 0f).endVertex();
                    tess.getBuffer().pos(alignment, 128, 16 - alignment).tex(0f, 0.5f).endVertex();
                    tess.draw();
                }
            }

            // West edge
            if (renderEast) {
                if (renderNorth) {
                    tess.getBuffer().begin(7, DefaultVertexFormats.POSITION_TEX);
                    tess.getBuffer().pos(16d - alignment, 0, 0 + alignment).tex(64f, 0.5f).endVertex();
                    tess.getBuffer().pos(16d - alignment, 0, 2 + alignment).tex(64f, 0f).endVertex();
                    tess.getBuffer().pos(16d - alignment, 128, 2 + alignment).tex(0f, 0f).endVertex();
                    tess.getBuffer().pos(16d - alignment, 128, 0 + alignment).tex(0f, 0.5f).endVertex();
                    tess.draw();
                }

                if (renderSouth) {
                    tess.getBuffer().begin(7, DefaultVertexFormats.POSITION_TEX);
                    tess.getBuffer().pos(16d - alignment, 0, 16 - alignment).tex(64f, 0.5f).endVertex();
                    tess.getBuffer().pos(16d - alignment, 0, 14 - alignment).tex(64f, 0f).endVertex();
                    tess.getBuffer().pos(16d - alignment, 128, 14 - alignment).tex(0f, 0f).endVertex();
                    tess.getBuffer().pos(16d - alignment, 128, 16 - alignment).tex(0f, 0.5f).endVertex();
                    tess.draw();
                }
            }
            if (renderNorth || renderSouth) {
                for (int x = 0; x < 16; x++) {
                    for (int y = scanMin; y < scanMax; y++) {
                        if (x < 15) {
                            if (renderNorth) {
                                boolean air0 = world.isAirBlock(mp.setPos(pos.getXStart() + x, y, pos.getZStart()));
                                boolean air1 = world.isAirBlock(mp.setPos(pos.getXStart() + x + 1, y, pos.getZStart()));
                                renderZEdge(world, tess, x, y, pos.getZStart(), smaller_alignment + 0.001d, air0, air1, 0);
                            }
                            if (renderSouth) {
                                boolean air0 = world.isAirBlock(mp.setPos(pos.getXStart() + x, y, pos.getZEnd()));
                                boolean air1 = world.isAirBlock(mp.setPos(pos.getXStart() + x + 1, y, pos.getZEnd()));
                                renderZEdge(world, tess, x, y, pos.getZEnd(), 16d - smaller_alignment + 0.001d, air0, air1, 0);
                            }
                        }
                        if (y < scanMax - 1) {
                            if (renderNorth) {
                                boolean air0 = world.isAirBlock(mp.setPos(pos.getXStart() + x, y, pos.getZStart()));
                                boolean air1 = world.isAirBlock(mp.setPos(pos.getXStart() + x, y + 1, pos.getZStart()));
                                if (x == 15 && renderEast) {
                                    renderZVerticalCorner(world, tess, x - smaller_alignment, y, smaller_alignment, air0, air1, 0, -smaller_alignment);
                                } else if (x == 0 && renderWest) {
                                    renderZVerticalCorner(world, tess, x, y, smaller_alignment, air0, air1, 0, -smaller_alignment);
                                } else {
                                    renderZVerticalEdge(world, tess, x, y, pos.getZStart(), smaller_alignment, air0, air1, 0);
                                }
                            }
                            if (renderSouth) {
                                boolean air0 = world.isAirBlock(mp.setPos(pos.getXStart() + x, y, pos.getZEnd()));
                                boolean air1 = world.isAirBlock(mp.setPos(pos.getXStart() + x, y + 1, pos.getZEnd()));
                                if (x == 15 && renderEast) {
                                    renderZVerticalCorner(world, tess, x - smaller_alignment, y, 16 - smaller_alignment, air0, air1, 0, -smaller_alignment);
                                } else if (x == 0 && renderWest) {
                                    renderZVerticalCorner(world, tess, x, y, 16 - smaller_alignment, air0, air1, 0, -smaller_alignment);
                                } else {
                                    renderZVerticalEdge(world, tess, x, y, pos.getZEnd(), 16d - smaller_alignment, air0, air1, 0);
                                }
                            }
                        }
                    }
                }
            }

            if (renderEast || renderWest) {
                for (int z = 0; z < 16; z++) {
                    for (int y = scanMin; y < scanMax; y++) {
                        if (z < 15) {
                            if (renderWest) {
                                boolean air0 = world.isAirBlock(mp.setPos(pos.getXStart(), y, pos.getZStart() + z));
                                boolean air1 = world.isAirBlock(mp.setPos(pos.getXStart(), y, pos.getZStart() + z + 1));
                                renderXEdge(world, tess, pos.getXStart(), y, z, smaller_alignment + 0.001d, air0, air1, 0);
                            }
                            if (renderEast) {
                                boolean air0 = world.isAirBlock(mp.setPos(pos.getXEnd(), y, pos.getZStart() + z));
                                boolean air1 = world.isAirBlock(mp.setPos(pos.getXEnd(), y, pos.getZStart() + z + 1));
                                renderXEdge(world, tess, pos.getXEnd(), y, z, 16d - smaller_alignment + 0.001d, air0, air1, 0);
                            }
                        }
                        if (y < scanMax - 1) {
                            if (renderWest) {
                                boolean air0 = world.isAirBlock(mp.setPos(pos.getXStart(), y, pos.getZStart() + z));
                                boolean air1 = world.isAirBlock(mp.setPos(pos.getXStart(), y + 1, pos.getZStart() + z));
                                if (z == 15 && renderSouth) {
                                    renderXVerticalCorner(world, tess, smaller_alignment, y, z - smaller_alignment, air0, air1, 0, -smaller_alignment);
                                } else if (z == 0 && renderNorth) {
                                    renderXVerticalCorner(world, tess, smaller_alignment, y, z, air0, air1, 0, -smaller_alignment);
                                } else {
                                    renderXVerticalEdge(world, tess, pos.getXStart(), y, z, smaller_alignment, air0, air1, 0);
                                }
                            }
                            if (renderEast) {
                                boolean air0 = world.isAirBlock(mp.setPos(pos.getXEnd(), y, pos.getZStart() + z));
                                boolean air1 = world.isAirBlock(mp.setPos(pos.getXEnd(), y + 1, pos.getZStart() + z));
                                if (z == 15 && renderSouth) {
                                    renderXVerticalCorner(world, tess, 16d - smaller_alignment, y, z - smaller_alignment, air0, air1, 0, -smaller_alignment);
                                } else if (z == 0 && renderNorth) {
                                    renderXVerticalCorner(world, tess, 16d - smaller_alignment, y, z, air0, air1, 0, -smaller_alignment);
                                } else {
                                    renderXVerticalEdge(world, tess, pos.getXEnd(), y, z, 16d - smaller_alignment, air0, air1, 0);
                                }
                            }
                        }
                    }
                }
            }

            //Edge corner cases because of autism

            if (renderNorthEast) {
                if (!renderNorth && !renderEast) {
                    for (int y = scanMin; y < scanMax; y++) {
                        boolean air0 = world.isAirBlock(mp.setPos(pos.getXEnd(), y, pos.getZStart()));
                        boolean air1 = world.isAirBlock(mp.setPos(pos.getXEnd(), y + 1, pos.getZStart()));
                        renderZVerticalCorner(world, tess, 15, y, smaller_alignment, air0, air1, 0, smaller_alignment - 1.0);
                        renderXVerticalCorner(world, tess, 16 - smaller_alignment, y, smaller_alignment - 1, air0, air1, 0, smaller_alignment - 1.0);
                    }
                }
            }
            if (renderNorthWest) {
                if (!renderNorth && !renderWest) {
                    for (int y = scanMin; y < scanMax; y++) {
                        boolean air0 = world.isAirBlock(mp.setPos(pos.getXStart(), y, pos.getZStart()));
                        boolean air1 = world.isAirBlock(mp.setPos(pos.getXStart(), y + 1, pos.getZStart()));
                        renderZVerticalCorner(world, tess, -1 + smaller_alignment, y, smaller_alignment, air0, air1, 0, smaller_alignment - 1.0);
                        renderXVerticalCorner(world, tess, smaller_alignment, y, smaller_alignment - 1, air0, air1, 0, smaller_alignment - 1.0);
                    }
                }
            }
            if (renderSouthWest) {
                if (!renderSouth && !renderWest) {
                    for (int y = scanMin; y < scanMax; y++) {
                        boolean air0 = world.isAirBlock(mp.setPos(pos.getXStart(), y, pos.getZEnd()));
                        boolean air1 = world.isAirBlock(mp.setPos(pos.getXStart(), y + 1, pos.getZEnd()));
                        renderZVerticalCorner(world, tess, -1 + smaller_alignment, y, 16 - smaller_alignment, air0, air1, 0, smaller_alignment - 1.0);
                        renderXVerticalCorner(world, tess, smaller_alignment, y, 15, air0, air1, 0, smaller_alignment - 1.0);
                    }
                }
            }
            if (renderSouthEast) {
                if (!renderSouth && !renderEast) {
                    for (int y = scanMin; y < scanMax; y++) {
                        boolean air0 = world.isAirBlock(mp.setPos(pos.getXEnd(), y, pos.getZEnd()));
                        boolean air1 = world.isAirBlock(mp.setPos(pos.getXEnd(), y + 1, pos.getZEnd()));
                        renderZVerticalCorner(world, tess, 15, y, 16 - smaller_alignment, air0, air1, 0, smaller_alignment - 1.0);
                        renderXVerticalCorner(world, tess, 16 - smaller_alignment, y, 15, air0, air1, 0, smaller_alignment - 1.0);
                    }
                }
            }


            GlStateManager.glEndList();
            break;
        }
    }


    @SubscribeEvent
    public void onRenderLast(RenderWorldLastEvent event) {
        // Cache Minecraft instance
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null) return;

        // Get the camera position
        Entity camera = mc.getRenderViewEntity();
        double x = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * event.getPartialTicks();
        double y = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * event.getPartialTicks();
        double z = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * event.getPartialTicks();

        // Push OpenGL matrix and attributes
        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();

        // Setup lighting and textures
        mc.entityRenderer.enableLightmap();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.disableCull();
        mc.entityRenderer.disableLightmap();

        // Update render data if necessary
        if (CLAIMS_DIRTY) {
            updateRenderData();
            CLAIMS_DIRTY = false;
        }

        // Choose rendering state for the active border set
        if (WarForgeConfig.DO_FANCY_RENDERING || hasConqueredBorders()) {
            GlStateManager.enableAlpha();
            GlStateManager.enableBlend();
        }

        // Slower update speed on fast graphics
        if (player.world.rand.nextInt(WarForgeConfig.RANDOM_BORDER_REDRAW_DENOMINATOR) == 0) {
            updateRandomMesh();
        }

        // Render chunk borders
        renderChunkBorders(x, y, z);

        // Render player placement overlay (if necessary)
        renderPlayerPlacementOverlay(player, x, y, z, event.getPartialTicks());

        // Render flags (Citadels)
        //renderCitadelFlags(x, y, z);

        // Reset OpenGL state
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.popAttrib();
        GlStateManager.popMatrix();
    }

    private void renderChunkBorders(double x, double y, double z) {
        if (!WarForgeMod.showBorders) {
			return;
		}

        int borderDistChunks = WarForgeConfig.BORDER_RENDER_DISTANCE > 0
                ? WarForgeConfig.BORDER_RENDER_DISTANCE
                : Minecraft.getMinecraft().gameSettings.renderDistanceChunks;
        double maxBorderDistSq = (borderDistChunks * 16.0) * (borderDistChunks * 16.0);

        ResourceLocation boundTexture = null;
        for (HashMap.Entry<DimChunkPos, BorderRenderData> kvp : renderData.entrySet()) {
            DimChunkPos pos = kvp.getKey();
            BorderRenderData data = kvp.getValue();

            double dxCam = (pos.x * 16 + 8) - x;
            double dzCam = (pos.z * 16 + 8) - z;
            if (dxCam * dxCam + dzCam * dzCam > maxBorderDistSq) {
                continue;
            }

            if (data.renderList >= 0) {
                GlStateManager.pushMatrix();

                ResourceLocation desiredTexture = getBorderTexture(data);
                if (desiredTexture != null && !desiredTexture.equals(boundTexture)) {
                    Minecraft.getMinecraft().renderEngine.bindTexture(desiredTexture);
                    boundTexture = desiredTexture;
                }

                int colour = data.colour;
                float r = (float) (colour >> 16 & 255) / 255.0F;
                float g = (float) (colour >> 8 & 255) / 255.0F;
                float b = (float) (colour & 255) / 255.0F;
                GlStateManager.color(r, g, b, 1.0F);

                GlStateManager.translate(pos.x * 16 - x, 0 - y, pos.z * 16 - z);
                GlStateManager.callList(data.renderList);

                GlStateManager.popMatrix();
            }
        }
    }

    private boolean hasConqueredBorders() {
        for (BorderRenderData data : renderData.values()) {
            if (data.outlineStyle == ClaimChunkInfo.OUTLINE_CONQUERED) {
                return true;
            }
        }
        return false;
    }

    private boolean sameBorderGroup(BorderRenderData first, BorderRenderData second) {
        return first.factionId.equals(second.factionId) && first.outlineStyle == second.outlineStyle;
    }

    private ResourceLocation getBorderTexture(BorderRenderData data) {
        if (data.outlineStyle == ClaimChunkInfo.OUTLINE_CONQUERED) {
            return textureConquered;
        }
        return WarForgeConfig.DO_FANCY_RENDERING ? texture : fastTexture;
    }

    private void renderPlayerPlacementOverlay(EntityPlayer player, double x, double y, double z, float partialTicks) {
        if (player.getHeldItemMainhand().getItem() instanceof ItemBlock) {
            boolean shouldRender = false;
            Block holding = ((ItemBlock) player.getHeldItemMainhand().getItem()).getBlock();

            // Check if the block being held is one that should render the placement overlay
            if (holding == Content.basicClaimBlock || holding == Content.citadelBlock || holding == Content.reinforcedClaimBlock) {
                shouldRender = true;
            }

            // If we need to render, check for ray tracing and render accordingly
            if (shouldRender) {
                renderPlacementOverlay(player, x, y, z, partialTicks);
            }
        }
    }

    private void renderPlacementOverlay(EntityPlayer player, double x, double y, double z, float partialTicks) {
        DimChunkPos playerPos = new DimChunkPos(player.dimension, player.getPosition());
        RayTraceResult result = player.rayTrace(10.0f, partialTicks);
        if (result != null && result.typeOfHit == RayTraceResult.Type.BLOCK) {
            playerPos = new DimChunkPos(player.dimension, result.getBlockPos());
        }

        boolean canPlace = checkPlacementValidity(playerPos, player.getHeldItem(EnumHand.MAIN_HAND).getItem(), player.getHorizontalFacing());
        GlStateManager.color(canPlace ? 0f : 1f, canPlace ? 1f : 0f, 0f, 1.0F);
        Minecraft.getMinecraft().renderEngine.bindTexture(overlayTex);
        GlStateManager.translate(playerPos.x * 16 - x, 0 - y, playerPos.z * 16 - z);

        for (int i = 0; i < 16; i++) {
            for (int k = 0; k < 16; k++) {
                BlockPos pos = new BlockPos(playerPos.x * 16 + i, player.posY, playerPos.z * 16 + k);
                tess.getBuffer().begin(7, DefaultVertexFormats.POSITION_TEX);
                tess.getBuffer().pos(i, pos.getY() + 1.5d, k).tex(0f, 0f).endVertex();
                tess.getBuffer().pos(i + 1, pos.getY() + 1.5d, k).tex(1f, 0f).endVertex();
                tess.getBuffer().pos(i + 1, pos.getY() + 1.5d, k + 1).tex(1f, 1f).endVertex();
                tess.getBuffer().pos(i, pos.getY() + 1.5d, k + 1).tex(0f, 1f).endVertex();
                tess.draw();
            }
        }
    }

    private boolean checkPlacementValidity(DimChunkPos playerPos, Item holding, EnumFacing facing) {
        boolean canPlace = true;
        List<DimChunkPos> siegeablePositions = new ArrayList<>();

        for (HashMap.Entry<DimChunkPos, ClaimChunkInfo> kvp : new HashMap<>(ClientBorderCache.getChunks()).entrySet()) {
            DimChunkPos chunkPos = kvp.getKey();
            ClaimChunkInfo info = kvp.getValue();
            if (info == null || info.factionId.equals(Faction.nullUuid)) {
                continue;
            }
            if (playerPos.x == chunkPos.x && playerPos.z == chunkPos.z && playerPos.dim == chunkPos.dim) {
                canPlace = false;
            }
            siegeablePositions.add(chunkPos);
        }

        // If holding siege camp block, allow placement if adjacent to siegable positions
        if (holding == Content.siegeCampBlockItem) {
            canPlace = canPlace && siegeablePositions.contains(playerPos.Offset(facing, 1));
        }

        return canPlace;
    }


    private static class BorderRenderData {
        public UUID factionId = Faction.nullUuid;
        public int colour = 0xFFFFFF;
        public byte outlineStyle = ClaimChunkInfo.OUTLINE_NONE;
        public int renderList = -1;
    }
}
