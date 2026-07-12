package com.flansmod.warforge.common.blocks;

public final class PoleGeometry {

    public static final float MODEL_SCALE = 2.0F;
    public static final float SHAFT_BASE_Y = 0.5625F;
    public static final float BASE_TRANSLATE = 1.0F;

    private PoleGeometry() {
    }

    public static float topOffset(float poleLength) {
        return BASE_TRANSLATE + MODEL_SCALE * (SHAFT_BASE_Y + poleLength);
    }
}
