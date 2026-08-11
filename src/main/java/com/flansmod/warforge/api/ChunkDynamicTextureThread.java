package com.flansmod.warforge.api;

import com.flansmod.warforge.Tags;
import com.mojang.blaze3d.platform.NativeImage;
import lombok.RequiredArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkDynamicTextureThread extends Thread {
    // Pending GL registrations keyed by texture name so a chunk that is rebuilt several times before the
    // next flush collapses to its latest pixels instead of piling up. A plain FIFO queue with a per-tick
    // cap let frequent rebuilds outrun the flush and permanently starve the tail entries (far map cells).
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
        Vec3 lightDir = new Vec3(-1, 1, -1).normalize();

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

                Vec3 normal = new Vec3(-dx, exaggeration, -dz).normalize(); // 2.0 exaggerates vertical steepness

                // Lambertian reflectance
                float shade = (float) normal.dot(lightDir);
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

        PENDING.put(name, new RegisterTextureAction(finalBuffer, 16 * scale, name));
    }


    public void applyHeightMap(int[] colorBuffer, int[] heightMap) {
        // Relief brightness is normalized against the visible elevation span, not a fixed 0..256
        // range, so it scales to 1.20.1 worlds (-64..320) and any custom dimension. A collapsed
        // span (flat / void / single-height server data) has no relief to show: grade everything
        // to the mid brightness instead of dividing by log(1)=0 and producing NaN pixels.
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

    /** Builds a NativeImage (ABGR pixels) from a square ARGB pixel array. */
    private static NativeImage toNativeImage(int[] argb, int size) {
        NativeImage nativeImage = new NativeImage(size, size, false);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int color = argb[x + y * size];
                int a = (color >>> 24) & 0xFF;
                int r = (color >>> 16) & 0xFF;
                int g = (color >>> 8) & 0xFF;
                int b = color & 0xFF;
                nativeImage.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        return nativeImage;
    }

    @RequiredArgsConstructor
    public static class RegisterTextureAction {
        final int[] argbPixels;
        final int size;
        final String name;

        public void register() {
            Minecraft mc = Minecraft.getInstance();
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(Tags.MODID, name);
            mc.getTextureManager().release(location);
            mc.getTextureManager().register(location, new DynamicTexture(toNativeImage(argbPixels, size)));
        }


    }
}
