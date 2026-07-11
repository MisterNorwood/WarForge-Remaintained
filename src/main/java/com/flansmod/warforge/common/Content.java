package com.flansmod.warforge.common;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.blocks.*;
import com.flansmod.warforge.server.Leaderboard.FactionStat;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class Content
{
	private static final DeferredRegister<Block> BLOCKS =
			DeferredRegister.create(ForgeRegistries.BLOCKS, Tags.MODID);
	private static final DeferredRegister<Item> ITEMS =
			DeferredRegister.create(ForgeRegistries.ITEMS, Tags.MODID);
	private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
			DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Tags.MODID);
	private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
			DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Tags.MODID);
	private static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
			DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Tags.MODID);

	// Particles
	public static final RegistryObject<SimpleParticleType> STAR_CIRCLE_PARTICLE =
			PARTICLE_TYPES.register("star_circle", () -> new SimpleParticleType(false));

	// Blocks
	public static final RegistryObject<Block> CITADEL_BLOCK = BLOCKS.register("citadelblock", BlockCitadel::new);
	public static final RegistryObject<Block> BASIC_CLAIM_BLOCK = BLOCKS.register("basicclaimblock", BlockBasicClaim::new);
	public static final RegistryObject<Block> REINFORCED_CLAIM_BLOCK = BLOCKS.register("reinforcedclaimblock", BlockBasicClaim::new);
	public static final RegistryObject<Block> SIEGE_CAMP_BLOCK = BLOCKS.register("siegecampblock", BlockSiegeCamp::new);
	public static final RegistryObject<Block> ISLAND_COLLECTOR_BLOCK = BLOCKS.register("islandcollector", BlockIslandCollector::new);
	public static final RegistryObject<Block> STATUE = BLOCKS.register("dummy", BlockDummy::new);
	public static final RegistryObject<Block> DUMMY_TRANSLUSENT = BLOCKS.register("dummy_translusent", BlockDummyTransparent::new);
	public static final RegistryObject<Block> TOP_LEADERBOARD_BLOCK = BLOCKS.register("topleaderboard", () -> new BlockLeaderboard(FactionStat.TOTAL));
	public static final RegistryObject<Block> WEALTH_LEADERBOARD_BLOCK = BLOCKS.register("wealthleaderboard", () -> new BlockLeaderboard(FactionStat.WEALTH));
	public static final RegistryObject<Block> NOTORIETY_LEADERBOARD_BLOCK = BLOCKS.register("notorietyleaderboard", () -> new BlockLeaderboard(FactionStat.NOTORIETY));
	public static final RegistryObject<Block> LEGACY_LEADERBOARD_BLOCK = BLOCKS.register("legacyleaderboard", () -> new BlockLeaderboard(FactionStat.LEGACY));
	public static final RegistryObject<Block> FOB_BLOCK = BLOCKS.register("fobblock", BlockFob::new);

	// Block items (statue/dummyTranslusent intentionally have no item)
	public static final RegistryObject<Item> CITADEL_BLOCK_ITEM = ITEMS.register("citadelblock", () -> new BlockItem(CITADEL_BLOCK.get(), new Item.Properties()));
	public static final RegistryObject<Item> BASIC_CLAIM_BLOCK_ITEM = ITEMS.register("basicclaimblock", () -> new BlockItem(BASIC_CLAIM_BLOCK.get(), new Item.Properties()));
	public static final RegistryObject<Item> REINFORCED_CLAIM_BLOCK_ITEM = ITEMS.register("reinforcedclaimblock", () -> new BlockItem(REINFORCED_CLAIM_BLOCK.get(), new Item.Properties()));
	public static final RegistryObject<Item> SIEGE_CAMP_BLOCK_ITEM = ITEMS.register("siegecampblock", () -> new BlockItem(SIEGE_CAMP_BLOCK.get(), new Item.Properties()));
	public static final RegistryObject<Item> ISLAND_COLLECTOR_ITEM = ITEMS.register("islandcollector", () -> new BlockItem(ISLAND_COLLECTOR_BLOCK.get(), new Item.Properties()));
	public static final RegistryObject<Item> TOP_LEADERBOARD_ITEM = ITEMS.register("topleaderboard", () -> new BlockItem(TOP_LEADERBOARD_BLOCK.get(), new Item.Properties()));
	public static final RegistryObject<Item> WEALTH_LEADERBOARD_ITEM = ITEMS.register("wealthleaderboard", () -> new BlockItem(WEALTH_LEADERBOARD_BLOCK.get(), new Item.Properties()));
	public static final RegistryObject<Item> NOTORIETY_LEADERBOARD_ITEM = ITEMS.register("notorietyleaderboard", () -> new BlockItem(NOTORIETY_LEADERBOARD_BLOCK.get(), new Item.Properties()));
	public static final RegistryObject<Item> LEGACY_LEADERBOARD_ITEM = ITEMS.register("legacyleaderboard", () -> new BlockItem(LEGACY_LEADERBOARD_BLOCK.get(), new Item.Properties()));
	public static final RegistryObject<Item> FOB_BLOCK_ITEM = ITEMS.register("fobblock", () -> new BlockItem(FOB_BLOCK.get(), new Item.Properties()));

	// Block entities. Names referenced by the BlockEntity classes via Content.TE_*.get().
	public static final RegistryObject<BlockEntityType<TileEntityCitadel>> TE_CITADEL =
			BLOCK_ENTITIES.register("citadel",
					() -> BlockEntityType.Builder.of(TileEntityCitadel::new, CITADEL_BLOCK.get()).build(null));
	public static final RegistryObject<BlockEntityType<TileEntityBasicClaim>> TE_BASIC_CLAIM =
			BLOCK_ENTITIES.register("basicclaim",
					() -> BlockEntityType.Builder.of(TileEntityBasicClaim::new, BASIC_CLAIM_BLOCK.get()).build(null));
	public static final RegistryObject<BlockEntityType<TileEntityReinforcedClaim>> TE_REINFORCED_CLAIM =
			BLOCK_ENTITIES.register("reinforcedclaim",
					() -> BlockEntityType.Builder.of(TileEntityReinforcedClaim::new, REINFORCED_CLAIM_BLOCK.get()).build(null));
	public static final RegistryObject<BlockEntityType<TileEntitySiegeCamp>> TE_SIEGE_CAMP =
			BLOCK_ENTITIES.register("siegecamp",
					() -> BlockEntityType.Builder.of(TileEntitySiegeCamp::new, SIEGE_CAMP_BLOCK.get()).build(null));
	public static final RegistryObject<BlockEntityType<TileEntityIslandCollector>> TE_ISLAND_COLLECTOR =
			BLOCK_ENTITIES.register("islandcollector",
					() -> BlockEntityType.Builder.of(TileEntityIslandCollector::new, ISLAND_COLLECTOR_BLOCK.get()).build(null));
	public static final RegistryObject<BlockEntityType<TileEntityLeaderboard>> TE_LEADERBOARD =
			BLOCK_ENTITIES.register("leaderboard",
					() -> BlockEntityType.Builder.of(TileEntityLeaderboard::new,
							TOP_LEADERBOARD_BLOCK.get(), WEALTH_LEADERBOARD_BLOCK.get(),
							NOTORIETY_LEADERBOARD_BLOCK.get(), LEGACY_LEADERBOARD_BLOCK.get()).build(null));
	public static final RegistryObject<BlockEntityType<TileEntityDummy>> TE_DUMMY =
			BLOCK_ENTITIES.register("tileentity_dummy",
					() -> BlockEntityType.Builder.of(TileEntityDummy::new, STATUE.get(), DUMMY_TRANSLUSENT.get()).build(null));
	public static final RegistryObject<BlockEntityType<TileEntityFob>> TE_FOB =
			BLOCK_ENTITIES.register("fob",
					() -> BlockEntityType.Builder.of(TileEntityFob::new, FOB_BLOCK.get()).build(null));

	// Creative tab
	public static final RegistryObject<CreativeModeTab> TAB = CREATIVE_TABS.register("main",
			() -> CreativeModeTab.builder()
					.title(Component.translatable("itemGroup." + Tags.MODID))
					.icon(() -> new ItemStack(CITADEL_BLOCK.get()))
					.displayItems((params, output) -> {
						output.accept(CITADEL_BLOCK_ITEM.get());
						output.accept(BASIC_CLAIM_BLOCK_ITEM.get());
						output.accept(REINFORCED_CLAIM_BLOCK_ITEM.get());
						output.accept(SIEGE_CAMP_BLOCK_ITEM.get());
						output.accept(ISLAND_COLLECTOR_ITEM.get());
						output.accept(TOP_LEADERBOARD_ITEM.get());
						output.accept(WEALTH_LEADERBOARD_ITEM.get());
						output.accept(NOTORIETY_LEADERBOARD_ITEM.get());
						output.accept(LEGACY_LEADERBOARD_ITEM.get());
						output.accept(FOB_BLOCK_ITEM.get());
					})
					.build());

	// Plain fields the rest of the mod reads directly. Populated by bake() after registration.
	public static Block citadelBlock, basicClaimBlock, reinforcedClaimBlock, siegeCampBlock, statue, dummyTranslusent;
	public static Item citadelBlockItem, basicClaimBlockItem, reinforcedClaimBlockItem, siegeCampBlockItem;
	public static Block islandCollectorBlock;
	public static Item islandCollectorItem;

	public static Block topLeaderboardBlock, notorietyLeaderboardBlock, wealthLeaderboardBlock, legacyLeaderboardBlock;
	public static Item topLeaderboardItem, notorietyLeaderboardItem, wealthLeaderboardItem, legacyLeaderboardItem;

	public static void register(IEventBus modBus)
	{
		BLOCKS.register(modBus);
		ITEMS.register(modBus);
		BLOCK_ENTITIES.register(modBus);
		CREATIVE_TABS.register(modBus);
		PARTICLE_TYPES.register(modBus);
	}

	// Resolve the plain fields from the RegistryObjects once registration has run.
	public static void bake()
	{
		citadelBlock = CITADEL_BLOCK.get();
		basicClaimBlock = BASIC_CLAIM_BLOCK.get();
		reinforcedClaimBlock = REINFORCED_CLAIM_BLOCK.get();
		siegeCampBlock = SIEGE_CAMP_BLOCK.get();
		islandCollectorBlock = ISLAND_COLLECTOR_BLOCK.get();
		statue = STATUE.get();
		dummyTranslusent = DUMMY_TRANSLUSENT.get();
		topLeaderboardBlock = TOP_LEADERBOARD_BLOCK.get();
		wealthLeaderboardBlock = WEALTH_LEADERBOARD_BLOCK.get();
		notorietyLeaderboardBlock = NOTORIETY_LEADERBOARD_BLOCK.get();
		legacyLeaderboardBlock = LEGACY_LEADERBOARD_BLOCK.get();

		citadelBlockItem = CITADEL_BLOCK_ITEM.get();
		basicClaimBlockItem = BASIC_CLAIM_BLOCK_ITEM.get();
		reinforcedClaimBlockItem = REINFORCED_CLAIM_BLOCK_ITEM.get();
		siegeCampBlockItem = SIEGE_CAMP_BLOCK_ITEM.get();
		islandCollectorItem = ISLAND_COLLECTOR_ITEM.get();
		topLeaderboardItem = TOP_LEADERBOARD_ITEM.get();
		wealthLeaderboardItem = WEALTH_LEADERBOARD_ITEM.get();
		notorietyLeaderboardItem = NOTORIETY_LEADERBOARD_ITEM.get();
		legacyLeaderboardItem = LEGACY_LEADERBOARD_ITEM.get();

		WarForgeMod.LOGGER.info("Registered WarForge content");
	}

	// Retained for the existing CONTENT.preInit() call site in WarForgeMod.
	public void preInit() {}
}
