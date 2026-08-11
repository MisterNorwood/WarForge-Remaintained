package com.flansmod.warforge.common.potions;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public class PotionTpAccept extends MobEffect
{
	protected PotionTpAccept()
	{
		super(MobEffectCategory.NEUTRAL, 0x00ffff);
	}

	@Override
	public boolean applyEffectTick(LivingEntity living, int amplifier)
	{
		return true;
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier)
	{
		return duration % 20 == 0;
	}
}
