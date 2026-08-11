package com.flansmod.warforge.common.effect;

import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Random;

public interface IEffect {

    @OnlyIn(Dist.CLIENT)
    void runEffect(Level world, Player player, TextureManager man, Random rand, double x, double y, double z, CompoundTag data);

}
