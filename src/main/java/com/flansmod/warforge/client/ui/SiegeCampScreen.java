package com.flansmod.warforge.client.ui;

import com.flansmod.warforge.api.Time;
import com.flansmod.warforge.api.modularui.ChunkMapTextureDaemon;
import com.flansmod.warforge.api.modularui.ChunkMapUtil;
import com.flansmod.warforge.api.modularui.ChunkMapViewport;
import com.flansmod.warforge.api.modularui.MapCellElement;
import com.flansmod.warforge.api.modularui.WarForgeUiTheme;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.network.PacketSiegeCampInfo;
import com.flansmod.warforge.common.network.PacketStartSiege;
import com.flansmod.warforge.common.network.SiegeCampAttackInfo;
import com.flansmod.warforge.common.network.SiegeCampAttackInfoRender;
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
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class SiegeCampScreen {
    private static final int RADIUS = 2;
    private static final int CELL_SIZE = 64;

    private SiegeCampScreen() {
    }

    public static void open(PacketSiegeCampInfo data) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        DimChunkPos centerChunk = data.mSiegeCampPos.toChunkPos();
        String dimName = data.mSiegeCampPos.dim.location().getPath();
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

        HashMap<Long, SiegeCampAttackInfo> byOffset = new HashMap<>();
        for (SiegeCampAttackInfo info : data.mPossibleAttacks) {
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
        ChunkMapTextureDaemon.requestMapUpdate(textureNamespace, data.mSiegeCampPos.dim, centerChunk.x, centerChunk.z, RADIUS, tintByChunk);

        boolean[][] adjacencyArray = new boolean[orderedAttacks.size()][4];
        ChunkMapUtil.computeAdjacency(orderedAttacks, RADIUS, adjacencyArray);

        int mapSize = CELL_SIZE * viewport.visibleSize;

        UIElement root = new UIElement();
        root.layout(l -> l.flexDirection(FlexDirection.COLUMN).paddingAll(8).gapRow(4));
        WarForgeUiTheme.panel(root);
        UIElement body = root;

        Button close = WarForgeUiTheme.closeButton();
        close.setOnClick(event -> Minecraft.getInstance().setScreen(null));
        body.addChild(WarForgeUiTheme.header("Siege Target Map", close));
        body.addChild(WarForgeUiTheme.text(
                "Camp [" + centerChunk.x + ", " + centerChunk.z + "] | Dim " + dimName + " | Radius " + RADIUS,
                WarForgeUiTheme.TEXT_SECONDARY));
        body.addChild(WarForgeUiTheme.text("Momentum: " + data.momentum, WarForgeUiTheme.TEXT_SECONDARY));

        UIElement grid = new UIElement();
        grid.layout(l -> l.flexDirection(FlexDirection.ROW).flexWrap(FlexWrap.WRAP).width(mapSize));

        for (int j = viewport.startZ; j < viewport.startZ + viewport.visibleSize; j++) {
            for (int i = viewport.startX; i < viewport.startX + viewport.visibleSize; i++) {
                int index = j * totalSize + i;
                SiegeCampAttackInfo rawInfo = orderedAttacks.get(index);
                if (rawInfo == null) {
                    UIElement blank = new UIElement();
                    blank.layout(l -> l.width(CELL_SIZE).height(CELL_SIZE));
                    grid.addChild(blank);
                    continue;
                }

                SiegeCampAttackInfoRender chunkInfo = new SiegeCampAttackInfoRender(rawInfo);
                if (chunkInfo.mOffset.getX() == 0 && chunkInfo.mOffset.getZ() == 0) {
                    chunkInfo.setCenterMarkType(SiegeCampAttackInfoRender.CenterMarkType.SIEGE_CAMP);
                }

                String texName = ChunkMapTextureDaemon.getTextureName(
                        textureNamespace,
                        data.mSiegeCampPos.dim,
                        centerChunk.x + chunkInfo.mOffset.getX(),
                        centerChunk.z + chunkInfo.mOffset.getZ()
                );

                MapCellElement cell = new MapCellElement(texName, chunkInfo, adjacencyArray[index]);
                cell.layout(l -> l.width(CELL_SIZE).height(CELL_SIZE));

                cell.onClick = button -> {
                    PacketStartSiege siegePacket = new PacketStartSiege();
                    siegePacket.mSiegeCampPos = data.mSiegeCampPos;
                    siegePacket.mOffset = chunkInfo.mOffset;
                    WarForgeMod.NETWORK.sendToServer(siegePacket);
                    Minecraft.getInstance().setScreen(null);
                };

                cell.addEventListener(UIEvents.HOVER_TOOLTIPS, (UIEventListener) event -> {
                    List<Component> lines = new ArrayList<>();
                    if (!chunkInfo.mFactionName.isEmpty()) {
                        lines.add(Component.literal("Territory of " + chunkInfo.mFactionName));
                    } else {
                        lines.add(Component.literal("Wilderness"));
                    }
                    lines.add(Component.literal("Claim Type: " + formatClaimType(chunkInfo.claimType)));
                    if (chunkInfo.mWarforgeVein != null) {
                        lines.add(Component.literal("Ore: " + translateVeinName(chunkInfo.mWarforgeVein.translationKey,
                                I18n.get(chunkInfo.mOreQuality.getTranslationKey()) + " [" +
                                        chunkInfo.mOreQuality.getMultString(chunkInfo.mWarforgeVein) + "]")));
                    } else {
                        lines.add(Component.literal("No ores in this chunk"));
                    }
                    if (chunkInfo.canAttack) {
                        Integer momentumMs = WarForgeConfig.SIEGE_MOMENTUM_TIME.get(data.momentum);
                        if (momentumMs != null) {
                            lines.add(Component.literal("Attack time: " + new Time((long) momentumMs * 1000L).getFormattedTime(Time.TimeFormat.HOURS_MINUTES_SECONDS, Time.Verbality.SHORT)));
                        }
                        lines.add(Component.literal("Click to attack!"));
                    }
                    event.hoverTooltips = (event.hoverTooltips == null
                            ? com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips.empty()
                            : event.hoverTooltips).append(lines.toArray(new Component[0]));
                });

                grid.addChild(cell);
            }
        }

        body.addChild(grid);
        body.addChild(WarForgeUiTheme.text("Click an attackable chunk to begin the siege.", WarForgeUiTheme.TEXT_MUTED));

        mc.setScreen(new ModularUIScreen(ModularUI.of(UI.of(root), mc.player), Component.literal("Siege Target Map")));
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
