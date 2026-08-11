package com.flansmod.warforge.common.potions;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.WarForgeConfig;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class PotionsModule
{
	private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, Tags.MODID);
	private static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(Registries.POTION, Tags.MODID);

	public final DeferredHolder<MobEffect, MobEffect> tpRequest = EFFECTS.register("tprequest", PotionTpRequest::new);
	public final DeferredHolder<MobEffect, MobEffect> tpAccept = EFFECTS.register("tpaccept", PotionTpAccept::new);

	public final DeferredHolder<Potion, Potion> tpRequestPotionType = POTIONS.register("tprequestpotion",
		() -> new Potion(new MobEffectInstance(tpRequest, 20 * 60)));
	public final DeferredHolder<Potion, Potion> tpAcceptPotionType = POTIONS.register("tpacceptpotion",
		() -> new Potion(new MobEffectInstance(tpAccept, 20 * 60)));

	public void register(IEventBus modBus)
	{
		if (!WarForgeConfig.ENABLE_TPA_POTIONS)
			return;

		EFFECTS.register(modBus);
		POTIONS.register(modBus);
		net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(this::registerBrewingRecipes);
	}

	public void preInit()
	{
	}

	public void registerBrewingRecipes(RegisterBrewingRecipesEvent event)
	{
		if (!WarForgeConfig.ENABLE_TPA_POTIONS)
			return;

		PotionBrewing.Builder builder = event.getBuilder();
		builder.addMix(Potions.LEAPING, Items.ENDER_PEARL, tpRequestPotionType);
		builder.addMix(Potions.LEAPING, Items.ENDER_EYE, tpAcceptPotionType);
	}
}
