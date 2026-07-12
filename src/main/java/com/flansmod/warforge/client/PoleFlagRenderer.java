package com.flansmod.warforge.client;

import com.flansmod.warforge.Tags;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public final class PoleFlagRenderer {

    private static final ResourceLocation POLE_MODEL = new ResourceLocation(Tags.MODID, "block/pole");

    private static final int WAVE_SEGMENTS = 8;
    private static final float WAVE_AMPLITUDE = 0.12F;
    private static final float WAVE_SPEED = 0.12F;
    private static final float WAVE_FREQUENCY = 0.45F;

    private static final float ANCHOR_X = 0.5625F;
    private static final float BANNER_TOP_Y = 4.6F;
    private static final float MAX_BANNER_WIDTH = 1.4F;
    private static final float MAX_BANNER_HEIGHT = 0.95F;

    private PoleFlagRenderer() {
    }

    public static void render(PoseStack pose, MultiBufferSource buffers, int packedOverlay,
                              Level level, BlockPos pos, int rotation, String flagId, float partialTicks) {
        BakedModel bakedModel = Minecraft.getInstance().getModelManager().getModel(POLE_MODEL);
        BlockState dummyState = Blocks.STONE.defaultBlockState();

        pose.pushPose();
        pose.translate(0.0D, 1.0D, 0.0D);
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(rotation * 45.0F));
        pose.translate(-0.5D, 0.0D, -0.5D);

        int poleLight = LevelRenderer.getLightColor(level, pos.above());

        VertexConsumer consumer = buffers.getBuffer(RenderType.cutout());
        PoseStack.Pose last = pose.last();
        RandomSource random = RandomSource.create();
        for (Direction dir : Direction.values()) {
            for (BakedQuad quad : bakedModel.getQuads(dummyState, dir, random)) {
                consumer.putBulkData(last, quad, 1.0F, 1.0F, 1.0F, poleLight, packedOverlay);
            }
        }
        for (BakedQuad quad : bakedModel.getQuads(dummyState, null, random)) {
            consumer.putBulkData(last, quad, 1.0F, 1.0F, 1.0F, poleLight, packedOverlay);
        }

        renderFlag(pose, buffers, level, poleLight, packedOverlay, flagId, partialTicks);

        pose.popPose();
    }

    private static void renderFlag(PoseStack pose, MultiBufferSource buffers, Level level,
                                   int packedLight, int packedOverlay, String flagId, float partialTicks) {
        ResourceLocation flagTexture = ClientFlagRegistry.getFlagTexture(flagId);
        if (flagTexture == null) return;

        int[] dims = ClientFlagRegistry.getFlagDimensions(flagId);
        float aspect = (dims != null && dims.length == 2 && dims[1] > 0) ? (float) dims[0] / dims[1] : 1.0F;
        float boxAspect = MAX_BANNER_WIDTH / MAX_BANNER_HEIGHT;
        float bannerWidth;
        float bannerHeight;
        if (aspect >= boxAspect) {
            bannerWidth = MAX_BANNER_WIDTH;
            bannerHeight = bannerWidth / aspect;
        } else {
            bannerHeight = MAX_BANNER_HEIGHT;
            bannerWidth = bannerHeight * aspect;
        }

        float bannerTop = BANNER_TOP_Y;
        float bannerBottom = bannerTop - bannerHeight;

        float time = WAVE_SPEED * ((float) (level.getGameTime() % 100000L) + partialTicks);

        VertexConsumer flag = buffers.getBuffer(RenderType.entityCutout(flagTexture));
        Matrix4f mat = pose.last().pose();
        Matrix3f norm = pose.last().normal();

        float x0 = ANCHOR_X;
        float dx = bannerWidth / WAVE_SEGMENTS;

        for (int seg = 0; seg < WAVE_SEGMENTS; seg++) {
            float xa = x0 + dx * seg;
            float xb = x0 + dx * (seg + 1);
            float ua = (float) seg / WAVE_SEGMENTS;
            float ub = (float) (seg + 1) / WAVE_SEGMENTS;

            float za = wave(time, seg);
            float zb = wave(time, seg + 1);

            quad(flag, mat, norm, xa, xb, za, zb, ua, ub, bannerTop, bannerBottom, packedLight, packedOverlay, true);
            quad(flag, mat, norm, xa, xb, za, zb, ua, ub, bannerTop, bannerBottom, packedLight, packedOverlay, false);
        }
    }

    private static float wave(float time, int seg) {
        return WAVE_AMPLITUDE * Mth.sin(time + seg * WAVE_FREQUENCY) * seg / WAVE_SEGMENTS;
    }

    private static void quad(VertexConsumer c, Matrix4f mat, Matrix3f norm,
                             float xa, float xb, float za, float zb, float ua, float ub,
                             float bannerTop, float bannerBottom,
                             int packedLight, int packedOverlay, boolean front) {
        float nz = front ? 1.0F : -1.0F;
        float z = front ? 0.005F : -0.005F;
        if (front) {
            c.vertex(mat, xa, bannerTop, za + z).color(255, 255, 255, 255).uv(ua, 0.0F).overlayCoords(packedOverlay).uv2(packedLight).normal(norm, 0.0F, 0.0F, nz).endVertex();
            c.vertex(mat, xa, bannerBottom, za + z).color(255, 255, 255, 255).uv(ua, 1.0F).overlayCoords(packedOverlay).uv2(packedLight).normal(norm, 0.0F, 0.0F, nz).endVertex();
            c.vertex(mat, xb, bannerBottom, zb + z).color(255, 255, 255, 255).uv(ub, 1.0F).overlayCoords(packedOverlay).uv2(packedLight).normal(norm, 0.0F, 0.0F, nz).endVertex();
            c.vertex(mat, xb, bannerTop, zb + z).color(255, 255, 255, 255).uv(ub, 0.0F).overlayCoords(packedOverlay).uv2(packedLight).normal(norm, 0.0F, 0.0F, nz).endVertex();
        } else {
            c.vertex(mat, xb, bannerTop, zb + z).color(255, 255, 255, 255).uv(ub, 0.0F).overlayCoords(packedOverlay).uv2(packedLight).normal(norm, 0.0F, 0.0F, nz).endVertex();
            c.vertex(mat, xb, bannerBottom, zb + z).color(255, 255, 255, 255).uv(ub, 1.0F).overlayCoords(packedOverlay).uv2(packedLight).normal(norm, 0.0F, 0.0F, nz).endVertex();
            c.vertex(mat, xa, bannerBottom, za + z).color(255, 255, 255, 255).uv(ua, 1.0F).overlayCoords(packedOverlay).uv2(packedLight).normal(norm, 0.0F, 0.0F, nz).endVertex();
            c.vertex(mat, xa, bannerTop, za + z).color(255, 255, 255, 255).uv(ua, 0.0F).overlayCoords(packedOverlay).uv2(packedLight).normal(norm, 0.0F, 0.0F, nz).endVertex();
        }
    }
}
