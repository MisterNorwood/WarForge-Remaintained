package com.flansmod.warforge.common.effect;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class EffectRegistry {

   public static Map<String,IEffect> EFFECT_REGISTRY =  new HashMap<>();
   public static void init(){
       EFFECT_REGISTRY.put("upgrade", new EffectUpgrade());
       EFFECT_REGISTRY.put("disband", new EffectDisband());


   }

   @OnlyIn(Dist.CLIENT)
   public static void runClientEffect(String type, Player player, double x, double y, double z, CompoundTag data) {
       IEffect effect = EFFECT_REGISTRY.get(type);
       if (effect == null) {
           return;
       }
       Minecraft mc = Minecraft.getInstance();
       effect.runEffect(mc.level, player, mc.getTextureManager(), new Random(), x, y, z, data);
   }
}
