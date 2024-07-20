package com.flansmod.warforge.common;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraftforge.common.config.Configuration;
import scala.Array;

public class WarForgeConfig 
{
	// Config
	public static Configuration configFile;
	
	// World gen
	public static final String CATEGORY_WORLD_GEN = "WorldGen";
	public static boolean ENABLE_WORLD_GEN = false;
	public static int DENSE_IRON_CELL_SIZE = 64;
	public static int DENSE_IRON_DEPOSIT_RADIUS = 4;
	public static int DENSE_IRON_MIN_INSTANCES_PER_CELL = 1;
	public static int DENSE_IRON_MAX_INSTANCES_PER_CELL = 3;
	public static int DENSE_IRON_MIN_HEIGHT = 28;
	public static int DENSE_IRON_MAX_HEIGHT = 56;
	public static int DENSE_IRON_OUTER_SHELL_RADIUS = 8;
	public static float DENSE_IRON_OUTER_SHELL_CHANCE = 0.1f;
	
	public static int DENSE_GOLD_CELL_SIZE = 128;
	public static int DENSE_GOLD_DEPOSIT_RADIUS = 3;
	public static int DENSE_GOLD_MIN_INSTANCES_PER_CELL = 1;
	public static int DENSE_GOLD_MAX_INSTANCES_PER_CELL = 2;
	public static int DENSE_GOLD_MIN_HEIGHT = 6;
	public static int DENSE_GOLD_MAX_HEIGHT = 26;
	public static int DENSE_GOLD_OUTER_SHELL_RADIUS = 6;
	public static float DENSE_GOLD_OUTER_SHELL_CHANCE = 0.05f;
	
	public static int DENSE_REDSTONE_CELL_SIZE = 1024;
	public static int DENSE_REDSTONE_DEPOSIT_RADIUS = 12;
	public static int DENSE_REDSTONE_MIN_INSTANCES_PER_CELL = 1;
	public static int DENSE_REDSTONE_MAX_INSTANCES_PER_CELL = 1;
	public static int DENSE_REDSTONE_MIN_HEIGHT = 6;
	public static int DENSE_REDSTONE_MAX_HEIGHT = 14;
	public static int DENSE_REDSTONE_OUTER_SHELL_RADIUS = 14;
	public static float DENSE_REDSTONE_OUTER_SHELL_CHANCE = 0.05f;
	
	public static int DENSE_DIAMOND_CELL_SIZE = 128;
	public static int DENSE_DIAMOND_DEPOSIT_RADIUS = 2;
	public static int DENSE_DIAMOND_MIN_INSTANCES_PER_CELL = 1;
	public static int DENSE_DIAMOND_MAX_INSTANCES_PER_CELL = 1;
	public static int DENSE_DIAMOND_MIN_HEIGHT = 1;
	public static int DENSE_DIAMOND_MAX_HEIGHT = 4;
	public static int DENSE_DIAMOND_OUTER_SHELL_RADIUS = 5;
	public static float DENSE_DIAMOND_OUTER_SHELL_CHANCE = 0.025f;
	
	public static int MAGMA_VENT_CELL_SIZE = 64;
	public static int MAGMA_VENT_DEPOSIT_RADIUS = 2;
	public static int MAGMA_VENT_MIN_INSTANCES_PER_CELL = 1;
	public static int MAGMA_VENT_MAX_INSTANCES_PER_CELL = 1;
	public static int MAGMA_VENT_MIN_HEIGHT = 1;
	public static int MAGMA_VENT_MAX_HEIGHT = 4;
	public static int MAGMA_VENT_OUTER_SHELL_RADIUS = 0;
	public static float MAGMA_VENT_OUTER_SHELL_CHANCE = 1.0f;
	
	public static int ANCIENT_OAK_CELL_SIZE = 256;
	public static float ANCIENT_OAK_CHANCE = 0.1f;
	public static float ANCIENT_OAK_HOLE_RADIUS = 24f;
	public static float ANCIENT_OAK_MAX_TRUNK_RADIUS = 8f;
	public static float ANCIENT_OAK_CORE_RADIUS = 2f;
	public static float ANCIENT_OAK_MAX_HEIGHT = 128f;
	
	public static int SLIME_POOL_CELL_SIZE = 512;
	public static int SLIME_POOL_LAKE_RADIUS = 16;
	public static int SLIME_POOL_LAKE_CEILING_HEIGHT = 8;
	public static int SLIME_POOL_MIN_INSTANCES_PER_CELL = 1;
	public static int SLIME_POOL_MAX_INSTANCES_PER_CELL = 2;
	public static int SLIME_POOL_MIN_HEIGHT = 15;
	public static int SLIME_POOL_MAX_HEIGHT = 30;
	
	public static int SHULKER_FOSSIL_CELL_SIZE = 256;
	public static float SHULKER_FOSSIL_MIN_ROTATIONS = 16f;
	public static float SHULKER_FOSSIL_MAX_ROTATIONS = 8f;
	public static float SHULKER_FOSSIL_RADIUS_PER_ROTATION = 3f;
	public static int SHULKER_FOSSIL_MIN_INSTANCES_PER_CELL = 1;
	public static int SHULKER_FOSSIL_MAX_INSTANCES_PER_CELL = 2;
	public static float SHULKER_FOSSIL_DISC_THICKNESS = 8f;
	public static int SHULKER_FOSSIL_MIN_HEIGHT = 12;
	public static int SHULKER_FOSSIL_MAX_HEIGHT = 106;
	
	public static int CLAY_POOL_CHANCE = 32;
	
	public static int QUARTZ_PILLAR_CHANCE = 128;
	
	public static final int HIGHEST_YIELD_ASSUMPTION = 64;
	
	// Claims
	public static final String CATEGORY_CLAIMS = "Claims";
	public static int[] CLAIM_DIM_WHITELIST = new int[]{0};
	public static int CLAIM_STRENGTH_CITADEL = 15;
	public static int CLAIM_STRENGTH_REINFORCED = 10;
	public static int CLAIM_STRENGTH_BASIC = 5;
	public static int SUPPORT_STRENGTH_CITADEL = 3;
	public static int SUPPORT_STRENGTH_REINFORCED = 2;
	public static int SUPPORT_STRENGTH_BASIC = 1;
	
	public static int ATTACK_STRENGTH_SIEGE_CAMP = 1;
	public static float LEECH_PROPORTION_SIEGE_CAMP = 0.25f;
	
	// Yields
	public static final String CATEGORY_YIELDS = "Yields";
	public static float YIELD_DAY_LENGTH = 1.0f; // In real-world hours
	public static float NUM_IRON_PER_DAY_PER_ORE = 0.05f;
	public static boolean IRON_YIELD_AS_ORE = true; // Otherwise, give ingots
	public static float NUM_GOLD_PER_DAY_PER_ORE = 0.05f;
	public static boolean GOLD_YIELD_AS_ORE = true; // Otherwise, give ingots
	public static float NUM_DIAMOND_PER_DAY_PER_ORE = 0.05f;
	public static boolean DIAMOND_YIELD_AS_ORE = false; // Otherwise, give diamonds
	public static float NUM_CLAY_PER_DAY_PER_ORE = 0.05f;
	public static boolean CLAY_YIELD_AS_BLOCKS = false; // Otherwise, clay balls
	public static float NUM_QUARTZ_PER_DAY_PER_ORE = 0.05f;
	public static boolean QUARTZ_YIELD_AS_BLOCKS = false; // Otherwise, quartz pieces
	public static float NUM_OAK_PER_DAY_PER_LOG = 0.05f;
	public static boolean ANCIENT_OAK_YIELD_AS_LOGS = false; // Otherwise, planks 
	public static float NUM_REDSTONE_PER_DAY_PER_ORE = 0.1f;
	public static boolean REDSTONE_YIELD_AS_BLOCKS = false; // Otherwise, dust 
	public static float NUM_SLIME_PER_DAY_PER_ORE = 0.1f;
	public static float NUM_SHULKER_PER_DAY_PER_ORE = 0.01f;
	
	// Sieges
	public static final String CATEGORY_SIEGES = "Sieges";
	public static int SIEGE_SWING_PER_DEFENDER_DEATH = 1;
	public static int SIEGE_SWING_PER_ATTACKER_DEATH = 1;
	public static int SIEGE_SWING_PER_DAY_ELAPSED_BASE = 1;
	public static int SIEGE_SWING_PER_DAY_ELAPSED_NO_ATTACKER_LOGINS = 1;
	public static int SIEGE_SWING_PER_DAY_ELAPSED_NO_DEFENDER_LOGINS = 1;
	public static float SIEGE_DAY_LENGTH = 24.0f; // In real-world hours
	public static float SIEGE_INFO_RADIUS = 200f;
	public static int SIEGE_SWING_PER_DEFENDER_FLAG = 1;
	public static int SIEGE_SWING_PER_ATTACKER_FLAG = 1;
	public static int SIEGE_DIFFICULTY_PER_DEFENDER_FLAG = 3;
	public static boolean SIEGE_CAPTURE = true;
	public static float SIEGE_COOLDOWN_FAIL = 30f; // In minutes
	public static float FLAG_COOLDOWN = 1f; // In minutes
	
	// Notoriety
	public static final String CATEGORY_NOTORIETY = "Notoriety";
	public static int NOTORIETY_PER_PLAYER_KILL = 1;
	public static int NOTORIETY_KILL_CAP_PER_PLAYER = 3;
	//public static int NOTORIETY_PER_DRAGON_KILL = 9;
	public static int NOTORIETY_PER_SIEGE_ATTACK_SUCCESS = 7;
	public static int NOTORIETY_PER_SIEGE_DEFEND_SUCCESS = 10;
	
	// Legacy
	public static final String CATEGORY_LEGACY = "Legacy";
	public static int LEGACY_PER_DAY = 3;
	public static boolean LEGACY_USES_YIELD_TIMER = true;
	
	// Wealth - Vault blocks
	public static String[] VAULT_BLOCK_IDS = new String[] { "minecraft:gold_block" };
	public static ArrayList<Block> VAULT_BLOCKS = new ArrayList<Block>();
	
	public static final String CATEGORY_CLIENT = "Client";
	public static float SHOW_NEW_AREA_TIMER = 200.0f;
    public static int FACTION_NAME_LENGTH_MAX = 32;
	public static boolean BLOCK_ENDER_CHEST = false;
	public static boolean SHOW_YIELD_TIMERS = true;
	public static int CITADEL_MOVE_NUM_DAYS = 7;
	
	public static boolean SHOW_OPPONENT_BORDERS = true;
	public static boolean SHOW_ALLY_BORDERS = true;
	
	// Warps
	public static final String CATEGORY_WARPS = "Warps";
	public static boolean ENABLE_F_HOME_COMMAND = true;
	public static boolean ALLOW_F_HOME_BETWEEN_DIMENSIONS = false;
	public static boolean ENABLE_F_HOME_POTION_EFFECT = false; // TODO
	public static int NUM_TICKS_FOR_WARP_COMMANDS = 20 * 20;
	public static boolean ENABLE_SPAWN_COMMAND = true;
	public static boolean ENABLE_SPAWN_POTION_EFFECT = false; // TODO
	public static boolean ALLOW_SPAWN_BETWEEN_DIMENSIONS = false;
	public static boolean ENABLE_TPA_POTIONS = true;
	

    public static long FACTIONS_BOT_CHANNEL_ID = 799595436154683422L;
    
	public static class ProtectionConfig
	{		
		public boolean BREAK_BLOCKS = true;
		public boolean PLACE_BLOCKS = true;
		public boolean INTERACT = true;
		public boolean USE_ITEM = true;
		public boolean BLOCK_REMOVAL = true;
		public boolean EXPLOSION_DAMAGE = false;
		public boolean PLAYER_TAKE_DAMAGE_FROM_MOB = true;
		public boolean PLAYER_TAKE_DAMAGE_FROM_PLAYER = true;
		public boolean PLAYER_TAKE_DAMAGE_FROM_OTHER = true;
		public boolean PLAYER_DEAL_DAMAGE = true;
		public boolean ALLOW_MOB_SPAWNS = true;
		public boolean ALLOW_MOB_ENTRY = true;
		public boolean ALLOW_MOUNT_ENTITY = true;
		public boolean ALLOW_DISMOUNT_ENTITY = true;
		
		private String[] BLOCK_PLACE_EXCEPTION_IDS = new String[] { "minecraft:torch" };
		private String[] BLOCK_BREAK_EXCEPTION_IDS = new String[] { "minecraft:torch" };
		private String[] BLOCK_INTERACT_EXCEPTION_IDS = new String[] { "minecraft:ender_chest", "warforge:citadelblock", "warforge:basicclaimblock", "warforge:reinforcedclaimblock", "warforge:siegecampblock"   };
		private String[] ITEM_USE_EXCEPTION_IDS = new String[] { "minecraft:snowball" };
		
		public List<Block> BLOCK_PLACE_EXCEPTIONS;
		public List<Block> BLOCK_BREAK_EXCEPTIONS;
		public List<Block> BLOCK_INTERACT_EXCEPTIONS;
		public List<Item> ITEM_USE_EXCEPTIONS;
		
		private List<Block> FindBlocks(String[] input)
		{
			List<Block> output = new ArrayList<Block>(input.length);
			for(String blockID : input)
			{
				Block block = Block.getBlockFromName(blockID);
				if(block != null)
					output.add(block);
			}
			return output;
		}
		
		private List<Item> FindItems(String[] input)
		{
			List<Item> output = new ArrayList<Item>(input.length);
			for(String itemID : input)
			{
				Item item = Item.getByNameOrId(itemID);
				if(item != null)
					output.add(item);
			}
			return output;
		}
		
		public void FindBlocks()
		{
			BLOCK_PLACE_EXCEPTIONS = FindBlocks(BLOCK_PLACE_EXCEPTION_IDS);
			BLOCK_BREAK_EXCEPTIONS = FindBlocks(BLOCK_BREAK_EXCEPTION_IDS);
			BLOCK_INTERACT_EXCEPTIONS = FindBlocks(BLOCK_INTERACT_EXCEPTION_IDS);
			ITEM_USE_EXCEPTIONS = FindItems(ITEM_USE_EXCEPTION_IDS);
		}
		
		public void SyncConfig(String name, String desc)
		{
			String category = name;
			BREAK_BLOCKS = configFile.getBoolean(name + " - Break Blocks", category, BREAK_BLOCKS, "Can players break blocks in " + desc);
			PLACE_BLOCKS = configFile.getBoolean(name + " - Place Blocks", category, PLACE_BLOCKS, "Can players place blocks in " + desc);
			BLOCK_REMOVAL = configFile.getBoolean(name + " - Block Removal", category, BLOCK_REMOVAL, "Can blocks be removed at all in (including from explosions, mobs etc) " + desc);
			EXPLOSION_DAMAGE = configFile.getBoolean(name + " - Explosion Damage", category, EXPLOSION_DAMAGE, "Can explosions damage blocks in " + desc);
			INTERACT = configFile.getBoolean(name + " - Interact", category, INTERACT, "Can players interact with blocks and entities in " + desc);
			USE_ITEM = configFile.getBoolean(name + " - Use Items", category, USE_ITEM, "Can players use items in " + desc);
			PLAYER_TAKE_DAMAGE_FROM_MOB = configFile.getBoolean(name + " - Take Dmg From Mob", category, PLAYER_TAKE_DAMAGE_FROM_MOB, "Can players take mob damage in " + desc);
			PLAYER_TAKE_DAMAGE_FROM_PLAYER = configFile.getBoolean(name + " - Take Dmg From Player", category, PLAYER_TAKE_DAMAGE_FROM_PLAYER, "Can players take damage from other players in " + desc);
			PLAYER_TAKE_DAMAGE_FROM_OTHER = configFile.getBoolean(name + " - Take Any Other Dmg", category, PLAYER_TAKE_DAMAGE_FROM_OTHER, "Can players take damage from any other source in " + desc);
			PLAYER_DEAL_DAMAGE = configFile.getBoolean(name + " - Deal Damage", category, PLAYER_DEAL_DAMAGE, "Can players deal damage in " + desc);
			
			BLOCK_PLACE_EXCEPTION_IDS = configFile.getStringList(name + " - Place Exceptions", category, BLOCK_PLACE_EXCEPTION_IDS, "The block IDs that can still be placed. Has no effect if block placement is allowed anyway");
			BLOCK_BREAK_EXCEPTION_IDS = configFile.getStringList(name + " - Break Exceptions", category, BLOCK_BREAK_EXCEPTION_IDS, "The block IDs that can still be broken. Has no effect if block breaking is allowed anyway");
			BLOCK_INTERACT_EXCEPTION_IDS = configFile.getStringList(name + " - Interact Exceptions", category, BLOCK_INTERACT_EXCEPTION_IDS, "The block IDs that can still be interacted with. Has no effect if interacting is allowed anyway");
			ITEM_USE_EXCEPTION_IDS = configFile.getStringList(name + " - Use Exceptions", category, ITEM_USE_EXCEPTION_IDS, "The item IDs that can still be used. Has no effect if interacting is allowed anyway");
				
			ALLOW_MOB_SPAWNS = configFile.getBoolean(name + " - Allow Mob Spawns", category, ALLOW_MOB_SPAWNS, "Can mobs spawn in " + desc);
			ALLOW_MOB_ENTRY = configFile.getBoolean(name + " - Allow Mob Entry", category, ALLOW_MOB_ENTRY, "Can mobs enter " + desc);

			ALLOW_DISMOUNT_ENTITY = configFile.getBoolean(name + " - Allow Dismount Entity", category, ALLOW_DISMOUNT_ENTITY, "Can players dismount entities " + desc);
			ALLOW_MOUNT_ENTITY = configFile.getBoolean(name + " - Allow Mount Entity", category, ALLOW_MOUNT_ENTITY, "Can players mount entities " + desc);

		}
	}
	
	// Permissions
	public static ProtectionConfig UNCLAIMED = new ProtectionConfig();
	public static ProtectionConfig SAFE_ZONE = new ProtectionConfig();
	public static ProtectionConfig WAR_ZONE = new ProtectionConfig();
	public static ProtectionConfig CITADEL_FRIEND = new ProtectionConfig();
	public static ProtectionConfig CITADEL_FOE = new ProtectionConfig();
	public static ProtectionConfig CLAIM_FRIEND = new ProtectionConfig();
	public static ProtectionConfig CLAIM_FOE = new ProtectionConfig();
	public static ProtectionConfig SIEGECAMP_SIEGER = new ProtectionConfig();
	public static ProtectionConfig SIEGECAMP_OTHER = new ProtectionConfig();	
	
	// Init default perms
	static
	{ 
		SAFE_ZONE.BREAK_BLOCKS = false;					SAFE_ZONE.PLACE_BLOCKS = false;						SAFE_ZONE.INTERACT = false;							SAFE_ZONE.USE_ITEM = false;
		SAFE_ZONE.PLAYER_TAKE_DAMAGE_FROM_MOB = false;	SAFE_ZONE.PLAYER_TAKE_DAMAGE_FROM_PLAYER = false;	SAFE_ZONE.PLAYER_TAKE_DAMAGE_FROM_OTHER = false;	SAFE_ZONE.PLAYER_DEAL_DAMAGE = false;
		SAFE_ZONE.BLOCK_REMOVAL = false;				SAFE_ZONE.EXPLOSION_DAMAGE = false;
		SAFE_ZONE.BLOCK_BREAK_EXCEPTION_IDS = new String[] {};	
		SAFE_ZONE.BLOCK_PLACE_EXCEPTION_IDS = new String[] {};	
		SAFE_ZONE.BLOCK_INTERACT_EXCEPTION_IDS = new String[] { "minecraft:ender_chest", "minecraft:lever", "minecraft:button", "warforge:leaderboard" };
		SAFE_ZONE.ALLOW_MOB_SPAWNS = false;				SAFE_ZONE.ALLOW_MOB_ENTRY = false;
		SAFE_ZONE.ALLOW_MOUNT_ENTITY = false;			SAFE_ZONE.ALLOW_DISMOUNT_ENTITY = false;
		
		WAR_ZONE.BREAK_BLOCKS = false;					WAR_ZONE.PLACE_BLOCKS = false;						WAR_ZONE.INTERACT = true;						WAR_ZONE.USE_ITEM = true;
		WAR_ZONE.PLAYER_TAKE_DAMAGE_FROM_MOB = true;	WAR_ZONE.PLAYER_TAKE_DAMAGE_FROM_PLAYER = true;		WAR_ZONE.PLAYER_TAKE_DAMAGE_FROM_OTHER = true;	WAR_ZONE.PLAYER_DEAL_DAMAGE = true;
		WAR_ZONE.BLOCK_REMOVAL = false;					WAR_ZONE.EXPLOSION_DAMAGE = false;
		WAR_ZONE.BLOCK_BREAK_EXCEPTION_IDS = new String[] { "minecraft:web", "minecraft:tnt", "minecraft:end_crystal" };	
		WAR_ZONE.BLOCK_PLACE_EXCEPTION_IDS = new String[] { "minecraft:web", "minecraft:tnt", "minecraft:end_crystal" };	
		WAR_ZONE.BLOCK_INTERACT_EXCEPTION_IDS = new String[] { "minecraft:ender_chest", "minecraft:lever", "minecraft:button", "warforge:leaderboard" };
	
		CITADEL_FOE.BREAK_BLOCKS = false;				CITADEL_FOE.PLACE_BLOCKS = false;					CITADEL_FOE.INTERACT = false;					CITADEL_FOE.USE_ITEM = false;
		CLAIM_FOE.BREAK_BLOCKS = false;					CLAIM_FOE.PLACE_BLOCKS = false;						CLAIM_FOE.INTERACT = false;						CLAIM_FOE.USE_ITEM = false;
		CITADEL_FOE.ALLOW_MOUNT_ENTITY = false;			CITADEL_FOE.ALLOW_DISMOUNT_ENTITY = false;
		CLAIM_FOE.ALLOW_MOUNT_ENTITY = false;			CLAIM_FOE.ALLOW_DISMOUNT_ENTITY = false;
		
		UNCLAIMED.EXPLOSION_DAMAGE = true;
	}

	public static void SyncConfig(File suggestedFile)
	{
		configFile = new Configuration(suggestedFile);
		
		// Protections
		UNCLAIMED.SyncConfig("Unclaimed", "Unclaimed Chunks");
		SAFE_ZONE.SyncConfig("SafeZone", "Safe Zone");
		WAR_ZONE.SyncConfig("WarZone", "War Zone");
		CITADEL_FRIEND.SyncConfig("CitadelFriend", "Citadels of their Faction");
		CITADEL_FOE.SyncConfig("CitadelFoe", "Citadels of other Factions");
		CLAIM_FRIEND.SyncConfig("ClaimFriend", "Claims of their Faction");
		CLAIM_FOE.SyncConfig("ClaimFoe", "Claims of other Factions");
		//SIEGECAMP_SIEGER.syncConfig("Sieger", "Sieges they started");
		//SIEGECAMP_OTHER.syncConfig("SiegeOther", "Other sieges, defending or neutral");
		
		// World Generation Settings
		ENABLE_WORLD_GEN = configFile.getBoolean("Enable worldgen", CATEGORY_WORLD_GEN, false, "Allow for dense ores to spawn");
		DENSE_IRON_CELL_SIZE = configFile.getInt("Dense Iron - Cell Size", CATEGORY_WORLD_GEN, DENSE_IRON_CELL_SIZE, 8, 4096, "Divide the world into cells of this size and generate 1 or more deposits per cell");
		DENSE_IRON_DEPOSIT_RADIUS = configFile.getInt("Dense Iron - Deposit Radius", CATEGORY_WORLD_GEN, DENSE_IRON_DEPOSIT_RADIUS, 1, 16, "Radius of a deposit");
		DENSE_IRON_MIN_INSTANCES_PER_CELL = configFile.getInt("Dense Iron - Min Deposits Per Cell", CATEGORY_WORLD_GEN, DENSE_IRON_MIN_INSTANCES_PER_CELL, 0, 256, "Minimum number of deposits per cell");
		DENSE_IRON_MAX_INSTANCES_PER_CELL = configFile.getInt("Dense Iron - Max Deposits Per Cell", CATEGORY_WORLD_GEN, DENSE_IRON_MAX_INSTANCES_PER_CELL, 0, 256, "Maximum number of deposits per cell");
		DENSE_IRON_MIN_HEIGHT = configFile.getInt("Dense Iron - Min Height", CATEGORY_WORLD_GEN, DENSE_IRON_MIN_HEIGHT, 0, 256, "Minimum height of deposits");
		DENSE_IRON_MAX_HEIGHT = configFile.getInt("Dense Iron - Max Height", CATEGORY_WORLD_GEN, DENSE_IRON_MAX_HEIGHT, 0, 256, "Maximum height of deposits");
		DENSE_IRON_OUTER_SHELL_RADIUS = configFile.getInt("Dense Iron - Outer Shell Radius", CATEGORY_WORLD_GEN, DENSE_IRON_OUTER_SHELL_RADIUS, 0, 32, "Radius in which to place vanilla ores");
		DENSE_IRON_OUTER_SHELL_CHANCE = configFile.getFloat("Dense Iron - Outer Shell Chance", CATEGORY_WORLD_GEN, DENSE_IRON_OUTER_SHELL_CHANCE, 0f, 1f, "Percent of blocks in outer radius that are vanilla ores");
		
		DENSE_GOLD_CELL_SIZE = configFile.getInt("Dense Gold - Cell Size", CATEGORY_WORLD_GEN, DENSE_GOLD_CELL_SIZE, 8, 4096, "Divide the world into cells of this size and generate 1 or more deposits per cell");
		DENSE_GOLD_DEPOSIT_RADIUS = configFile.getInt("Dense Gold - Deposit Radius", CATEGORY_WORLD_GEN, DENSE_GOLD_DEPOSIT_RADIUS, 1, 16, "Radius of a deposit");
		DENSE_GOLD_MIN_INSTANCES_PER_CELL = configFile.getInt("Dense Gold - Min Deposits Per Cell", CATEGORY_WORLD_GEN, DENSE_GOLD_MIN_INSTANCES_PER_CELL, 0, 256, "Minimum number of deposits per cell");
		DENSE_GOLD_MAX_INSTANCES_PER_CELL = configFile.getInt("Dense Gold - Max Deposits Per Cell", CATEGORY_WORLD_GEN, DENSE_GOLD_MAX_INSTANCES_PER_CELL, 0, 256, "Maximum number of deposits per cell");
		DENSE_GOLD_MIN_HEIGHT = configFile.getInt("Dense Gold - Min Height", CATEGORY_WORLD_GEN, DENSE_GOLD_MIN_HEIGHT, 0, 256, "Minimum height of deposits");
		DENSE_GOLD_MAX_HEIGHT = configFile.getInt("Dense Gold - Max Height", CATEGORY_WORLD_GEN, DENSE_GOLD_MAX_HEIGHT, 0, 256, "Maximum height of deposits");
		DENSE_GOLD_OUTER_SHELL_RADIUS = configFile.getInt("Dense Gold - Outer Shell Radius", CATEGORY_WORLD_GEN, DENSE_GOLD_OUTER_SHELL_RADIUS, 0, 32, "Radius in which to place vanilla ores");
		DENSE_GOLD_OUTER_SHELL_CHANCE = configFile.getFloat("Dense Gold - Outer Shell Chance", CATEGORY_WORLD_GEN, DENSE_GOLD_OUTER_SHELL_CHANCE, 0f, 1f, "Percent of blocks in outer radius that are vanilla ores");

		DENSE_REDSTONE_CELL_SIZE = configFile.getInt("Dense Redstone - Cell Size", CATEGORY_WORLD_GEN, DENSE_REDSTONE_CELL_SIZE, 8, 4096, "Divide the world into cells of this size and generate 1 or more deposits per cell");
		DENSE_REDSTONE_DEPOSIT_RADIUS = configFile.getInt("Dense Redstone - Deposit Radius", CATEGORY_WORLD_GEN, DENSE_REDSTONE_DEPOSIT_RADIUS, 1, 16, "Radius of a deposit");
		DENSE_REDSTONE_MIN_INSTANCES_PER_CELL = configFile.getInt("Dense Redstone - Min Deposits Per Cell", CATEGORY_WORLD_GEN, DENSE_REDSTONE_MIN_INSTANCES_PER_CELL, 0, 256, "Minimum number of deposits per cell");
		DENSE_REDSTONE_MAX_INSTANCES_PER_CELL = configFile.getInt("Dense Redstone - Max Deposits Per Cell", CATEGORY_WORLD_GEN, DENSE_REDSTONE_MAX_INSTANCES_PER_CELL, 0, 256, "Maximum number of deposits per cell");
		DENSE_REDSTONE_MIN_HEIGHT = configFile.getInt("Dense Redstone - Min Height", CATEGORY_WORLD_GEN, DENSE_REDSTONE_MIN_HEIGHT, 0, 256, "Minimum height of deposits");
		DENSE_REDSTONE_MAX_HEIGHT = configFile.getInt("Dense Redstone - Max Height", CATEGORY_WORLD_GEN, DENSE_REDSTONE_MAX_HEIGHT, 0, 256, "Maximum height of deposits");
		DENSE_REDSTONE_OUTER_SHELL_RADIUS = configFile.getInt("Dense Redstone - Outer Shell Radius", CATEGORY_WORLD_GEN, DENSE_REDSTONE_OUTER_SHELL_RADIUS, 0, 32, "Radius in which to place vanilla ores");
		DENSE_REDSTONE_OUTER_SHELL_CHANCE = configFile.getFloat("Dense Redstone - Outer Shell Chance", CATEGORY_WORLD_GEN, DENSE_REDSTONE_OUTER_SHELL_CHANCE, 0f, 1f, "Percent of blocks in outer radius that are vanilla ores");

		
		DENSE_DIAMOND_CELL_SIZE = configFile.getInt("Dense Diamond - Cell Size", CATEGORY_WORLD_GEN, DENSE_DIAMOND_CELL_SIZE, 8, 4096, "Divide the world into cells of this size and generate 1 or more deposits per cell");
		DENSE_DIAMOND_DEPOSIT_RADIUS = configFile.getInt("Dense Diamond - Deposit Radius", CATEGORY_WORLD_GEN, DENSE_DIAMOND_DEPOSIT_RADIUS, 1, 16, "Radius of a deposit");
		DENSE_DIAMOND_MIN_INSTANCES_PER_CELL = configFile.getInt("Dense Diamond - Min Deposits Per Cell", CATEGORY_WORLD_GEN, DENSE_DIAMOND_MIN_INSTANCES_PER_CELL, 0, 256, "Minimum number of deposits per cell");
		DENSE_DIAMOND_MAX_INSTANCES_PER_CELL = configFile.getInt("Dense Diamond - Max Deposits Per Cell", CATEGORY_WORLD_GEN, DENSE_DIAMOND_MAX_INSTANCES_PER_CELL, 0, 256, "Maximum number of deposits per cell");
		DENSE_DIAMOND_MIN_HEIGHT = configFile.getInt("Dense Diamond - Min Height", CATEGORY_WORLD_GEN, DENSE_DIAMOND_MIN_HEIGHT, 0, 256, "Minimum height of deposits");
		DENSE_DIAMOND_MAX_HEIGHT = configFile.getInt("Dense Diamond - Max Height", CATEGORY_WORLD_GEN, DENSE_DIAMOND_MAX_HEIGHT, 0, 256, "Maximum height of deposits");
		DENSE_DIAMOND_OUTER_SHELL_RADIUS = configFile.getInt("Dense Diamond - Outer Shell Radius", CATEGORY_WORLD_GEN, DENSE_DIAMOND_OUTER_SHELL_RADIUS, 0, 32, "Radius in which to place vanilla ores");
		DENSE_DIAMOND_OUTER_SHELL_CHANCE = configFile.getFloat("Dense Diamond - Outer Shell Chance", CATEGORY_WORLD_GEN, DENSE_DIAMOND_OUTER_SHELL_CHANCE, 0f, 1f, "Percent of blocks in outer radius that are vanilla ores");
		
		MAGMA_VENT_CELL_SIZE = configFile.getInt("Magma Vent - Cell Size", CATEGORY_WORLD_GEN, MAGMA_VENT_CELL_SIZE, 8, 4096, "Divide the world into cells of this size and generate 1 or more deposits per cell");
		MAGMA_VENT_DEPOSIT_RADIUS = configFile.getInt("Magma Vent - Deposit Radius", CATEGORY_WORLD_GEN, MAGMA_VENT_DEPOSIT_RADIUS, 1, 16, "Radius of a deposit");
		MAGMA_VENT_MIN_INSTANCES_PER_CELL = configFile.getInt("Magma Vent - Min Deposits Per Cell", CATEGORY_WORLD_GEN, MAGMA_VENT_MIN_INSTANCES_PER_CELL, 0, 256, "Minimum number of deposits per cell");
		MAGMA_VENT_MAX_INSTANCES_PER_CELL = configFile.getInt("Magma Vent - Max Deposits Per Cell", CATEGORY_WORLD_GEN, MAGMA_VENT_MAX_INSTANCES_PER_CELL, 0, 256, "Maximum number of deposits per cell");
		MAGMA_VENT_MIN_HEIGHT = configFile.getInt("Magma Vent - Min Height", CATEGORY_WORLD_GEN, MAGMA_VENT_MIN_HEIGHT, 0, 256, "Minimum height of deposits");
		MAGMA_VENT_MAX_HEIGHT = configFile.getInt("Magma Vent - Max Height", CATEGORY_WORLD_GEN, MAGMA_VENT_MAX_HEIGHT, 0, 256, "Maximum height of deposits");
		MAGMA_VENT_OUTER_SHELL_RADIUS = configFile.getInt("Magma Vent - Outer Shell Radius", CATEGORY_WORLD_GEN, MAGMA_VENT_OUTER_SHELL_RADIUS, 0, 32, "Radius in which to place vanilla ores");
		MAGMA_VENT_OUTER_SHELL_CHANCE = configFile.getFloat("Magma Vent - Outer Shell Chance", CATEGORY_WORLD_GEN, MAGMA_VENT_OUTER_SHELL_CHANCE, 0f, 1f, "Percent of blocks in outer radius that are vanilla ores");

		ANCIENT_OAK_CELL_SIZE = configFile.getInt("Ancient Oak - Cell Size", CATEGORY_WORLD_GEN, ANCIENT_OAK_CELL_SIZE, 8, 4096, "Divide the world into cells of this size and generate 1 or more deposits per cell");
		ANCIENT_OAK_HOLE_RADIUS = configFile.getFloat("Ancient Oak - Hole Radius", CATEGORY_WORLD_GEN, ANCIENT_OAK_HOLE_RADIUS, 0f, 100f, "Radius of the hole dug into the ground for the tree");
		ANCIENT_OAK_MAX_TRUNK_RADIUS = configFile.getFloat("Ancient Oak - Trunk Radius", CATEGORY_WORLD_GEN, ANCIENT_OAK_MAX_TRUNK_RADIUS, 0f, 100f, "Radius of the trunk of regular logs");
		ANCIENT_OAK_CORE_RADIUS = configFile.getFloat("Ancient Oak - Core Radius", CATEGORY_WORLD_GEN, ANCIENT_OAK_CORE_RADIUS, 0f, 100f, "Radius of the core of ancient oak blocks");
		ANCIENT_OAK_MAX_HEIGHT = configFile.getFloat("Ancient Oak - Max Height", CATEGORY_WORLD_GEN, ANCIENT_OAK_MAX_HEIGHT, 0f, 256f, "Max height of the tree");
		ANCIENT_OAK_CHANCE = configFile.getFloat("Ancient Oak - Chance", CATEGORY_WORLD_GEN, ANCIENT_OAK_CHANCE, 0f, 1f, "Chance of the tree spawning in a cell");		
		
		SLIME_POOL_CELL_SIZE = configFile.getInt("Slime Pool - Cell Size", CATEGORY_WORLD_GEN, SLIME_POOL_CELL_SIZE, 8, 4096, "Divide the world into cells of this size and generate 1 or more deposits per cell");
		SLIME_POOL_LAKE_RADIUS = configFile.getInt("Slime Pool - Lake Radius", CATEGORY_WORLD_GEN, SLIME_POOL_LAKE_RADIUS, 1, 64, "Radius of the lake");
		SLIME_POOL_LAKE_CEILING_HEIGHT = configFile.getInt("Slime Pool - Lake Ceiling Height", CATEGORY_WORLD_GEN, SLIME_POOL_LAKE_CEILING_HEIGHT, 1, 64, "Height of the lake");
		SLIME_POOL_MIN_INSTANCES_PER_CELL = configFile.getInt("Slime Pool - Min Deposits Per Cell", CATEGORY_WORLD_GEN, SLIME_POOL_MIN_INSTANCES_PER_CELL, 0, 256, "Minimum number of deposits per cell");
		SLIME_POOL_MAX_INSTANCES_PER_CELL = configFile.getInt("Slime Pool - Max Deposits Per Cell", CATEGORY_WORLD_GEN, SLIME_POOL_MAX_INSTANCES_PER_CELL, 0, 256, "Maximum number of deposits per cell");
		SLIME_POOL_MIN_HEIGHT = configFile.getInt("Slime Pool - Min Height", CATEGORY_WORLD_GEN, SLIME_POOL_MIN_HEIGHT, 0, 256, "Minimum height of deposits");
		SLIME_POOL_MAX_HEIGHT = configFile.getInt("Slime Pool - Max Height", CATEGORY_WORLD_GEN, SLIME_POOL_MAX_HEIGHT, 0, 256, "Maximum height of deposits");
		
		SHULKER_FOSSIL_CELL_SIZE = configFile.getInt("Shulker Fossil - Cell Size", CATEGORY_WORLD_GEN, SHULKER_FOSSIL_CELL_SIZE, 8, 4096, "Divide the world into cells of this size and generate 1 or more deposits per cell");
		SHULKER_FOSSIL_MIN_INSTANCES_PER_CELL = configFile.getInt("Shulker Fossil - Min Deposits Per Cell", CATEGORY_WORLD_GEN, SHULKER_FOSSIL_MIN_INSTANCES_PER_CELL, 0, 256, "Minimum number of deposits per cell");
		SHULKER_FOSSIL_MAX_INSTANCES_PER_CELL = configFile.getInt("Shulker Fossil - Max Deposits Per Cell", CATEGORY_WORLD_GEN, SHULKER_FOSSIL_MAX_INSTANCES_PER_CELL, 0, 256, "Maximum number of deposits per cell");
		SHULKER_FOSSIL_MIN_HEIGHT = configFile.getInt("Shulker Fossil - Min Height", CATEGORY_WORLD_GEN, SHULKER_FOSSIL_MIN_HEIGHT, 0, 256, "Minimum height of deposits");
		SHULKER_FOSSIL_MAX_HEIGHT = configFile.getInt("Shulker Fossil - Max Height", CATEGORY_WORLD_GEN, SHULKER_FOSSIL_MAX_HEIGHT, 0, 256, "Maximum height of deposits");
		SHULKER_FOSSIL_MIN_ROTATIONS = configFile.getFloat("Shulker Fossil - Min Rotations", CATEGORY_WORLD_GEN, SHULKER_FOSSIL_MIN_ROTATIONS, 1f, 16f, "Minimum number of spirals on the fossil");
		SHULKER_FOSSIL_MAX_ROTATIONS = configFile.getFloat("Shulker Fossil - Max Rotations", CATEGORY_WORLD_GEN, SHULKER_FOSSIL_MAX_ROTATIONS, 1f, 16f, "Maximum number of spirals on the fossil");
		SHULKER_FOSSIL_RADIUS_PER_ROTATION = configFile.getFloat("Shulker Fossil - Radius Per Rotation", CATEGORY_WORLD_GEN, SHULKER_FOSSIL_RADIUS_PER_ROTATION, 1f, 16f, "The radius added for each spiral");
		SHULKER_FOSSIL_DISC_THICKNESS = configFile.getFloat("Shulker Fossil - Disc Thickness", CATEGORY_WORLD_GEN, SHULKER_FOSSIL_DISC_THICKNESS, 1f, 16f, "Thickness of the fossil at its widest point");
		
		
		CLAY_POOL_CHANCE = configFile.getInt("Clay Pool Rarity", CATEGORY_WORLD_GEN, CLAY_POOL_CHANCE, 1, 1024, "Chance of a clay pool appearing per chunk");
		
		QUARTZ_PILLAR_CHANCE = configFile.getInt("Quartz Pillar Rarity", CATEGORY_WORLD_GEN, QUARTZ_PILLAR_CHANCE, 1, 1024, "Chance of a quartz pillar appearing per chunk");

		
		// Claim Settings
		CLAIM_DIM_WHITELIST = configFile.get(CATEGORY_CLAIMS, "Claim Dimension Whitelist", CLAIM_DIM_WHITELIST, "In which dimensions should player be able to claim chunks").getIntList();
		CLAIM_STRENGTH_CITADEL = configFile.getInt("Citadel Claim Strength", CATEGORY_CLAIMS, CLAIM_STRENGTH_CITADEL, 1, 1024, "The strength of citadel claims");
		CLAIM_STRENGTH_REINFORCED = configFile.getInt("Reinforced Claim Strength", CATEGORY_CLAIMS, CLAIM_STRENGTH_REINFORCED, 1, 1024, "The strength of reinforced claims");
		CLAIM_STRENGTH_BASIC = configFile.getInt("Basic Claim Strength", CATEGORY_CLAIMS, CLAIM_STRENGTH_BASIC, 1, 1024, "The strength of basic claims");
		SUPPORT_STRENGTH_CITADEL = configFile.getInt("Citadel Support Strength", CATEGORY_CLAIMS, SUPPORT_STRENGTH_CITADEL, 1, 1024, "The support strength a citadel gives to adjacent claims");
		SUPPORT_STRENGTH_REINFORCED = configFile.getInt("Reinforced Support Strength", CATEGORY_CLAIMS, SUPPORT_STRENGTH_REINFORCED, 1, 1024, "The support strength a reinforced claim gives to adjacent claims");
		SUPPORT_STRENGTH_BASIC = configFile.getInt("Basic Support Strength", CATEGORY_CLAIMS, SUPPORT_STRENGTH_BASIC, 1, 1024, "The support strength a basic claim gives to adjacent claims");
		CITADEL_MOVE_NUM_DAYS = configFile.getInt("Days Between Citadel Moves", CATEGORY_CLAIMS, CITADEL_MOVE_NUM_DAYS, 0, 1024, "How many days a faction has to wait to move their citadel again");
		
		// Siege Camp Settings
		ATTACK_STRENGTH_SIEGE_CAMP = configFile.getInt("Siege Camp Attack Strength", CATEGORY_SIEGES, ATTACK_STRENGTH_SIEGE_CAMP, 1, 1024, "How much attack pressure a siege camp exerts on adjacent enemy claims");
		LEECH_PROPORTION_SIEGE_CAMP = configFile.getFloat("Siege Camp Leech Proportion", CATEGORY_SIEGES, LEECH_PROPORTION_SIEGE_CAMP, 0f, 1f, "What proportion of a claim's yields are leeched when a siege camp is set to leech mode");

		// Siege swing parameters
		SIEGE_SWING_PER_DEFENDER_DEATH = configFile.getInt("Siege Swing Per Defender Death", CATEGORY_SIEGES, SIEGE_SWING_PER_DEFENDER_DEATH, 0, 1024, "How much a siege progress swings when a defender dies in the siege");
		SIEGE_SWING_PER_ATTACKER_DEATH = configFile.getInt("Siege Swing Per Attacker Death", CATEGORY_SIEGES, SIEGE_SWING_PER_ATTACKER_DEATH, 0, 1024, "How much a siege progress swings when an attacker dies in the siege");
		SIEGE_SWING_PER_DAY_ELAPSED_BASE = configFile.getInt("Siege Swing Per Day", CATEGORY_SIEGES, SIEGE_SWING_PER_DAY_ELAPSED_BASE, 0, 1024, "How much a siege progress swings each day (see below). This happens regardless of logins");
		SIEGE_SWING_PER_DAY_ELAPSED_NO_ATTACKER_LOGINS = configFile.getInt("Siege Swing Per Day Without Attacker Logins", CATEGORY_SIEGES, SIEGE_SWING_PER_DAY_ELAPSED_NO_ATTACKER_LOGINS, 0, 1024, "How much a siege progress swings when no attackers have logged on for a day (see below)");
		SIEGE_SWING_PER_DAY_ELAPSED_NO_DEFENDER_LOGINS = configFile.getInt("Siege Swing Per Day Without Defender Logins", CATEGORY_SIEGES, SIEGE_SWING_PER_DAY_ELAPSED_NO_DEFENDER_LOGINS, 0, 1024, "How much a siege progress swings when no defenders have logged on for a day (see below)");
		SIEGE_DAY_LENGTH = configFile.getFloat("Siege Day Length", CATEGORY_SIEGES, SIEGE_DAY_LENGTH, 0.0001f, 100000f, "The length of a day for siege login purposes, in real-world hours.");
		SIEGE_INFO_RADIUS = configFile.getFloat("Siege Info Radius", CATEGORY_SIEGES, SIEGE_INFO_RADIUS, 1f, 1000f, "The range at which you see siege information. (Capped by the server setting)");
		SIEGE_SWING_PER_DEFENDER_DEATH = configFile.getInt("Siege Swing Per Defender Death", CATEGORY_SIEGES, SIEGE_SWING_PER_DEFENDER_DEATH, 0, 1024, "How much a siege progress swings when a defender dies in the siege");
		SIEGE_SWING_PER_DEFENDER_FLAG = configFile.getInt("Siege Swing Per Defender Flag", CATEGORY_SIEGES, SIEGE_SWING_PER_DEFENDER_FLAG, 0, 1024, "How much the siege swings per defender flag per day");
		SIEGE_COOLDOWN_FAIL = configFile.getFloat("Cooldown between sieges after failure", CATEGORY_SIEGES, SIEGE_COOLDOWN_FAIL, 0, 100000f, "Cooldown between sieges, in minutes");
		FLAG_COOLDOWN = configFile.getFloat("Cooldown between Flag move", CATEGORY_SIEGES, FLAG_COOLDOWN, 0, 100000f, "Cooldown between flag moves, in minutes");
		SIEGE_SWING_PER_ATTACKER_FLAG = configFile.getInt("Siege Swing Per Attacker Flag", CATEGORY_SIEGES, SIEGE_SWING_PER_ATTACKER_FLAG, 0, 1024, "How much the siege swings per attacker flag per day");
		SIEGE_DIFFICULTY_PER_DEFENDER_FLAG = configFile.getInt("Siege Difficulty Reinforcement Per Defender Flag", CATEGORY_SIEGES, SIEGE_DIFFICULTY_PER_DEFENDER_FLAG, 0, 1024, "How much having a defender flag at a base reinforces the difficulty of the siege for the attackers");
		SIEGE_CAPTURE = configFile.getBoolean("Siege Captures", CATEGORY_SIEGES, SIEGE_CAPTURE, "Does a successful siege convert the claim");
		
		// Vault parameters
		VAULT_BLOCK_IDS = configFile.getStringList("Valuable Blocks", Configuration.CATEGORY_GENERAL, VAULT_BLOCK_IDS, "The block IDs that count towards the value of your citadel's vault");
		
		// Yield paramters
		NUM_IRON_PER_DAY_PER_ORE = configFile.getFloat("#Iron Per Day Per Ore", CATEGORY_YIELDS, NUM_IRON_PER_DAY_PER_ORE, 0.001f, 1000f, "For each dense iron ore block in a claim, how many resources do players get per yield timer");
		NUM_GOLD_PER_DAY_PER_ORE = configFile.getFloat("#Gold Per Day Per Ore", CATEGORY_YIELDS, NUM_GOLD_PER_DAY_PER_ORE, 0.001f, 1000f, "For each dense gold ore block in a claim, how many resources do players get per yield timer");
		NUM_DIAMOND_PER_DAY_PER_ORE = configFile.getFloat("#Diamond Per Day Per Ore", CATEGORY_YIELDS, NUM_DIAMOND_PER_DAY_PER_ORE, 0.001f, 1000f, "For each dense diamond ore block in a claim, how many resources do players get per yield timer");
		NUM_QUARTZ_PER_DAY_PER_ORE = configFile.getFloat("#Quartz Per Day Per Ore", CATEGORY_YIELDS, NUM_QUARTZ_PER_DAY_PER_ORE, 0.001f, 1000f, "For each dense quartz ore block in a claim, how many resources do players get per yield timer");
		NUM_CLAY_PER_DAY_PER_ORE = configFile.getFloat("#Clay Per Day Per Dense Clay", CATEGORY_YIELDS, NUM_CLAY_PER_DAY_PER_ORE, 0.001f, 1000f, "For each dense clay block in a claim, how many resources do players get per yield timer");
		NUM_OAK_PER_DAY_PER_LOG = configFile.getFloat("#Log Per Day Per Ancient Oak", CATEGORY_YIELDS, NUM_OAK_PER_DAY_PER_LOG, 0.001f, 1000f, "For each ancient oak block in a claim, how many resources do players get per yield timer");
		NUM_REDSTONE_PER_DAY_PER_ORE = configFile.getFloat("#Redstone Per Day Per Ore", CATEGORY_YIELDS, NUM_REDSTONE_PER_DAY_PER_ORE, 0.001f, 1000f, "For each dense redstone ore block in a claim, how many resources do players get per yield timer");
		NUM_SLIME_PER_DAY_PER_ORE = configFile.getFloat("#Slime Per Day Per Block", CATEGORY_YIELDS, NUM_SLIME_PER_DAY_PER_ORE, 0.001f, 1000f, "For each dense slime block in a claim, how many resources do players get per yield timer");
		IRON_YIELD_AS_ORE = configFile.getBoolean("Iron Yield As Ore", CATEGORY_YIELDS, IRON_YIELD_AS_ORE, "If true, dense iron ore gives ore blocks. If false, it gives ingots");
		GOLD_YIELD_AS_ORE = configFile.getBoolean("Gold Yield As Ore", CATEGORY_YIELDS, GOLD_YIELD_AS_ORE, "If true, dense gold ore gives ore blocks. If false, it gives ingots");
		DIAMOND_YIELD_AS_ORE = configFile.getBoolean("Diamond Yield As Ore", CATEGORY_YIELDS, DIAMOND_YIELD_AS_ORE, "If true, dense diamond ore gives ore blocks. If false, it gives diamonds");
		CLAY_YIELD_AS_BLOCKS = configFile.getBoolean("Clay Yield As Blocks", CATEGORY_YIELDS, CLAY_YIELD_AS_BLOCKS, "If true, dense clay gives clay blocks. If false, it gives clay balls");
		QUARTZ_YIELD_AS_BLOCKS = configFile.getBoolean("Quartz Yield As Blocks", CATEGORY_YIELDS, QUARTZ_YIELD_AS_BLOCKS, "If true, dense quartz ore gives quartz blocks. If false, it gives quartz items");
		ANCIENT_OAK_YIELD_AS_LOGS = configFile.getBoolean("Ancient Oak Yield As Logs", CATEGORY_YIELDS, ANCIENT_OAK_YIELD_AS_LOGS, "If true, ancient oak gives logs. If false, it gives planks");
		REDSTONE_YIELD_AS_BLOCKS = configFile.getBoolean("Redstone Yield As Blocks", CATEGORY_YIELDS, REDSTONE_YIELD_AS_BLOCKS, "If true, redstone ore gives redstone blocks. If false, it gives redstone dust");
		YIELD_DAY_LENGTH = configFile.getFloat("Yield Day Length", CATEGORY_YIELDS, YIELD_DAY_LENGTH, 0.0001f, 100000f, "The length of time between yields, in real-world hours.");	
		
		// Notoriety
		NOTORIETY_PER_PLAYER_KILL = configFile.getInt("Notoriety gain per PVP kill", CATEGORY_NOTORIETY, NOTORIETY_PER_PLAYER_KILL, 0, 1024, "How much notoriety a player earns for their faction when killing another player");
		NOTORIETY_PER_SIEGE_ATTACK_SUCCESS = configFile.getInt("Notoriety gain per siege attack win", CATEGORY_NOTORIETY, NOTORIETY_PER_SIEGE_ATTACK_SUCCESS, 0, 1024, "How much notoriety a faction earns when successfully winning an siege as attacker");
		NOTORIETY_PER_SIEGE_DEFEND_SUCCESS = configFile.getInt("Notoriety gain per siege defend win", CATEGORY_NOTORIETY, NOTORIETY_PER_SIEGE_DEFEND_SUCCESS, 0, 1024, "How much notoriety a faction earns when successfully defending a siege");
		NOTORIETY_KILL_CAP_PER_PLAYER = configFile.getInt("Max # kills per player", CATEGORY_NOTORIETY, NOTORIETY_KILL_CAP_PER_PLAYER, 0, 1024, "How many times a faction can kill the same player and still get points");
		// Legacy
		LEGACY_PER_DAY = configFile.getInt("Legacy gain per day", CATEGORY_LEGACY, LEGACY_PER_DAY, 0, 1024, "How much legacy a faction gets for having at least one player on");
		LEGACY_USES_YIELD_TIMER = configFile.getBoolean("Legacy uses yield timer", CATEGORY_LEGACY, LEGACY_USES_YIELD_TIMER, "If true, legacy triggers every yield timer. Otherwise, every siege timer");
				
		// Visual
		SHOW_NEW_AREA_TIMER = configFile.getFloat("New Area Timer", CATEGORY_CLIENT, SHOW_NEW_AREA_TIMER, 0.0f, 1000f, "How many in-game ticks to show the 'You have entered {faction}' message for.");
		FACTION_NAME_LENGTH_MAX = configFile.getInt("Max Faction Name Length", Configuration.CATEGORY_GENERAL, FACTION_NAME_LENGTH_MAX, 3, 128, "How many characters long can a faction name be.");
		SHOW_OPPONENT_BORDERS = configFile.getBoolean("Show Opponent Chunk Borders", Configuration.CATEGORY_GENERAL, SHOW_OPPONENT_BORDERS, "Turns the in-world border rendering on/off for opponent chunks");
		SHOW_ALLY_BORDERS = configFile.getBoolean("Show Ally Chunk Borders", Configuration.CATEGORY_GENERAL, SHOW_ALLY_BORDERS, "Turns the in-world border rendering on/off for ally chunks");
		SHOW_YIELD_TIMERS = configFile.getBoolean("Show yield timers", CATEGORY_CLIENT, SHOW_YIELD_TIMERS, "Whether to show a readout of the time until the next yield / siege in top left of your screen");
		
		// Other permissions
		BLOCK_ENDER_CHEST = configFile.getBoolean("Disable Ender Chest", Configuration.CATEGORY_GENERAL, BLOCK_ENDER_CHEST, "Prevent players from opening ender chests");
		ENABLE_TPA_POTIONS = configFile.getBoolean("Enable TPA Potions", Configuration.CATEGORY_GENERAL, ENABLE_TPA_POTIONS, "Allow players to craft and consume /tpa and /tpaccept style potions");
		
		//Warps
		ENABLE_F_HOME_COMMAND = configFile.getBoolean("Enable /f home Command", CATEGORY_WARPS, ENABLE_F_HOME_COMMAND, "Allow players to use /f home to teleport to their citadel");
		ENABLE_F_HOME_POTION_EFFECT = configFile.getBoolean("Enable /f home Potion", CATEGORY_WARPS, ENABLE_F_HOME_POTION_EFFECT, "Allow players to craft a potion that takes them to their citadel");
		ALLOW_F_HOME_BETWEEN_DIMENSIONS = configFile.getBoolean("Allow /f home across dimensions", CATEGORY_WARPS, ALLOW_F_HOME_BETWEEN_DIMENSIONS, "Allow players to use /f home when in a different dimension to their citadel");
		ENABLE_SPAWN_COMMAND = configFile.getBoolean("Enable /spawn Command", CATEGORY_WARPS, ENABLE_SPAWN_COMMAND, "Allow players to use /spawn to teleport to the world spawn");
		ENABLE_SPAWN_POTION_EFFECT = configFile.getBoolean("Enable /spawn Potion", CATEGORY_WARPS, ENABLE_SPAWN_POTION_EFFECT, "Allow players to craft a potion that takes them to the world spawn");
		ALLOW_SPAWN_BETWEEN_DIMENSIONS = configFile.getBoolean("Allow /spawn across dimensions", CATEGORY_WARPS, ALLOW_SPAWN_BETWEEN_DIMENSIONS, "Allow players to use /spawn when in a different dimension to the world spawn");
		NUM_TICKS_FOR_WARP_COMMANDS = configFile.getInt("Num Ticks for Warps", CATEGORY_WARPS, NUM_TICKS_FOR_WARP_COMMANDS, 0, 20 * 60 * 5, "How many ticks must the player stand still for a warp command to take effect");
	
		
		String botChannelString = configFile.getString("Discord Bot Channel ID", Configuration.CATEGORY_GENERAL, "" + FACTIONS_BOT_CHANNEL_ID, "https://github.com/Chikachi/DiscordIntegration/wiki/IMC-Feature");
		FACTIONS_BOT_CHANNEL_ID = Long.parseLong(botChannelString);
		
		if(configFile.hasChanged())
			configFile.save();
	}
}
