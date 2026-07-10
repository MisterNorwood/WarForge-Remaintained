package com.flansmod.warforge.common.mixins;

import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.server.Faction;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;
import java.util.UUID;

@Mixin(net.minecraft.block.BlockDynamicLiquid.class)
public class FluidClaimBorderMixin {

    @Unique
    private BlockPos warforge$currentUpdatePos;

    @Inject(method = "updateTick", at = @At("HEAD"))
    private void warforge$capturePos(World worldIn, BlockPos pos, IBlockState state, Random rand, CallbackInfo ci) {
        warforge$currentUpdatePos = pos;
    }

    @Inject(method = "tryFlowInto", at = @At("HEAD"), cancellable = true)
    private void warforge$blockForeignInflow(World worldIn, BlockPos toPos, IBlockState toState, int level, CallbackInfo ci) {
        if (!WarForgeConfig.BLOCK_FOREIGN_FLUID_INFLOW)
            return;
        if (worldIn.isRemote)
            return;
        BlockPos fromPos = warforge$currentUpdatePos;
        if (fromPos == null)
            return;
        if ((fromPos.getX() >> 4) == (toPos.getX() >> 4) && (fromPos.getZ() >> 4) == (toPos.getZ() >> 4))
            return;
        int dim = worldIn.provider.getDimension();
        UUID toOwner = WarForgeMod.FACTIONS.getClaim(new DimChunkPos(dim, toPos));
        if (toOwner.equals(Faction.nullUuid))
            return;
        UUID fromOwner = WarForgeMod.FACTIONS.getClaim(new DimChunkPos(dim, fromPos));
        if (toOwner.equals(fromOwner))
            return;
        ci.cancel();
    }
}
