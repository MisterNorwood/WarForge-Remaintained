package com.flansmod.warforge.api.modularui;

import com.flansmod.warforge.api.ChunkDynamicTextureThread;
import com.flansmod.warforge.api.Color4i;
import com.flansmod.warforge.client.ServerTerrainCache;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class ChunkMapTextureDaemon {
    private static final ConcurrentHashMap<String, AtomicInteger> GENERATIONS = new ConcurrentHashMap<String, AtomicInteger>();
    private static final ConcurrentHashMap<String, Set<String>> ACTIVE_TEXTURES = new ConcurrentHashMap<String, Set<String>>();
    private static final ConcurrentHashMap<String, MapRequest> LAST_REQUEST = new ConcurrentHashMap<String, MapRequest>();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "WarForge-ChunkMapTextureDaemon");
        thread.setDaemon(true);
        return thread;
    });

    public static void requestMapUpdate(String namespace, int dim, int centerX, int centerZ, int radius, Map<Long, Integer> tintByChunk) {
        final HashMap<Long, Integer> tintCopy = new HashMap<Long, Integer>(tintByChunk);


        MapRequest request = new MapRequest(dim, centerX, centerZ, radius, tintCopy);
        if (request.equals(LAST_REQUEST.get(namespace))) {
            return;
        }
        LAST_REQUEST.put(namespace, request);

        final int generation = GENERATIONS.computeIfAbsent(namespace, ignored -> new AtomicInteger(0)).incrementAndGet();
        HashSet<String> desired = new HashSet<String>();
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                desired.add(getTextureName(namespace, dim, x, z));
            }
        }
        replaceActiveTextures(namespace, desired);
        EXECUTOR.submit(() -> buildTextures(namespace, generation, dim, centerX, centerZ, radius, tintCopy));
    }

    // Rebuilds the last map request for a namespace, bypassing the dedupe. Used when new data that
    // doesn't change the request key (e.g. server-sampled terrain colours) arrives and the textures
    // need to be regenerated to pick it up.
    public static void rebuildLast(String namespace) {
        MapRequest request = LAST_REQUEST.get(namespace);
        if (request == null) {
            return;
        }
        LAST_REQUEST.remove(namespace);
        requestMapUpdate(namespace, request.dim(), request.centerX(), request.centerZ(), request.radius(), request.tints());
    }

    public static void flushTextureQueue() {
        if (ChunkDynamicTextureThread.PENDING.isEmpty()) {
            return;
        }
        for (String name : new ArrayList<>(ChunkDynamicTextureThread.PENDING.keySet())) {
            ChunkDynamicTextureThread.RegisterTextureAction action = ChunkDynamicTextureThread.PENDING.remove(name);
            if (action != null) {
                action.register();
            }
        }
    }

    public static String getTextureName(String namespace, int dim, int x, int z) {
        return namespace + "/" + dim + "_" + x + "_" + z;
    }

    public static void releaseNamespace(String namespace) {
        LAST_REQUEST.remove(namespace);
        Set<String> active = ACTIVE_TEXTURES.remove(namespace);
        if (active == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        for (String name : active) {
            ChunkDynamicTextureThread.PENDING.remove(name);
            mc.getTextureManager().deleteTexture(new ResourceLocation(com.flansmod.warforge.Tags.MODID, name));
        }
    }

    public static void releaseAll() {
        HashSet<String> namespaces = new HashSet<String>(ACTIVE_TEXTURES.keySet());
        for (String namespace : namespaces) {
            releaseNamespace(namespace);
        }
        GENERATIONS.clear();
        LAST_REQUEST.clear();
    }

    private static void replaceActiveTextures(String namespace, Set<String> desired) {
        Set<String> current = ACTIVE_TEXTURES.getOrDefault(namespace, Collections.<String>emptySet());
        Minecraft mc = Minecraft.getMinecraft();
        for (String old : current) {
            if (!desired.contains(old)) {
                ChunkDynamicTextureThread.PENDING.remove(old);
                mc.getTextureManager().deleteTexture(new ResourceLocation(com.flansmod.warforge.Tags.MODID, old));
            }
        }
        ACTIVE_TEXTURES.put(namespace, desired);
    }

    private static void buildTextures(String namespace, int generation, int dim, int centerX, int centerZ, int radius, Map<Long, Integer> tintByChunk) {
        Minecraft mc = Minecraft.getMinecraft();
        WorldClient world = mc.world;
        if (world == null || world.provider.getDimension() != dim) {
            return;
        }

        HashMap<ChunkPos, Chunk> loadedChunks = new HashMap<ChunkPos, Chunk>();
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int x = centerX - radius - 1; x <= centerX + radius + 1; x++) {
            for (int z = centerZ - radius - 1; z <= centerZ + radius + 1; z++) {
                Chunk chunk = world.getChunkProvider().getLoadedChunk(x, z);
                if (chunk == null) {
                    // Fold server-supplied heights (if any) into the relief range so remote terrain
                    // shades against the same min/max as loaded chunks.
                    for (int lx = 0; lx < 16; lx++) {
                        for (int lz = 0; lz < 16; lz++) {
                            int sh = ServerTerrainCache.heightAt(dim, (x << 4) + lx, (z << 4) + lz);
                            if (sh != Integer.MIN_VALUE) {
                                if (sh < minY) minY = sh;
                                if (sh > maxY) maxY = sh;
                            }
                        }
                    }
                    continue;
                }
                loadedChunks.put(chunk.getPos(), chunk);

                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int y = chunk.getHeightValue(lx, lz);
                        if (y < minY) {
                            minY = y;
                        }
                        if (y > maxY) {
                            maxY = y;
                        }
                    }
                }
            }
        }

        if (minY == Integer.MAX_VALUE) {
            minY = 0;
            maxY = 1;
        }

        if (MapBlockColorSampler.DEBUG_LOGGING) {
            com.flansmod.warforge.common.WarForgeMod.LOGGER.info(
                    "[MapRelief] dim={} grading elevation span minY={} maxY={} (span={})",
                    dim, minY, maxY, maxY - minY);
        }

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                AtomicInteger current = GENERATIONS.get(namespace);
                if (current == null || current.get() != generation) {
                    return;
                }

                int[] rawChunk17 = new int[17 * 17];
                int[] heightMap17 = new int[17 * 17];
                Integer tint = tintByChunk.get(ChunkMapUtil.key(x, z));

                for (int dz = -1; dz <= 15; dz++) {
                    for (int dx = -1; dx <= 15; dx++) {
                        int worldX = (x << 4) + dx;
                        int worldZ = (z << 4) + dz;
                        int neighborCX = worldX >> 4;
                        int neighborCZ = worldZ >> 4;
                        int localX = worldX & 15;
                        int localZ = worldZ & 15;
                        int index = (dx + 1) + (dz + 1) * 17;

                        Chunk neighbor = loadedChunks.get(new ChunkPos(neighborCX, neighborCZ));
                        if (neighbor != null) {
                            int y = neighbor.getHeightValue(localX, localZ);
                            heightMap17[index] = y;
                            IBlockState state = neighbor.getBlockState(localX, y - 1, localZ);
                            int rgb = MapBlockColorSampler.sampleColor(world, state, new BlockPos(worldX, y - 1, worldZ));
                            rawChunk17[index] = 0xFF000000 | (rgb & 0x00FFFFFF);
                        } else {
                            int serverColor = ServerTerrainCache.colorAt(dim, worldX, worldZ);
                            if (serverColor != Integer.MIN_VALUE) {
                                int serverHeight = ServerTerrainCache.heightAt(dim, worldX, worldZ);
                                heightMap17[index] = serverHeight != Integer.MIN_VALUE ? serverHeight : minY;
                                rawChunk17[index] = 0xFF000000 | (serverColor & 0x00FFFFFF);
                            } else if (tint != null) {
                                heightMap17[index] = minY;
                                rawChunk17[index] = 0xFF000000 | (tint & 0x00FFFFFF);
                            } else {
                                heightMap17[index] = minY;
                                rawChunk17[index] = 0xFF2A2A2A;
                            }
                        }
                    }
                }

                if (tint != null && tint != 0) {
                    int tintRgb = tint & 0x00FFFFFF;
                    for (int i = 0; i < rawChunk17.length; i++) {
                        Color4i base = Color4i.fromRGB(rawChunk17[i]);
                        Color4i fac = Color4i.fromRGB(tintRgb);
                        rawChunk17[i] = new Color4i(
                                255,
                                (base.getRed() * 7 + fac.getRed() * 3) / 10,
                                (base.getGreen() * 7 + fac.getGreen() * 3) / 10,
                                (base.getBlue() * 7 + fac.getBlue() * 3) / 10
                        ).toRGB();
                    }
                }

                new ChunkDynamicTextureThread(
                        4,
                        getTextureName(namespace, dim, x, z),
                        rawChunk17,
                        heightMap17,
                        maxY,
                        minY
                ).run();
            }
        }
    }

    /**
     * Identifies a map build request so identical re-evaluations can be skipped.
     */
        private record MapRequest(int dim, int centerX, int centerZ, int radius, HashMap<Long, Integer> tints) {

        @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (!(o instanceof MapRequest)) {
                    return false;
                }
                MapRequest other = (MapRequest) o;
                return dim == other.dim
                        && centerX == other.centerX
                        && centerZ == other.centerZ
                        && radius == other.radius
                        && tints.equals(other.tints);
            }

    }
}
