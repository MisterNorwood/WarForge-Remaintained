package com.flansmod.warforge.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;

public class ClaimFlagRenderer {
    private static final double RENDER_DISTANCE_SQ = 64.0 * 64.0;
    private static final double HALF_WIDTH = 0.45;
    private static final double BANNER_BOTTOM = 1.5;
    private static final double BANNER_TOP = 2.1;

    @SubscribeEvent
    public void onRenderWorldLast(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        //TODO: Finish this, Im too lazy rn
//        Minecraft mc = Minecraft.getInstance();
//        if (mc.level == null || mc.player == null) {
//            return;
//        }
//
//        float partialTicks = event.getPartialTick().getGameTimeDeltaPartialTick(false);
//        PoseStack pose = event.getPoseStack();
//        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
//        double camX = cam.x;
//        double camY = cam.y;
//        double camZ = cam.z;
//
//        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
//
//        for (BlockEntity tile : mc.level.blockEntityList) {
//            if (!(tile instanceof TileEntityClaim claim)) {
//                continue;
//            }
//            if (claim.factionFlagId == null || claim.factionFlagId.isEmpty()) {
//                continue;
//            }
//            BlockPos pos = claim.getBlockPos();
//            double dx = pos.getX() + 0.5 - camX;
//            double dy = pos.getY() - camY;
//            double dz = pos.getZ() + 0.5 - camZ;
//            if (dx * dx + dy * dy + dz * dz > RENDER_DISTANCE_SQ) {
//                continue;
//            }
//            ResourceLocation texture = ClientFlagRegistry.getFlagTexture(claim.factionFlagId);
//            if (texture == null) {
//                continue;
//            }
//
//            pose.pushPose();
//            pose.translate(dx, dy, dz);
//            VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(texture));
//            Matrix4f mat = pose.last().pose();
//            consumer.addVertex(mat, (float) -HALF_WIDTH, (float) BANNER_TOP, 0.0F).setColor(255, 255, 255, 255).setUv(0.0F, 0.0F).setLight(packedLight).setNormal(0, 0, 1);
//            consumer.addVertex(mat, (float) -HALF_WIDTH, (float) BANNER_BOTTOM, 0.0F).setColor(255, 255, 255, 255).setUv(0.0F, 1.0F).setLight(packedLight).setNormal(0, 0, 1);
//            consumer.addVertex(mat, (float) HALF_WIDTH, (float) BANNER_BOTTOM, 0.0F).setColor(255, 255, 255, 255).setUv(1.0F, 1.0F).setLight(packedLight).setNormal(0, 0, 1);
//            consumer.addVertex(mat, (float) HALF_WIDTH, (float) BANNER_TOP, 0.0F).setColor(255, 255, 255, 255).setUv(1.0F, 0.0F).setLight(packedLight).setNormal(0, 0, 1);
//            pose.popPose();
//        }
//
//        buffers.endBatch();
    }

    private void begin() {
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void end() {
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }
}
