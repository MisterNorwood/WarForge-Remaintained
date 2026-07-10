package com.flansmod.warforge.client;

import com.flansmod.warforge.common.ProtectionsModule;
import com.flansmod.warforge.common.WarForgeConfig.ProtectionConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.util.DimChunkPos;
import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class ClientPlacementPredictor {

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        World world = event.getWorld();
        if (!world.isRemote)
            return;
        if (event.getUseItem() == Event.Result.DENY)
            return;

        if (ProtectionsModule.OP_OVERRIDE && WarForgeMod.isOp(event.getEntityPlayer()))
            return;

        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof ItemBlock))
            return;

        ItemBlock blockItem = (ItemBlock) stack.getItem();
        BlockPos placePos = event.getPos().offset(event.getFace());
        ChunkPos cp = new ChunkPos(placePos);
        DimChunkPos chunkPos = new DimChunkPos(world.provider.getDimension(), cp.x, cp.z);

        ProtectionConfig config = ClientProtections.configFor(chunkPos);
        if (config == null)
            return;

        Block placedBlock = blockItem.getBlock();
        if (ProtectionsModule.placeDenied(config, placedBlock))
            event.setUseItem(Event.Result.DENY);
    }
}
