package com.flansmod.warforge.common.factories;

import com.flansmod.warforge.client.ui.ClaimManagerScreen;
import com.flansmod.warforge.common.util.DimChunkPos;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public final class ClaimManagerGuiFactory {
    public static final ClaimManagerGuiFactory INSTANCE = new ClaimManagerGuiFactory();

    public static boolean siegeTargetMode = false;
    public static DimChunkPos siegeStartPickFor = null;

    public static boolean isRemoteSiegeView() {
        return siegeStartPickFor != null;
    }

    public static void resetSiegeState() {
        siegeTargetMode = false;
        siegeStartPickFor = null;
    }

    private ClaimManagerGuiFactory() {
    }

    public static void init() {
    }

    public void open(Player player, DimChunkPos center, int radius, int pageX, int pageZ) {
    }

    @OnlyIn(Dist.CLIENT)
    public void openClient(DimChunkPos center, int radius, int pageX, int pageZ) {
        ClaimManagerScreen.open(center, radius, pageX, pageZ);
    }
}
