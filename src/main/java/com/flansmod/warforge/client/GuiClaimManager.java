package com.flansmod.warforge.client;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.drawable.IngredientDrawable;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.viewport.GuiContext;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.ScrollingTextWidget;
import brachy.modularui.widgets.layout.Flow;
import com.flansmod.warforge.common.util.TimeHelper;
import com.flansmod.warforge.api.modularui.ChunkMapTextureDaemon;
import com.flansmod.warforge.api.modularui.ChunkMapViewport;
import com.flansmod.warforge.api.modularui.MapDrawable;
import com.flansmod.warforge.Tags;
import com.flansmod.warforge.client.util.SkinUtil;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.factories.ClaimManagerGuiData;
import com.flansmod.warforge.common.factories.ClaimManagerGuiFactory;
import com.flansmod.warforge.common.network.ClaimChunkInfo;
import com.flansmod.warforge.common.network.ClaimChunkRenderInfo;
import com.flansmod.warforge.common.network.SiegeCampAttackInfoRender.CenterMarkType;
import com.flansmod.warforge.common.network.PacketClaimChunkAction;
import com.flansmod.warforge.common.network.PacketDeclareSiege;
import com.flansmod.warforge.common.network.PacketRequestTerrainColors;
import com.flansmod.warforge.common.network.SiegeCampAttackInfo;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.server.Faction;
import com.flansmod.warforge.server.ItemMatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.Objects;

public final class GuiClaimManager {
    private static final int CELL_SIZE = 64;
    private static final int MAX_VISIBLE_RADIUS = 4;
    private static final int CONTENT_LEFT = 12;
    private static final int HEADER_HEIGHT = 40;
    private static final int INFO_HEIGHT = 30;
    private static final int LEGEND_HEIGHT = 20;
    private static final int MAP_GUTTER = 18;
    private static final int SECTION_SPACING = 4;
    private static final int NAV_BUTTON_SIZE = 16;
    private static final int MAP_BACKDROP_FILL = 0xFF161B20;
    private static final int WILDERNESS_ACCENT = 0x4A4A4A;

    private GuiClaimManager() {
    }

    public static ModularPanel buildPanel(ClaimManagerGuiData data) {
        int size = data.radius * 2 + 1;
        var window = Minecraft.getInstance().getWindow();
        ChunkMapViewport viewport = ChunkMapViewport.create(
                size,
                3,
                Math.min(size, MAX_VISIBLE_RADIUS * 2 + 1),
                CELL_SIZE,
                window.getGuiScaledWidth(),
                window.getGuiScaledHeight(),
                CONTENT_LEFT * 2 + 40,
                64,
                data.pageX,
                data.pageZ
        );
        int mapSize = viewport.visibleSize * CELL_SIZE;
        int mapSectionWidth = mapSize + MAP_GUTTER * 2;
        int mapSectionHeight = mapSize + MAP_GUTTER * 2;
        int width = mapSectionWidth + CONTENT_LEFT * 2;
        int infoY = HEADER_HEIGHT + SECTION_SPACING;
        int mapY = infoY + INFO_HEIGHT + SECTION_SPACING;
        int legendY = mapY + mapSectionHeight + SECTION_SPACING;
        int height = legendY + LEGEND_HEIGHT + 12;
        int mapLeft = CONTENT_LEFT + MAP_GUTTER;
        int mapTop = mapY + MAP_GUTTER;
        int accentColor = resolveAccentColor(data);

        // Two-stage siege declaration state (client-side, see ClaimManagerGuiFactory).
        DimChunkPos siegePickFor = ClaimManagerGuiFactory.siegeStartPickFor;
        final boolean startPickMode = siegePickFor != null && siegePickFor.dim.equals(data.dim)
                && siegePickFor.x == data.centerX && siegePickFor.z == data.centerZ;
        if (siegePickFor != null && !startPickMode) {
            ClaimManagerGuiFactory.siegeStartPickFor = null; // stale: centered somewhere other than the target
        }
        final boolean targetMode = ClaimManagerGuiFactory.siegeTargetMode && !startPickMode;
        final boolean canDeclareSiege = WarForgeConfig.SIEGE_ALLOW_UI_DECLARE
                && !ClientClaimChunkCache.playerFactionId.equals(Faction.nullUuid);

        ModularPanel panel = ModularPanel.defaultPanel("claim_manager")
                .width(width)
                .height(height)
                .topRel(0.5f);

        Flow infoSection = ModularGuiStyle.section(mapSectionWidth, INFO_HEIGHT).name("claim_manager_info_section").pos(CONTENT_LEFT, infoY);
        Flow legendSection = ModularGuiStyle.section(mapSectionWidth, LEGEND_HEIGHT).name("claim_manager_legend_section").pos(CONTENT_LEFT, legendY);

        panel.child(new IDrawable.DrawableWidget(ModularGuiStyle.headerBackdrop()).size(width, HEADER_HEIGHT));
        panel.child(infoSection);
        panel.child(new IDrawable.DrawableWidget(ModularGuiStyle.sectionBackdrop()).size(mapSectionWidth, mapSectionHeight).pos(CONTENT_LEFT, mapY));
        panel.child(new IDrawable.DrawableWidget(ModularGuiStyle.insetBackdrop(MAP_BACKDROP_FILL)).size(mapSize, mapSize).pos(mapLeft, mapTop));
        panel.child(legendSection);
        panel.child(new IDrawable.DrawableWidget(ModularGuiStyle.colorStripe(accentColor)).size(6, height));
        panel.child(ModularGuiStyle.panelCloseButton(width));

        panel.child(Text.str(startPickMode ? "Choose Siege Origin" : targetMode ? "Select Siege Target" : "Territory Map")
                .style(ChatFormatting.BOLD)
                .shadow(true)
                .color(ModularGuiStyle.TEXT_PRIMARY)
                .scale(1.15f)
                .asWidget()
                .name("claim_manager_title")
                .pos(CONTENT_LEFT, 12));
        panel.child(new ScrollingTextWidget(Text.str("Center [" + data.centerX + ", " + data.centerZ + "] | Radius " + data.radius + " | Dim " + data.dim.location()))
                .name("claim_manager_subtitle")
                .pos(CONTENT_LEFT, 27)
                .width(width - CONTENT_LEFT * 2 - 18)
                .color(ModularGuiStyle.TEXT_SECONDARY));

        infoSection.child(new ScrollingTextWidget(Text.dynamic(() -> {
                    String claimCap = ClientClaimChunkCache.claimMax == Short.MAX_VALUE ? "INF" : String.valueOf(ClientClaimChunkCache.claimMax);
                    return Component.literal("Claims " + ClientClaimChunkCache.claimCount + "/" + claimCap + " | Loaded " + ClientClaimChunkCache.forceLoadedCount + "/" + ClientClaimChunkCache.forceLoadedMax);
                }))
                .name("claim_manager_status_text")
                .width(mapSectionWidth - 10)
                .margin(0, 0, 0, 4)
                .color(ModularGuiStyle.TEXT_PRIMARY));
        infoSection.child(new ScrollingTextWidget(Text.str(viewport.visibleSize < size
                        ? "Paged view " + viewport.visibleSize + "x" + viewport.visibleSize + " across the selected radius."
                        : "Showing the full selected radius."))
                .name("claim_manager_page_text")
                .width(mapSectionWidth - 10)
                .margin(0, 0, 0, 2)
                .color(ModularGuiStyle.TEXT_MUTED));
        legendSection.child(new ScrollingTextWidget(Text.str(startPickMode
                        ? "Click a highlighted chunk to launch the siege from. This consumes one siege camp block."
                        : targetMode
                        ? "Click an enemy claim to target it, then pick where to attack from."
                        : "Left click claim or unclaim. Right click toggles force-loading. Use arrows to page the map."))
                .name("claim_manager_controls_text")
                .width(mapSectionWidth - 10)
                .color(ModularGuiStyle.TEXT_MUTED));

        addPanButtons(panel, data, viewport, CONTENT_LEFT, mapY, mapSectionWidth, mapSectionHeight);

        if (canDeclareSiege && !targetMode && !startPickMode) {
            panel.child(ModularGuiStyle.actionButton("Declare Siege", 84, () -> {
                ClaimManagerGuiFactory.siegeTargetMode = true;
                ClaimManagerGuiFactory.INSTANCE.openClient(data.getCenter(), data.radius, data.pageX, data.pageZ);
            }).pos(width - 112, 9).height(16));
        } else if (targetMode) {
            panel.child(ModularGuiStyle.actionButton("Cancel", 60, () -> {
                ClaimManagerGuiFactory.resetSiegeState();
                ClaimManagerGuiFactory.INSTANCE.openClient(data.getCenter(), data.radius, data.pageX, data.pageZ);
            }).pos(width - 86, 9).height(16));
        } else if (startPickMode) {
            panel.child(ModularGuiStyle.actionButton("Cancel", 60, () -> {
                ClaimManagerGuiFactory.resetSiegeState();
                Minecraft.getInstance().setScreen(null);
            }).pos(width - 86, 9).height(16));
        }

        for (int i = viewport.startX; i < viewport.startX + viewport.visibleSize; i++) {
            for (int j = viewport.startZ; j < viewport.startZ + viewport.visibleSize; j++) {
                final int chunkX = data.centerX - data.radius + i;
                final int chunkZ = data.centerZ - data.radius + j;
                final int localX = i - viewport.startX;
                final int localZ = j - viewport.startZ;

                panel.child(new ButtonWidget<>()
                        .name("claim_chunk_" + data.dim.location() + "_" + chunkX + "_" + chunkZ)
                        .overlay(liveChunkOverlay(data, chunkX, chunkZ))
                        .onMousePressed((context, mouseButton) -> {
                            if (startPickMode) {
                                return handleStartPick(data, chunkX, chunkZ);
                            }
                            ClaimChunkInfo info = getLiveInfo(data, chunkX, chunkZ);
                            if (targetMode) {
                                return handleTargetPick(data, info, chunkX, chunkZ);
                            }
                            byte action = determineAction(info, mouseButton);
                            if (action == -1) {
                                return false;
                            }

                            PacketClaimChunkAction packet = new PacketClaimChunkAction();
                            packet.action = action;
                            packet.chunk = new DimChunkPos(data.dim, chunkX, chunkZ);
                            packet.center = data.getCenter();
                            packet.radius = data.radius;
                            WarForgeMod.NETWORK.sendToServer(packet);
                            return true;
                        })
                        .tooltipDynamic(tooltip -> {
                            ClaimChunkInfo info = getLiveInfo(data, chunkX, chunkZ);
                            if (startPickMode) {
                                if (chunkX == data.centerX && chunkZ == data.centerZ) {
                                    tooltip.addLine("Siege target");
                                } else {
                                    tooltip.addLine("Click to launch the siege from here");
                                }
                            } else if (targetMode) {
                                boolean enemy = !info.factionId.equals(Faction.nullUuid)
                                        && !info.factionId.equals(ClientClaimChunkCache.playerFactionId);
                                tooltip.addLine(enemy ? "Click to target this claim for a siege" : "Not a siege-able enemy claim");
                            }
                            if (info.factionId.equals(Faction.nullUuid)) {
                                tooltip.addLine("Wilderness");
                            } else {
                                tooltip.addLine("Faction: " + info.factionName);
                            }
                            if (info.conqueredRemainingMs > 0) {
                                tooltip.addLine("Conquered — reverts to wilderness in " + TimeHelper.formatTime(info.conqueredRemainingMs));
                            }
                            tooltip.addLine("Claim Type: " + formatClaimType(info.claimType));
                            tooltip.addLine("Chunk: [" + info.x + ", " + info.z + "]");
                            if (info.vein != null) {
                                tooltip.addDrawableLine(new IngredientDrawable(
                                        info.vein.compIds.stream()
                                                .map(ItemMatcher::toStack)
                                                .filter(stack -> !stack.isEmpty())
                                                .toArray(ItemStack[]::new)
                                ).asIcon().size(25));
                                if (info.oreQuality != null) {
                                    tooltip.addLine(Text.str("Ore In the chunk: " +
                                            translateVeinName(info.vein.translationKey,
                                                    I18n.get(info.oreQuality.getTranslationKey()) + " [" +
                                                            info.oreQuality.getMultString(info.vein) + "]")));
                                } else {
                                    tooltip.addLine(Text.str("Ore In the chunk: " + translateVeinName(info.vein.translationKey, "")));
                                }
                            } else {
                                tooltip.addLine(Text.str("No ores in this chunk"));
                            }
                            if (info.hasFlag(ClaimChunkInfo.FLAG_FORCE_LOADED)) {
                                tooltip.addLine("Force-loaded");
                            }
                            if (info.hasFlag(ClaimChunkInfo.FLAG_HAS_COLLECTOR)) {
                                tooltip.addLine("Has Faction Yield Storage");
                            }
                            if (info.hasFlag(ClaimChunkInfo.FLAG_CAN_CLAIM)) {
                                tooltip.addLine("Left click to claim using first claim block in inventory");
                            }
                            if (info.hasFlag(ClaimChunkInfo.FLAG_CAN_UNCLAIM)) {
                                tooltip.addLine("Left click to unclaim and refund claim block");
                            }
                            if (info.hasFlag(ClaimChunkInfo.FLAG_CAN_TOGGLE_FORCELOAD)) {
                                tooltip.addLine(info.hasFlag(ClaimChunkInfo.FLAG_FORCE_LOADED) ? "Right click to unforce-load" : "Right click to force-load");
                            }
                        })
                        .size(CELL_SIZE)
                        .pos((localX * CELL_SIZE) + mapLeft, (localZ * CELL_SIZE) + mapTop));
            }
        }

        return panel;
    }

    private static void addPanButtons(ModularPanel panel, ClaimManagerGuiData data, ChunkMapViewport viewport, int mapSectionX, int mapSectionY, int mapSectionWidth, int mapSectionHeight) {
        if (!viewport.canPanNorth() && !viewport.canPanSouth() && !viewport.canPanWest() && !viewport.canPanEast()) {
            return;
        }

        if (viewport.canPanNorth()) {
            panel.child(panButton("^", mapSectionX + mapSectionWidth / 2 - NAV_BUTTON_SIZE / 2, mapSectionY + 1, data, viewport.startX, viewport.panNorth()));
        }
        if (viewport.canPanSouth()) {
            panel.child(panButton("v", mapSectionX + mapSectionWidth / 2 - NAV_BUTTON_SIZE / 2, mapSectionY + mapSectionHeight - NAV_BUTTON_SIZE - 1, data, viewport.startX, viewport.panSouth()));
        }
        if (viewport.canPanWest()) {
            panel.child(panButton("<", mapSectionX + 1, mapSectionY + mapSectionHeight / 2 - NAV_BUTTON_SIZE / 2, data, viewport.panWest(), viewport.startZ));
        }
        if (viewport.canPanEast()) {
            panel.child(panButton(">", mapSectionX + mapSectionWidth - NAV_BUTTON_SIZE - 1, mapSectionY + mapSectionHeight / 2 - NAV_BUTTON_SIZE / 2, data, viewport.panEast(), viewport.startZ));
        }
    }

    private static ButtonWidget<?> panButton(String text, int x, int y, ClaimManagerGuiData data, int pageX, int pageZ) {
        ButtonWidget<?> button = ModularGuiStyle.actionButton(text, NAV_BUTTON_SIZE, () ->
                ClaimManagerGuiFactory.INSTANCE.openClient(data.getCenter(), data.radius, pageX, pageZ));
        button.name("claim_map_pan_" + switch (text) {
            case "^" -> "north";
            case "v" -> "south";
            case "<" -> "west";
            case ">" -> "east";
            default -> "unknown";
        });
        button.height(NAV_BUTTON_SIZE);
        button.pos(x, y);
        return button;
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

    private static IDrawable liveChunkOverlay(ClaimManagerGuiData data, int chunkX, int chunkZ) {
        return (GuiContext context, int x, int y, int width, int height, WidgetTheme theme) -> {
            ClaimChunkInfo info = getLiveInfo(data, chunkX, chunkZ);
            DimChunkPos pickFor = ClaimManagerGuiFactory.siegeStartPickFor;
            boolean startPick = pickFor != null && pickFor.dim.equals(data.dim)
                    && pickFor.x == data.centerX && pickFor.z == data.centerZ;
            boolean isTarget = chunkX == data.centerX && chunkZ == data.centerZ;
            // In stage 2 highlight every valid (in-view, non-target) launch chunk and mark the target;
            // otherwise fall back to the normal battle-zone overlay / player-face center icon.
            boolean highlight = startPick ? !isTarget : isBattleZoneChunk(data.dim, chunkX, chunkZ);
            ResourceLocation centerIcon = startPick
                    ? (isTarget ? new ResourceLocation(Tags.MODID, "gui/icon_siege_attack.png") : null)
                    : getCenterIcon(data, chunkX, chunkZ);
            // The siege-attack target marker is a whole icon; getCenterIcon yields a full player skin sheet.
            CenterMarkType centerIconType = startPick ? CenterMarkType.CUSTOM_TEXTURE : CenterMarkType.PLAYER_FACE;
            ClaimChunkRenderInfo renderInfo = new ClaimChunkRenderInfo(
                    toMapState(info, data.centerX, data.centerZ),
                    info.claimType,
                    info.hasFlag(ClaimChunkInfo.FLAG_FORCE_LOADED),
                    info.outlineStyle == ClaimChunkInfo.OUTLINE_CONQUERED,
                    highlight,
                    centerIcon,
                    centerIconType
            );
            new MapDrawable(textureName(data.dim, chunkX, chunkZ), renderInfo, getLiveAdjacency(data, chunkX, chunkZ))
                    .draw(context, x, y, width, height, theme);
        };
    }

    // Stage 1: a chunk was clicked while choosing the siege target. Validates it is an enemy claim,
    // then opens the stage-2 picker centered on it and requests server terrain for that region.
    private static boolean handleTargetPick(ClaimManagerGuiData data, ClaimChunkInfo info, int chunkX, int chunkZ) {
        if (info.factionId.equals(Faction.nullUuid) || info.factionId.equals(ClientClaimChunkCache.playerFactionId)) {
            status("Pick an enemy faction claim to siege");
            return false;
        }
        DimChunkPos target = new DimChunkPos(data.dim, chunkX, chunkZ);
        ClaimManagerGuiFactory.siegeTargetMode = false;
        ClaimManagerGuiFactory.siegeStartPickFor = target;
        int range = Math.min(WarForgeConfig.SIEGE_DECLARE_MAX_RANGE, WarForgeConfig.CLAIM_MANAGER_RADIUS);
        PacketRequestTerrainColors terrainReq = new PacketRequestTerrainColors();
        terrainReq.center = target;
        terrainReq.radius = range;
        WarForgeMod.NETWORK.sendToServer(terrainReq);
        ClaimManagerGuiFactory.INSTANCE.openClient(target, range, -1, -1);
        return true;
    }

    // Stage 2: a chunk was clicked while choosing where to launch the siege from. Sends the declare
    // packet (server re-validates range, ownership, sieg-ability, cooldown and the item cost).
    private static boolean handleStartPick(ClaimManagerGuiData data, int chunkX, int chunkZ) {
        DimChunkPos target = ClaimManagerGuiFactory.siegeStartPickFor;
        if (target == null) {
            return false;
        }
        if (chunkX == target.x && chunkZ == target.z && data.dim.equals(target.dim)) {
            status("Pick a chunk other than the target to launch from");
            return false;
        }
        PacketDeclareSiege packet = new PacketDeclareSiege();
        packet.targetChunk = target;
        packet.fromChunk = new DimChunkPos(data.dim, chunkX, chunkZ);
        WarForgeMod.NETWORK.sendToServer(packet);
        ClaimManagerGuiFactory.resetSiegeState();
        Minecraft.getInstance().setScreen(null);
        return true;
    }

    private static void status(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), true);
        }
    }

    private static ClaimChunkInfo getLiveInfo(ClaimManagerGuiData data, int chunkX, int chunkZ) {
        ClaimChunkInfo info = ClientClaimChunkCache.get(new DimChunkPos(data.dim, chunkX, chunkZ));
        if (info != null) {
            return info;
        }

        ClaimChunkInfo fallback = new ClaimChunkInfo();
        fallback.x = chunkX;
        fallback.z = chunkZ;
        return fallback;
    }

    private static boolean[] getLiveAdjacency(ClaimManagerGuiData data, int chunkX, int chunkZ) {
        boolean[] adjacency = new boolean[4];
        ClaimChunkInfo current = getLiveInfo(data, chunkX, chunkZ);

        if (chunkZ > data.centerZ - data.radius) {
            adjacency[0] = !current.factionId.equals(getLiveInfo(data, chunkX, chunkZ - 1).factionId);
        }
        if (chunkX < data.centerX + data.radius) {
            adjacency[1] = !current.factionId.equals(getLiveInfo(data, chunkX + 1, chunkZ).factionId);
        }
        if (chunkZ < data.centerZ + data.radius) {
            adjacency[2] = !current.factionId.equals(getLiveInfo(data, chunkX, chunkZ + 1).factionId);
        }
        if (chunkX > data.centerX - data.radius) {
            adjacency[3] = !current.factionId.equals(getLiveInfo(data, chunkX - 1, chunkZ).factionId);
        }

        return adjacency;
    }

    private static ResourceLocation getCenterIcon(ClaimManagerGuiData data, int chunkX, int chunkZ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return null;
        }
        if (chunkX != data.centerX || chunkZ != data.centerZ) {
            return null;
        }
        return SkinUtil.getPlayerFace(minecraft.player.getUUID());
    }

    private static boolean isBattleZoneChunk(ResourceKey<Level> dim, int chunkX, int chunkZ) {
        ChunkPos target = new ChunkPos(chunkX, chunkZ);
        for (var siegeInfo : ClientProxy.sSiegeInfo.values()) {
            if (siegeInfo == null || siegeInfo.attackingPos == null || !siegeInfo.attackingPos.dim.equals(dim) || siegeInfo.warzoneChunks == null) {
                continue;
            }
            if (siegeInfo.warzoneChunks.contains(target)) {
                return true;
            }
        }
        return false;
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

    private static String textureName(ResourceKey<Level> dim, int x, int z) {
        return ChunkMapTextureDaemon.getTextureName("claimmap", dim, x, z);
    }

    private static int resolveAccentColor(ClaimManagerGuiData data) {
        ClaimChunkInfo center = getLiveInfo(data, data.centerX, data.centerZ);
        if (center != null && !center.factionId.equals(Faction.nullUuid)) {
            return center.colour;
        }
        return WILDERNESS_ACCENT;
    }
}
