package com.flansmod.warforge.client;

import com.flansmod.warforge.common.MineTime;
import com.flansmod.warforge.common.ProtectionsModule;
import com.flansmod.warforge.common.WarForgeConfig.ProtectionConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.util.DimChunkPos;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ClientMineTimePredictor {

    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        net.minecraft.entity.player.EntityPlayer player = event.getEntityPlayer();
        if (!player.world.isRemote)
            return;
        if (player.capabilities.isCreativeMode)
            return;
        if (ProtectionsModule.OP_OVERRIDE && WarForgeMod.isOp(player))
            return;

        BlockPos blockPos = event.getPos();
        if (blockPos == null)
            return;
        if (event.getState().getBlockHardness(player.world, blockPos) <= 0)
            return;

        ChunkPos cp = new ChunkPos(blockPos);
        DimChunkPos chunkPos = new DimChunkPos(player.dimension, cp.x, cp.z);

        ProtectionConfig config = ClientProtections.configFor(chunkPos);
        if (config == null)
            return;

        Block block = event.getState().getBlock();
        if (!ProtectionsModule.breakDenied(config, block))
            return;

        MineTime.Rule rule = config.mineTime.resolve(block);
        if (rule == null)
            return;

        event.setNewSpeed(MineTime.applySpeed(rule, event.getNewSpeed(), event.getState(), player.world, blockPos, player));
    }
}
