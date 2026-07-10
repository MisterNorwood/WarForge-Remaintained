package com.flansmod.warforge.common.mixins;

import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.server.Faction;
import net.minecraft.block.state.BlockPistonStructureHelper;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(net.minecraft.block.BlockPistonBase.class)
public class PistonClaimBorderMixin {

    @Inject(method = "doMove", at = @At("HEAD"), cancellable = true)
    private void warforge$blockForeignPistonPush(World worldIn, BlockPos pos, EnumFacing direction, boolean extending,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (!WarForgeConfig.BLOCK_FOREIGN_PISTON_PUSH)
            return;
        if (worldIn.isRemote)
            return;

        int dim = worldIn.provider.getDimension();
        UUID pistonOwner = WarForgeMod.FACTIONS.getClaim(new DimChunkPos(dim, pos));

        BlockPistonStructureHelper helper = new BlockPistonStructureHelper(worldIn, pos, direction, extending);
        if (!helper.canMove())
            return;

        EnumFacing moveDir = extending ? direction : direction.getOpposite();

        for (BlockPos pushed : helper.getBlocksToMove()) {
            if (warforge$crossesForeignClaim(dim, pistonOwner, pushed)
                    || warforge$crossesForeignClaim(dim, pistonOwner, pushed.offset(moveDir))) {
                cir.setReturnValue(false);
                return;
            }
        }
        for (BlockPos destroyed : helper.getBlocksToDestroy()) {
            if (warforge$crossesForeignClaim(dim, pistonOwner, destroyed)) {
                cir.setReturnValue(false);
                return;
            }
        }
        if (extending && warforge$crossesForeignClaim(dim, pistonOwner, pos.offset(direction))) {
            cir.setReturnValue(false);
        }
    }

    private static boolean warforge$crossesForeignClaim(int dim, UUID pistonOwner, BlockPos pos) {
        UUID owner = WarForgeMod.FACTIONS.getClaim(new DimChunkPos(dim, pos));
        return !owner.equals(Faction.nullUuid) && !owner.equals(pistonOwner);
    }
}
