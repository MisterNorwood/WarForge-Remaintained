package com.flansmod.warforge.client.ui;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.api.modularui.ChunkMapTextureDaemon;
import com.flansmod.warforge.api.modularui.ChunkMapUtil;
import com.flansmod.warforge.api.modularui.ChunkMapViewport;
import com.flansmod.warforge.api.modularui.MapCellElement;
import com.flansmod.warforge.api.modularui.WarForgeUiTheme;
import com.flansmod.warforge.client.ClientClaimChunkCache;
import com.flansmod.warforge.client.ClientProxy;
import com.flansmod.warforge.client.util.SkinUtil;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.factories.ClaimManagerGuiFactory;
import com.flansmod.warforge.common.network.ClaimChunkInfo;
import com.flansmod.warforge.common.network.ClaimChunkRenderInfo;
import com.flansmod.warforge.common.network.PacketClaimChunkAction;
import com.flansmod.warforge.common.network.PacketDeclareSiege;
import com.flansmod.warforge.common.network.PacketRequestTerrainColors;
import com.flansmod.warforge.common.network.SiegeCampAttackInfo;
import com.flansmod.warforge.common.network.SiegeCampAttackInfoRender;
import com.flansmod.warforge.common.network.SiegeCampProgressInfo;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.server.Faction;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class ClaimManagerScreen {
    private static final int CELL_SIZE = 64;
    private static final int MAX_VISIBLE_RADIUS = 4;

    private static ModularUIScreen activeScreen = null;
    private static DimChunkPos lastCenter = null;
    private static int lastRadius = 4;
    private static int lastPageX = -1;
    private static int lastPageZ = -1;

    private ClaimManagerScreen() {
    }

    public static boolean isCurrentlyOpen() {
        Minecraft mc = Minecraft.getInstance();
        return activeScreen != null && mc.screen == activeScreen;
    }

    public static void refreshIfOpen() {
        if (!isCurrentlyOpen() || lastCenter == null) {
            return;
        }
        open(lastCenter, lastRadius, lastPageX, lastPageZ);
    }

    public static void open(DimChunkPos center, int radius, int pageX, int pageZ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        lastCenter = center;
        lastRadius = radius;
        lastPageX = pageX;
        lastPageZ = pageZ;

        int size = radius * 2 + 1;
        ChunkMapViewport viewport = ChunkMapViewport.create(
                size,
                3,
                Math.min(size, MAX_VISIBLE_RADIUS * 2 + 1),
                CELL_SIZE,
                mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight(),
                80,
                120,
                pageX,
                pageZ
        );

        HashMap<Long, Integer> tintByChunk = new HashMap<>();
        for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int z = center.z - radius; z <= center.z + radius; z++) {
                ClaimChunkInfo info = ClientClaimChunkCache.get(new DimChunkPos(center.dim, x, z));
                if (info != null && !info.factionId.equals(Faction.nullUuid)) {
                    tintByChunk.put(ChunkMapUtil.key(x, z), info.colour);
                }
            }
        }

        ChunkMapTextureDaemon.requestMapUpdate("claimmap", center.dim, center.x, center.z, radius, tintByChunk);

        int mapSize = viewport.visibleSize * CELL_SIZE;

        DimChunkPos siegePickFor = ClaimManagerGuiFactory.siegeStartPickFor;
        final boolean startPickMode = siegePickFor != null && siegePickFor.dim.equals(center.dim)
                && siegePickFor.x == center.x && siegePickFor.z == center.z;
        if (siegePickFor != null && !startPickMode) {
            ClaimManagerGuiFactory.siegeStartPickFor = null;
        }
        final boolean targetMode = ClaimManagerGuiFactory.siegeTargetMode && !startPickMode;
        final boolean canDeclareSiege = WarForgeConfig.SIEGE_ALLOW_UI_DECLARE
                && !ClientClaimChunkCache.playerFactionId.equals(Faction.nullUuid);

        UIElement root = new UIElement();
        root.layout(l -> l.flexDirection(FlexDirection.COLUMN).paddingAll(8).gapRow(4));
        WarForgeUiTheme.panel(root);
        UIElement body = root;

        String titleText = startPickMode ? "Choose Siege Origin" : targetMode ? "Select Siege Target" : "Territory Map";
        body.addChild(WarForgeUiTheme.header(titleText));

        body.addChild(WarForgeUiTheme.text(
                "Center [" + center.x + ", " + center.z + "] | Radius " + radius + " | Dim " + center.dim.location(),
                WarForgeUiTheme.TEXT_SECONDARY));

        String claimCap = ClientClaimChunkCache.claimMax == Short.MAX_VALUE ? "INF" : String.valueOf(ClientClaimChunkCache.claimMax);
        body.addChild(WarForgeUiTheme.text(
                "Claims " + ClientClaimChunkCache.claimCount + "/" + claimCap
                        + " | Loaded " + ClientClaimChunkCache.forceLoadedCount + "/" + ClientClaimChunkCache.forceLoadedMax,
                WarForgeUiTheme.TEXT_SECONDARY));

        UIElement headerActions = new UIElement();
        headerActions.layout(l -> l.flexDirection(FlexDirection.ROW).gapColumn(4));
        if (canDeclareSiege && !targetMode && !startPickMode) {
            Button declareBtn = new Button().setText("Declare Siege");
            WarForgeUiTheme.styleButton(declareBtn, 84);
            declareBtn.setOnClick(event -> {
                ClaimManagerGuiFactory.siegeTargetMode = true;
                ClaimManagerGuiFactory.INSTANCE.openClient(center, radius, pageX, pageZ);
            });
            headerActions.addChild(declareBtn);
        } else if (targetMode) {
            Button cancelBtn = new Button().setText("Cancel");
            WarForgeUiTheme.styleButton(cancelBtn, 60);
            cancelBtn.setOnClick(event -> {
                ClaimManagerGuiFactory.resetSiegeState();
                ClaimManagerGuiFactory.INSTANCE.openClient(center, radius, pageX, pageZ);
            });
            headerActions.addChild(cancelBtn);
        } else if (startPickMode) {
            Button cancelBtn = new Button().setText("Cancel");
            WarForgeUiTheme.styleButton(cancelBtn, 60);
            cancelBtn.setOnClick(event -> {
                ClaimManagerGuiFactory.resetSiegeState();
                Minecraft.getInstance().setScreen(null);
            });
            headerActions.addChild(cancelBtn);
        }
        body.addChild(headerActions);

        UIElement navRow = new UIElement();
        navRow.layout(l -> l.flexDirection(FlexDirection.ROW).gapColumn(4));
        if (viewport.canPanWest()) {
            Button westBtn = new Button().setText("<");
            WarForgeUiTheme.styleButton(westBtn, 16);
            westBtn.setOnClick(event ->
                    ClaimManagerGuiFactory.INSTANCE.openClient(center, radius, viewport.panWest(), viewport.startZ));
            navRow.addChild(westBtn);
        }
        if (viewport.canPanNorth()) {
            Button northBtn = new Button().setText("^");
            WarForgeUiTheme.styleButton(northBtn, 16);
            northBtn.setOnClick(event ->
                    ClaimManagerGuiFactory.INSTANCE.openClient(center, radius, viewport.startX, viewport.panNorth()));
            navRow.addChild(northBtn);
        }
        if (viewport.canPanSouth()) {
            Button southBtn = new Button().setText("v");
            WarForgeUiTheme.styleButton(southBtn, 16);
            southBtn.setOnClick(event ->
                    ClaimManagerGuiFactory.INSTANCE.openClient(center, radius, viewport.startX, viewport.panSouth()));
            navRow.addChild(southBtn);
        }
        if (viewport.canPanEast()) {
            Button eastBtn = new Button().setText(">");
            WarForgeUiTheme.styleButton(eastBtn, 16);
            eastBtn.setOnClick(event ->
                    ClaimManagerGuiFactory.INSTANCE.openClient(center, radius, viewport.panEast(), viewport.startZ));
            navRow.addChild(eastBtn);
        }
        body.addChild(navRow);

        UIElement grid = new UIElement();
        grid.layout(l -> l.flexDirection(FlexDirection.ROW).flexWrap(FlexWrap.WRAP).width(mapSize));

        for (int j = viewport.startZ; j < viewport.startZ + viewport.visibleSize; j++) {
            for (int i = viewport.startX; i < viewport.startX + viewport.visibleSize; i++) {
                final int chunkX = center.x - radius + i;
                final int chunkZ = center.z - radius + j;

                ClaimChunkInfo info = getLiveInfo(center.dim, chunkX, chunkZ);
                SiegeCampAttackInfo baseInfo = toMapState(info, center.x, center.z);
                SiegeCampAttackInfoRender renderInfo = buildRenderInfo(center, baseInfo, info, chunkX, chunkZ, startPickMode, mc);
                boolean[] adjacency = computeAdjacency(center.dim, center.x, center.z, radius, chunkX, chunkZ);
                String texName = ChunkMapTextureDaemon.getTextureName("claimmap", center.dim, chunkX, chunkZ);

                MapCellElement cell = new MapCellElement(texName, renderInfo, adjacency);
                cell.layout(l -> l.width(CELL_SIZE).height(CELL_SIZE));

                final ClaimChunkInfo finalInfo = info;
                final boolean finalStartPickMode = startPickMode;
                final boolean finalTargetMode = targetMode;

                cell.onClick = button -> {
                    if (finalStartPickMode) {
                        handleStartPick(center, chunkX, chunkZ, mc);
                        return;
                    }
                    if (finalTargetMode) {
                        handleTargetPick(center, radius, finalInfo, chunkX, chunkZ);
                        return;
                    }
                    byte action = determineAction(finalInfo, button);
                    if (action == -1) {
                        return;
                    }
                    PacketClaimChunkAction packet = new PacketClaimChunkAction();
                    packet.action = action;
                    packet.chunk = new DimChunkPos(center.dim, chunkX, chunkZ);
                    packet.center = center;
                    packet.radius = radius;
                    WarForgeMod.NETWORK.sendToServer(packet);
                };

                cell.addEventListener(UIEvents.HOVER_TOOLTIPS, (UIEventListener) event -> {
                    List<Component> lines = new ArrayList<>();
                    if (finalStartPickMode) {
                        if (chunkX == center.x && chunkZ == center.z) {
                            lines.add(Component.literal("Siege target"));
                        } else {
                            lines.add(Component.literal("Click to launch the siege from here"));
                        }
                    } else if (finalTargetMode) {
                        boolean enemy = !finalInfo.factionId.equals(Faction.nullUuid)
                                && !finalInfo.factionId.equals(ClientClaimChunkCache.playerFactionId);
                        lines.add(Component.literal(enemy ? "Click to target this claim for a siege" : "Not a siege-able enemy claim"));
                    }
                    if (finalInfo.factionId.equals(Faction.nullUuid)) {
                        lines.add(Component.literal("Wilderness"));
                    } else {
                        lines.add(Component.literal("Faction: " + finalInfo.factionName));
                    }
                    lines.add(Component.literal("Claim Type: " + formatClaimType(finalInfo.claimType)));
                    lines.add(Component.literal("Chunk: [" + chunkX + ", " + chunkZ + "]"));
                    if (finalInfo.vein != null) {
                        if (finalInfo.oreQuality != null) {
                            lines.add(Component.literal("Ore: " + translateVeinName(finalInfo.vein.translationKey,
                                    I18n.get(finalInfo.oreQuality.getTranslationKey()) + " [" +
                                            finalInfo.oreQuality.getMultString(finalInfo.vein) + "]")));
                        } else {
                            lines.add(Component.literal("Ore: " + translateVeinName(finalInfo.vein.translationKey)));
                        }
                    } else {
                        lines.add(Component.literal("No ores in this chunk"));
                    }
                    if (finalInfo.hasFlag(ClaimChunkInfo.FLAG_FORCE_LOADED)) {
                        lines.add(Component.literal("Force-loaded"));
                    }
                    if (finalInfo.hasFlag(ClaimChunkInfo.FLAG_HAS_COLLECTOR)) {
                        lines.add(Component.literal("Has Faction Yield Storage"));
                    }
                    if (finalInfo.hasFlag(ClaimChunkInfo.FLAG_CAN_CLAIM)) {
                        lines.add(Component.literal("Left click to claim"));
                    }
                    if (finalInfo.hasFlag(ClaimChunkInfo.FLAG_CAN_UNCLAIM)) {
                        lines.add(Component.literal("Left click to unclaim"));
                    }
                    if (finalInfo.hasFlag(ClaimChunkInfo.FLAG_CAN_TOGGLE_FORCELOAD)) {
                        lines.add(Component.literal(finalInfo.hasFlag(ClaimChunkInfo.FLAG_FORCE_LOADED)
                                ? "Right click to unforce-load" : "Right click to force-load"));
                    }
                    event.hoverTooltips = (event.hoverTooltips == null
                            ? com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips.empty()
                            : event.hoverTooltips).append(lines.toArray(new Component[0]));
                });

                grid.addChild(cell);
            }
        }

        body.addChild(grid);

        String legendText = startPickMode
                ? "Click a chunk to launch the siege from. This consumes one siege camp block."
                : targetMode
                ? "Click an enemy claim to target it, then pick where to attack from."
                : "Left click claim/unclaim. Right click toggles force-loading.";
        body.addChild(WarForgeUiTheme.text(legendText, WarForgeUiTheme.TEXT_SECONDARY));

        activeScreen = new ModularUIScreen(ModularUI.of(UI.of(root), mc.player), Component.literal("Claim Manager"));
        mc.setScreen(activeScreen);
    }

    private static SiegeCampAttackInfo toMapState(ClaimChunkInfo info, int centerX, int centerZ) {
        SiegeCampAttackInfo state = new SiegeCampAttackInfo();
        state.canAttack = false;
        state.mOffset = new Vec3i(info.x - centerX, 0, info.z - centerZ);
        state.mFactionUUID = info.factionId;
        state.mFactionName = info.factionName;
        state.mFactionColour = info.colour;
        state.mWarforgeVein = info.vein;
        state.mOreQuality = info.oreQuality;
        return state;
    }

    private static SiegeCampAttackInfoRender buildRenderInfo(DimChunkPos center, SiegeCampAttackInfo baseInfo, ClaimChunkInfo info, int chunkX, int chunkZ, boolean startPickMode, Minecraft mc) {
        boolean isTarget = chunkX == center.x && chunkZ == center.z;
        boolean battleZone = isBattleZoneChunk(center.dim, chunkX, chunkZ);
        boolean highlight = startPickMode ? !isTarget : battleZone;
        ResourceLocation centerIcon;
        SiegeCampAttackInfoRender.CenterMarkType centerIconType;
        if (startPickMode) {
            centerIcon = isTarget ? ResourceLocation.fromNamespaceAndPath(Tags.MODID, "gui/icon_siege_attack.png") : null;
            centerIconType = SiegeCampAttackInfoRender.CenterMarkType.CUSTOM_TEXTURE;
        } else {
            centerIcon = (isTarget && mc.player != null) ? SkinUtil.getPlayerFace(mc.player.getUUID()) : null;
            centerIconType = SiegeCampAttackInfoRender.CenterMarkType.PLAYER_FACE;
        }
        return new ClaimChunkRenderInfo(
                baseInfo,
                info.claimType,
                info.hasFlag(ClaimChunkInfo.FLAG_FORCE_LOADED),
                info.outlineStyle == ClaimChunkInfo.OUTLINE_CONQUERED,
                highlight,
                centerIcon,
                centerIconType
        );
    }

    private static boolean isBattleZoneChunk(ResourceKey<Level> dim, int chunkX, int chunkZ) {
        ChunkPos target = new ChunkPos(chunkX, chunkZ);
        for (SiegeCampProgressInfo siegeInfo : ClientProxy.sSiegeInfo.values()) {
            if (siegeInfo == null || siegeInfo.attackingPos == null || !siegeInfo.attackingPos.dim.equals(dim) || siegeInfo.warzoneChunks == null) {
                continue;
            }
            if (siegeInfo.warzoneChunks.contains(target)) {
                return true;
            }
        }
        return false;
    }

    private static ClaimChunkInfo getLiveInfo(ResourceKey<Level> dim, int chunkX, int chunkZ) {
        ClaimChunkInfo info = ClientClaimChunkCache.get(new DimChunkPos(dim, chunkX, chunkZ));
        if (info != null) {
            return info;
        }
        ClaimChunkInfo fallback = new ClaimChunkInfo();
        fallback.x = chunkX;
        fallback.z = chunkZ;
        return fallback;
    }

    private static boolean[] computeAdjacency(ResourceKey<Level> dim, int centerX, int centerZ, int radius, int chunkX, int chunkZ) {
        boolean[] adjacency = new boolean[4];
        ClaimChunkInfo current = getLiveInfo(dim, chunkX, chunkZ);
        if (chunkZ > centerZ - radius) {
            adjacency[0] = !current.factionId.equals(getLiveInfo(dim, chunkX, chunkZ - 1).factionId);
        }
        if (chunkX < centerX + radius) {
            adjacency[1] = !current.factionId.equals(getLiveInfo(dim, chunkX + 1, chunkZ).factionId);
        }
        if (chunkZ < centerZ + radius) {
            adjacency[2] = !current.factionId.equals(getLiveInfo(dim, chunkX, chunkZ + 1).factionId);
        }
        if (chunkX > centerX - radius) {
            adjacency[3] = !current.factionId.equals(getLiveInfo(dim, chunkX - 1, chunkZ).factionId);
        }
        return adjacency;
    }

    private static void handleTargetPick(DimChunkPos center, int radius, ClaimChunkInfo info, int chunkX, int chunkZ) {
        if (info.factionId.equals(Faction.nullUuid) || info.factionId.equals(ClientClaimChunkCache.playerFactionId)) {
            status("Pick an enemy faction claim to siege", Minecraft.getInstance());
            return;
        }
        DimChunkPos target = new DimChunkPos(center.dim, chunkX, chunkZ);
        ClaimManagerGuiFactory.siegeTargetMode = false;
        ClaimManagerGuiFactory.siegeStartPickFor = target;
        int range = Math.min(WarForgeConfig.SIEGE_DECLARE_MAX_RANGE, WarForgeConfig.CLAIM_MANAGER_RADIUS);
        PacketRequestTerrainColors terrainReq = new PacketRequestTerrainColors();
        terrainReq.center = target;
        terrainReq.radius = range;
        WarForgeMod.NETWORK.sendToServer(terrainReq);
        ClaimManagerGuiFactory.INSTANCE.openClient(target, range, -1, -1);
    }

    private static void handleStartPick(DimChunkPos center, int chunkX, int chunkZ, Minecraft mc) {
        DimChunkPos target = ClaimManagerGuiFactory.siegeStartPickFor;
        if (target == null) {
            return;
        }
        if (chunkX == target.x && chunkZ == target.z && center.dim.equals(target.dim)) {
            status("Pick a chunk other than the target to launch from", mc);
            return;
        }
        PacketDeclareSiege packet = new PacketDeclareSiege();
        packet.targetChunk = target;
        packet.fromChunk = new DimChunkPos(center.dim, chunkX, chunkZ);
        WarForgeMod.NETWORK.sendToServer(packet);
        ClaimManagerGuiFactory.resetSiegeState();
        mc.setScreen(null);
    }

    private static void status(String message, Minecraft mc) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), true);
        }
    }

    private static byte determineAction(ClaimChunkInfo info, int mouseButton) {
        if (mouseButton == 1 && info.hasFlag(ClaimChunkInfo.FLAG_CAN_TOGGLE_FORCELOAD)) {
            return PacketClaimChunkAction.ACTION_TOGGLE_FORCELOAD;
        }
        if (info.hasFlag(ClaimChunkInfo.FLAG_CAN_CLAIM)) {
            return PacketClaimChunkAction.ACTION_CLAIM;
        }
        if (info.hasFlag(ClaimChunkInfo.FLAG_CAN_UNCLAIM)) {
            return PacketClaimChunkAction.ACTION_UNCLAIM;
        }
        if (info.hasFlag(ClaimChunkInfo.FLAG_CAN_TOGGLE_FORCELOAD)) {
            return PacketClaimChunkAction.ACTION_TOGGLE_FORCELOAD;
        }
        return -1;
    }

    private static String formatClaimType(Faction.ClaimType claimType) {
        return switch (claimType) {
            case BASIC -> "Basic";
            case REINFORCED -> "Reinforced";
            case CITADEL -> "Citadel";
            case ADMIN -> "Admin";
            case SIEGE -> "Siege";
            default -> "None";
        };
    }

    private static String translateVeinName(String translationKey, Object... args) {
        String resolvedKey = translationKey;
        if (!I18n.exists(resolvedKey) && I18n.exists(resolvedKey + ".name")) {
            resolvedKey += ".name";
        }
        return I18n.get(resolvedKey, args);
    }
}
