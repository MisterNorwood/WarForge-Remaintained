package com.flansmod.warforge.client.util;

import com.flansmod.warforge.common.util.DimChunkPos;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Matrix4f;

public class RenderUtil {

    public static void vertexAt(DimChunkPos chunkPos, Level world, Matrix4f matrix, VertexConsumer buffer, int color, int x, int z, double groundLevelBlend, double playerHeight) {
        double topHeight = playerHeight + 128;

        double maxHeight = world.getHeight(Heightmap.Types.MOTION_BLOCKING, chunkPos.x * 16 + x, chunkPos.z * 16 + z) + 8;
        if (maxHeight > playerHeight + 16) maxHeight = playerHeight + 16;

        double height = topHeight + (maxHeight - topHeight) * groundLevelBlend;

        buffer.addVertex(matrix, x, (float) height, z).setColor(color).setUv(z / 16f, x / 16f);
    }

    public static void drawTexturedModalRect(Matrix4f matrix, VertexConsumer buffer, int color, int x, int y, float u, float v, int w, int h) {
        float texScale = 1f / 256f;

        buffer.addVertex(matrix, x, y + h, -90f).setColor(color).setUv(u * texScale, (v + h) * texScale);
        buffer.addVertex(matrix, x + w, y + h, -90f).setColor(color).setUv((u + w) * texScale, (v + h) * texScale);
        buffer.addVertex(matrix, x + w, y, -90f).setColor(color).setUv((u + w) * texScale, (v) * texScale);
        buffer.addVertex(matrix, x, y, -90f).setColor(color).setUv(u * texScale, (v) * texScale);
    }

    public static void renderZAlignedSquare(Matrix4f matrix, VertexConsumer buffer, int color, int x, int y, double z, int ori) {
        buffer.addVertex(matrix, x, y, (float) z).setColor(color).setUv(((ori) / 2) % 2, ((ori + 3) / 2) % 2);
        buffer.addVertex(matrix, x + 1, y, (float) z).setColor(color).setUv(((ori + 1) / 2) % 2, ((ori) / 2) % 2);
        buffer.addVertex(matrix, x + 1, y + 1, (float) z).setColor(color).setUv(((ori + 2) / 2) % 2, ((ori + 1) / 2) % 2);
        buffer.addVertex(matrix, x, y + 1, (float) z).setColor(color).setUv(((ori + 3) / 2) % 2, ((ori + 2) / 2) % 2);
    }

    public static void renderZAlignedRecangle(Matrix4f matrix, VertexConsumer buffer, int color, double x, int y, double z, int ori, double width) {
        buffer.addVertex(matrix, (float) (x + 0 - width), y, (float) z).setColor(color).setUv(((ori) / 2) % 2, ((ori + 3) / 2) % 2);
        buffer.addVertex(matrix, (float) (x + 1), y, (float) z).setColor(color).setUv(((ori + 1) / 2) % 2, ((ori) / 2) % 2);
        buffer.addVertex(matrix, (float) (x + 1), y + 1, (float) z).setColor(color).setUv(((ori + 2) / 2) % 2, ((ori + 1) / 2) % 2);
        buffer.addVertex(matrix, (float) (x + 0 - width), y + 1, (float) z).setColor(color).setUv(((ori + 3) / 2) % 2, ((ori + 2) / 2) % 2);
    }

    public static void renderXAlignedSquare(Matrix4f matrix, VertexConsumer buffer, int color, double x, int y, int z, int ori) {
        buffer.addVertex(matrix, (float) x, y, z).setColor(color).setUv(((ori) / 2) % 2, ((ori + 3) / 2) % 2);
        buffer.addVertex(matrix, (float) x, y, z + 1).setColor(color).setUv(((ori + 1) / 2) % 2, ((ori) / 2) % 2);
        buffer.addVertex(matrix, (float) x, y + 1, z + 1).setColor(color).setUv(((ori + 2) / 2) % 2, ((ori + 1) / 2) % 2);
        buffer.addVertex(matrix, (float) x, y + 1, z).setColor(color).setUv(((ori + 3) / 2) % 2, ((ori + 2) / 2) % 2);
    }

    public static void renderXAlignedRecangle(Matrix4f matrix, VertexConsumer buffer, int color, double x, int y, double z, int ori, double width) {
        buffer.addVertex(matrix, (float) x, y, (float) (z + 0 - width)).setColor(color).setUv(((ori) / 2) % 2, ((ori + 3) / 2) % 2);
        buffer.addVertex(matrix, (float) x, y, (float) (z + 1)).setColor(color).setUv(((ori + 1) / 2) % 2, ((ori) / 2) % 2);
        buffer.addVertex(matrix, (float) x, y + 1, (float) (z + 1)).setColor(color).setUv(((ori + 2) / 2) % 2, ((ori + 1) / 2) % 2);
        buffer.addVertex(matrix, (float) x, y + 1, (float) (z + 0 - width)).setColor(color).setUv(((ori + 3) / 2) % 2, ((ori + 2) / 2) % 2);
    }

    public static void renderZEdge(Level world, Matrix4f matrix, VertexConsumer buffer, int color, int x, int y, int z, double align, boolean air0, boolean air1, int dir) {
        if (!air0 && air1) RenderUtil.renderZAlignedSquare(matrix, buffer, color, x + 1, y, align, dir);
        if (air0 && !air1) RenderUtil.renderZAlignedSquare(matrix, buffer, color, x, y, align, 2 + dir);
    }

    public static void renderXEdge(Level world, Matrix4f matrix, VertexConsumer buffer, int color, int x, int y, int z, double align, boolean air0, boolean air1, int dir) {
        if (!air0 && air1) RenderUtil.renderXAlignedSquare(matrix, buffer, color, align, y, z + 1, dir);
        if (air0 && !air1) RenderUtil.renderXAlignedSquare(matrix, buffer, color, align, y, z, 2 + dir);
    }

    public static void renderXVerticalEdge(Level world, Matrix4f matrix, VertexConsumer buffer, int color, int x, int y, int z, double align, boolean air0, boolean air1, int dir) {
        if (!air0 && air1) RenderUtil.renderXAlignedSquare(matrix, buffer, color, align, y + 1, z, 3 + dir);
        if (air0 && !air1) RenderUtil.renderXAlignedSquare(matrix, buffer, color, align, y, z, 1 + dir);
    }

    public static void renderXVerticalCorner(Level world, Matrix4f matrix, VertexConsumer buffer, int color, double x, int y, double z, boolean air0, boolean air1, int dir, double width) {
        if (!air0 && air1) RenderUtil.renderXAlignedRecangle(matrix, buffer, color, x, y + 1, z, 3 + dir, width);
        if (air0 && !air1) RenderUtil.renderXAlignedRecangle(matrix, buffer, color, x, y, z, 1 + dir, width);
    }

    public static void renderZVerticalCorner(Level world, Matrix4f matrix, VertexConsumer buffer, int color, double x, int y, double z, boolean air0, boolean air1, int dir, double width) {
        if (!air0 && air1) RenderUtil.renderZAlignedRecangle(matrix, buffer, color, x, y + 1, z, 3 + dir, width);
        if (air0 && !air1) RenderUtil.renderZAlignedRecangle(matrix, buffer, color, x, y, z, 1 + dir, width);
    }

    public static void renderZVerticalEdge(Level world, Matrix4f matrix, VertexConsumer buffer, int color, int x, int y, int z, double align, boolean air0, boolean air1, int dir) {
        if (!air0 && air1) RenderUtil.renderZAlignedSquare(matrix, buffer, color, x, y + 1, align, 3 + dir);
        if (air0 && !air1) RenderUtil.renderZAlignedSquare(matrix, buffer, color, x, y, align, 1 + dir);
    }
}
