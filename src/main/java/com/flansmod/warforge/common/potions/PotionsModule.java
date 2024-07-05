package com.flansmod.warforge.common.potions;

import javax.annotation.Nonnull;

import com.flansmod.warforge.common.WarForgeConfig;

import net.minecraft.init.Items;
import net.minecraft.init.PotionTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.potion.PotionType;
import net.minecraft.potion.PotionUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.brewing.AbstractBrewingRecipe;
import net.minecraftforge.common.brewing.BrewingRecipe;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.oredict.OreDictionary;

public class PotionsModule 
{	
	public Potion tpRequest;
	public Potion tpAccept;
	public PotionType tpRequestPotionType;
	public PotionType tpAcceptPotionType;
	
	public void preInit()
	{
		if(!WarForgeConfig.ENABLE_TPA_POTIONS)
			return;
	
		MinecraftForge.EVENT_BUS.register(this);
		
		tpRequest = new PotionTpRequest();
		tpAccept = new PotionTpAccept();
		
		tpRequestPotionType = new PotionType(new PotionEffect(tpRequest, 20 * 60));
		tpAcceptPotionType = new PotionType(new PotionEffect(tpAccept, 20 * 60));
		
		tpRequestPotionType.setRegistryName("tpRequestPotion");
		tpAcceptPotionType.setRegistryName("tpAcceptPotion");
	}
	
	@SubscribeEvent
	public void registerPotions(RegistryEvent.Register<PotionType> event)
	{
		event.getRegistry().register(tpRequestPotionType);
		event.getRegistry().register(tpAcceptPotionType);
	}
	
	public class BrewingRecipeFixed extends AbstractBrewingRecipe<ItemStack>
	{
	    @Nonnull
	    private final ItemStack input;
	    private final ItemStack ingredient;
		
		protected BrewingRecipeFixed(ItemStack in, ItemStack ing, ItemStack out) 
		{
			super(in, ing, out);
			input = in;
			ingredient = ing;
		}
		
	    @Override
	    public boolean isInput(@Nonnull ItemStack stack)
	    {
	        return PotionUtils.getPotionFromItem(stack).equals(PotionUtils.getPotionFromItem(input));
	    }

		@Override
		public boolean isIngredient(ItemStack test) 
		{
			return test.getItem() == ingredient.getItem()
				&& test.getItemDamage() == ingredient.getItemDamage();
		}
		
	}
	
	@SubscribeEvent
	public void registerRecipes(RegistryEvent.Register<IRecipe> event)
	{
		ItemStack basePotionStack = PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM), PotionTypes.LEAPING);
		ItemStack tpRequestPotionStack = PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM), tpRequestPotionType);
		ItemStack tpAcceptPotionStack = PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM), tpAcceptPotionType);

		BrewingRecipeRegistry.addRecipe(new BrewingRecipeFixed(basePotionStack, new ItemStack(Items.ENDER_PEARL), tpRequestPotionStack));
		BrewingRecipeRegistry.addRecipe(new BrewingRecipeFixed(basePotionStack, new ItemStack(Items.ENDER_EYE), tpAcceptPotionStack));
	}
}
