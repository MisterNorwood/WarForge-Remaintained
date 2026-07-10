package com.flansmod.warforge.api;

import com.flansmod.warforge.Tags;
import lombok.RequiredArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkDynamicTextureThread extends Thread {
    public static final Map<String, RegisterTextureAction> PENDING = new ConcurrentHashMap<>();
    final int[] rawChunk;
    final int[] heightMapCopy;
    final int maxHeight;
    final int minHeight;
    int scale;
    String name;

    public ChunkDynamicTextureThread(int scale, String name, int[] rawChunk1, int[] heightMapCopy1, int maxHeight, int minHeight) {
        this.scale = scale;
        this.name = name;
        this.rawChunk = rawChunk1;
        this.heightMapCopy = heightMapCopy1;
        this.maxHeight = maxHeight;
        this.minHeight = minHeight;
    }

    public static int[] scaleRGBAArray(int[] originalPixels, int originalWidth, int originalHeight, int scale) {
        int newWidth = originalWidth * scale;
        int newHeight = originalHeight * scale;
        int[] scaledPixels = new int[newWidth * newHeight];

        for (int y = 0; y < originalHeight; y++) {
            for (int x = 0; x < originalWidth; x++) {
                int color = originalPixels[x + y * originalWidth];

                int startX = x * scale;
                int startY = y * scale;

                for (int dy = 0; dy < scale; dy++) {
                    for (int dx = 0; dx < scale; dx++) {
                        int scaledIndex = (startX + dx) + (startY + dy) * newWidth;
                        scaledPixels[scaledIndex] = color;
                    }
                }
            }
        }

        return scaledPixels;
    }

    public static void applyShadingWithHeight(int[] rawChunk, int[] heightMap, int width, int height) {
        int[] shaded = new int[rawChunk.length];

        // Light from northwest (toward +X and +Z)
        Vec3d lightDir = new Vec3d(-1, 1, -1).normalize();

        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int idx = x + z * width;

                int baseColor = rawChunk[idx];

                // Sample neighboring heights for central difference
                int hL = heightMap[(x > 0 ? x - 1 : x) + z * width];                  // West
                int hR = heightMap[(x < width - 1 ? x + 1 : x) + z * width];         // East
                int hU = heightMap[x + (z > 0 ? z - 1 : z) * width];                 // North
                int hD = heightMap[x + (z < height - 1 ? z + 1 : z) * width];        // South

                // Compute normal vector
                double dx = hR - hL;
                double dz = hD - hU;
                float exaggeration = 1.5f;

                Vec3d normal = new Vec3d(-dx, exaggeration, -dz).normalize(); // 2.0 exaggerates vertical steepness

                // Lambertian reflectance
                float shade = (float) normal.dotProduct(lightDir);
                shade = Math.max(0.0f, Math.min(1.0f, shade));

                float brightness = 0.5f + (float) Math.pow(shade, exaggeration) * 0.5f;

                shaded[idx] = new Color4i(baseColor)
                        .withGammaBrightness(brightness, false)
                        .toRGB();
            }
        }

        System.arraycopy(shaded, 0, rawChunk, 0, rawChunk.length);
    }


    @Override
    public void run() {
        int padded = 17;
        int paddedScaled = padded * scale;

        applyHeightMap(rawChunk, heightMapCopy);

        int[] scaled = scaleRGBAArray(rawChunk, padded, padded, scale);

        applyShadingWithHeight(scaled, scaleRGBAArray(heightMapCopy, 17, 17, 4), paddedScaled, paddedScaled);

        // Crop 16×16 center
        int[] finalBuffer = new int[16 * scale * 16 * scale];
        for (int z = 0; z < 16 * scale; z++) {
            int srcOffset = (z + scale) * paddedScaled + scale;
            int dstOffset = z * 16 * scale;
            System.arraycopy(scaled, srcOffset, finalBuffer, dstOffset, 16 * scale);
        }

        // Final image
        BufferedImage image = new BufferedImage(16 * scale, 16 * scale, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 16 * scale, 16 * scale, finalBuffer, 0, 16 * scale);
        PENDING.put(name, new RegisterTextureAction(image, name));
    }


    public void applyHeightMap(int[] colorBuffer, int[] heightMap) {
        int span = maxHeight - minHeight;
        if (span <= 0) {
            for (int i = 0; i < colorBuffer.length; i++) {
                colorBuffer[i] = Color4i.fromRGB(colorBuffer[i]).withHSVBrightness(0.8f).toRGB();
            }
            return;
        }

        float logSpan = (float) Math.log(span + 1);
        for (int i = 0; i < colorBuffer.length; i++) {
            int delta = heightMap[i] - minHeight;
            if (delta < 0) {
                delta = 0;
            } else if (delta > span) {
                delta = span;
            }
            float normalized = (float) Math.log(delta + 1) / logSpan;
            float brightness = 0.6f + normalized * 0.4f;

            colorBuffer[i] = Color4i.fromRGB(colorBuffer[i])
                    .withHSVBrightness(brightness)
                    .toRGB();
        }
    }

    @RequiredArgsConstructor
    public static class RegisterTextureAction {
        final BufferedImage mapTexture;
        final String name;

        public void register() {
            Minecraft.getMinecraft().getTextureManager().deleteTexture(
                    new ResourceLocation(Tags.MODID, name)
            );
            Minecraft.getMinecraft().getTextureManager().loadTexture(
                    new ResourceLocation(Tags.MODID, name),
                    new DynamicTexture(mapTexture)
            );
        }


    }
}
