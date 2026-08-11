package com.flansmod.warforge.common.effect;

import com.flansmod.warforge.common.Content;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.network.PacketEffect;
import lombok.RequiredArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Random;

public class EffectUpgrade implements IEffect {
    public static CompoundTag toNbtCompound(int segments, double radius, double speed, int color, int count) {
        CompoundTag compound = new CompoundTag();
        compound.putInt("segments", segments);
        compound.putDouble("radius", radius);
        compound.putDouble("speed", speed);
        compound.putInt("color", color);
        compound.putInt("count", count);

        return compound;
    }

    public static void composeEffect(ResourceKey<Level> dim, double x, double y, double z, float radius, int segments, double circleRadius, double speed, int color, int count) {
        PacketEffect packet = new PacketEffect();
        packet.x = x;
        packet.y = y;
        packet.z = z;
        packet.type = "upgrade";
        packet.dataNBT = toNbtCompound(segments, circleRadius, speed, color, count).toString();

        WarForgeMod.NETWORK.sendToAllAround(packet, x, y, z, radius, dim);
    }

    public static void composeEffect(ResourceKey<Level> dim, BlockPos pos, float radius, int segments, double circleRadius, double speed, int color, int count) {
        composeEffect(dim, pos.getX(), pos.getY(), pos.getZ(), radius, segments, circleRadius, speed, color, count);
    }

    public static void composeEffect(DimBlockPos pos, float radius, int segments, double circleRadius, double speed, int color, int count) {
        composeEffect(pos.dim, pos.getX(), pos.getY(), pos.getZ(), radius, segments, circleRadius, speed, color, count);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void runEffect(Level world, Player player, TextureManager man, Random rand, double x, double y, double z, CompoundTag data) {
        double radius = data.getDouble("radius");
        int segments = data.getInt("segments");
        double speed = data.getDouble("speed");
        int color = data.getInt("color");
        int effectCount = data.getInt("count");
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        float r = red / 255f;
        float g = green / 255f;
        float b = blue / 255f;

        for (int j = 0; j <= effectCount; j++) {
            EffectUpgradeContext context = new EffectUpgradeContext(BlockPos.containing(x, y, z), j * 0.1, radius, segments, speed, r, g, b);
            AnimatedEffectHandler.add(new EffectAnimated<>(context, 60, (EffectUpgradeContext ctx, Integer i) -> {
                Minecraft.getInstance().execute(() -> {
                    ClientLevel world1 = Minecraft.getInstance().level;
                    double angle = (2 * Math.PI * i / ctx.segments) + context.angleOffset;

                    double rotatedAngle = angle + ctx.rotation;

                    double px = ctx.pos.getX() + 0.5 + ctx.radius * Math.cos(rotatedAngle);
                    double pz = ctx.pos.getZ() + 0.5 + ctx.radius * Math.sin(rotatedAngle);
                    double py = ctx.pos.getY() + context.angleOffset;

                    world1.addParticle(Content.STAR_CIRCLE_PARTICLE.get(), px, py, pz, r, g, b);
                });
                ctx.tickRotation(context.speed);

            }));
        }

    }

    @RequiredArgsConstructor
    static class EffectUpgradeContext {
        public final BlockPos pos;
        public final double angleOffset;
        public final double radius;
        public final int segments;
        public final double speed;
        public final double r, g, b;
        public double rotation = 0;

        public void tickRotation(double delta) {
            rotation += delta;
            if (rotation > 2 * Math.PI) rotation -= 2 * Math.PI;
        }

    }
}
