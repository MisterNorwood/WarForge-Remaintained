package com.flansmod.warforge.common.effect;

import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.network.PacketEffect;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Random;

public class EffectDisband implements IEffect {
    public static void composeEffect(ResourceKey<Level> dim, double x, double y, double z, float radius) {
        PacketEffect packet = new PacketEffect();
        packet.x = x;
        packet.y = y;
        packet.z = z;
        packet.type = "disband";
        CompoundTag compound = new CompoundTag();
        packet.dataNBT = compound.toString();

        WarForgeMod.NETWORK.sendToAllAround(packet, x, y, z, radius, dim);
    }

    public static void composeEffect(ResourceKey<Level> dim, BlockPos pos, float radius) {
        composeEffect(dim, pos.getX(), pos.getY(), pos.getZ(), radius);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runEffect(Level world, Player player, TextureManager man, Random rand, double x, double y, double z, CompoundTag data) {

        world.playLocalSound(x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0F, 0.9F, false);
        world.playLocalSound(x, y, z, SoundEvents.ANVIL_DESTROY, SoundSource.PLAYERS, 1.0F, 0.9F, false);

        for (int i = 0; i < 32; i++) {
            world.addParticle(
                    ParticleTypes.EXPLOSION_EMITTER,
                    x + world.random.nextFloat(),
                    y + (i % 2) + world.random.nextFloat(),
                    z + world.random.nextFloat(),
                    0.0D,
                    0.0D,
                    0.0D
            );
        }
    }
}
