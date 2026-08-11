package com.flansmod.warforge.client;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.blocks.PoleGeometry;
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

    private static final net.minecraft.client.resources.model.ModelResourceLocation POLE_MODEL = net.minecraft.client.resources.model.ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "block/pole"));

    private static final int WAVE_SEGMENTS = 8;
    private static final float WAVE_AMPLITUDE = 0.12F;
    private static final float WAVE_SPEED = 0.12F;
    private static final float WAVE_FREQUENCY = 0.45F;

    private static final float ANCHOR_X = 0.0625F;
    private static final float MAX_BANNER_WIDTH = 1.4F;
    private static final float MAX_BANNER_HEIGHT = 0.95F;

    private static final float MODEL_SCALE = PoleGeometry.MODEL_SCALE;
    private static final float SHAFT_BASE_Y = PoleGeometry.SHAFT_BASE_Y;
    public static final float NATURAL_SHAFT_LENGTH = 4.375F;
    private static final float BANNER_BELOW_TOP = 0.1F;

    private PoleFlagRenderer() {
    }

    public static void render(PoseStack pose, MultiBufferSource buffers, int packedOverlay,
                              Level level, BlockPos pos, int rotation, String flagId,
                              float poleLength, float partialTicks) {
        BakedModel bakedModel = Minecraft.getInstance().getModelManager().getModel(POLE_MODEL);
        BlockState dummyState = Blocks.STONE.defaultBlockState();

        int poleLight = LevelRenderer.getLightColor(level, pos.above());

        pose.pushPose();
        pose.translate(0.5D, 1.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(rotation * 45.0F));
        pose.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        //pose.translate(-0.5D, 0.0D, -0.5D);

        VertexConsumer consumer = buffers.getBuffer(RenderType.cutout());
        PoseStack.Pose basePose = pose.last();

        float shaftStretch = poleLength / NATURAL_SHAFT_LENGTH;
        pose.pushPose();
        pose.translate(0.0D, SHAFT_BASE_Y, 0.0D);
        pose.scale(1.0F, shaftStretch, 1.0F);
        pose.translate(0.0D, -SHAFT_BASE_Y, 0.0D);
        PoseStack.Pose shaftPose = pose.last();

        RandomSource random = RandomSource.create();
        for (Direction dir : Direction.values()) {
            for (BakedQuad quad : bakedModel.getQuads(dummyState, dir, random)) {
                consumer.putBulkData(isShaftQuad(quad) ? shaftPose : basePose, quad, 1.0F, 1.0F, 1.0F, 1.0F, poleLight, packedOverlay);
            }
        }
        for (BakedQuad quad : bakedModel.getQuads(dummyState, null, random)) {
            consumer.putBulkData(isShaftQuad(quad) ? shaftPose : basePose, quad, 1.0F, 1.0F, 1.0F, 1.0F, poleLight, packedOverlay);
        }
        pose.popPose();

        float bannerTop = SHAFT_BASE_Y + poleLength - BANNER_BELOW_TOP;
        renderFlag(pose, buffers, level, poleLight, packedOverlay, flagId, bannerTop, partialTicks);

        pose.popPose();
    }

    private static boolean isShaftQuad(BakedQuad quad) {
        int[] vertices = quad.getVertices();
        int stride = vertices.length / 4;
        for (int i = 0; i < 4; i++) {
            if (Float.intBitsToFloat(vertices[i * stride + 1]) > SHAFT_BASE_Y + 1.0E-4F) {
                return true;
            }
        }
        return false;
    }

    private static void renderFlag(PoseStack pose, MultiBufferSource buffers, Level level,
                                   int packedLight, int packedOverlay, String flagId,
                                   float bannerTop, float partialTicks) {
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

        float bannerBottom = bannerTop - bannerHeight;

        float time = WAVE_SPEED * ((float) (level.getGameTime() % 100000L) + partialTicks);

        VertexConsumer flag = buffers.getBuffer(RenderType.entityCutout(flagTexture));

        float x0 = ANCHOR_X;
        float dx = bannerWidth / WAVE_SEGMENTS;

        for (int seg = 0; seg < WAVE_SEGMENTS; seg++) {
            float xa = x0 + dx * seg;
            float xb = x0 + dx * (seg + 1);
            float ua = (float) seg / WAVE_SEGMENTS;
            float ub = (float) (seg + 1) / WAVE_SEGMENTS;

            float za = wave(time, seg);
            float zb = wave(time, seg + 1);

            quad(flag, pose.last(), xa, xb, za, zb, ua, ub, bannerTop, bannerBottom, packedLight, packedOverlay, true);
            quad(flag, pose.last(), xa, xb, za, zb, ua, ub, bannerTop, bannerBottom, packedLight, packedOverlay, false);
        }
    }

    private static float wave(float time, int seg) {
        return WAVE_AMPLITUDE * Mth.sin(time + seg * WAVE_FREQUENCY) * seg / WAVE_SEGMENTS;
    }

    private static void quad(VertexConsumer c, com.mojang.blaze3d.vertex.PoseStack.Pose pose,
                             float xa, float xb, float za, float zb, float ua, float ub,
                             float bannerTop, float bannerBottom,
                             int packedLight, int packedOverlay, boolean front) {
        float nz = front ? 1.0F : -1.0F;
        float z = front ? 0.005F : -0.005F;
        if (front) {
            c.addVertex(pose, xa, bannerTop, za + z).setColor(255, 255, 255, 255).setUv(ua, 0.0F).setOverlay(packedOverlay).setLight(packedLight).setNormal(pose, 0.0F, 0.0F, nz);
            c.addVertex(pose, xa, bannerBottom, za + z).setColor(255, 255, 255, 255).setUv(ua, 1.0F).setOverlay(packedOverlay).setLight(packedLight).setNormal(pose, 0.0F, 0.0F, nz);
            c.addVertex(pose, xb, bannerBottom, zb + z).setColor(255, 255, 255, 255).setUv(ub, 1.0F).setOverlay(packedOverlay).setLight(packedLight).setNormal(pose, 0.0F, 0.0F, nz);
            c.addVertex(pose, xb, bannerTop, zb + z).setColor(255, 255, 255, 255).setUv(ub, 0.0F).setOverlay(packedOverlay).setLight(packedLight).setNormal(pose, 0.0F, 0.0F, nz);
        } else {
            c.addVertex(pose, xb, bannerTop, zb + z).setColor(255, 255, 255, 255).setUv(ub, 0.0F).setOverlay(packedOverlay).setLight(packedLight).setNormal(pose, 0.0F, 0.0F, nz);
            c.addVertex(pose, xb, bannerBottom, zb + z).setColor(255, 255, 255, 255).setUv(ub, 1.0F).setOverlay(packedOverlay).setLight(packedLight).setNormal(pose, 0.0F, 0.0F, nz);
            c.addVertex(pose, xa, bannerBottom, za + z).setColor(255, 255, 255, 255).setUv(ua, 1.0F).setOverlay(packedOverlay).setLight(packedLight).setNormal(pose, 0.0F, 0.0F, nz);
            c.addVertex(pose, xa, bannerTop, za + z).setColor(255, 255, 255, 255).setUv(ua, 0.0F).setOverlay(packedOverlay).setLight(packedLight).setNormal(pose, 0.0F, 0.0F, nz);
        }
    }
}
