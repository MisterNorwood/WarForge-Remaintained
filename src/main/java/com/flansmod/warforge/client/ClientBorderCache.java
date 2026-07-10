package com.flansmod.warforge.client;

import com.flansmod.warforge.common.network.ClaimChunkInfo;
import com.flansmod.warforge.common.util.DimChunkPos;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ClientBorderCache {
    private static final HashMap<DimChunkPos, ClaimChunkInfo> CHUNKS = new HashMap<>();

    public static void replaceAll(int dim, Collection<ClaimChunkInfo> chunks) {
        CHUNKS.clear();
        for (ClaimChunkInfo info : chunks) {
            CHUNKS.put(new DimChunkPos(dim, info.x, info.z), info);
        }
    }

    public static Map<DimChunkPos, ClaimChunkInfo> getChunks() {
        return CHUNKS;
    }

    public static ClaimChunkInfo get(DimChunkPos pos) {
        return CHUNKS.get(pos);
    }

    public static void clear() {
        CHUNKS.clear();
    }
}
