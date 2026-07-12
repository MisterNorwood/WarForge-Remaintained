package com.flansmod.warforge.client;

import com.flansmod.warforge.common.blocks.PoleGeometry;
import com.flansmod.warforge.common.blocks.TileEntityClaim;
import com.flansmod.warforge.common.blocks.TileEntityDummy;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;

public class RenderTileEntityDummy implements BlockEntityRenderer<TileEntityDummy> {

    public RenderTileEntityDummy(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TileEntityDummy te, float partialTicks, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (!te.getLaserRender()) return;

        long time = te.getLevel().getGameTime();
        float[] color = te.getLaserRGB();

        double poleTop = te.getBlockPos().getY() + PoleGeometry.BASE_TRANSLATE;
        BlockPos master = te.getMasterTile();
        if (master != null) {
            float poleLength = TileEntityClaim.DEFAULT_POLE_LENGTH;
            if (te.getLevel().getBlockEntity(master) instanceof TileEntityClaim claim) {
                poleLength = claim.getPoleLength();
            }
            poleTop = master.getY() + PoleGeometry.topOffset(poleLength);
        }

        double beamStart = poleTop - te.getBlockPos().getY();
        int height = Math.max(0, Mth.ceil(257.0D - poleTop));

        pose.pushPose();
        pose.translate(0.0D, beamStart, 0.0D); // X/Z centering done inside BeaconRenderer.renderBeaconBeam
        BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, partialTicks, 1.0F, time, 0, height, color, 0.2F, 0.25F);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(TileEntityDummy te) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
