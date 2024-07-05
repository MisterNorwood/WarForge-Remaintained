package com.flansmod.warforge.common.potions;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.Potion;

public class PotionTpAccept extends Potion 
{
	protected PotionTpAccept()
	{
		super(false, 0x00ffff);
		setPotionName("effect.tpaccept");
	}

	@Override
    public void performEffect(EntityLivingBase entityLivingBaseIn, int amplifier)
    {
    	
    }
	
	 @Override
	 public boolean isReady(int duration, int amplifier)
     {
		 return duration % 20 == 0;
     }
}
