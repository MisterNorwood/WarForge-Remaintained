package com.flansmod.warforge.client;

import com.flansmod.warforge.common.blocks.TileEntityClaim;
import com.flansmod.warforge.server.Faction;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

public class RenderTileEntityClaim implements BlockEntityRenderer<TileEntityClaim> {

    public RenderTileEntityClaim() {
    }

    @Override
    public void render(TileEntityClaim te, float partialTicks, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (te.getFaction().equals(Faction.nullUuid)) return;
        PoleFlagRenderer.render(pose, buffers, packedOverlay, te.getLevel(), te.getBlockPos(),
                te.rotation, te.factionFlagId, te.getPoleLength(), partialTicks);
    }

    @Override
    public boolean shouldRenderOffScreen(TileEntityClaim te) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return Integer.MAX_VALUE;
    }

    @Override
    public AABB getRenderBoundingBox(TileEntityClaim be) {
       BlockPos p = be.getBlockPos();
        return new AABB(p.getX() - 1, p.getY(), p.getZ() - 1, p.getX() + 2, p.getY() + 16, p.getZ() + 2);
    }
}
