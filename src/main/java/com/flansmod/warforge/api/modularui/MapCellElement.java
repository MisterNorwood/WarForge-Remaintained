package com.flansmod.warforge.api.modularui;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.api.Color4i;
import com.flansmod.warforge.common.network.ClaimChunkRenderInfo;
import com.flansmod.warforge.common.network.SiegeCampAttackInfoRender;
import com.flansmod.warforge.server.Faction;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class MapCellElement extends UIElement {

    public static final int CB_THICKNESS = 2;
    public static final int HL_THICKNESS = 1;
    public static final int HL_COLOR = 0xFFC6C6C6;
    public static final int GRID_COLOR = new Color4i(0.15f, 26, 26, 26).toARGB();
    public static final int GRID_THICKNESS = HL_THICKNESS;
    public static final int OFFSET = 2;
    public static final int SIZE = 12;

    private static final int MAP_TEX_SIZE = 64;

    private static final int ATTACK_ICON_SIZE = 46;
    private static final int SELF_ICON_SIZE = 480;
    private static final int CONQUERED_TEX_SIZE = 64;
    private static final int VEIN_TEX_SIZE = 16;
    private static final int CENTER_CUSTOM_ICON_SIZE = 64;

    private final String mapData;
    private final SiegeCampAttackInfoRender chunkState;
    private final boolean[] adjacency;
    private final boolean campChunk;
    private final float[] rgb;

    private final ResourceLocation attackIcon = ResourceLocation.fromNamespaceAndPath(Tags.MODID, "gui/icon_siege_attack.png");
    private final ResourceLocation selfIcon = ResourceLocation.fromNamespaceAndPath(Tags.MODID, "gui/icon_siege_self.png");
    private final ResourceLocation selfIconBase = ResourceLocation.fromNamespaceAndPath(Tags.MODID, "gui/icon_siege_self_base.png");
    private final ResourceLocation conqueredOverlay = ResourceLocation.fromNamespaceAndPath(Tags.MODID, "gui/conquered.png");

    public Consumer<Integer> onClick = null;

    public MapCellElement(String mapData, SiegeCampAttackInfoRender chunkState, boolean[] adjacency) {
        this.mapData = mapData;
        this.chunkState = chunkState;
        this.adjacency = adjacency;
        this.campChunk = chunkState.mOffset.getZ() == 0 && chunkState.mOffset.getX() == 0;
        this.rgb = Color4i.fromRGB(chunkState.mFactionColour).asFloatRGB();

        addEventListener(UIEvents.CLICK, (UIEventListener) event -> {
            if (campChunk && !chunkState.canAttack) {
                return;
            }
            if (onClick != null) {
                onClick.accept(event.button);
            }
        });
    }

    @Override
    public void drawContents(GUIContext ctx) {
        super.drawContents(ctx);

        GuiGraphics graphics = ctx.graphics;
        int x = (int) getPositionX();
        int y = (int) getPositionY();
        int width = (int) getSizeWidth();
        int height = (int) getSizeHeight();
        boolean hovered = isHover();

        ResourceLocation mapTexture = ResourceLocation.fromNamespaceAndPath(Tags.MODID, mapData);

        RenderSystem.enableBlend();

        if (!hovered || chunkState.mFactionUUID.equals(Faction.nullUuid)) {
            RenderSystem.setShaderColor(0.9f, 0.9f, 0.9f, 1f);
        } else {
            RenderSystem.setShaderColor(rgb[0], rgb[1], rgb[2], 1f);
        }

        AbstractTexture loadedTexture = Minecraft.getInstance().getTextureManager().getTexture(mapTexture, null);
        if (loadedTexture instanceof DynamicTexture) {
            graphics.blit(mapTexture, x, y, width, height, 0f, 0f, MAP_TEX_SIZE, MAP_TEX_SIZE, MAP_TEX_SIZE, MAP_TEX_SIZE);
        } else {
            graphics.fill(x, y, x + width, y + height, 0xFF2A2A2A);
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        boolean conquered = chunkState.conquered;
        boolean battleZone = chunkState instanceof ClaimChunkRenderInfo claimInfo && claimInfo.battleZone;
        if (conquered || battleZone) {
            float alpha = conquered && battleZone ? 0.65f : 0.5f;
            RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
            graphics.blit(conqueredOverlay, x, y, width, height, 0f, 0f, CONQUERED_TEX_SIZE, CONQUERED_TEX_SIZE, CONQUERED_TEX_SIZE, CONQUERED_TEX_SIZE);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }

        if (!chunkState.mFactionUUID.equals(Faction.nullUuid)) {
            int baseNoAlpha = chunkState.mFactionColour & 0x00FFFFFF;
            int base = baseNoAlpha | 0xFF000000;
            int lightColor = brighten(base);
            int darkColor = darken(base);

            graphics.fill(x, y, x + width + 1, y + height + 1, baseNoAlpha | 0x20_000000);

            if (adjacency[0])
                graphics.fill(x, y, x + width + 1, y + CB_THICKNESS, lightColor);
            if (adjacency[3])
                graphics.fill(x, y + CB_THICKNESS - 2, x + CB_THICKNESS, y + height, lightColor);

            if (adjacency[2])
                graphics.fill(x, y + height - CB_THICKNESS, x + width, y + height, darkColor);
            if (adjacency[1])
                graphics.fill(x + width - CB_THICKNESS, y, x + width, y + height, darkColor);
        } else {
            graphics.fill(x, y, x + width + 1, y + GRID_THICKNESS, GRID_COLOR);
            graphics.fill(x, y + GRID_THICKNESS - 2, x + GRID_THICKNESS, y + height, GRID_COLOR);
            graphics.fill(x, y + height - GRID_THICKNESS, x + width, y + height, GRID_COLOR);
            graphics.fill(x + width - GRID_THICKNESS, y, x + width, y + height, GRID_COLOR);
        }

        if (hovered) {
            if (chunkState.canAttack && !campChunk) {
                RenderSystem.setShaderColor(rgb[0], rgb[1], rgb[2], 1f);
                int xOffset = x + (width - 46) / 2;
                int yOffset = y + (height - 46) / 2;
                graphics.blit(attackIcon, xOffset, yOffset, 46, 46, 0f, 0f, ATTACK_ICON_SIZE, ATTACK_ICON_SIZE, ATTACK_ICON_SIZE, ATTACK_ICON_SIZE);
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            } else {
                graphics.fill(x, y, x + width + 1, y + HL_THICKNESS, HL_COLOR);
                graphics.fill(x, y + HL_THICKNESS - 2, x + HL_THICKNESS, y + height, HL_COLOR);
                graphics.fill(x, y + height - HL_THICKNESS, x + width, y + height, HL_COLOR);
                graphics.fill(x + width - HL_THICKNESS, y, x + width, y + height, HL_COLOR);
            }
        }

        switch (chunkState.getCenterMarkType()) {
            case SIEGE_CAMP -> {
                int xOffset = x + (width - 48) / 2;
                int yOffset = y + (height - 48) / 2;

                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                graphics.blit(selfIconBase, xOffset, yOffset, 48, 48, 0f, 0f, SELF_ICON_SIZE, SELF_ICON_SIZE, SELF_ICON_SIZE, SELF_ICON_SIZE);

                RenderSystem.setShaderColor(rgb[0], rgb[1], rgb[2], 1f);
                graphics.blit(selfIcon, xOffset, yOffset, 48, 48, 0f, 0f, SELF_ICON_SIZE, SELF_ICON_SIZE, SELF_ICON_SIZE, SELF_ICON_SIZE);
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            }
            case PLAYER_FACE -> {
                if (chunkState.getCenterIcon() == null) break;
                int xOffset = x + (width - 24) / 2;
                int yOffset = y + (height - 24) / 2;
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                PlayerFaceRenderer.draw(graphics, chunkState.getCenterIcon(), xOffset, yOffset, 24);
            }
            case CUSTOM_TEXTURE -> {
                if (chunkState.getCenterIcon() == null) break;
                int xOffset = x + (width - 24) / 2;
                int yOffset = y + (height - 24) / 2;
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                graphics.blit(chunkState.getCenterIcon(), xOffset, yOffset, 24, 24, 0f, 0f, CENTER_CUSTOM_ICON_SIZE, CENTER_CUSTOM_ICON_SIZE, CENTER_CUSTOM_ICON_SIZE, CENTER_CUSTOM_ICON_SIZE);
            }
            case NONE -> {
            }
        }

        if (chunkState.veinIcon != null) {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            graphics.blit(chunkState.veinIcon, x + OFFSET, y + OFFSET, SIZE, SIZE, 0f, 0f, VEIN_TEX_SIZE, VEIN_TEX_SIZE, VEIN_TEX_SIZE, VEIN_TEX_SIZE);
        }

        if (chunkState instanceof ClaimChunkRenderInfo claimInfo && claimInfo.claimType != Faction.ClaimType.NONE) {
            Font font = Minecraft.getInstance().font;
            String label = claimInfo.claimType.shortLabel;
            if (!label.isEmpty()) {
                graphics.drawString(font, label, x + 2, y + height - 10, 0xFFFFFFFF, true);
            }
            if (claimInfo.forceLoaded) {
                graphics.drawString(font, "[F]", x + width - 20, y + height - 10, 0xFFFFFFFF, true);
            }
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private int brighten(int color) {
        int r = Math.min(((color >> 16) & 0xFF) + 16, 255);
        int g = Math.min(((color >> 8) & 0xFF) + 16, 255);
        int b = Math.min((color & 0xFF) + 16, 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private int darken(int color) {
        int r = Math.max(((color >> 16) & 0xFF) - 16, 0);
        int g = Math.max(((color >> 8) & 0xFF) - 16, 0);
        int b = Math.max((color & 0xFF) - 16, 0);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
