package com.flansmod.warforge.common.blocks.structure;

import com.flansmod.warforge.common.WarForgeMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

public class StructureStamper {
    public static void stampStructure(Level level, BlockPos center) {
        Map<BlockPos, BlockState> layout = FobStructureLayout.resolve(center, level);
        for (Map.Entry<BlockPos, BlockState> entry : layout.entrySet()) {
            stampBlock(level, entry.getKey(), entry.getValue());
        }
    }

    private static void stampBlock(Level level, BlockPos pos, BlockState state) {
        BlockState existing = level.getBlockState(pos);
        if (existing.getDestroySpeed(level, pos) < 0) {
            return;
        }

        if (WarForgeMod.isClaim(existing.getBlock())) {
            return;
        }

        level.setBlock(pos, state, 3);
        level.sendBlockUpdated(pos, existing, state, 3);
    }

    public static void clearStructure(Level level, BlockPos center) {
        Map<BlockPos, BlockState> layout = FobStructureLayout.resolve(center, level);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos pos : layout.keySet()) {
            if (pos.equals(center)) {
                continue;
            }

            BlockState existing = level.getBlockState(pos);
            if (existing.getDestroySpeed(level, pos) < 0) {
                continue;
            }

            if (WarForgeMod.isClaim(existing.getBlock())) {
                continue;
            }

            level.setBlock(pos, air, 3);
            level.sendBlockUpdated(pos, existing, air, 3);
        }
    }
}
