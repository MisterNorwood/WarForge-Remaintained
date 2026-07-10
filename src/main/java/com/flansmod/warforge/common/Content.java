package com.flansmod.warforge.common;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.blocks.*;
import com.flansmod.warforge.common.blocks.models.ClaimModels;
import com.flansmod.warforge.server.Leaderboard.FactionStat;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.registries.IForgeRegistry;

public class Content 
{
	static public Block citadelBlock, basicClaimBlock, reinforcedClaimBlock, siegeCampBlock, statue, dummyTranslusent;
	static public Item citadelBlockItem, basicClaimBlockItem, reinforcedClaimBlockItem, siegeCampBlockItem;
    static public Block islandCollectorBlock;
    static public Item islandCollectorItem;
	
	static public Block topLeaderboardBlock, notorietyLeaderboardBlock, wealthLeaderboardBlock, legacyLeaderboardBlock;
	static public Item topLeaderboardItem, notorietyLeaderboardItem, wealthLeaderboardItem, legacyLeaderboardItem;
	
	
	public void preInit()
	{
        var models = new ClaimModels();
        citadelBlock = new BlockCitadel(Material.ROCK).setRegistryName("citadelblock").setTranslationKey("citadelblock");
        citadelBlockItem = new ItemBlock(citadelBlock).setRegistryName("citadelblock").setTranslationKey("citadelblock");
        GameRegistry.registerTileEntity(TileEntityCitadel.class, new ResourceLocation(Tags.MODID, "citadel"));
        
        // Basic and reinforced claims, they share a tile entity
        basicClaimBlock = new BlockBasicClaim(Material.ROCK).setRegistryName("basicclaimblock").setTranslationKey("basicclaimblock");
        basicClaimBlockItem = new ItemBlock(basicClaimBlock).setRegistryName("basicclaimblock").setTranslationKey("basicclaimblock");
        GameRegistry.registerTileEntity(TileEntityBasicClaim.class, new ResourceLocation(Tags.MODID, "basicclaim"));
        reinforcedClaimBlock = new BlockBasicClaim(Material.ROCK).setRegistryName("reinforcedclaimblock").setTranslationKey("reinforcedclaimblock");
        reinforcedClaimBlockItem = new ItemBlock(reinforcedClaimBlock).setRegistryName("reinforcedclaimblock").setTranslationKey("reinforcedclaimblock");
        GameRegistry.registerTileEntity(TileEntityReinforcedClaim.class, new ResourceLocation(Tags.MODID, "reinforcedclaim"));
        
        // Siege camp
        siegeCampBlock = new BlockSiegeCamp(Material.ROCK).setRegistryName("siegecampblock").setTranslationKey("siegecampblock");
        siegeCampBlockItem = new ItemBlock(siegeCampBlock).setRegistryName("siegecampblock").setTranslationKey("siegecampblock");
        GameRegistry.registerTileEntity(TileEntitySiegeCamp.class, new ResourceLocation(Tags.MODID, "siegecamp"));

        islandCollectorBlock = new BlockIslandCollector(Material.ROCK).setRegistryName("islandcollector").setTranslationKey("islandcollector");
        islandCollectorItem = new ItemBlock(islandCollectorBlock).setRegistryName("islandcollector").setTranslationKey("islandcollector");
        GameRegistry.registerTileEntity(TileEntityIslandCollector.class, new ResourceLocation(Tags.MODID, "islandcollector"));

		//Dummy Block
		statue = new BlockDummy().setRegistryName("dummy").setTranslationKey("dummy");
		dummyTranslusent = new BlockDummyTransparent().setRegistryName("dummy_translusent").setTranslationKey("dummy_translusent");
		GameRegistry.registerTileEntity(TileEntityDummy.class, new ResourceLocation(Tags.MODID, "tileentity_dummy"));


 
        topLeaderboardBlock = new BlockLeaderboard(Material.ROCK, FactionStat.TOTAL).setRegistryName("topleaderboard").setTranslationKey("topleaderboard");
        wealthLeaderboardBlock = new BlockLeaderboard(Material.ROCK, FactionStat.WEALTH).setRegistryName("wealthleaderboard").setTranslationKey("wealthleaderboard");
        notorietyLeaderboardBlock = new BlockLeaderboard(Material.ROCK, FactionStat.NOTORIETY).setRegistryName("notorietyleaderboard").setTranslationKey("notorietyleaderboard");
        legacyLeaderboardBlock = new BlockLeaderboard(Material.ROCK, FactionStat.LEGACY).setRegistryName("legacyleaderboard").setTranslationKey("legacyleaderboard");
        
        topLeaderboardItem = new ItemBlock(topLeaderboardBlock).setRegistryName("topleaderboard").setTranslationKey("topleaderboard");
        wealthLeaderboardItem = new ItemBlock(wealthLeaderboardBlock).setRegistryName("wealthleaderboard").setTranslationKey("wealthleaderboard");
        notorietyLeaderboardItem = new ItemBlock(notorietyLeaderboardBlock).setRegistryName("notorietyleaderboard").setTranslationKey("notorietyleaderboard");
        legacyLeaderboardItem = new ItemBlock(legacyLeaderboardBlock).setRegistryName("legacyleaderboard").setTranslationKey("legacyleaderboard");
        GameRegistry.registerTileEntity(TileEntityLeaderboard.class, new ResourceLocation(Tags.MODID, "leaderboard"));
        
        MinecraftForge.EVENT_BUS.register(this);
	}


	@SubscribeEvent
	public void registerItems(RegistryEvent.Register<Item> event)
	{
		final Item[] items = {
				citadelBlockItem, basicClaimBlockItem, reinforcedClaimBlockItem, siegeCampBlockItem,
                islandCollectorItem,
				topLeaderboardItem, wealthLeaderboardItem, notorietyLeaderboardItem,
				legacyLeaderboardItem,
		};
		IForgeRegistry<Item> registry = event.getRegistry();
		for(Item item : items) {
			registry.register(item);
		}
		WarForgeMod.LOGGER.info("Registered items");
	}
	
	@SubscribeEvent
	public void registerBlocks(RegistryEvent.Register<Block> event)
	{
		final Block[] blocks = {
			citadelBlock, basicClaimBlock, reinforcedClaimBlock, siegeCampBlock, islandCollectorBlock,
			topLeaderboardBlock, wealthLeaderboardBlock, notorietyLeaderboardBlock,
			legacyLeaderboardBlock, statue, dummyTranslusent
		};
		IForgeRegistry<Block> registry = event.getRegistry();
		for(Block block : blocks) {
			registry.register(block);
		}

		WarForgeMod.LOGGER.info("Registered blocks");
	}
}
