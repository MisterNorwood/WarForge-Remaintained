package com.flansmod.warforge.common.blocks.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

public class FobStructureLayout {
    private static final BlockState FLOOR = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState ACCENT = Blocks.RED_SANDSTONE.defaultBlockState();

    private static final int PLATFORM_MIN = 4;
    private static final int PLATFORM_MAX = 11;

    public static BlockPos centerBlock(BlockPos anyPosInChunk) {
        int chunkMinX = (anyPosInChunk.getX() >> 4) << 4;
        int chunkMinZ = (anyPosInChunk.getZ() >> 4) << 4;
        return new BlockPos(chunkMinX + 8, anyPosInChunk.getY(), chunkMinZ + 7);
    }

    public static Map<BlockPos, BlockState> resolve(BlockPos center, Level level) {
        Map<BlockPos, BlockState> result = new HashMap<>();

        int chunkMinX = (center.getX() >> 4) << 4;
        int chunkMinZ = (center.getZ() >> 4) << 4;
        int y = center.getY();

        for (int lx = PLATFORM_MIN; lx <= PLATFORM_MAX; lx++) {
            for (int lz = PLATFORM_MIN; lz <= PLATFORM_MAX; lz++) {
                BlockPos pos = new BlockPos(chunkMinX + lx, y, chunkMinZ + lz);
                if (pos.equals(center)) {
                    continue;
                }
                result.put(pos, lz == PLATFORM_MIN ? ACCENT : FLOOR);
            }
        }

        result.put(center.below(), FLOOR);

        return result;
    }
}
