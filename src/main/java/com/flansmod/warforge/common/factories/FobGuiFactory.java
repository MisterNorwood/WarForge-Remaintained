package com.flansmod.warforge.common.factories;

import com.flansmod.warforge.common.util.DimBlockPos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public final class FobGuiFactory {
    public static final FobGuiFactory INSTANCE = new FobGuiFactory();

    private FobGuiFactory() {
    }

    public static void init() {
    }

    public void open(Player player, BlockPos pos) {
    }
}
