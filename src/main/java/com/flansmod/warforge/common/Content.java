package com.flansmod.warforge.common;

import com.flansmod.warforge.common.blocks.BlockAdminClaim;
import com.flansmod.warforge.common.blocks.BlockBasicClaim;
import com.flansmod.warforge.common.blocks.BlockCitadel;
import com.flansmod.warforge.common.blocks.BlockLeaderboard;
import com.flansmod.warforge.common.blocks.BlockSiegeCamp;
import com.flansmod.warforge.common.blocks.BlockYieldProvider;
import com.flansmod.warforge.common.blocks.TileEntityAdminClaim;
import com.flansmod.warforge.common.blocks.TileEntityBasicClaim;
import com.flansmod.warforge.common.blocks.TileEntityCitadel;
import com.flansmod.warforge.common.blocks.TileEntityLeaderboard;
import com.flansmod.warforge.common.blocks.TileEntityReinforcedClaim;
import com.flansmod.warforge.common.blocks.TileEntitySiegeCamp;
import com.flansmod.warforge.server.Leaderboard.FactionStat;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

public class Content 
{
	public Block citadelBlock, basicClaimBlock, reinforcedClaimBlock, siegeCampBlock;
	public Item citadelBlockItem, basicClaimBlockItem, reinforcedClaimBlockItem, siegeCampBlockItem;
	
	public Block denseIronOreBlock, denseGoldOreBlock, denseDiamondOreBlock, magmaVentBlock,
		denseQuartzOreBlock, denseClayBlock, ancientOakBlock, denseRedstoneOreBlock, denseSlimeBlock,
		shulkerFossilBlock;
	public Item denseIronOreItem, denseGoldOreItem, denseDiamondOreItem, magmaVentItem,
		denseQuartzOreItem, denseClayItem, ancientOakItem, denseRedstoneOreItem, denseSlimeItem,
		shulkerFossilItem;
	
	public Block adminClaimBlock;
	public Item adminClaimBlockItem;
	
	public Block topLeaderboardBlock, notorietyLeaderboardBlock, wealthLeaderboardBlock, legacyLeaderboardBlock;
	public Item topLeaderboardItem, notorietyLeaderboardItem, wealthLeaderboardItem, legacyLeaderboardItem;
	
	
	public void preInit()
	{
        citadelBlock = new BlockCitadel(Material.ROCK).setRegistryName("citadelblock").setTranslationKey("citadelblock");
        citadelBlockItem = new ItemBlock(citadelBlock).setRegistryName("citadelblock").setTranslationKey("citadelblock");
        GameRegistry.registerTileEntity(TileEntityCitadel.class, new ResourceLocation(WarForgeMod.MODID, "citadel"));
        
        // Basic and reinforced claims, they share a tile entity
        basicClaimBlock = new BlockBasicClaim(Material.ROCK).setRegistryName("basicclaimblock").setTranslationKey("basicclaimblock");
        basicClaimBlockItem = new ItemBlock(basicClaimBlock).setRegistryName("basicclaimblock").setTranslationKey("basicclaimblock");
        GameRegistry.registerTileEntity(TileEntityBasicClaim.class, new ResourceLocation(WarForgeMod.MODID, "basicclaim"));
        reinforcedClaimBlock = new BlockBasicClaim(Material.ROCK).setRegistryName("reinforcedclaimblock").setTranslationKey("reinforcedclaimblock");
        reinforcedClaimBlockItem = new ItemBlock(reinforcedClaimBlock).setRegistryName("reinforcedclaimblock").setTranslationKey("reinforcedclaimblock");
        GameRegistry.registerTileEntity(TileEntityReinforcedClaim.class, new ResourceLocation(WarForgeMod.MODID, "reinforcedclaim"));
        
        // Siege camp
        siegeCampBlock = new BlockSiegeCamp(Material.ROCK).setRegistryName("siegecampblock").setTranslationKey("siegecampblock");
        siegeCampBlockItem = new ItemBlock(siegeCampBlock).setRegistryName("siegecampblock").setTranslationKey("siegecampblock");
        GameRegistry.registerTileEntity(TileEntitySiegeCamp.class, new ResourceLocation(WarForgeMod.MODID, "siegecamp"));
 
        // Admin claim block
        adminClaimBlock = new BlockAdminClaim().setRegistryName("adminclaimblock").setTranslationKey("adminclaimblock");
        adminClaimBlockItem = new ItemBlock(adminClaimBlock).setRegistryName("adminclaimblock").setTranslationKey("adminclaimblock");
        GameRegistry.registerTileEntity(TileEntityAdminClaim.class, new ResourceLocation(WarForgeMod.MODID, "adminclaim"));
 
        
        denseIronOreBlock = new BlockYieldProvider(Material.ROCK, WarForgeConfig.IRON_YIELD_AS_ORE ? new ItemStack(Blocks.IRON_ORE) : new ItemStack(Items.IRON_INGOT), WarForgeConfig.NUM_IRON_PER_DAY_PER_ORE).setRegistryName("denseironore").setTranslationKey("denseironore");
        denseGoldOreBlock = new BlockYieldProvider(Material.ROCK, WarForgeConfig.GOLD_YIELD_AS_ORE ? new ItemStack(Blocks.GOLD_ORE) : new ItemStack(Items.GOLD_INGOT), WarForgeConfig.NUM_GOLD_PER_DAY_PER_ORE).setRegistryName("densegoldore").setTranslationKey("densegoldore");
        denseDiamondOreBlock = new BlockYieldProvider(Material.ROCK, WarForgeConfig.DIAMOND_YIELD_AS_ORE ? new ItemStack(Blocks.DIAMOND_ORE) : new ItemStack(Items.DIAMOND), WarForgeConfig.NUM_DIAMOND_PER_DAY_PER_ORE).setRegistryName("densediamondore").setTranslationKey("densediamondore");
        magmaVentBlock = new BlockYieldProvider(Material.ROCK, new ItemStack(Items.LAVA_BUCKET), 0.0f).setRegistryName("magmavent").setTranslationKey("magmavent");
        denseQuartzOreBlock = new BlockYieldProvider(Material.ROCK, WarForgeConfig.QUARTZ_YIELD_AS_BLOCKS ? new ItemStack(Blocks.QUARTZ_BLOCK) : new ItemStack(Items.QUARTZ), WarForgeConfig.NUM_QUARTZ_PER_DAY_PER_ORE).setRegistryName("densequartzore").setTranslationKey("densequartzore");
        denseClayBlock = new BlockYieldProvider(Material.CLAY, WarForgeConfig.CLAY_YIELD_AS_BLOCKS ? new ItemStack(Blocks.CLAY) : new ItemStack(Items.CLAY_BALL), WarForgeConfig.NUM_CLAY_PER_DAY_PER_ORE).setRegistryName("denseclay").setTranslationKey("denseclay");
        ancientOakBlock = new BlockYieldProvider(Material.WOOD, WarForgeConfig.ANCIENT_OAK_YIELD_AS_LOGS ? new ItemStack(Blocks.LOG2, 1, 1) : new ItemStack(Blocks.PLANKS, 1, 5), WarForgeConfig.NUM_OAK_PER_DAY_PER_LOG).setRegistryName("ancientoak").setTranslationKey("ancientoak");
        denseRedstoneOreBlock = new BlockYieldProvider(Material.ROCK, WarForgeConfig.REDSTONE_YIELD_AS_BLOCKS ? new ItemStack(Blocks.REDSTONE_BLOCK) : new ItemStack(Items.REDSTONE), WarForgeConfig.NUM_REDSTONE_PER_DAY_PER_ORE).setRegistryName("denseredstoneore").setTranslationKey("denseredstoneore");
        denseSlimeBlock = new BlockYieldProvider(Material.SPONGE, new ItemStack(Items.SLIME_BALL), WarForgeConfig.NUM_SLIME_PER_DAY_PER_ORE).setRegistryName("denseslime").setTranslationKey("denseslime");
        shulkerFossilBlock = new BlockYieldProvider(Material.ROCK, new ItemStack(Items.SHULKER_SHELL), WarForgeConfig.NUM_SHULKER_PER_DAY_PER_ORE).setRegistryName("shulkerfossil").setTranslationKey("shulkerfossil");

        
        denseIronOreItem = new ItemBlock(denseIronOreBlock).setRegistryName("denseironore").setTranslationKey("denseironore");
        denseGoldOreItem = new ItemBlock(denseGoldOreBlock).setRegistryName("densegoldore").setTranslationKey("densegoldore");
        denseDiamondOreItem = new ItemBlock(denseDiamondOreBlock).setRegistryName("densediamondore").setTranslationKey("densediamondore");
        magmaVentItem = new ItemBlock(magmaVentBlock).setRegistryName("magmavent").setTranslationKey("magmavent");
        denseClayItem = new ItemBlock(denseClayBlock).setRegistryName("denseclay").setTranslationKey("denseclay");
        denseQuartzOreItem = new ItemBlock(denseQuartzOreBlock).setRegistryName("densequartzore").setTranslationKey("densequartzore");
        ancientOakItem = new ItemBlock(ancientOakBlock).setRegistryName("ancientoak").setTranslationKey("ancientoak");
        denseRedstoneOreItem = new ItemBlock(denseRedstoneOreBlock).setRegistryName("denseredstoneore").setTranslationKey("denseredstoneore");
        denseSlimeItem = new ItemBlock(denseSlimeBlock).setRegistryName("denseslime").setTranslationKey("denseslime");
        shulkerFossilItem = new ItemBlock(shulkerFossilBlock).setRegistryName("shulkerfossil").setTranslationKey("shulkerfossil");
                        
        topLeaderboardBlock = new BlockLeaderboard(Material.ROCK, FactionStat.TOTAL).setRegistryName("topleaderboard").setTranslationKey("topleaderboard");
        wealthLeaderboardBlock = new BlockLeaderboard(Material.ROCK, FactionStat.WEALTH).setRegistryName("wealthleaderboard").setTranslationKey("wealthleaderboard");
        notorietyLeaderboardBlock = new BlockLeaderboard(Material.ROCK, FactionStat.NOTORIETY).setRegistryName("notorietyleaderboard").setTranslationKey("notorietyleaderboard");
        legacyLeaderboardBlock = new BlockLeaderboard(Material.ROCK, FactionStat.LEGACY).setRegistryName("legacyleaderboard").setTranslationKey("legacyleaderboard");
        
        topLeaderboardItem = new ItemBlock(topLeaderboardBlock).setRegistryName("topleaderboard").setTranslationKey("topleaderboard");
        wealthLeaderboardItem = new ItemBlock(wealthLeaderboardBlock).setRegistryName("wealthleaderboard").setTranslationKey("wealthleaderboard");
        notorietyLeaderboardItem = new ItemBlock(notorietyLeaderboardBlock).setRegistryName("notorietyleaderboard").setTranslationKey("notorietyleaderboard");
        legacyLeaderboardItem = new ItemBlock(legacyLeaderboardBlock).setRegistryName("legacyleaderboard").setTranslationKey("legacyleaderboard");
        GameRegistry.registerTileEntity(TileEntityLeaderboard.class, new ResourceLocation(WarForgeMod.MODID, "leaderboard"));
        
        MinecraftForge.EVENT_BUS.register(this);
	}
	
	@SubscribeEvent
	public void registerItems(RegistryEvent.Register<Item> event)
	{		
		event.getRegistry().register(citadelBlockItem);
		event.getRegistry().register(basicClaimBlockItem);
		event.getRegistry().register(reinforcedClaimBlockItem);
		event.getRegistry().register(siegeCampBlockItem);
		event.getRegistry().register(adminClaimBlockItem);
		event.getRegistry().register(denseIronOreItem);
		event.getRegistry().register(denseGoldOreItem);
		event.getRegistry().register(denseSlimeItem);
		event.getRegistry().register(shulkerFossilItem);
		event.getRegistry().register(denseDiamondOreItem);
		event.getRegistry().register(denseQuartzOreItem);
		event.getRegistry().register(denseRedstoneOreItem);
		event.getRegistry().register(denseClayItem);
		event.getRegistry().register(ancientOakItem);
		event.getRegistry().register(magmaVentItem);
		event.getRegistry().register(topLeaderboardItem);
		event.getRegistry().register(wealthLeaderboardItem);
		event.getRegistry().register(notorietyLeaderboardItem);
		event.getRegistry().register(legacyLeaderboardItem);
		WarForgeMod.LOGGER.info("Registered items");
	}
	
	@SubscribeEvent
	public void registerBlocks(RegistryEvent.Register<Block> event)
	{
		event.getRegistry().register(citadelBlock);
		event.getRegistry().register(basicClaimBlock);
		event.getRegistry().register(reinforcedClaimBlock);
		event.getRegistry().register(siegeCampBlock);
		event.getRegistry().register(adminClaimBlock);
		event.getRegistry().register(denseIronOreBlock);
		event.getRegistry().register(denseGoldOreBlock);
		event.getRegistry().register(denseSlimeBlock);
		event.getRegistry().register(shulkerFossilBlock);
		event.getRegistry().register(denseDiamondOreBlock);
		event.getRegistry().register(denseQuartzOreBlock);
		event.getRegistry().register(denseRedstoneOreBlock);
		event.getRegistry().register(denseClayBlock);
		event.getRegistry().register(ancientOakBlock);
		event.getRegistry().register(magmaVentBlock);
		event.getRegistry().register(topLeaderboardBlock);
		event.getRegistry().register(wealthLeaderboardBlock);
		event.getRegistry().register(notorietyLeaderboardBlock);
		event.getRegistry().register(legacyLeaderboardBlock);
		WarForgeMod.LOGGER.info("Registered blocks");
	}
}
