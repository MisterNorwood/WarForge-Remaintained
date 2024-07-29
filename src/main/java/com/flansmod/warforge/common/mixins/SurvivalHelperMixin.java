package com.flansmod.warforge.common.mixins;

import com.flansmod.warforge.common.DimBlockPos;
import com.flansmod.warforge.common.ProtectionsModule;
import com.flansmod.warforge.common.WarForgeConfig.ProtectionConfig;
import com.flansmod.warforge.common.WarForgeMod;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import nl.requios.effortlessbuilding.helper.SurvivalHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SurvivalHelper.class)
public class SurvivalHelperMixin {
    @Inject(method = "placeBlock", at = @At("HEAD"), remap = false, cancellable = true)
    private static void PlaceBlockMixin(World world, EntityPlayer player, BlockPos pos, IBlockState blockState,
                                        ItemStack origstack, EnumFacing facing, Vec3d hitVec, boolean skipPlaceCheck,
                                        boolean skipCollisionCheck, boolean playSound,
                                        CallbackInfoReturnable<Boolean> cir) {

        if (ProtectionsModule.OP_OVERRIDE && WarForgeMod.IsOp(player)) return;

        DimBlockPos dimPos = new DimBlockPos(player.dimension, pos);
        ProtectionConfig config = ProtectionsModule.GetProtections(player.getUniqueID(), dimPos);

        if (!config.PLACE_BLOCKS) {
            if (!config.BLOCK_PLACE_EXCEPTIONS.contains(blockState.getBlock())) {
                cir.setReturnValue(false);
            }
        }
    }
}
