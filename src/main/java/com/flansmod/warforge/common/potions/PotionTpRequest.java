package com.flansmod.warforge.common.potions;

import com.flansmod.warforge.common.WarForgeMod;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class PotionTpRequest extends Potion 
{
	public static final ResourceLocation EXTRA_EFFECTS = new ResourceLocation(WarForgeMod.MODID, "textures/potions.png");
	
	
	protected PotionTpRequest() 
	{
		super(false, 0x00afff);
		setPotionName("effect.tprequest");
	}
	
	@Override
    public void performEffect(EntityLivingBase living, int amplifier)
    {
		Entity bestEntity = null;
		double bestDistanceSq = Double.MAX_VALUE;
		
    	for(Entity entity : living.world.loadedEntityList)
    	{
    		if(entity instanceof EntityLivingBase)
    		{
    			if(((EntityLivingBase)entity).isPotionActive(WarForgeMod.POTIONS.tpAccept))
    			{
    				double distanceSq = entity.getDistanceSq(living);
    				if(distanceSq < bestDistanceSq)
    				{
    					bestEntity = entity;
    					bestDistanceSq = distanceSq;
    				}
    			}
    		}
    	}
    	
    	if(bestEntity != null)
    	{
    		if(living.attemptTeleport(bestEntity.posX, bestEntity.posY, bestEntity.posZ))
    		{
    			living.removeActivePotionEffect(this);
    		}
    		
    	}
    }
	
	@SideOnly(Side.CLIENT)
	@Override
	public int getStatusIconIndex() 
	{
		Minecraft.getMinecraft().renderEngine.bindTexture(EXTRA_EFFECTS);
		return 1;
	}

	@Override
	public boolean isReady(int duration, int amplifier)
    {
		 return duration % 20 == 0;
    }
	
}
