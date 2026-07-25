package com.flansmod.warforge.api.modularui;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.api.widget.Interactable;
import brachy.modularui.drawable.GuiDraw;
import brachy.modularui.screen.viewport.GuiContext;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.utils.Color;
import com.flansmod.warforge.api.Color4i;
import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.network.SiegeCampAttackInfoRender;
import com.flansmod.warforge.common.network.ClaimChunkRenderInfo;
import com.flansmod.warforge.server.Faction;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MapDrawable implements IDrawable, Interactable {

    public final static int CB_THICKNESS = 2; //Controls border thickness
    public final static int HL_THICKNESS = 1; //Controls border thickness on highlighted chunks
    public static final int HL_COLOR = 0xFFC6C6C6;
    public static final int GRID_COLOR = new Color4i(0.15f, 26, 26, 26).toARGB();
    public static final int GRID_THICKNESS = HL_THICKNESS;
    public float[] rgb;
    public final static int OFFSET = 2;
    public final static int SIZE = 12;
    public static final boolean DEBUG = false;

    private final String mapData;
    private final SiegeCampAttackInfoRender chunkState;
    private final ResourceLocation attackIcon = new ResourceLocation(Tags.MODID, "gui/icon_siege_attack.png");
    private final ResourceLocation selfIcon = new ResourceLocation(Tags.MODID, "gui/icon_siege_self.png");
    private final ResourceLocation selfIconBase = new ResourceLocation(Tags.MODID, "gui/icon_siege_self_base.png");
    private final ResourceLocation conqueredOverlay = new ResourceLocation(Tags.MODID, "gui/conquered.png");
    private final boolean[] adjacency;
    private final boolean campChunk;

    public MapDrawable(String mapData, SiegeCampAttackInfoRender chunkState, boolean[] adjacency) {
        this.mapData = mapData;
        this.chunkState = chunkState;
        this.adjacency = adjacency;
        this.campChunk = chunkState.mOffset.getZ() == 0 && chunkState.mOffset.getX() == 0;
        rgb = Color4i.fromRGB(chunkState.mFactionColour).asFloatRGB();
    }

    public static String extractNumbers(String input) {
        StringBuilder builder = new StringBuilder();
        Matcher matcher = Pattern.compile("\\d+").matcher(input);
        while (matcher.find()) {
            builder.append(matcher.group());
        }
        return builder.toString();
    }

    @Override
    public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme widgetTheme) {
        GuiGraphics graphics = context.getGraphics();
        Matrix4f pose = context.getLastGraphicsPose();
        ResourceLocation mapTexture = new ResourceLocation(Tags.MODID, mapData);
        boolean hovered = context.getMouseX() >= x && context.getMouseX() < x + width &&
                context.getMouseY() >= y && context.getMouseY() < y + height;

        RenderSystem.enableBlend();

        if (!hovered || chunkState.mFactionUUID.equals(Faction.nullUuid)) {
            RenderSystem.setShaderColor(0.9f, 0.9f, 0.9f, 1f);
        } else {
            setShaderColor();
        }

        // Probe the texture WITHOUT the single-arg getTexture's side effect. That overload synthesizes a
        // SimpleTexture and loads it from the resource pack for any id it has not seen; for these
        // runtime-generated claim-map textures the file does not exist, so it throws FileNotFoundException,
        // spams the log, and caches a broken entry until the daemon overwrites it. There is also a race:
        // a chunk's texture can be in the daemon's desired set but not yet registered (still building off
        // -thread), or it may have just been released. The two-arg form is a pure lookup that returns the
        // registered texture or, when it is absent/stale, our null fallback — in which case we draw a
        // neutral placeholder. Only a confirmed DynamicTexture is bound for drawing, so the bind inside
        // GuiDraw.drawTexture (which itself calls the single-arg getTexture) can never hit the load path.
        AbstractTexture loadedTexture = Minecraft.getInstance().getTextureManager().getTexture(mapTexture, null);
        if (loadedTexture instanceof DynamicTexture) {
            GuiDraw.drawTexture(pose, mapTexture, x, y, x + width, y + height, 0f, 0f, 1f, 1f, true);
        } else {
            GuiDraw.drawRect(graphics, x, y, width, height, 0xFF2A2A2A);
        }
        Color.resetGlColor();

        if (DEBUG) {
            Font font = Minecraft.getInstance().font;
            String numberText = extractNumbers(mapData);
            graphics.drawString(font, numberText, x + 10, y + 10, 0xFFFFFFFF, false); // index
        }

        boolean conquered = chunkState.conquered;
        boolean battleZone = chunkState instanceof ClaimChunkRenderInfo claimInfo && claimInfo.battleZone;
        if (conquered || battleZone) {
            float alpha = conquered && battleZone ? 0.65f : 0.5f;
            RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
            GuiDraw.drawTexture(pose, conqueredOverlay, x, y, x + width, y + height, 0f, 0f, 1f, 1f, true);
            Color.resetGlColor();
        }

        if (!chunkState.mFactionUUID.equals(Faction.nullUuid)) {
            int baseNoAlpha = chunkState.mFactionColour & 0x00FFFFFF;
            int base = baseNoAlpha | 0xFF000000;
            int lightColor = brighten(base);
            int darkColor = darken(base);

            GuiDraw.drawRect(graphics, x, y, width + 1, height + 1, baseNoAlpha | 0x20_000000);

            // Top / North (light)
            if (adjacency[0])
                GuiDraw.drawRect(graphics, x, y, width + 1, CB_THICKNESS, lightColor);
            // Left / West (light)
            if (adjacency[3])
                GuiDraw.drawRect(graphics, x, y + CB_THICKNESS - 2, CB_THICKNESS, height - (CB_THICKNESS - 2), lightColor);

            // Bottom / South (dark)
            if (adjacency[2])
                GuiDraw.drawRect(graphics, x, y + height - CB_THICKNESS, width, CB_THICKNESS, darkColor);
            // Right / East (dark)
            if (adjacency[1])
                GuiDraw.drawRect(graphics, x + width - CB_THICKNESS, y, CB_THICKNESS, height, darkColor);
        } else {
            GuiDraw.drawRect(graphics, x, y, width + 1, GRID_THICKNESS, GRID_COLOR);
            GuiDraw.drawRect(graphics, x, y + GRID_THICKNESS - 2, GRID_THICKNESS, height - (GRID_THICKNESS - 2), GRID_COLOR);
            GuiDraw.drawRect(graphics, x, y + height - GRID_THICKNESS, width, GRID_THICKNESS, GRID_COLOR);
            GuiDraw.drawRect(graphics, x + width - GRID_THICKNESS, y, GRID_THICKNESS, height, GRID_COLOR);
        }
        if (hovered) {
            if (chunkState.canAttack && !campChunk) {
                setShaderColor(rgb);
                int xOffset = x + (width - 46) / 2;
                int yOffset = y + (height - 46) / 2;
                GuiDraw.drawTexture(pose, attackIcon, xOffset, yOffset, xOffset + 46, yOffset + 46, 0f, 0f, 1f, 1f, true);
                Color.resetGlColor();
            } else {
                GuiDraw.drawRect(graphics, x, y, width + 1, HL_THICKNESS, HL_COLOR);
                GuiDraw.drawRect(graphics, x, y + HL_THICKNESS - 2, HL_THICKNESS, height - (HL_THICKNESS - 2), HL_COLOR);
                GuiDraw.drawRect(graphics, x, y + height - HL_THICKNESS, width, HL_THICKNESS, HL_COLOR);
                GuiDraw.drawRect(graphics, x + width - HL_THICKNESS, y, HL_THICKNESS, height, HL_COLOR);
            }
        }
        switch (chunkState.getCenterMarkType()) {
            case SIEGE_CAMP -> {
                int xOffset = x + (width - 48) / 2;
                int yOffset = y + (height - 48) / 2;

                setShaderColor();
                GuiDraw.drawTexture(pose, selfIconBase, xOffset, yOffset, xOffset + 48, yOffset + 48, 0f, 0f, 1f, 1f, true);

                setShaderColor(rgb);
                GuiDraw.drawTexture(pose, selfIcon, xOffset, yOffset, xOffset + 48, yOffset + 48, 0f, 0f, 1f, 1f, true);
                Color.resetGlColor();
            }
            case PLAYER_FACE -> {
                if (chunkState.getCenterIcon() == null) break;
                int xOffset = x + (width - 24) / 2;
                int yOffset = y + (height - 24) / 2;
                setShaderColor();
                // centerIcon is the full skin sheet; PlayerFaceRenderer crops the head face + hat overlay.
                // (The manual GuiDraw path does not reliably draw dynamically-loaded skin textures.)
                PlayerFaceRenderer.draw(graphics, chunkState.getCenterIcon(), xOffset, yOffset, 24);
                Color.resetGlColor();
            }
            case CUSTOM_TEXTURE -> {
                if (chunkState.getCenterIcon() == null) break;
                int xOffset = x + (width - 24) / 2;
                int yOffset = y + (height - 24) / 2;
                setShaderColor();
                GuiDraw.drawTexture(pose, chunkState.getCenterIcon(), xOffset, yOffset, xOffset + 24, yOffset + 24, 0f, 0f, 1f, 1f, true);
                Color.resetGlColor();
            }
            case NONE -> {
            }
        }

        if (chunkState.veinIcon != null) {
            setShaderColor();
            GuiDraw.drawTexture(pose, chunkState.veinIcon, x + OFFSET, y + OFFSET, x + OFFSET + SIZE, y + OFFSET + SIZE, 0f, 0f, 1f, 1f, true);
            Color.resetGlColor();
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

        Color.resetGlColor();
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

    private static void setShaderColor(float[] rgb) {
        RenderSystem.setShaderColor(rgb[0], rgb[1], rgb[2], 1f);
    }

    private static void setShaderColor() {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }
}
