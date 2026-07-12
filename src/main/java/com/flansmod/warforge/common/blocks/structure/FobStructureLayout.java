package com.flansmod.warforge.common.blocks.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FobStructureLayout {
    private static final BlockState FLOOR = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState BRACKET = Blocks.SANDSTONE.defaultBlockState();
    private static final BlockState ACCENT = Blocks.RED_SANDSTONE.defaultBlockState();

    private static final int MIN = -8;
    private static final int MAX = 7;

    private static final List<int[]> FLOOR_OFFSETS = buildFloorOffsets();
    private static final List<int[]> BRACKET_OFFSETS = buildBracketOffsets();

    private static List<int[]> buildFloorOffsets() {
        List<int[]> offsets = new ArrayList<>();
        for (int dx = MIN; dx <= MAX; dx++) {
            for (int dz = MIN; dz <= MAX; dz++) {
                if (dx == 0 || dz == 0) {
                    offsets.add(new int[]{dx, dz});
                }
            }
        }
        return offsets;
    }

    private static List<int[]> buildBracketOffsets() {
        List<int[]> offsets = new ArrayList<>();
        int[][] corners = new int[][]{{MIN, MIN}, {MIN, MAX}, {MAX, MIN}, {MAX, MAX}};
        for (int[] corner : corners) {
            int cx = corner[0];
            int cz = corner[1];
            int sx = cx < 0 ? 1 : -1;
            int sz = cz < 0 ? 1 : -1;
            for (int i = 0; i < 3; i++) {
                offsets.add(new int[]{cx + sx * i, cz});
                offsets.add(new int[]{cx, cz + sz * i});
            }
        }
        return offsets;
    }

    public static Map<BlockPos, BlockState> resolve(BlockPos center, Level level) {
        Map<BlockPos, BlockState> result = new HashMap<>();

        int chunkMinX = (center.getX() >> 4) << 4;
        int chunkMinZ = (center.getZ() >> 4) << 4;
        BlockPos anchor = new BlockPos(chunkMinX - MIN, center.getY(), chunkMinZ - MIN);

        for (int[] offset : FLOOR_OFFSETS) {
            BlockPos pos = anchor.offset(offset[0], 0, offset[1]);
            if (!pos.equals(center)) {
                result.put(pos, FLOOR);
            }
        }

        for (int[] offset : BRACKET_OFFSETS) {
            BlockPos base = anchor.offset(offset[0], 0, offset[1]);
            if (!base.equals(center)) {
                result.put(base, BRACKET);
                result.put(base.above(), BRACKET);
            }
        }

        BlockPos accent = anchor.offset(0, 0, MIN);
        if (!accent.equals(center)) {
            result.put(accent, ACCENT);
        }

        return result;
    }
}
