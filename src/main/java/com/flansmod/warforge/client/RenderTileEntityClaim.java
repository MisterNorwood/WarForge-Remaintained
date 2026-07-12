package com.flansmod.warforge.client;

import com.flansmod.warforge.common.blocks.TileEntityClaim;
import com.flansmod.warforge.server.Faction;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;

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
}
