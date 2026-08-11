package com.flansmod.warforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.apache.commons.lang3.tuple.Pair;
import com.flansmod.warforge.api.vein.Quality;
import com.flansmod.warforge.api.vein.Vein;
import com.flansmod.warforge.api.vein.init.VeinUtils;
import com.flansmod.warforge.api.modularui.ChunkMapTextureDaemon;
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
import com.flansmod.warforge.server.ItemMatcher;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

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
    // Border meshes are cached per chunk; only rebuild a few per frame so a mass invalidation
    private static final int MAX_BORDER_REBUILDS_PER_FRAME = 8;
    // Vertical padding (blocks) added around the chunk's perimeter surface band when scanning the
    // per-block border silhouette, so the loop covers the surface step without walking the full column.
    private static final int BORDER_Y_SCAN_MARGIN = 4;
    // Border claim sync: prefetch this many chunks beyond the render-distance cull so movement never
    // reveals unsynced borders, and only re-request after the player drifts this far from the last
    // sync centre (the request "cache" that stops per-chunk flooding). Plus a slow safety resync to
    // pick up claim changes inside the synced window.
    private static final int BORDER_SYNC_PREFETCH = 4;
    private static final int BORDER_SAFETY_RESYNC_TICKS = 100;
    // Identity: border meshes are built in chunk-local space and offset per frame via the model-view
    // matrix at draw time, so the cached buffer never depends on the camera position.
    private static final Matrix4f BORDER_LOCAL_MATRIX = new Matrix4f();
    private static final ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(Tags.MODID, "world/borders.png");
    private static final ResourceLocation textureConquered = ResourceLocation.fromNamespaceAndPath(Tags.MODID, "world/borders_restricted.png");
    private static final ResourceLocation fastTexture = ResourceLocation.fromNamespaceAndPath(Tags.MODID, "world/borders_fast.png");
    private static final ResourceLocation overlayTex = ResourceLocation.fromNamespaceAndPath(Tags.MODID, "world/overlay.png");
    private static final ResourceLocation siegeprogress = ResourceLocation.fromNamespaceAndPath(Tags.MODID, "gui/siegeprogressslim.png");
    public static long nextSiegeDayMs = 0L;
    public static long nextYieldDayMs = 0L;
    public static boolean CLAIMS_DIRTY = false;
    public static boolean UI_DEBUG = false;
    public static boolean TIMER_DEBUG = false;
    public static boolean DYNAMIC_SIEGE_OVERLAY = true;
    public static boolean animateDebugSiege = false;
    public static SiegeCampProgressInfo debugSiegeInfo = null;
    private static int debugAnimTicks = 0;
    private static int debugAnimDir = 1;
    public static boolean showVeinOverlay = true;
    private DimChunkPos playerChunkPos = new DimChunkPos(Level.OVERWORLD, 0, 0);
    private DimChunkPos lastClaimSyncChunk = new DimChunkPos(Level.OVERWORLD, Integer.MIN_VALUE, Integer.MIN_VALUE);
    private float newAreaToastTime = 0;
    private String areaMessage = "";
    private int areaMessageColour = 0xFF_FF_FF_FF;
    private String areaFlagId = "";
    private HashMap<DimChunkPos, BorderRenderData> renderData = new HashMap<>();
    // Coverage cache for the passive border sync: the centre/radius of the last window we requested.
    private DimChunkPos lastBorderSyncCenter = null;
    private int lastBorderSyncRadius = 0;

    // -1 indicates the chunk wasn't the targeting of previous probe(s)
    private static ArrayList<String> cachedCompStrings = null;
    private static Object2LongOpenHashMap<DimChunkPos> permitChunkReprobeMs = new Object2LongOpenHashMap<>();
    private static long lastRenderStartTimeMs = -1;  // (curr time - this) / (display time (ms)) to get index
    private static Iterator<ItemMatcher> compIt = null;
    private static ItemMatcher currComp = null;

    public ClientTickHandler() {
    }

    public static KeyMapping toggleBordersKey = new KeyMapping("key.warforge.showborders", KeyConflictContext.IN_GAME, KeyModifier.NONE, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.warforge.cathegory");
    public static KeyMapping claimManagerKey = new KeyMapping("key.warforge.claimmanager", KeyConflictContext.IN_GAME, KeyModifier.NONE, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, "key.warforge.cathegory");
    public static KeyMapping toggleVeinOverlayKey = new KeyMapping("key.warforge.toggleveinoverlay", KeyConflictContext.IN_GAME, KeyModifier.NONE, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, "key.warforge.cathegory");

    private void cleanupBorderRenderData() {
        for (BorderRenderData data : renderData.values()) {
            data.disposeVbo();
        }
        renderData.clear();
        ClientBorderCache.clear();
        lastBorderSyncCenter = null;
    }

    @SubscribeEvent
    public void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
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
        ClientClaimChunkCache.replaceAll(Level.OVERWORLD, 0, 0, 0, Faction.nullUuid, 0, 0, 0, 0, new ArrayList<ClaimChunkInfo>());
        CLAIMS_DIRTY = true;
        showVeinOverlay = true;
        areaFlagId = "";
    }

    @SubscribeEvent
    public void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        cleanupBorderRenderData();
        Minecraft.getInstance().tell(ChunkMapTextureDaemon::releaseAll);
        ServerTerrainCache.clear();
        ClaimManagerGuiFactory.resetSiegeState();
        ClientFlagRegistry.clear();
    }

    @SubscribeEvent
    public void onScreenInit(ScreenEvent.Init.Post event) {
        DeferredGuiOpen.onScreenOpened();
    }

    // A claim chunk's border traces its terrain, so when that chunk's block data (re)loads on the
    // client its cached mesh is stale — mark it for rebuild. (Block edits within an already-loaded
    // chunk are picked up by the periodic CLAIMS_DIRTY refresh in onTick.)
    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof Level level) || !level.isClientSide()) {
            return;
        }
        ChunkPos cp = event.getChunk().getPos();
        BorderRenderData data = renderData.get(new DimChunkPos(level.dimension(), cp.x, cp.z));
        if (data != null) {
            data.dirty = true;
        }
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent.Post tick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // Open any sub-GUI requested from inside another MUI screen (see DeferredGuiOpen).
        DeferredGuiOpen.tick();

        // Handle client packets and perform the client-side tick
        WarForgeMod.NETWORK.handleClientPackets();
        ChunkMapTextureDaemon.flushTextureQueue();
        WarForgeMod.proxy.TickClient();

        tickDebugSiege();

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

        LocalPlayer player = mc.player;
        if (player.tickCount % 200 == 0) {
            CLAIMS_DIRTY = true;
        }

        ResourceKey<Level> playerDim = player.level().dimension();
        DimChunkPos standing = new DimChunkPos(playerDim, player.blockPosition());

        // when we leave a chunk, restart iteration on vein members
        if (!standing.equals(playerChunkPos)) {
            lastRenderStartTimeMs = -1;
        }

        boolean guiOpen = mc.screen != null;
        if (!guiOpen) {
            // Sync claim outlines for borders over the render distance (clamped to min of client and
            // server view distance via getEffectiveRenderDistance), plus a prefetch ring. Only
            // re-request when the player drifts past the prefetch from the last sync centre, the
            // radius changes, or a slow safety interval elapses — so we don't flood the server.
            int renderDist = WarForgeConfig.BORDER_RENDER_DISTANCE > 0
                    ? WarForgeConfig.BORDER_RENDER_DISTANCE
                    : mc.options.getEffectiveRenderDistance();
            int syncRadius = Math.min(renderDist + BORDER_SYNC_PREFETCH, WarForgeConfig.BORDER_SYNC_MAX_RADIUS);
            boolean resync = lastBorderSyncCenter == null
                    || !lastBorderSyncCenter.dim.equals(playerDim)
                    || syncRadius != lastBorderSyncRadius
                    || Math.max(Math.abs(standing.x - lastBorderSyncCenter.x), Math.abs(standing.z - lastBorderSyncCenter.z)) > BORDER_SYNC_PREFETCH
                    || player.tickCount % BORDER_SAFETY_RESYNC_TICKS == 0;
            if (resync) {
                requestClaimChunkData(standing, syncRadius, true);
                lastBorderSyncCenter = standing;
                lastBorderSyncRadius = syncRadius;
            }
            lastClaimSyncChunk = standing;
        } else if (player.tickCount % 40 == 0 && lastClaimSyncChunk.dim.equals(playerDim)
                && !ClaimManagerGuiFactory.isRemoteSiegeView()) {
            // While a stage-2 siege picker is open on a remote target, don't clobber its
            // target-centered claim window with a player-centered refresh.
            requestClaimChunkData(lastClaimSyncChunk);
            // Force a fresh border sync once the GUI closes.
            lastBorderSyncCenter = null;
        }

        if (claimManagerKey.consumeClick()) {
            ClaimManagerGuiFactory.resetSiegeState();
            ClaimManagerGuiFactory.INSTANCE.openClient(standing, WarForgeConfig.CLAIM_MANAGER_RADIUS, -1, -1);
        }

        if (toggleBordersKey.consumeClick()) {
            WarForgeMod.showBorders = !WarForgeMod.showBorders;
            player.sendSystemMessage(Component.literal("Borders " + (WarForgeMod.showBorders ? "enabled" : "disabled")));
        }

        if (toggleVeinOverlayKey.consumeClick()) {
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
    public void onRenderHUD(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        ScreenSpaceUtil.resetOffsets();

        GuiGraphics graphics = event.getGuiGraphics();
        float partialTicks = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        LocalPlayer player = mc.player;

        // Siege camp info
        SiegeCampProgressInfo infoToRender = !UI_DEBUG ? getClosestSiegeCampInfo(player) : getOrCreateDebugSiege();

        if (infoToRender != null) {
            renderSiegeOverlay(mc, graphics, infoToRender, partialTicks);
        }

        // Timer info
        if (WarForgeConfig.SHOW_YIELD_TIMERS) {
            renderTimers(mc, graphics);
        }

        // New Area Toast
        if (newAreaToastTime > 0.0f) {
            renderNewAreaToast(mc, graphics);
        }

        // get the vein info
        if (showVeinOverlay) {
            DimChunkPos currPos = new DimChunkPos(player.level().dimension(), player.blockPosition());
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

            renderVeinData(mc, graphics, veinInfo, hasPosData);
        }
    }

    private void renderTimers(Minecraft mc, GuiGraphics graphics) {
        int screenWidth = ScreenSpaceUtil.RESOLUTIONX;

        int padding = 4;
        int textHeight = ScreenSpaceUtil.TEXTHEIGHT + padding;

        ScreenSpaceUtil.ScreenPos pos = WarForgeConfig.POS_TIMERS;

        // Siege progress
        if (!WarForgeConfig.SIEGE_ENABLE_NEW_TIMER || UI_DEBUG) {
            String siegeText = "Siege Progress: " + formatPaddedTimer(nextSiegeDayMs - System.currentTimeMillis());
            int textWidth = mc.font.width(siegeText);
            int x = ScreenSpaceUtil.shouldCenterX(pos) ? ScreenSpaceUtil.centerX(screenWidth, textWidth) : ScreenSpaceUtil.getX(pos, textWidth) + ScreenSpaceUtil.getXOffset(pos, padding);
            int ySiege = pos.getY() + ScreenSpaceUtil.getYOffset(pos, textHeight);

            graphics.drawString(mc.font, siegeText, x, ySiege, 0xffffff, true);
            ScreenSpaceUtil.incrementY(pos, textHeight);
        }

        // Next yields
        String yieldText = "Next yields: " + formatPaddedTimer(nextYieldDayMs - System.currentTimeMillis());
        int textWidth = mc.font.width(yieldText);
        int x = ScreenSpaceUtil.shouldCenterX(pos) ? ScreenSpaceUtil.centerX(screenWidth, textWidth) : ScreenSpaceUtil.getX(pos, textWidth) + ScreenSpaceUtil.getXOffset(pos, padding);
        int yYield = pos.getY() + ScreenSpaceUtil.getYOffset(pos, textHeight);

        graphics.drawString(mc.font, yieldText, x, yYield, 0xffffff, true);
        ScreenSpaceUtil.incrementY(pos, textHeight);
    }

    public static String formatPaddedTimer(long msRemaining) {
        long s = msRemaining / 1000;
        long m = s / 60;
        long h = m / 60;
        long d = h / 24;

        return (d > 0 ? (d) + " days, " : "") + String.format("%02d", (h % 24)) + ":" + String.format("%02d", (m % 60)) + ":" + String.format("%02d", (s % 60));
    }

    private SiegeCampProgressInfo getClosestSiegeCampInfo(LocalPlayer player) {
        SiegeCampProgressInfo closestInfo = null;
        double bestDistanceSq = Double.MAX_VALUE;

        ResourceKey<Level> playerDim = player.level().dimension();
        for (SiegeCampProgressInfo info : ClientProxy.sSiegeInfo.values()) {
            double distSq = info.defendingPos.distSqr(new BlockPos((int) player.getX(), (int) player.getY(), (int) player.getZ()));
            if (info.defendingPos.dim.equals(playerDim) && distSq < WarForgeConfig.SIEGE_INFO_RADIUS * WarForgeConfig.SIEGE_INFO_RADIUS) {
                if (distSq < bestDistanceSq) {
                    bestDistanceSq = distSq;
                    closestInfo = info;
                }
            }
        }

        return closestInfo;
    }

    public static SiegeCampProgressInfo getOrCreateDebugSiege() {
        if (debugSiegeInfo == null) {
            debugSiegeInfo = SiegeCampProgressInfo.getDebugInfo();
            debugSiegeInfo.endTimestamp = System.currentTimeMillis() + 90000L;
        }
        return debugSiegeInfo;
    }

    private void tickDebugSiege() {
        if (!UI_DEBUG || !animateDebugSiege) return;

        SiegeCampProgressInfo dbg = getOrCreateDebugSiege();
        int defence = Math.max(1, WarForgeConfig.SIEGE_DEFENCE_THRESHOLD);

        if (++debugAnimTicks >= 8) {
            debugAnimTicks = 0;
            dbg.mPreviousProgress = dbg.progress;
            dbg.progress += debugAnimDir;
            if (dbg.progress >= dbg.completionPoint) {
                dbg.progress = dbg.completionPoint;
                debugAnimDir = -1;
            } else if (dbg.progress <= -defence) {
                dbg.progress = -defence;
                debugAnimDir = 1;
            }
        }

        if (dbg.endTimestamp - System.currentTimeMillis() < 0) {
            dbg.endTimestamp = System.currentTimeMillis() + 90000L;
        }
    }

    public static void applyDebugScenario(String scenario) {
        SiegeCampProgressInfo dbg = SiegeCampProgressInfo.getDebugInfo();
        dbg.endTimestamp = System.currentTimeMillis() + 90000L;
        switch (scenario.toLowerCase(java.util.Locale.ROOT)) {
            case "attacker" -> { dbg.completionPoint = 10; dbg.mPreviousProgress = 6; dbg.progress = 8; }
            case "defender" -> { dbg.completionPoint = 10; dbg.mPreviousProgress = -1; dbg.progress = -3; }
            case "even" -> { dbg.completionPoint = 10; dbg.mPreviousProgress = 0; dbg.progress = 0; }
            case "blowout" -> { dbg.completionPoint = 20; dbg.mPreviousProgress = 18; dbg.progress = 19; }
            case "wide" -> { dbg.completionPoint = 30; dbg.mPreviousProgress = 10; dbg.progress = 12; }
            case "abandon" -> {
                dbg.completionPoint = 10;
                dbg.mPreviousProgress = 4;
                dbg.progress = 5;
                dbg.attackerAbandonSeconds = 30;
                dbg.attackingFactionId = ClientClaimChunkCache.playerFactionId;
            }
            default -> { dbg.completionPoint = 10; dbg.mPreviousProgress = 4; dbg.progress = 6; }
        }
        debugSiegeInfo = dbg;
    }

    private void renderVeinData(Minecraft mc, GuiGraphics graphics, Pair<Vein, Quality> veinInfo, boolean hasData) {
        // even if we aren't rendering, count up start time to indicate we are within the same chunk as before
        boolean isNewChunk = lastRenderStartTimeMs == -1;
        long currTimeMs = System.currentTimeMillis();

        // even though intelliJ thinks veinInfo is never null, it definitely should be able to be
        // we render either the item, or some waiting icon
        ItemStack currMemberItemStack = null;
        boolean hasItemToRender = veinInfo != null && veinInfo.getLeft() != null && !veinInfo.getLeft().compIds.isEmpty();
        if (hasItemToRender) {
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

            currMemberItemStack = currComp.toStack();

            if (currMemberItemStack.isEmpty()) {
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
        final int veinTitleWidth = mc.font.width(compInfoStrings.get(0));
        final int titleX = ScreenSpaceUtil.getX(veinPos, veinTitleWidth);
        final int veinInfoHeight = Math.max(textHeight, (textHeight + imageSize) / 2);
        final int titleY = ScreenSpaceUtil.getY(veinPos, veinInfoHeight) + 4 + (veinInfoHeight - textHeight);
        final int imageX = titleX - imageSize / 2;  // offset the image to the left of the title + gap

        if (titleY > WarForgeConfig.HUD_VERT_CUTOFF_PERCENT * ScreenSpaceUtil.RESOLUTIONY) { return; }  // don't overdraw onto main part of screen
        graphics.drawString(mc.font, compInfoStrings.get(0), titleX, titleY, 0xFFFFFF, true);
        veinPos.incrementY(textHeight);

        // draw the item
        if (currMemberItemStack != null) {
            // 16x16 base item icon scaled to imageSize, centered to the left of the title text
            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate(imageX - 4 - imageSize / 2f, titleY + textHeight / 2f - imageSize / 2f, 0);
            pose.scale(imageSize / 16f, imageSize / 16f, 1f);
            graphics.renderItem(currMemberItemStack, 0, 0);
            pose.popPose();
        }

        // draw the component strings
        for (int i = 1; i < compInfoStrings.size(); ++i) {
            // we want the components to look left aligned and indented
            String currFormattedComp = compInfoStrings.get(i);
            graphics.drawString(mc.font, currFormattedComp, titleX + 4, veinPos.getY(), 0xFFFFFF, true);
            veinPos.incrementY(textHeight);
        }
    }

    private void toggleVeinOverlay(LocalPlayer player) {
        showVeinOverlay = !showVeinOverlay;
        lastRenderStartTimeMs = -1;
        cachedCompStrings = null;
        compIt = null;
        currComp = null;

        player.sendSystemMessage(Component.literal(I18n.get(
                showVeinOverlay ? "warforge.info.vein.toggle.enabled" : "warforge.info.vein.toggle.disabled")));
    }

    private ArrayList<String> createVeinInfoStrings(Pair<Vein, Quality> veinInfo, boolean hasCached) {
        ArrayList<String> result = new ArrayList<>(1);

        // handle no data specially
        if (!hasCached) {
            result.add(I18n.get("warforge.info.vein.waiting"));
            return result;
        }

        // handle null veins specially
        if (veinInfo == null) {
            result.add(I18n.get("warforge.info.vein.null"));
            return result;
        }

        // translate and format the vein name by supplying the localized quality name as an argument
        Vein currVein = veinInfo.getLeft();
        Quality currQual = veinInfo.getRight();

        // handle unrecognized veins specially
        if (currVein == null || currQual == null) {
            result.add(I18n.get("warforge.info.vein.unrecognized"));
            return result;
        }

        // vein is now guaranteed valid and received; prepare formatted data for display
        result.add(I18n.get(currVein.translationKey,
                I18n.get(currQual.getTranslationKey()) + " [" + currQual.getMultString(currVein) + "]"));
        ResourceKey<Level> dim = Minecraft.getInstance().player.level().dimension();

        // turn each component into the item we will be displaying and list them
        for (ItemMatcher currComp : currVein.compIds) {
            ItemStack currStack = currComp.toStack();
            if (currStack.isEmpty()) {
                WarForgeMod.LOGGER.atError().log("Couldn't find item with component id " +
                        currComp + " in vein " + currVein.translationKey);
                continue;
            }

            // Use the item stack's own display name rather than formatting its raw description id: some
            // items (e.g. GregTech's material items) share one translation per shape (like "%s Ore") that
            // needs the material name substituted in, which only getHoverName()/getName() do correctly.
            // Naively I18n.get()-ing the description id with no args throws on those and shows
            // "Format error: ..." instead of the item's name.
            StringBuilder compInfo = new StringBuilder(currStack.getHoverName().getString());
            parseCompInfo(compInfo, currComp, veinInfo, dim);
            result.add(compInfo.toString());
        }

        return result;
    }

    private void parseCompInfo(StringBuilder compInfoStr, ItemMatcher currComp, Pair<Vein, Quality> veinInfo, ResourceKey<Level> dim) {
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

    private void renderSiegeOverlay(Minecraft mc, GuiGraphics graphics, SiegeCampProgressInfo infoToRender, float partialTicks) {
        if (DYNAMIC_SIEGE_OVERLAY) {
            SiegeOverlayRenderer.render(mc, graphics, infoToRender, partialTicks);
            return;
        }

        // Render Background and Bars
        var pos = WarForgeConfig.POS_SIEGE;
        int xText = ScreenSpaceUtil.getX(pos, 256);  // 256 = width of bar
        int yText = ScreenSpaceUtil.getY(pos, 40);   // 40 = total height (bar + text)

        // slowly scrolling progress fill, derived from wall-clock time for a smooth animation
        float scroll = (float) ((System.currentTimeMillis() / 50.0 + partialTicks) * 0.25);
        scroll = scroll % 10;

        graphics.blit(siegeprogress, xText, yText, 0, 0, 256, 30, 256, 256);

        renderSiegeProgressBar(graphics, infoToRender, xText, yText, scroll);
        renderSiegeNotches(graphics, infoToRender, xText, yText);

        renderSiegeText(mc, graphics, infoToRender, xText, yText);
        if (WarForgeConfig.SIEGE_ENABLE_NEW_TIMER)
            renderSiegeTimer(mc, graphics, infoToRender, xText, yText + 5);

        renderSiegeAbandonTimer(mc, graphics, infoToRender, xText, yText);
    }

    // Shows the attacking faction how long until their siege is considered abandoned. Only rendered for
    // members of the attacking faction, and only while the abandon timer is actively counting.
    private void renderSiegeAbandonTimer(Minecraft mc, GuiGraphics graphics, SiegeCampProgressInfo info, int xText, int yText) {
        if (info.attackerAbandonSeconds <= 0)
            return;
        UUID playerFaction = ClientClaimChunkCache.playerFactionId;
        if (playerFaction == null || !playerFaction.equals(info.attackingFactionId))
            return;

        String text = "Siege abandoned in: " + formatPaddedTimer(info.attackerAbandonSeconds * 1000L);
        int textWidth = mc.font.width(text);
        int color = info.attackerAbandonSeconds <= 10 ? 0xFF5555 : 0xC79A3A;
        graphics.drawString(mc.font, text, xText + (128 - textWidth / 2), yText + 44, color, true);
    }

    private void renderSiegeTimer(Minecraft mc, GuiGraphics graphics, SiegeCampProgressInfo infoToRender, int xText, int yText) {
        String siegeText = formatPaddedTimer(infoToRender.endTimestamp - System.currentTimeMillis());
        int textWidth = mc.font.width(siegeText);
        int color = infoToRender.endTimestamp - System.currentTimeMillis() < 60000 ? 0xFF0000 : 0xFFFFFF;

        graphics.drawString(mc.font, siegeText, xText + (128 - textWidth / 2), yText + 28, color, true);
        if (TIMER_DEBUG) {
            graphics.drawString(mc.font, "End timestamp :" + infoToRender.endTimestamp, xText - textWidth, yText + 30, 0xFFFFFF, true);
            graphics.drawString(mc.font, "Raw timestamp difference: " + (infoToRender.endTimestamp - System.currentTimeMillis()), xText - textWidth, yText + 40, 0xFFFFFF, true);
        }
    }

    private void renderSiegeProgressBar(GuiGraphics graphics, SiegeCampProgressInfo infoToRender, int xText, int yText, float scroll) {
        float siegeLength = infoToRender.completionPoint + 5;
        float notchDistance = 224 / siegeLength;

        int firstPx = (int) (notchDistance * (infoToRender.progress > 0 ? 5 : 5 + infoToRender.progress));
        int lastPx = (int) (notchDistance * (infoToRender.progress > 0 ? (infoToRender.progress + 5) : 5));

        boolean isIncreasing = infoToRender.progress > infoToRender.mPreviousProgress;

        int barColor = isIncreasing ? infoToRender.attackingColour : infoToRender.defendingColour;
        float u = isIncreasing ? 16 + (10 - scroll) : 16 + scroll;
        float v = isIncreasing ? 44 : 54;

        graphics.setColor(((barColor >> 16 & 255) / 255.0F), ((barColor >> 8 & 255) / 255.0F), ((barColor & 255) / 255.0F), 1.0F);
        graphics.blit(siegeprogress, xText + 16 + firstPx, yText + 17, u, v, lastPx - firstPx, 8, 256, 256);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void renderSiegeNotches(GuiGraphics graphics, SiegeCampProgressInfo infoToRender, int xText, int yText) {
        float notchDistance = (float) 224 / (infoToRender.completionPoint + 5);

        for (int i = -4; i < infoToRender.completionPoint; i++) {
            int x = (int) ((i + 5) * notchDistance + 16);
            if (i == 0) graphics.blit(siegeprogress, xText + x - 2, yText + 17, 6, 43, 5, 8, 256, 256);
            else graphics.blit(siegeprogress, xText + x - 2, yText + 17, 1, 43, 4, 8, 256, 256);
        }
    }

    private void renderSiegeText(Minecraft mc, GuiGraphics graphics, SiegeCampProgressInfo infoToRender, int xText, int yText) {
        graphics.drawString(mc.font, infoToRender.defendingName, xText + 6, yText + 6, infoToRender.defendingColour, true);
        graphics.drawString(mc.font, "VS", (int) (xText + 128 - (float) mc.font.width("VS") / 2), yText + 6, 0xffffff, true);
        graphics.drawString(mc.font, infoToRender.attackingName, xText + 256 - 6 - mc.font.width(infoToRender.attackingName), yText + 6, infoToRender.attackingColour, true);

        String toWin = (infoToRender.progress < infoToRender.completionPoint) ? (infoToRender.completionPoint - infoToRender.progress) + " to win" : "Station siege to win";
        String toDefend = (infoToRender.progress + 5) + " to defend";
        graphics.drawString(mc.font, toWin, xText + 256 - 8 - mc.font.width(toWin), yText + 32, infoToRender.attackingColour, true);
        graphics.drawString(mc.font, toDefend, xText + 8, yText + 32, infoToRender.attackingColour, true);
    }

    private void renderNewAreaToast(Minecraft mc, GuiGraphics graphics) {
        final int stringWidth = mc.font.width(areaMessage);
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
        int lineColour = ((int) (fadeOut * 255f) << 24) | 0xFFFFFF;

        graphics.fill(xText - 50, yText, xText - 50 + stringWidth + 100, yText + 1, lineColour);            // top line
        graphics.fill(xText - 25, yText + 23, xText - 25 + stringWidth + 50, yText + 24, lineColour);       // bottom line

        graphics.drawString(mc.font, areaMessage, xText, yText + 11, colour, true);   // vertically centered text

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

            graphics.setColor(1f, 1f, 1f, fadeOut);
            graphics.blit(flagTexture, flagX, flagY, drawWidth, drawHeight, 0, 0, flagDims[0], flagDims[1], flagDims[0], flagDims[1]);
            graphics.setColor(1f, 1f, 1f, 1f);
            ScreenSpaceUtil.incrementY(pos, drawHeight + 4);
        }

        ScreenSpaceUtil.incrementY(pos, totalHeight + 14 + extraPadding);
    }

    private void updateRenderData() {
        Level world = Minecraft.getInstance().level;
        if (world == null) return;

        ResourceKey<Level> worldDim = world.dimension();

        // Update our list from the old one
        HashMap<DimChunkPos, BorderRenderData> tempData = new HashMap<DimChunkPos, BorderRenderData>();

        // Find all synced claim chunks in our current dimension.
        for (HashMap.Entry<DimChunkPos, ClaimChunkInfo> kvp : new HashMap<>(ClientBorderCache.getChunks()).entrySet()) {
            DimChunkPos chunkPos = kvp.getKey();
            if (!chunkPos.dim.equals(worldDim)) {
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
                // The claim set changed, which can change this chunk's colour or its neighbour-based
                // edge culling, so its cached mesh must be rebuilt.
                existing.dirty = true;
                tempData.put(chunkPos, existing);
            } else {
                BorderRenderData data = new BorderRenderData();
                data.factionId = info.outlineFactionId;
                data.colour = info.outlineColour;
                data.outlineStyle = info.outlineStyle;
                tempData.put(chunkPos, data);
            }
        }

        // Free GPU buffers for chunks that are no longer claimed/visible.
        for (HashMap.Entry<DimChunkPos, BorderRenderData> kvp : renderData.entrySet()) {
            if (!tempData.containsKey(kvp.getKey())) {
                kvp.getValue().disposeVbo();
            }
        }

        renderData = tempData;
    }

    // Build the border mesh for every claim chunk. With display lists gone in 1.20.1 the wall geometry
    // is emitted directly into a POSITION_COLOR_TEX buffer each frame; the chunk-local vertices are
    // offset into world space relative to the camera by the caller via the pose stack.
    private void buildBorderMesh(Level world, Matrix4f matrix, VertexConsumer buffer, DimChunkPos pos, BorderRenderData data) {
        int colour = data.colour;
        int color = 0xFF000000 | (colour & 0xFFFFFF);

        int minY = world.getMinBuildHeight();
        int maxY = world.getMaxBuildHeight();

        // The per-block silhouette only needs the elevation band around this chunk's perimeter
        // surface, not the full world column (-64..320 in 1.20.1). Sample the four edge rows and scan
        // only [surfMin-margin, surfMax+margin]. This misses enclosed caves/overhangs entirely below
        // the lowest perimeter surface (invisible from outside anyway) but slashes the scan; the faint
        // corner walls below still span minY..maxY (build limit) unconditionally.
        int scanMin = minY;
        int scanMax = maxY;
        int originX = pos.getMinBlockX();
        int originZ = pos.getMinBlockZ();
        int surfMin = Integer.MAX_VALUE;
        int surfMax = Integer.MIN_VALUE;
        for (int i = 0; i < 16; i++) {
            int hN = world.getHeight(Heightmap.Types.WORLD_SURFACE, originX + i, originZ);
            int hS = world.getHeight(Heightmap.Types.WORLD_SURFACE, originX + i, originZ + 15);
            int hW = world.getHeight(Heightmap.Types.WORLD_SURFACE, originX, originZ + i);
            int hE = world.getHeight(Heightmap.Types.WORLD_SURFACE, originX + 15, originZ + i);
            surfMin = Math.min(surfMin, Math.min(Math.min(hN, hS), Math.min(hW, hE)));
            surfMax = Math.max(surfMax, Math.max(Math.max(hN, hS), Math.max(hW, hE)));
        }
        if (surfMin != Integer.MAX_VALUE) {
            scanMin = Math.max(minY, surfMin - BORDER_Y_SCAN_MARGIN);
            scanMax = Math.min(maxY, surfMax + BORDER_Y_SCAN_MARGIN);
        }

        // Reused across the many world.isEmptyBlock samples below to avoid a BlockPos alloc each call.
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
                buffer.addVertex(matrix, (float) (0 + alignment), (float) minY, (float) alignment).setColor(color).setUv(64f, 0.5f);
                buffer.addVertex(matrix, (float) (2 + alignment), (float) minY, (float) alignment).setColor(color).setUv(64f, 0f);
                buffer.addVertex(matrix, (float) (2 + alignment), (float) maxY, (float) alignment).setColor(color).setUv(0f, 0f);
                buffer.addVertex(matrix, (float) (0 + alignment), (float) maxY, (float) alignment).setColor(color).setUv(0f, 0.5f);
            }

            // A smidge of semi-translucent wall from [14,0,0] to [16,256,0] offset by 0.25
            if (renderEast) {
                buffer.addVertex(matrix, (float) (16 - alignment), (float) minY, (float) alignment).setColor(color).setUv(64f, 0.5f);
                buffer.addVertex(matrix, (float) (14 - alignment), (float) minY, (float) alignment).setColor(color).setUv(64f, 0f);
                buffer.addVertex(matrix, (float) (14 - alignment), (float) maxY, (float) alignment).setColor(color).setUv(0f, 0f);
                buffer.addVertex(matrix, (float) (16 - alignment), (float) maxY, (float) alignment).setColor(color).setUv(0f, 0.5f);
            }
        }

        // South edge
        if (renderSouth) {
            if (renderWest) {
                buffer.addVertex(matrix, (float) (0 + alignment), (float) minY, (float) (16d - alignment)).setColor(color).setUv(64f, 0.5f);
                buffer.addVertex(matrix, (float) (2 + alignment), (float) minY, (float) (16d - alignment)).setColor(color).setUv(64f, 0f);
                buffer.addVertex(matrix, (float) (2 + alignment), (float) maxY, (float) (16d - alignment)).setColor(color).setUv(0f, 0f);
                buffer.addVertex(matrix, (float) (0 + alignment), (float) maxY, (float) (16d - alignment)).setColor(color).setUv(0f, 0.5f);
            }

            if (renderEast) {
                buffer.addVertex(matrix, (float) (16 - alignment), (float) minY, (float) (16d - alignment)).setColor(color).setUv(64f, 0.5f);
                buffer.addVertex(matrix, (float) (14 - alignment), (float) minY, (float) (16d - alignment)).setColor(color).setUv(64f, 0f);
                buffer.addVertex(matrix, (float) (14 - alignment), (float) maxY, (float) (16d - alignment)).setColor(color).setUv(0f, 0f);
                buffer.addVertex(matrix, (float) (16 - alignment), (float) maxY, (float) (16d - alignment)).setColor(color).setUv(0f, 0.5f);
            }
        }

        // East edge, [0,0] -> [0,16] wall
        if (renderWest) {
            if (renderNorth) {
                buffer.addVertex(matrix, (float) alignment, (float) minY, (float) (0 + alignment)).setColor(color).setUv(64f, 0.5f);
                buffer.addVertex(matrix, (float) alignment, (float) minY, (float) (2 + alignment)).setColor(color).setUv(64f, 0f);
                buffer.addVertex(matrix, (float) alignment, (float) maxY, (float) (2 + alignment)).setColor(color).setUv(0f, 0f);
                buffer.addVertex(matrix, (float) alignment, (float) maxY, (float) (0 + alignment)).setColor(color).setUv(0f, 0.5f);
            }

            if (renderSouth) {
                buffer.addVertex(matrix, (float) alignment, (float) minY, (float) (16 - alignment)).setColor(color).setUv(64f, 0.5f);
                buffer.addVertex(matrix, (float) alignment, (float) minY, (float) (14 - alignment)).setColor(color).setUv(64f, 0f);
                buffer.addVertex(matrix, (float) alignment, (float) maxY, (float) (14 - alignment)).setColor(color).setUv(0f, 0f);
                buffer.addVertex(matrix, (float) alignment, (float) maxY, (float) (16 - alignment)).setColor(color).setUv(0f, 0.5f);
            }
        }

        // West edge
        if (renderEast) {
            if (renderNorth) {
                buffer.addVertex(matrix, (float) (16d - alignment), (float) minY, (float) (0 + alignment)).setColor(color).setUv(64f, 0.5f);
                buffer.addVertex(matrix, (float) (16d - alignment), (float) minY, (float) (2 + alignment)).setColor(color).setUv(64f, 0f);
                buffer.addVertex(matrix, (float) (16d - alignment), (float) maxY, (float) (2 + alignment)).setColor(color).setUv(0f, 0f);
                buffer.addVertex(matrix, (float) (16d - alignment), (float) maxY, (float) (0 + alignment)).setColor(color).setUv(0f, 0.5f);
            }

            if (renderSouth) {
                buffer.addVertex(matrix, (float) (16d - alignment), (float) minY, (float) (16 - alignment)).setColor(color).setUv(64f, 0.5f);
                buffer.addVertex(matrix, (float) (16d - alignment), (float) minY, (float) (14 - alignment)).setColor(color).setUv(64f, 0f);
                buffer.addVertex(matrix, (float) (16d - alignment), (float) maxY, (float) (14 - alignment)).setColor(color).setUv(0f, 0f);
                buffer.addVertex(matrix, (float) (16d - alignment), (float) maxY, (float) (16 - alignment)).setColor(color).setUv(0f, 0.5f);
            }
        }

        if (renderNorth || renderSouth) {
            for (int x = 0; x < 16; x++) {
                for (int y = scanMin; y < scanMax; y++) {
                    if (x < 15) {
                        if (renderNorth) {
                            boolean air0 = world.isEmptyBlock(mp.set(pos.getMinBlockX() + x, y, pos.getMinBlockZ()));
                            boolean air1 = world.isEmptyBlock(mp.set(pos.getMinBlockX() + x + 1, y, pos.getMinBlockZ()));
                            renderZEdge(world, matrix, buffer, color, x, y, pos.getMinBlockZ(), smaller_alignment + 0.001d, air0, air1, 0);
                        }
                        if (renderSouth) {
                            boolean air0 = world.isEmptyBlock(mp.set(pos.getMinBlockX() + x, y, pos.getMaxBlockZ()));
                            boolean air1 = world.isEmptyBlock(mp.set(pos.getMinBlockX() + x + 1, y, pos.getMaxBlockZ()));
                            renderZEdge(world, matrix, buffer, color, x, y, pos.getMaxBlockZ(), 16d - smaller_alignment + 0.001d, air0, air1, 0);
                        }
                    }
                    if (y < scanMax - 1) {
                        if (renderNorth) {
                            boolean air0 = world.isEmptyBlock(mp.set(pos.getMinBlockX() + x, y, pos.getMinBlockZ()));
                            boolean air1 = world.isEmptyBlock(mp.set(pos.getMinBlockX() + x, y + 1, pos.getMinBlockZ()));
                            if (x == 15 && renderEast) {
                                renderZVerticalCorner(world, matrix, buffer, color, x - smaller_alignment, y, smaller_alignment, air0, air1, 0, -smaller_alignment);
                            } else if (x == 0 && renderWest) {
                                renderZVerticalCorner(world, matrix, buffer, color, x, y, smaller_alignment, air0, air1, 0, -smaller_alignment);
                            } else {
                                renderZVerticalEdge(world, matrix, buffer, color, x, y, pos.getMinBlockZ(), smaller_alignment, air0, air1, 0);
                            }
                        }
                        if (renderSouth) {
                            boolean air0 = world.isEmptyBlock(mp.set(pos.getMinBlockX() + x, y, pos.getMaxBlockZ()));
                            boolean air1 = world.isEmptyBlock(mp.set(pos.getMinBlockX() + x, y + 1, pos.getMaxBlockZ()));
                            if (x == 15 && renderEast) {
                                renderZVerticalCorner(world, matrix, buffer, color, x - smaller_alignment, y, 16 - smaller_alignment, air0, air1, 0, -smaller_alignment);
                            } else if (x == 0 && renderWest) {
                                renderZVerticalCorner(world, matrix, buffer, color, x, y, 16 - smaller_alignment, air0, air1, 0, -smaller_alignment);
                            } else {
                                renderZVerticalEdge(world, matrix, buffer, color, x, y, pos.getMaxBlockZ(), 16d - smaller_alignment, air0, air1, 0);
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
                            boolean air0 = world.isEmptyBlock(mp.set(pos.getMinBlockX(), y, pos.getMinBlockZ() + z));
                            boolean air1 = world.isEmptyBlock(mp.set(pos.getMinBlockX(), y, pos.getMinBlockZ() + z + 1));
                            renderXEdge(world, matrix, buffer, color, pos.getMinBlockX(), y, z, smaller_alignment + 0.001d, air0, air1, 0);
                        }
                        if (renderEast) {
                            boolean air0 = world.isEmptyBlock(mp.set(pos.getMaxBlockX(), y, pos.getMinBlockZ() + z));
                            boolean air1 = world.isEmptyBlock(mp.set(pos.getMaxBlockX(), y, pos.getMinBlockZ() + z + 1));
                            renderXEdge(world, matrix, buffer, color, pos.getMaxBlockX(), y, z, 16d - smaller_alignment + 0.001d, air0, air1, 0);
                        }
                    }
                    if (y < scanMax - 1) {
                        if (renderWest) {
                            boolean air0 = world.isEmptyBlock(mp.set(pos.getMinBlockX(), y, pos.getMinBlockZ() + z));
                            boolean air1 = world.isEmptyBlock(mp.set(pos.getMinBlockX(), y + 1, pos.getMinBlockZ() + z));
                            if (z == 15 && renderSouth) {
                                renderXVerticalCorner(world, matrix, buffer, color, smaller_alignment, y, z - smaller_alignment, air0, air1, 0, -smaller_alignment);
                            } else if (z == 0 && renderNorth) {
                                renderXVerticalCorner(world, matrix, buffer, color, smaller_alignment, y, z, air0, air1, 0, -smaller_alignment);
                            } else {
                                renderXVerticalEdge(world, matrix, buffer, color, pos.getMinBlockX(), y, z, smaller_alignment, air0, air1, 0);
                            }
                        }
                        if (renderEast) {
                            boolean air0 = world.isEmptyBlock(mp.set(pos.getMaxBlockX(), y, pos.getMinBlockZ() + z));
                            boolean air1 = world.isEmptyBlock(mp.set(pos.getMaxBlockX(), y + 1, pos.getMinBlockZ() + z));
                            if (z == 15 && renderSouth) {
                                renderXVerticalCorner(world, matrix, buffer, color, 16d - smaller_alignment, y, z - smaller_alignment, air0, air1, 0, -smaller_alignment);
                            } else if (z == 0 && renderNorth) {
                                renderXVerticalCorner(world, matrix, buffer, color, 16d - smaller_alignment, y, z, air0, air1, 0, -smaller_alignment);
                            } else {
                                renderXVerticalEdge(world, matrix, buffer, color, pos.getMaxBlockX(), y, z, 16d - smaller_alignment, air0, air1, 0);
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
                    boolean air0 = world.isEmptyBlock(mp.set(pos.getMaxBlockX(), y, pos.getMinBlockZ()));
                    boolean air1 = world.isEmptyBlock(mp.set(pos.getMaxBlockX(), y + 1, pos.getMinBlockZ()));
                    renderZVerticalCorner(world, matrix, buffer, color, 15, y, smaller_alignment, air0, air1, 0, smaller_alignment - 1.0);
                    renderXVerticalCorner(world, matrix, buffer, color, 16 - smaller_alignment, y, smaller_alignment - 1, air0, air1, 0, smaller_alignment - 1.0);
                }
            }
        }
        if (renderNorthWest) {
            if (!renderNorth && !renderWest) {
                for (int y = scanMin; y < scanMax; y++) {
                    boolean air0 = world.isEmptyBlock(mp.set(pos.getMinBlockX(), y, pos.getMinBlockZ()));
                    boolean air1 = world.isEmptyBlock(mp.set(pos.getMinBlockX(), y + 1, pos.getMinBlockZ()));
                    renderZVerticalCorner(world, matrix, buffer, color, -1 + smaller_alignment, y, smaller_alignment, air0, air1, 0, smaller_alignment - 1.0);
                    renderXVerticalCorner(world, matrix, buffer, color, smaller_alignment, y, smaller_alignment - 1, air0, air1, 0, smaller_alignment - 1.0);
                }
            }
        }
        if (renderSouthWest) {
            if (!renderSouth && !renderWest) {
                for (int y = scanMin; y < scanMax; y++) {
                    boolean air0 = world.isEmptyBlock(mp.set(pos.getMinBlockX(), y, pos.getMaxBlockZ()));
                    boolean air1 = world.isEmptyBlock(mp.set(pos.getMinBlockX(), y + 1, pos.getMaxBlockZ()));
                    renderZVerticalCorner(world, matrix, buffer, color, -1 + smaller_alignment, y, 16 - smaller_alignment, air0, air1, 0, smaller_alignment - 1.0);
                    renderXVerticalCorner(world, matrix, buffer, color, smaller_alignment, y, 15, air0, air1, 0, smaller_alignment - 1.0);
                }
            }
        }
        if (renderSouthEast) {
            if (!renderSouth && !renderEast) {
                for (int y = scanMin; y < scanMax; y++) {
                    boolean air0 = world.isEmptyBlock(mp.set(pos.getMaxBlockX(), y, pos.getMaxBlockZ()));
                    boolean air1 = world.isEmptyBlock(mp.set(pos.getMaxBlockX(), y + 1, pos.getMaxBlockZ()));
                    renderZVerticalCorner(world, matrix, buffer, color, 15, y, 16 - smaller_alignment, air0, air1, 0, smaller_alignment - 1.0);
                    renderXVerticalCorner(world, matrix, buffer, color, 16 - smaller_alignment, y, 15, air0, air1, 0, smaller_alignment - 1.0);
                }
            }
        }
    }

    @SubscribeEvent
    public void onRenderLast(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        Level world = mc.level;
        if (world == null) return;

        // RenderLevelStageEvent's pose is set up relative to the camera EYE position
        // (Camera.getPosition()), not the view entity's interpolated feet. Subtracting the
        // entity feet position instead would leave a residual ~= eye height (~1.62), shifting
        // the world-space geometry ~1.5 blocks upward. Use the real camera position.
        float partialTicks = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double x = cam.x;
        double y = cam.y;
        double z = cam.z;

        // Update render data if necessary
        if (CLAIMS_DIRTY) {
            updateRenderData();
            CLAIMS_DIRTY = false;
        }

        PoseStack pose = event.getPoseStack();
        Matrix4f viewMatrix = event.getModelViewMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        // Render chunk borders
        renderChunkBorders(world, viewMatrix, x, y, z);

        // Render player placement overlay (if necessary)
        renderPlayerPlacementOverlay(player, pose, x, y, z, partialTicks);

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void renderChunkBorders(Level world, Matrix4f viewMatrix, double x, double y, double z) {
        if (!WarForgeMod.showBorders) {
            return;
        }

        // The wall geometry is camera-independent and only changes when the claim set or the chunk's
        // blocks change, so cache it in a per-chunk VertexBuffer and just replay it each frame (the
        // modern stand-in for the old display lists). Dirty chunks are rebuilt lazily, capped per
        // frame so a bulk invalidation does not hitch.
        ShaderInstance shader = GameRenderer.getPositionTexColorShader();
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        int rebuildBudget = MAX_BORDER_REBUILDS_PER_FRAME;
        // 0 = follow the client's render distance so borders never float in unloaded terrain;
        // a positive config value overrides it.
        int borderDistChunks = WarForgeConfig.BORDER_RENDER_DISTANCE > 0
                ? WarForgeConfig.BORDER_RENDER_DISTANCE
                : Minecraft.getInstance().options.getEffectiveRenderDistance();
        double maxBorderDist = borderDistChunks * 16.0;
        double maxBorderDistSq = maxBorderDist * maxBorderDist;

        for (HashMap.Entry<DimChunkPos, BorderRenderData> kvp : renderData.entrySet()) {
            DimChunkPos pos = kvp.getKey();
            BorderRenderData data = kvp.getValue();

            ResourceLocation desiredTexture = getBorderTexture(data);
            if (desiredTexture == null) {
                continue;
            }

            // Distance cull: skip (and don't rebuild) claim chunks beyond the configured render
            // distance from the camera, so dense far-off claims cost nothing.
            double dxCam = (pos.x * 16 + 8) - x;
            double dzCam = (pos.z * 16 + 8) - z;
            if (dxCam * dxCam + dzCam * dzCam > maxBorderDistSq) {
                continue;
            }

            // Rebuild only when dirty, or not yet built and not known-empty (empty stays vbo == null,
            // so this must not retrigger every frame).
            if ((data.dirty || (data.vbo == null && !data.empty)) && rebuildBudget > 0) {
                rebuildBorderMesh(world, pos, data);
                rebuildBudget--;
            }
            if (data.empty || data.vbo == null) {
                continue;
            }

            RenderSystem.setShaderTexture(0, desiredTexture);

            Matrix4f modelView = new Matrix4f(viewMatrix);
            modelView.translate((float) (pos.x * 16 - x), (float) (0 - y), (float) (pos.z * 16 - z));

            data.vbo.bind();
            data.vbo.drawWithShader(modelView, projection, shader);
            VertexBuffer.unbind();
        }
    }

    // Regenerate the chunk's cached border mesh into its VertexBuffer. Geometry is built in chunk-local
    // space (BORDER_LOCAL_MATRIX) so the buffer is camera-independent; the per-chunk offset is applied
    // by the model-view matrix at draw time. build() returns null for chunks that produce
    // no geometry (e.g. interior chunks fully surrounded by same-faction claims).
    private void rebuildBorderMesh(Level world, DimChunkPos pos, BorderRenderData data) {
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buildBorderMesh(world, BORDER_LOCAL_MATRIX, builder, pos, data);
        MeshData rendered = builder.build();

        data.dirty = false;
        data.empty = rendered == null;
        if (data.empty) {
            return;
        }

        if (data.vbo == null) {
            data.vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
        }
        data.vbo.bind();
        data.vbo.upload(rendered);
        VertexBuffer.unbind();
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

    private void renderPlayerPlacementOverlay(Player player, PoseStack pose, double x, double y, double z, float partialTicks) {
        if (player.getMainHandItem().getItem() instanceof BlockItem blockItem) {
            boolean shouldRender = false;
            Block holding = blockItem.getBlock();

            // Check if the block being held is one that should render the placement overlay
            if (holding == Content.basicClaimBlock || holding == Content.citadelBlock || holding == Content.reinforcedClaimBlock) {
                shouldRender = true;
            }

            // If we need to render, check for ray tracing and render accordingly
            if (shouldRender) {
                renderPlacementOverlay(player, pose, x, y, z, partialTicks);
            }
        }
    }

    private void renderPlacementOverlay(Player player, PoseStack pose, double x, double y, double z, float partialTicks) {
        ResourceKey<Level> dim = player.level().dimension();
        DimChunkPos playerPos = new DimChunkPos(dim, player.blockPosition());
        HitResult result = player.pick(10.0f, partialTicks, false);
        if (result != null && result.getType() == HitResult.Type.BLOCK) {
            playerPos = new DimChunkPos(dim, ((BlockHitResult) result).getBlockPos());
        }

        boolean canPlace = checkPlacementValidity(playerPos, player.getItemInHand(InteractionHand.MAIN_HAND).getItem(), player.getDirection());
        int color = canPlace ? 0xFF00FF00 : 0xFFFF0000;

        RenderSystem.setShaderTexture(0, overlayTex);

        pose.pushPose();
        pose.translate(playerPos.x * 16 - x, 0 - y, playerPos.z * 16 - z);
        Matrix4f matrix = pose.last().pose();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (int i = 0; i < 16; i++) {
            for (int k = 0; k < 16; k++) {
                float yPlane = (float) ((int) player.getY() + 1.5d);
                builder.addVertex(matrix, i, yPlane, k).setColor(color).setUv(0f, 0f);
                builder.addVertex(matrix, i + 1, yPlane, k).setColor(color).setUv(1f, 0f);
                builder.addVertex(matrix, i + 1, yPlane, k + 1).setColor(color).setUv(1f, 1f);
                builder.addVertex(matrix, i, yPlane, k + 1).setColor(color).setUv(0f, 1f);
            }
        }

        MeshData mesh = builder.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
        pose.popPose();
    }

    private boolean checkPlacementValidity(DimChunkPos playerPos, Item holding, Direction facing) {
        boolean canPlace = true;
        List<DimChunkPos> siegeablePositions = new ArrayList<>();

        for (HashMap.Entry<DimChunkPos, ClaimChunkInfo> kvp : new HashMap<>(ClientBorderCache.getChunks()).entrySet()) {
            DimChunkPos chunkPos = kvp.getKey();
            ClaimChunkInfo info = kvp.getValue();
            if (info == null || info.factionId.equals(Faction.nullUuid)) {
                continue;
            }
            if (playerPos.x == chunkPos.x && playerPos.z == chunkPos.z && playerPos.dim.equals(chunkPos.dim)) {
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

        // Cached GPU mesh (the modern equivalent of the old display list). Rebuilt only when the
        // claim/terrain that produced it changed (dirty); empty == the build produced no geometry
        // (interior chunk fully surrounded by same-faction claims), so there is nothing to draw.
        public VertexBuffer vbo = null;
        public boolean dirty = true;
        public boolean empty = false;

        void disposeVbo() {
            if (this.vbo != null) {
                this.vbo.close();
                this.vbo = null;
            }
        }
    }
}
