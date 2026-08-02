package com.flansmod.warforge.client;

import com.flansmod.warforge.common.blocks.TileEntityFob;
import com.flansmod.warforge.server.Faction;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;

public class RenderTileEntityFob implements BlockEntityRenderer<TileEntityFob> {

    public RenderTileEntityFob() {
    }

    @Override
    public void render(TileEntityFob te, float partialTicks, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (te.ownerFaction.equals(Faction.nullUuid)) return;
        PoleFlagRenderer.render(pose, buffers, packedOverlay, te.getLevel(), te.getBlockPos(),
                0, te.factionFlagId, te.getPoleLength(), partialTicks);
    }

    @Override
    public boolean shouldRenderOffScreen(TileEntityFob te) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return Integer.MAX_VALUE;
    }
}
