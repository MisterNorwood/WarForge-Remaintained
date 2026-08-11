package com.flansmod.warforge.common.factories;

import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.blocks.TileEntityCitadel;
import com.flansmod.warforge.common.network.PacketFactionInfo;
import com.flansmod.warforge.common.network.PacketOpenCreateFaction;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.server.Faction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public final class CitadelGuiFactory {
    public static final CitadelGuiFactory INSTANCE = new CitadelGuiFactory();

    private CitadelGuiFactory() {
    }

    public static void init() {
    }

    public void open(Player player, BlockPos pos) {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        BlockEntity be = sp.level().getBlockEntity(pos);
        if (!(be instanceof TileEntityCitadel citadel)) {
            return;
        }
        WarForgeMod.syncClaimToPlayer(player, pos);
        com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType.openUI(sp, pos);
    }

    @OnlyIn(Dist.CLIENT)
    public void openClient(BlockPos pos) {
    }
}
