package com.flansmod.warforge.client.util;

import com.flansmod.warforge.Tags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public final class LayeredItemIconCache {
    private static final ConcurrentHashMap<String, ResourceLocation> CACHE = new ConcurrentHashMap<>();

    private LayeredItemIconCache() {
    }

    @Nullable
    public static ResourceLocation getIcon(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        String key = makeKey(stack);
        ResourceLocation cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        BufferedImage baked = bakeIcon(stack);
        if (baked == null) {
            return null;
        }

        NativeImage native_ = toNativeImage(baked);
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(Tags.MODID, "generated/vein/" + Integer.toUnsignedString(key.hashCode()));
        mc.getTextureManager().register(location, new DynamicTexture(native_));
        CACHE.put(key, location);
        return location;
    }

    public static void clear() {
        Minecraft mc = Minecraft.getInstance();
        for (ResourceLocation location : CACHE.values()) {
            mc.getTextureManager().release(location);
        }
        CACHE.clear();
    }

    @Nullable
    private static BufferedImage bakeIcon(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getItemRenderer().getModel(stack, null, null, 0);
        List<Layer> layers = extractLayers(model, stack);
        if (layers.isEmpty()) {
            return null;
        }

        int width = 16;
        int height = 16;
        BufferedImage combined = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (Layer layer : layers) {
            BufferedImage spriteImage = spriteToImage(layer.sprite);
            if (spriteImage == null) {
                continue;
            }
            BufferedImage scaled = scaleToIcon(spriteImage, width, height);
            composite(combined, scaled, layer.tint);
        }

        return combined;
    }

    private static List<Layer> extractLayers(BakedModel model, ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        RandomSource rand = RandomSource.create();
        List<Layer> ordered = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        collectLayers(ordered, seen, model.getQuads(null, null, rand), stack, mc);
        for (Direction facing : Direction.values()) {
            collectLayers(ordered, seen, model.getQuads(null, facing, rand), stack, mc);
        }

        TextureAtlasSprite particle = model.getParticleIcon();
        if (ordered.isEmpty() && particle != null) {
            ordered.add(new Layer(particle, 0xFFFFFFFF));
        }
        return ordered;
    }

    private static void collectLayers(List<Layer> ordered, Set<String> seen, List<BakedQuad> quads, ItemStack stack, Minecraft mc) {
        for (BakedQuad quad : quads) {
            if (quad == null || quad.getSprite() == null) {
                continue;
            }
            int tint = (quad.getTintIndex() != -1) ? mc.getItemColors().getColor(stack, quad.getTintIndex()) : 0xFFFFFFFF;
            if ((tint >>> 24) == 0) {
                tint |= 0xFF000000;
            }
            String key = quad.getSprite().contents().name().toString() + "|" + Integer.toUnsignedString(tint);
            if (seen.add(key)) {
                ordered.add(new Layer(quad.getSprite(), tint));
            }
        }
    }

    @Nullable
    private static BufferedImage spriteToImage(TextureAtlasSprite sprite) {
        SpriteContents contents = sprite.contents();
        int width = contents.width();
        int height = contents.height();
        if (width <= 0 || height <= 0) {
            return null;
        }

        NativeImage image = contents.getOriginalImage();
        if (image == null) {
            return null;
        }

        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // NativeImage stores pixels as ABGR; convert to ARGB for BufferedImage
                int abgr = image.getPixelRGBA(x, y);
                int a = (abgr >>> 24) & 0xFF;
                int b = (abgr >>> 16) & 0xFF;
                int g = (abgr >>> 8) & 0xFF;
                int r = abgr & 0xFF;
                buffered.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return buffered;
    }

    private static BufferedImage scaleToIcon(BufferedImage image, int width, int height) {
        if (image.getWidth() == width && image.getHeight() == height) {
            return image;
        }

        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(image, 0, 0, width, height, null);
        graphics.dispose();
        return scaled;
    }

    private static NativeImage toNativeImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        NativeImage native_ = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // BufferedImage is ARGB; NativeImage.setPixelRGBA expects ABGR
                int argb = image.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                native_.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        return native_;
    }

    private static void composite(BufferedImage target, BufferedImage layer, int tint) {
        float tintA = ((tint >>> 24) & 0xFF) / 255.0f;
        int tintR = (tint >>> 16) & 0xFF;
        int tintG = (tint >>> 8) & 0xFF;
        int tintB = tint & 0xFF;

        for (int y = 0; y < target.getHeight(); y++) {
            for (int x = 0; x < target.getWidth(); x++) {
                int src = layer.getRGB(x, y);
                int srcA = (src >>> 24) & 0xFF;
                if (srcA == 0) {
                    continue;
                }

                float srcAlpha = (srcA / 255.0f) * tintA;
                int srcR = (((src >>> 16) & 0xFF) * tintR) / 255;
                int srcG = (((src >>> 8) & 0xFF) * tintG) / 255;
                int srcB = ((src & 0xFF) * tintB) / 255;

                int dst = target.getRGB(x, y);
                float dstAlpha = ((dst >>> 24) & 0xFF) / 255.0f;
                float outAlpha = srcAlpha + dstAlpha * (1.0f - srcAlpha);
                if (outAlpha <= 0.0f) {
                    target.setRGB(x, y, 0);
                    continue;
                }

                int dstR = (dst >>> 16) & 0xFF;
                int dstG = (dst >>> 8) & 0xFF;
                int dstB = dst & 0xFF;

                int outR = Math.min(255, Math.round((srcR * srcAlpha + dstR * dstAlpha * (1.0f - srcAlpha)) / outAlpha));
                int outG = Math.min(255, Math.round((srcG * srcAlpha + dstG * dstAlpha * (1.0f - srcAlpha)) / outAlpha));
                int outB = Math.min(255, Math.round((srcB * srcAlpha + dstB * dstAlpha * (1.0f - srcAlpha)) / outAlpha));
                int outA = Math.min(255, Math.round(outAlpha * 255.0f));

                target.setRGB(x, y, (outA << 24) | (outR << 16) | (outG << 8) | outB);
            }
        }
    }

    private static String makeKey(ItemStack stack) {
        StringBuilder builder = new StringBuilder();
        builder.append(stack.getItem().builtInRegistryHolder().key().location());
        builder.append('#').append(stack.getComponentsPatch());
        return builder.toString();
    }

    private static final class Layer {
        private final TextureAtlasSprite sprite;
        private final int tint;

        private Layer(TextureAtlasSprite sprite, int tint) {
            this.sprite = sprite;
            this.tint = tint;
        }
    }
}
