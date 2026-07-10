package com.flansmod.warforge.client.util;

import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.Tags;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SideOnly(Side.CLIENT)
/**
 * System that parses and caches player's skins
 */
public class SkinUtil {
    private static final Map<UUID, ResourceLocation> parsedSkinCache = new ConcurrentHashMap<>();
    private static final ResourceLocation DEFAULT_FACE = new ResourceLocation(Tags.MODID, "textures/gui/default_face.png");
    static Minecraft mc = Minecraft.getMinecraft();
    static SkinManager mcSkinManager = mc.getSkinManager();


    public static ResourceLocation getPlayerFace(UUID uuid) {
        ResourceLocation loc = parsedSkinCache.get(uuid);
        if (loc != null) return loc;
        generatePlayerFace(uuid);
        return DEFAULT_FACE; // temporary placeholder
    }

    private static void generatePlayerFace(UUID uuid) {
        var playerGameProfile = new GameProfile(uuid, null);
        mcSkinManager.loadProfileTextures(playerGameProfile, (typeIn, location, profileTexture) -> processSkin(uuid, typeIn, location, profileTexture), true);
    }

    private static void processSkin(UUID uuid, MinecraftProfileTexture.Type typeIn, ResourceLocation location, MinecraftProfileTexture profileTexture) {
        if (typeIn == MinecraftProfileTexture.Type.SKIN) {
            ITextureObject texture = mc.getTextureManager().getTexture(location);
            if (texture instanceof ThreadDownloadImageData imageData) {
                var skinImage = imageData.bufferedImage;
                if (skinImage == null) return;
                var face = skinImage.getSubimage(8, 8, 8, 8); // front face
                var faceOverlay = skinImage.getSubimage(40, 8, 8, 8); // hat layer
                BufferedImage combined = overlayImages(face, faceOverlay);
                var loc = new ResourceLocation(Tags.MODID, "player_face_" + uuid);
                mc.addScheduledTask(() -> {
                    mc.getTextureManager().loadTexture(loc, new DynamicTexture(combined));
                    parsedSkinCache.put(uuid, loc);
                });
            }
        }

    }

    public static void drawFace(GuiContext context, UUID uuid, int x, int y, int size) {
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        ResourceLocation face = getPlayerFace(uuid);
        mc.getTextureManager().bindTexture(face);
        Gui.drawScaledCustomSizeModalRect(x, y, 0, 0, 8, 8, size, size, 8, 8);
    }

    public static BufferedImage overlayImages(BufferedImage base, BufferedImage overlay) {
        int width = Math.max(base.getWidth(), overlay.getWidth());
        int height = Math.max(base.getHeight(), overlay.getHeight());
        BufferedImage combined = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = combined.createGraphics();
        g.drawImage(base, 0, 0, null);
        g.drawImage(overlay, 0, 0, null);
        g.dispose();
        return combined;
    }
}

