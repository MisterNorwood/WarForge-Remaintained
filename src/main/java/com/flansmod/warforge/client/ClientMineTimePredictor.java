package com.flansmod.warforge.client;

import com.flansmod.warforge.common.MineTime;
import com.flansmod.warforge.common.ProtectionsModule;
import com.flansmod.warforge.common.WarForgeConfig.ProtectionConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.util.DimChunkPos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Client mirror of {@link ProtectionsModule}'s server BreakSpeed handler. The server stays
 * authoritative; this only predicts the MineTime slow-down so survival mining of a protected block
 * does not rubber-band. It never blocks a break outright — the client cannot perfectly resolve
 * ally/siege/defended zones, so a hard cancel here could be a false positive. Hard protection remains a
 * server-side {@code BlockEvent.BreakEvent} cancel; over-predicting a slow-down (and never a block) is
 * the only error this can make, and the server corrects it.
 */
public class ClientMineTimePredictor {

    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (!level.isClientSide)
            return;
        if (player.getAbilities().instabuild)
            return;
        if (ProtectionsModule.OP_OVERRIDE && WarForgeMod.isOp(player))
            return;

        BlockPos blockPos = event.getPosition().orElse(null);
        if (blockPos == null)
            return;

        ChunkPos cp = new ChunkPos(blockPos);
        DimChunkPos chunkPos = new DimChunkPos(level.dimension(), cp.x, cp.z);

        ProtectionConfig config = ClientProtections.configFor(chunkPos);
        if (config == null)
            return; // unknown ownership: defer to the server

        Block block = event.getState().getBlock();
        if (!ProtectionsModule.breakDenied(config, block))
            return;

        MineTime.Rule rule = config.mineTime.resolve(block);
        if (rule == null)
            return; // hard-protected: leave to the server cancel
        if (event.getState().getDestroySpeed(level, blockPos) <= 0)
            return; // instant-break: cannot be paced

        event.setNewSpeed(MineTime.applySpeed(rule, event.getNewSpeed(), event.getState(), level, blockPos, player));
    }
}
