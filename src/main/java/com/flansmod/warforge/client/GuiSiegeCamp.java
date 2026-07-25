package com.flansmod.warforge.client;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.drawable.ItemDrawable;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.ScrollingTextWidget;
import brachy.modularui.widgets.layout.Flow;
import com.flansmod.warforge.api.Color4i;
import com.flansmod.warforge.api.Time;
import com.flansmod.warforge.api.modularui.ChunkMapTextureDaemon;
import com.flansmod.warforge.api.modularui.ChunkMapUtil;
import com.flansmod.warforge.api.modularui.ChunkMapViewport;
import com.flansmod.warforge.api.modularui.MapDrawable;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.factories.SiegeCampGuiData;
import com.flansmod.warforge.common.network.PacketStartSiege;
import com.flansmod.warforge.common.network.SiegeCampAttackInfo;
import com.flansmod.warforge.common.network.SiegeCampAttackInfoRender;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.server.Faction;
import com.flansmod.warforge.server.ItemMatcher;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class GuiSiegeCamp {
    private static final int RADIUS = 2;
    private static final int CELL_SIZE = 64;
    private static final int CONTENT_LEFT = 12;
    private static final int HEADER_HEIGHT = 40;
    private static final int INFO_HEIGHT = 48;
    private static final int LEGEND_HEIGHT = 28;
    private static final int MAP_GUTTER = 18;
    private static final int SECTION_SPACING = 8;
    private static final int MAP_BACKDROP_FILL = 0xFF161B20;

    private GuiSiegeCamp() {
    }

    public static ModularPanel buildPanel(SiegeCampGuiData data) {
        DimChunkPos centerChunk = data.siegeCampPos.toChunkPos();
        String dimName = data.siegeCampPos.dim.location().getPath();
        int totalSize = 2 * RADIUS + 1;
        ChunkMapViewport viewport = ChunkMapViewport.create(
                totalSize,
                3,
                totalSize,
                CELL_SIZE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                48,
                72,
                -1,
                -1
        );

        int mapSize = CELL_SIZE * viewport.visibleSize;
        int mapSectionWidth = mapSize + MAP_GUTTER * 2;
        int mapSectionHeight = mapSize + MAP_GUTTER * 2;
        int width = mapSectionWidth + CONTENT_LEFT * 2;
        int infoY = HEADER_HEIGHT + SECTION_SPACING;
        int mapY = infoY + INFO_HEIGHT + SECTION_SPACING;
        int legendY = mapY + mapSectionHeight + SECTION_SPACING;
        int height = legendY + LEGEND_HEIGHT + 12;
        int mapLeft = CONTENT_LEFT + MAP_GUTTER;
        int mapTop = mapY + MAP_GUTTER;

        ModularPanel panel = ModularPanel.defaultPanel("siege_main", width, height).topRel(0.5f);

        Flow infoSection = ModularGuiStyle.section(mapSectionWidth, INFO_HEIGHT).name("siege_info_section").pos(CONTENT_LEFT, infoY);
        Flow legendSection = ModularGuiStyle.section(mapSectionWidth, LEGEND_HEIGHT).name("siege_legend_section").pos(CONTENT_LEFT, legendY);

        HashMap<Long, SiegeCampAttackInfo> byOffset = new HashMap<>();
        for (SiegeCampAttackInfo info : data.possibleAttacks) {
            byOffset.put(ChunkMapUtil.key(info.mOffset.getX(), info.mOffset.getZ()), info);
        }

        List<SiegeCampAttackInfo> orderedAttacks = new ArrayList<>(totalSize * totalSize);
        for (int z = -RADIUS; z <= RADIUS; z++) {
            for (int x = -RADIUS; x <= RADIUS; x++) {
                orderedAttacks.add(byOffset.get(ChunkMapUtil.key(x, z)));
            }
        }

        HashMap<Long, Integer> tintByChunk = new HashMap<>();
        for (SiegeCampAttackInfo info : orderedAttacks) {
            if (info != null && !info.mFactionUUID.equals(Faction.nullUuid)) {
                tintByChunk.put(ChunkMapUtil.key(centerChunk.x + info.mOffset.getX(), centerChunk.z + info.mOffset.getZ()), info.mFactionColour);
            }
        }
        String textureNamespace = "siegemap_" + dimName + "_" + centerChunk.x + "_" + centerChunk.z;
        ChunkMapTextureDaemon.requestMapUpdate(textureNamespace, data.siegeCampPos.dim, centerChunk.x, centerChunk.z, RADIUS, tintByChunk);

        boolean[][] adjacencyArray = new boolean[orderedAttacks.size()][4];
        ChunkMapUtil.computeAdjacency(orderedAttacks, RADIUS, adjacencyArray);

        panel.child(new IDrawable.DrawableWidget(ModularGuiStyle.headerBackdrop()).name("siege_header_backdrop").size(width, HEADER_HEIGHT));
        panel.child(infoSection);
        panel.child(new IDrawable.DrawableWidget(ModularGuiStyle.sectionBackdrop()).name("siege_map_frame").size(mapSectionWidth, mapSectionHeight).pos(CONTENT_LEFT, mapY));
        panel.child(new IDrawable.DrawableWidget(ModularGuiStyle.insetBackdrop(MAP_BACKDROP_FILL)).name("siege_map_backdrop").size(mapSize, mapSize).pos(mapLeft, mapTop));
        panel.child(legendSection);
        panel.child(new IDrawable.DrawableWidget(ModularGuiStyle.colorStripe(data.color)).name("siege_color_stripe").size(6, height));
        panel.child(ModularGuiStyle.panelCloseButton(width));

        panel.child(Text.str("Siege Target Map").asWidget()
                .name("siege_title")
                .pos(CONTENT_LEFT, 12)
                .style(Text.BOLD)
                .shadow(true)
                .color(ModularGuiStyle.TEXT_PRIMARY)
                .scale(1.15f));
        panel.child(new ScrollingTextWidget(Text.str("Camp [" + centerChunk.x + ", " + centerChunk.z + "] | Dim " + dimName + " | Radius " + RADIUS))
                .name("siege_subtitle")
                .pos(CONTENT_LEFT, 27)
                .width(width - CONTENT_LEFT * 2 - 18)
                .color(ModularGuiStyle.TEXT_SECONDARY));

        infoSection.child(new ScrollingTextWidget(Text.str("Current momentum: " + data.momentum))
                .name("siege_momentum_text")
                .width(mapSectionWidth - 10)
                .margin(0, 0, 0, 4)
                .color(ModularGuiStyle.TEXT_PRIMARY)
                .tooltip(richTooltip -> {
                    richTooltip.addLine("§6Momentum System§r");
                    richTooltip.addLine("§7Momentum affects siege duration.§r");
                    richTooltip.addLine("§7Gained via §aWON§7 sieges, lost on §cLOST§7 sieges.§r");
                    richTooltip.addLine("§7Expires after §b" + WarForgeConfig.SIEGE_MOMENTUM_DURATION + " minutes§7.§r");
                    for (byte level : WarForgeConfig.SIEGE_MOMENTUM_TIME.keySet()) {
                        long time = WarForgeConfig.SIEGE_MOMENTUM_TIME.get(level) * 1000L;
                        richTooltip.addLine("§eLevel " + level + "§r: §a" + new Time(time).getFormattedTime(Time.TimeFormat.MINUTES_SECONDS, Time.Verbality.SHORT));
                    }
                }));
        infoSection.child(new ScrollingTextWidget(Text.str("Select a target chunk around the siege camp. Center tile is your current camp position."))
                .name("siege_prompt_text")
                .width(mapSectionWidth - 10)
                .margin(0, 0, 0, 2)
                .color(ModularGuiStyle.TEXT_MUTED));
        legendSection.child(new ScrollingTextWidget(Text.str("Click an attackable chunk to begin the siege. Tooltips show faction ownership, claim type, and vein data."))
                .name("siege_controls_text")
                .width(mapSectionWidth - 10)
                .color(ModularGuiStyle.TEXT_MUTED));

        for (int i = viewport.startX; i < viewport.startX + viewport.visibleSize; i++) {
            for (int j = viewport.startZ; j < viewport.startZ + viewport.visibleSize; j++) {
                int index = j * totalSize + i;
                int localX = i - viewport.startX;
                int localZ = j - viewport.startZ;
                SiegeCampAttackInfoRender chunkInfo = new SiegeCampAttackInfoRender(orderedAttacks.get(index));
                if (chunkInfo.mOffset.getX() == 0 && chunkInfo.mOffset.getZ() == 0) {
                    chunkInfo.setCenterMarkType(SiegeCampAttackInfoRender.CenterMarkType.SIEGE_CAMP);
                }
                panel.child(new ButtonWidget<>()
                        .name("siege_chunk_" + dimName + "_" + (centerChunk.x + chunkInfo.mOffset.getX()) + "_" + (centerChunk.z + chunkInfo.mOffset.getZ()))
                        .overlay(new MapDrawable(ChunkMapTextureDaemon.getTextureName(textureNamespace, data.siegeCampPos.dim, centerChunk.x + chunkInfo.mOffset.getX(), centerChunk.z + chunkInfo.mOffset.getZ()), chunkInfo, adjacencyArray[index]))
                        .onMousePressed((context, button) -> {
                            if ((chunkInfo.mOffset.getX() == 0 && chunkInfo.mOffset.getZ() == 0) && !chunkInfo.canAttack) {
                                return false;
                            }

                            PacketStartSiege siegePacket = new PacketStartSiege();
                            siegePacket.mSiegeCampPos = data.siegeCampPos;
                            siegePacket.mOffset = chunkInfo.mOffset;
                            WarForgeMod.NETWORK.sendToServer(siegePacket);
                            panel.closeIfOpen();
                            return true;
                        })
                        .tooltip(richTooltip -> {
                            if (!chunkInfo.mFactionName.isEmpty()) {
                                richTooltip.addLine(Text.str("Territory of " + chunkInfo.mFactionName).color(Color4i.fromRGB(chunkInfo.mFactionColour).toARGB()));
                            } else {
                                richTooltip.addLine(Text.str("Wilderness").style(Text.GREEN));
                            }
                            richTooltip.addLine("Claim Type: " + formatClaimType(chunkInfo.claimType));
                            if (chunkInfo.mWarforgeVein != null) {
                                richTooltip.addDrawableLine(new ItemDrawable(
                                        chunkInfo.mWarforgeVein.compIds.stream()
                                                .map(ItemMatcher::toStack)
                                                .filter(stack -> !stack.isEmpty())
                                                .toArray(ItemStack[]::new)
                                ).asIcon().size(25));
                                richTooltip.addLine(Text.str("Ore In the chunk: " +
                                        translateVeinName(chunkInfo.mWarforgeVein.translationKey,
                                                I18n.get(chunkInfo.mOreQuality.getTranslationKey()) + " [" +
                                                        chunkInfo.mOreQuality.getMultString(chunkInfo.mWarforgeVein) + "]")));
                            } else {
                                richTooltip.addLine(Text.str("No ores in this chunk"));
                            }
                            if (chunkInfo.canAttack) {
                                richTooltip.addLine(Text.str("Total attack time: " + new Time(WarForgeConfig.SIEGE_MOMENTUM_TIME.get(data.momentum) * 1000L).getFormattedTime(Time.TimeFormat.HOURS_MINUTES_SECONDS, Time.Verbality.SHORT)).style(Text.RED));
                                richTooltip.addLine(Text.str("Click to attack now!").style(Text.BOLD, Text.RED));
                            }
                        })
                        .size(CELL_SIZE)
                        .pos((localX * CELL_SIZE) + mapLeft, (localZ * CELL_SIZE) + mapTop));
            }
        }

        return panel;
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
