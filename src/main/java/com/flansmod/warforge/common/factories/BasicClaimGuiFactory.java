package com.flansmod.warforge.common.factories;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public final class BasicClaimGuiFactory {
    public static final BasicClaimGuiFactory INSTANCE = new BasicClaimGuiFactory();

    private BasicClaimGuiFactory() { }

    public static void init() { }

    public void open(Player player, BlockPos pos) { }

    @OnlyIn(Dist.CLIENT)
    public void openClient(BlockPos pos) { }
}
