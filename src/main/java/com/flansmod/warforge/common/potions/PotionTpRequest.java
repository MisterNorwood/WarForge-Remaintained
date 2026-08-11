package com.flansmod.warforge.common.potions;

import java.util.List;

import com.flansmod.warforge.common.WarForgeMod;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

public class PotionTpRequest extends MobEffect
{
	protected PotionTpRequest()
	{
		super(MobEffectCategory.NEUTRAL, 0x00afff);
	}

	@Override
	public boolean applyEffectTick(LivingEntity living, int amplifier)
	{
		LivingEntity bestEntity = null;
		double bestDistanceSq = Double.MAX_VALUE;

		AABB searchBox = living.getBoundingBox().inflate(64.0D);
		List<LivingEntity> candidates = living.level().getEntitiesOfClass(LivingEntity.class, searchBox);
		for (LivingEntity entity : candidates)
		{
			if (entity.hasEffect(WarForgeMod.POTIONS.tpAccept))
			{
				double distanceSq = entity.distanceToSqr(living);
				if (distanceSq < bestDistanceSq)
				{
					bestEntity = entity;
					bestDistanceSq = distanceSq;
				}
			}
		}

		if (bestEntity != null)
		{
			if (living.randomTeleport(bestEntity.getX(), bestEntity.getY(), bestEntity.getZ(), true))
			{
				living.removeEffect(WarForgeMod.POTIONS.tpRequest);
			}
		}
		return true;
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier)
	{
		return duration % 20 == 0;
	}
}
