package com.flansmod.warforge.client.util;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;

public class FullColorNameplate {

    /**
     * Draws a faction nameplate using the modern batched-text pipeline, mirroring vanilla
     * {@link net.minecraft.client.renderer.entity.EntityRenderer#renderNameTag}.
     *
     * <p>Forge posts {@code RenderNameTagEvent} at the top of the name-tag wrapper, BEFORE vanilla
     * lifts the pose to head height and billboards it toward the camera, so the event pose is still
     * at the entity's feet in world orientation. This applies vanilla's full transform itself
     * (head-height translate, camera orientation, text scale); without it the text renders flat at
     * the player's feet.
     */
    public static void drawNameplate(Font font, Component name, Entity entity, PoseStack pose, MultiBufferSource buffers, int verticalShift, boolean isSneaking, int color, int darker, int packedLight) {
        pose.pushPose();
        pose.translate(0.0F, entity.getBbHeight() + 0.5F, 0.0F);
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-0.025F, -0.025F, 0.025F);

        Matrix4f matrix = pose.last().pose();
        float backgroundOpacity = Minecraft.getInstance().options.getBackgroundOpacity(0.25F);
        int bgColor = (int) (backgroundOpacity * 255.0F) << 24;
        float xOffset = -font.width(name) / 2.0F;
        // Apply the vertical offset in the scaled text frame (post-scale) instead of in block units.
        float yOffset = verticalShift;

        if (isSneaking) {
            font.drawInBatch(name, xOffset, yOffset, darker, false, matrix, buffers, Font.DisplayMode.NORMAL, bgColor, packedLight);
        } else {
            // First pass behind blocks (see-through), second pass on top, matching vanilla.
            font.drawInBatch(name, xOffset, yOffset, darker, false, matrix, buffers, Font.DisplayMode.SEE_THROUGH, bgColor, packedLight);
            font.drawInBatch(name, xOffset, yOffset, color, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, packedLight);
        }

        pose.popPose();
    }
}
