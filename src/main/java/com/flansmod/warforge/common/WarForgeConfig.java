package com.flansmod.warforge.common;

import com.flansmod.warforge.api.Time;
import com.flansmod.warforge.api.vein.Quality;
import com.flansmod.warforge.common.network.PacketSyncConfig;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

import static com.flansmod.warforge.client.util.ScreenSpaceUtil.ScreenPos;
import static com.flansmod.warforge.common.WarForgeMod.VEIN_HANDLER;

public class WarForgeConfig {
    public static final String CATEGORY_CLAIMS = "Claims";

    // Yields
    public static final String CATEGORY_YIELDS = "Yields";
    public static final String CATEGORY_SIEGES = "Sieges";

    // Notoriety
    public static final String CATEGORY_NOTORIETY = "Notoriety";

    // Legacy
    public static final String CATEGORY_LEGACY = "Legacy";
    public static final String CATEGORY_CLIENT = "Client";

    // Warps
    public static final String CATEGORY_WARPS = "Warps";
    public static final String CATEGORY_DISPLAY = "Display";

    // Debug / diagnostics
    public static final String CATEGORY_DEBUG = "Debug";

    // Alliances
    public static final String CATEGORY_ALLIANCES = "Alliances";

    // Config
    public static Configuration configFile;
    public static boolean DO_FANCY_RENDERING = true;
    public static boolean SHOW_OPPONENT_BORDERS = true;
    public static boolean SHOW_ALLY_BORDERS = true;
    public static float HUD_VERT_CUTOFF_PERCENT = 0.40f;

    // Yields/ Vein
    public static float YIELD_DAY_LENGTH = 1.0f; // In real-world hours
    public static long VEIN_MEMBER_DISPLAY_TIME_MS = 1000;
    public static float POOR_QUAL_MULT = 0.5f;
    public static float FAIR_QUAL_MULT = 1f;
    public static float RICH_QUAL_MULT = 2f;

    // Claims
    public static boolean ENABLE_CITADEL_UPGRADES = false;
    public static int[] CLAIM_DIM_WHITELIST = new int[]{0};
    public static int CLAIM_STRENGTH_CITADEL = 15;
    public static int CLAIM_STRENGTH_REINFORCED = 10;
    public static int CLAIM_STRENGTH_BASIC = 5;
    public static int SUPPORT_STRENGTH_CITADEL = 3;

    public static int SUPPORT_STRENGTH_REINFORCED = 2;
    public static int SUPPORT_STRENGTH_BASIC = 1;
    public static int FORCE_LOADED_CHUNKS_TOTAL = 8;
    public static int MAX_CLAIMS_PER_FACTION = -1;
    public static int CLAIM_MANAGER_RADIUS = 4;
    public static int ISLAND_COLLECTOR_SLOTS = 100;
    public static boolean ENABLE_OFFLINE_RAID_PROTECTION = true;
    public static int OFFLINE_RAID_PROTECTION_HOURS = 24;
    public static int ATTACK_STRENGTH_SIEGE_CAMP = 1;
    public static float LEECH_PROPORTION_SIEGE_CAMP = 0.25f;
    public static boolean ENABLE_ISOLATED_CLAIMS = true;
    public static boolean BLOCK_FOREIGN_FLUID_INFLOW = true;
    public static boolean BLOCK_FOREIGN_PISTON_PUSH = true;
    public static int MIN_DISTANCE_BETWEEN_FACTIONS = 1;
    public static String[] INSURANCE_BLACKLIST_IDS = new String[]{"minecraft:*shulker_box", "appliedenergistics2:*cell*"};
    public static String[] DEFAULT_FLAG_IDS = new String[]{
            "white", "light_gray", "gray", "black", "red", "orange", "yellow", "lime",
            "green", "cyan", "light_blue", "blue", "purple", "magenta", "pink", "brown"
    };
    public static String[] CUSTOM_FLAG_ALLOWLIST = new String[]{"*"};

    // Sieges
    public static boolean SIEGE_ENABLE_NEW_TIMER = true;
    public static byte SIEGE_MOMENTUM_MAX = 4;
    public static int SIEGE_MOMENTUM_DURATION = 60;  //Minutes
    public static Map<Byte, Integer> SIEGE_MOMENTUM_TIME = new HashMap<>();
    public static int SIEGE_SWING_PER_DEFENDER_DEATH = 1;
    public static int SIEGE_SWING_PER_ATTACKER_DEATH = 1;
    public static int SIEGE_SWING_PER_DAY_ELAPSED_BASE = 1;
    public static int SIEGE_SWING_PER_DAY_ELAPSED_NO_ATTACKER_LOGINS = 1;
    public static int SIEGE_SWING_PER_DAY_ELAPSED_NO_DEFENDER_LOGINS = 1;
    public static float SIEGE_DAY_LENGTH = 24.0f; // In real-world hours
    public static float SIEGE_INFO_RADIUS = 200f;
    public static int SIEGE_SWING_PER_DEFENDER_FLAG = 1;
    public static int SIEGE_SWING_PER_ATTACKER_FLAG = 1;
    public static int SIEGE_DIFF_PER_MEMBER = 1;
    public static boolean SIEGE_CAPTURE = false;
    public static int SIEGE_COOLDOWN_FAIL = 30; // in minutes;
    public static float FLAG_COOLDOWN = 1f; // In minutes
    public static int MAX_SIEGES = 3;
    public static int ATTACKER_DESERTION_TIMER = 180; // in seconds
    public static int DEFENDER_DESERTION_TIMER = 300; // in seconds (5 mins by default)
    public static int ATTACKER_CONQUERED_CHUNK_PERIOD = 3600000; // in ms (one hour by default)
    public static int DEFENDER_CONQUERED_CHUNK_PERIOD = 7200000; // in ms (2h by default)
    public static int COMBAT_LOG_THRESHOLD = 10000; // in ms (10s by default)
    public static int LIVE_QUIT_TIMER = 900000; // in ms (15min by default)
    public static int QUITTER_FAIL_TIMER = 300000; // in ms (5 min by default)
    public static int MAX_OFFLINE_PLAYER_COUNT_MINIMUM = 0; // # players which can be on before playerCount dropping to 0 subsequently is marked as a live quit; Exclusively used when negative
    public static float MAX_OFFLINE_PLAYER_PERCENT = 0.5f; // % member count which must be online at some point during a siege before live quit penalties apply
    public static int VERTICAL_SIEGE_DIST = 40; // inclusive distance in blocks siege can be placed/started from/on a potential target claim
    public static int SIEGE_BATTLE_RADIUS = 1;
    public static int SIEGE_SIEGED_RADIUS = 1;
    public static boolean SIEGE_COUNT_ALL_ZONE_DEATHS = false;
    public static boolean ENABLE_SIEGE_GRACE_PERIOD = true;
    public static int SIEGE_GRACE_PERIOD_HOURS = 24;
    public static int SIEGE_ATTACKER_RADIUS = 1; // number of chunks player can be away from siege chunk in both directions
    public static int SIEGE_DEFENDER_RADIUS = 15;

    // UI-declared (camp-less) sieges
    public static boolean SIEGE_ALLOW_UI_DECLARE = true;
    public static int SIEGE_DECLARE_MAX_RANGE = 4; // chunks (chebyshev) the target may be from the start-from chunk
    public static boolean SIEGE_DECLARE_REQUIRE_PRESENCE = true;

    // Alliances
    public static int ALLIANCE_TRUCE_DURATION_MINUTES = 60; // truce length after a broken alliance (0 disables the truce)
    public static int MAX_ALLIES = 10; // maximum simultaneous alliances per faction; -1 for unlimited
    public static int NOTORIETY_PER_PLAYER_KILL = 1;
    public static int NOTORIETY_KILL_CAP_PER_PLAYER = 3;
    //public static int NOTORIETY_PER_DRAGON_KILL = 9;
    public static int NOTORIETY_PER_SIEGE_ATTACK_SUCCESS = 7;
    public static int NOTORIETY_PER_SIEGE_DEFEND_SUCCESS = 10;
    public static int LEGACY_PER_DAY = 3;
    public static boolean LEGACY_USES_YIELD_TIMER = true;
    public static boolean MODERN_WARFARE_MODELS = false;

    // Wealth - Vault blocks
    public static String[] VAULT_BLOCK_IDS = new String[]{"minecraft:gold_block"};
    public static ArrayList<Block> VAULT_BLOCKS = new ArrayList<Block>();
    public static float SHOW_NEW_AREA_TIMER = 200.0f;
    public static int INVITE_DECAY_TIME = 5;
    public static int RANDOM_BORDER_REDRAW_DENOMINATOR = 5;
    public static int BORDER_RENDER_DISTANCE = 0;
    public static final int BORDER_SYNC_MAX_RADIUS = 48;
    public static int FACTION_NAME_LENGTH_MAX = 32;
    public static String[] FACTION_NAME_BANLIST = new String[]{"admin", "mod", "staff"};
    public static boolean BLOCK_ENDER_CHEST = false;
    public static boolean SHOW_YIELD_TIMERS = true;
    public static int CITADEL_MOVE_NUM_DAYS = 7;

    // Display
    public static boolean FACTION_PREFIX_IN_CHAT = true;
    public static boolean FACTION_PREFIX_IN_TABLIST = true;

    // JourneyMap claim border overlay (optional compat)
    public static final int JM_MODE_DISABLED = 0;
    public static final int JM_MODE_AUTO = 1;
    public static final int JM_MODE_PLAYER = 2;
    public static int JOURNEYMAP_CLAIM_MODE = JM_MODE_DISABLED;
    public static int JOURNEYMAP_VEIN_MODE = JM_MODE_DISABLED;
    public static int JOURNEYMAP_VEIN_AUTO_RADIUS = 24;

    public static int parseJourneyMapMode(String value) {
        if (value == null) return JM_MODE_DISABLED;
        switch (value.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "AUTO": return JM_MODE_AUTO;
            case "PLAYER": return JM_MODE_PLAYER;
            default: return JM_MODE_DISABLED;
        }
    }

    public static boolean ENABLE_F_HOME_COMMAND = true;
    public static boolean ALLOW_F_HOME_BETWEEN_DIMENSIONS = false;
    public static boolean ENABLE_F_HOME_POTION_EFFECT = false; // TODO
    public static int NUM_TICKS_FOR_WARP_COMMANDS = 20 * 20;
    public static boolean ENABLE_SPAWN_COMMAND = true;
    public static boolean ENABLE_SPAWN_POTION_EFFECT = false; // TODO
    public static boolean ALLOW_SPAWN_BETWEEN_DIMENSIONS = false;
    public static boolean ENABLE_TPA_POTIONS = true;

    // When enabled, logs every server-side setBlockState that lands inside a claimed chunk.
    // Diagnostic aid for finding mods that mutate protected terrain outside the explosion-event path.
    public static boolean DEBUG_TRACE_SETBLOCK = false;

    public static ScreenPos POS_TIMERS = ScreenPos.BOTTOM_RIGHT;
    public static ScreenPos POS_VEIN_INDICATOR = ScreenPos.TOP;
    public static ScreenPos POS_TOAST_INDICATOR = ScreenPos.TOP;
    public static ScreenPos POS_SIEGE = ScreenPos.TOP;

    public static long FACTIONS_BOT_CHANNEL_ID = 799595436154683422L;
    // Permissions
    public static ProtectionConfig UNCLAIMED = new ProtectionConfig();
    public static ProtectionConfig SAFE_ZONE = new ProtectionConfig();
    public static ProtectionConfig WAR_ZONE = new ProtectionConfig();
    public static ProtectionConfig CITADEL_FRIEND = new ProtectionConfig();
    public static ProtectionConfig CITADEL_FOE = new ProtectionConfig();
    public static ProtectionConfig CLAIM_FRIEND = new ProtectionConfig();
    public static ProtectionConfig CLAIM_ALLY = new ProtectionConfig();
    public static ProtectionConfig CLAIM_FOE = new ProtectionConfig();
    public static ProtectionConfig SIEGECAMP_SIEGER = new ProtectionConfig();
    public static ProtectionConfig SIEGECAMP_OTHER = new ProtectionConfig();
    public static ProtectionConfig CLAIM_DEFENDED = new ProtectionConfig();
    public static ProtectionConfig SIEGED_FRIEND = new ProtectionConfig();
    public static ProtectionConfig SIEGED_FOE = new ProtectionConfig();
    public static ProtectionConfig WAR_FRIEND = new ProtectionConfig();
    public static ProtectionConfig WAR_FOE = new ProtectionConfig();

    // Init default perms
    static {
        SAFE_ZONE.BREAK_BLOCKS = false;
        SAFE_ZONE.PLACE_BLOCKS = false;
        SAFE_ZONE.INTERACT = false;
        SAFE_ZONE.USE_ITEM = false;
        SAFE_ZONE.PLAYER_TAKE_DAMAGE_FROM_MOB = false;
        SAFE_ZONE.PLAYER_TAKE_DAMAGE_FROM_PLAYER = false;
        SAFE_ZONE.PLAYER_TAKE_DAMAGE_FROM_OTHER = false;
        SAFE_ZONE.PLAYER_DEAL_DAMAGE = false;
        SAFE_ZONE.BLOCK_REMOVAL = false;
        SAFE_ZONE.EXPLOSION_DAMAGE = false;
        SAFE_ZONE.BLOCK_BREAK_WHITELIST_IDS = new String[]{};
        SAFE_ZONE.BLOCK_PLACE_WHITELIST_IDS = new String[]{};
        SAFE_ZONE.BLOCK_INTERACT_WHITELIST_IDS = new String[]{"minecraft:ender_chest", "minecraft:lever", "minecraft:button", "warforge:leaderboard"};
        SAFE_ZONE.ALLOW_MOB_SPAWNS = false;
        SAFE_ZONE.ALLOW_MOB_ENTRY = false;
        SAFE_ZONE.ALLOW_MOUNT_ENTITY = false;
        SAFE_ZONE.ALLOW_DISMOUNT_ENTITY = false;

        WAR_ZONE.BREAK_BLOCKS = false;
        WAR_ZONE.PLACE_BLOCKS = false;
        WAR_ZONE.INTERACT = true;
        WAR_ZONE.USE_ITEM = true;
        WAR_ZONE.PLAYER_TAKE_DAMAGE_FROM_MOB = true;
        WAR_ZONE.PLAYER_TAKE_DAMAGE_FROM_PLAYER = true;
        WAR_ZONE.PLAYER_TAKE_DAMAGE_FROM_OTHER = true;
        WAR_ZONE.PLAYER_DEAL_DAMAGE = true;
        WAR_ZONE.BLOCK_REMOVAL = false;
        WAR_ZONE.EXPLOSION_DAMAGE = false;
        WAR_ZONE.BLOCK_BREAK_WHITELIST_IDS = new String[]{"minecraft:web", "minecraft:tnt", "minecraft:end_crystal"};
        WAR_ZONE.BLOCK_PLACE_WHITELIST_IDS = new String[]{"minecraft:web", "minecraft:tnt", "minecraft:end_crystal"};
        WAR_ZONE.BLOCK_INTERACT_WHITELIST_IDS = new String[]{"minecraft:ender_chest", "minecraft:lever", "minecraft:button", "warforge:leaderboard"};

        CITADEL_FOE.BREAK_BLOCKS = false;
        CITADEL_FOE.PLACE_BLOCKS = false;
        CITADEL_FOE.INTERACT = false;
        CITADEL_FOE.USE_ITEM = false;
        CLAIM_FOE.BREAK_BLOCKS = false;
        CLAIM_FOE.PLACE_BLOCKS = false;
        CLAIM_FOE.INTERACT = false;
        CLAIM_FOE.USE_ITEM = false;
        CITADEL_FOE.ALLOW_MOUNT_ENTITY = false;
        CITADEL_FOE.ALLOW_DISMOUNT_ENTITY = false;
        CLAIM_FOE.ALLOW_MOUNT_ENTITY = false;
        CLAIM_FOE.ALLOW_DISMOUNT_ENTITY = false;

        // Allies (when the owning faction enables ally interaction): use & interact, but no build/place.
        CLAIM_ALLY.BREAK_BLOCKS = false;
        CLAIM_ALLY.PLACE_BLOCKS = false;

        UNCLAIMED.EXPLOSION_DAMAGE = true;

        SIEGECAMP_SIEGER.BREAK_BLOCKS = false;
        SIEGECAMP_SIEGER.PLACE_BLOCKS = false;
        SIEGECAMP_SIEGER.INTERACT = true;
        SIEGECAMP_SIEGER.USE_ITEM = true;
        SIEGECAMP_SIEGER.BLOCK_REMOVAL = true;
        SIEGECAMP_SIEGER.EXPLOSION_DAMAGE = true;
        SIEGECAMP_SIEGER.BLOCK_BREAK_WHITELIST_IDS = new String[]{"minecraft:torch", "warforge:siegecampblock", "gregtech:machine"};
        SIEGECAMP_SIEGER.BLOCK_PLACE_WHITELIST_IDS = new String[]{"minecraft:torch", "minecraft:web", "minecraft:tnt", "minecraft:end_crystal"};

        //WarForgeMod.VEIN_MAP.defaultReturnValue(null);

        CLAIM_DEFENDED.BREAK_BLOCKS = true;

        SIEGED_FRIEND.BREAK_BLOCKS = true; SIEGED_FRIEND.PLACE_BLOCKS = true;
        SIEGED_FRIEND.INTERACT = true; SIEGED_FRIEND.USE_ITEM = true;
        SIEGED_FRIEND.BLOCK_REMOVAL = true; SIEGED_FRIEND.EXPLOSION_DAMAGE = true;

        SIEGED_FOE.BREAK_BLOCKS = true; SIEGED_FOE.PLACE_BLOCKS = true;
        SIEGED_FOE.INTERACT = true; SIEGED_FOE.USE_ITEM = true;
        SIEGED_FOE.BLOCK_REMOVAL = true; SIEGED_FOE.EXPLOSION_DAMAGE = true;

        WAR_FRIEND.BREAK_BLOCKS = true; WAR_FRIEND.PLACE_BLOCKS = true;
        WAR_FRIEND.INTERACT = true; WAR_FRIEND.USE_ITEM = true;
        WAR_FRIEND.BLOCK_REMOVAL = true;

        WAR_FOE.BREAK_BLOCKS = false; WAR_FOE.PLACE_BLOCKS = false;
        WAR_FOE.INTERACT = false; WAR_FOE.USE_ITEM = true;
        WAR_FOE.EXPLOSION_DAMAGE = false;

    }

    public static void syncConfig(File suggestedFile) {
        configFile = new Configuration(suggestedFile);

        // Protections
        UNCLAIMED.SyncConfig("Unclaimed", "Unclaimed Chunks");
        SAFE_ZONE.SyncConfig("SafeZone", "Safe Zone");
        WAR_ZONE.SyncConfig("WarZone", "War Zone");
        CITADEL_FRIEND.SyncConfig("CitadelFriend", "Citadels of their Faction");
        CITADEL_FOE.SyncConfig("CitadelFoe", "Citadels of other Factions");
        CLAIM_FRIEND.SyncConfig("ClaimFriend", "Claims of their Faction");
        CLAIM_ALLY.SyncConfig("ClaimAlly", "Claims of allied Factions (only applies when the owning faction enables ally interaction)");
        CLAIM_FOE.SyncConfig("ClaimFoe", "Claims of other Factions");
        SIEGECAMP_SIEGER.SyncConfig("Sieger", "Sieges they started");
        SIEGECAMP_OTHER.SyncConfig("SiegeOther", "Other sieges, defending or neutral");
        CLAIM_DEFENDED.SyncConfig("ClaimDefended", "Claims under sieged faction that are not under direct siege");
        SIEGED_FRIEND.SyncConfig("SiegedFriend", "Inner sieged zone, member of the defending faction");
        SIEGED_FOE.SyncConfig("SiegedFoe", "Inner sieged zone, attacker or other faction (chunk protection disabled)");
        WAR_FRIEND.SyncConfig("WarFriend", "Outer war zone, member of the defending faction");
        WAR_FOE.SyncConfig("WarFoe", "Outer war zone, attacker or other faction (kills count, cannot break blocks)");

        // Claim Settings
        CLAIM_DIM_WHITELIST = configFile.get(CATEGORY_CLAIMS, "Claim Dimension Whitelist", CLAIM_DIM_WHITELIST, "In which dimensions should player be able to claim chunks").getIntList();
        CLAIM_STRENGTH_CITADEL = configFile.getInt("Citadel Claim Strength", CATEGORY_CLAIMS, CLAIM_STRENGTH_CITADEL, 1, 1024, "The strength of citadel claims");
        CLAIM_STRENGTH_REINFORCED = configFile.getInt("Reinforced Claim Strength", CATEGORY_CLAIMS, CLAIM_STRENGTH_REINFORCED, 1, 1024, "The strength of reinforced claims");
        CLAIM_STRENGTH_BASIC = configFile.getInt("Basic Claim Strength", CATEGORY_CLAIMS, CLAIM_STRENGTH_BASIC, 1, 1024, "The strength of basic claims");
        SUPPORT_STRENGTH_CITADEL = configFile.getInt("Citadel Support Strength", CATEGORY_CLAIMS, SUPPORT_STRENGTH_CITADEL, 1, 1024, "The support strength a citadel gives to adjacent claims");
        SUPPORT_STRENGTH_REINFORCED = configFile.getInt("Reinforced Support Strength", CATEGORY_CLAIMS, SUPPORT_STRENGTH_REINFORCED, 1, 1024, "The support strength a reinforced claim gives to adjacent claims");
        SUPPORT_STRENGTH_BASIC = configFile.getInt("Basic Support Strength", CATEGORY_CLAIMS, SUPPORT_STRENGTH_BASIC, 1, 1024, "The support strength a basic claim gives to adjacent claims");
        FORCE_LOADED_CHUNKS_TOTAL = configFile.getInt("Force-loaded Chunks Total", CATEGORY_CLAIMS, FORCE_LOADED_CHUNKS_TOTAL, 0, 1024, "Total chunks each faction can force-load. Ignored when citadel upgrade system is enabled; the per-level 'loaded_chunks' value from upgrade_levels.yml is used instead.");
        MAX_CLAIMS_PER_FACTION = configFile.getInt("Max Claims Per Faction", CATEGORY_CLAIMS, MAX_CLAIMS_PER_FACTION, -1, 1000000, "Maximum number of chunks a single faction may claim. Set to -1 for unlimited. When the citadel upgrade system is enabled, the per-level limit is applied in addition to this cap.");
        CLAIM_MANAGER_RADIUS = configFile.getInt("Claim Manager Radius", CATEGORY_CLAIMS, CLAIM_MANAGER_RADIUS, 1, 12, "Square radius in chunks shown in the claim manager UI.");
        ISLAND_COLLECTOR_SLOTS = configFile.getInt("Island Collector Slot Count", CATEGORY_CLAIMS, ISLAND_COLLECTOR_SLOTS, 1, 1024, "Number of pull-only storage slots in the faction yield collector block. Shrinking this on an existing world relocates any items that no longer fit into remaining slots.");
        ENABLE_OFFLINE_RAID_PROTECTION = configFile.getBoolean("Enable Offline Raid Protection", CATEGORY_CLAIMS, ENABLE_OFFLINE_RAID_PROTECTION, "If enabled, factions cannot be sieged for a limited period after all their members go offline.");
        OFFLINE_RAID_PROTECTION_HOURS = configFile.getInt("Offline Raid Protection Hours", CATEGORY_CLAIMS, OFFLINE_RAID_PROTECTION_HOURS, 0, 168, "How many hours a faction remains protected from new sieges after the last member goes offline.");
        CITADEL_MOVE_NUM_DAYS = configFile.getInt("Days Between Citadel Moves", CATEGORY_CLAIMS, CITADEL_MOVE_NUM_DAYS, 0, 1024, "How many days a faction has to wait to move their citadel again");
        ENABLE_CITADEL_UPGRADES = configFile.getBoolean("Enable Citadel Upgrade System", CATEGORY_CLAIMS, false, "Applies claim limits that require upgrading to extend your faction's claim limit");
        ENABLE_ISOLATED_CLAIMS = configFile.getBoolean("Enabled Isolated Claims", CATEGORY_CLAIMS, ENABLE_ISOLATED_CLAIMS, "If true, forces all newly placed claim blocks, excluding siege blocks and citadels, to be directly adjacent to a pre-existing claim.");
        BLOCK_FOREIGN_FLUID_INFLOW = configFile.getBoolean("Block Foreign Fluid Inflow", CATEGORY_CLAIMS, BLOCK_FOREIGN_FLUID_INFLOW,
            "If true, liquids cannot flow from a chunk into a differently-claimed chunk (stops lavacast/water griefing across claim borders).");
        BLOCK_FOREIGN_PISTON_PUSH = configFile.getBoolean("Block Foreign Piston Push", CATEGORY_CLAIMS, BLOCK_FOREIGN_PISTON_PUSH,
            "If true, pistons cannot push or pull blocks across a claim border into/out of a differently-claimed chunk.");
        MIN_DISTANCE_BETWEEN_FACTIONS = configFile.getInt("Minimum Distance Between Opposing Factions", CATEGORY_CLAIMS, MIN_DISTANCE_BETWEEN_FACTIONS, 0, 64,
            "Minimum gap in chunks (Chebyshev/square radius) separating a new claim from an opposing faction's claims. 0 disables.");
        INSURANCE_BLACKLIST_IDS = configFile.getStringList("Insurance Blacklist", CATEGORY_CLAIMS, INSURANCE_BLACKLIST_IDS, "Registry-id patterns blocked from the faction insurance stash. Supports '*' wildcards, for example 'minecraft:*shulker_box' or 'appliedenergistics2:*cell*'.");
        DEFAULT_FLAG_IDS = configFile.getStringList("Available Default Flags", CATEGORY_CLAIMS, DEFAULT_FLAG_IDS, "Default built-in flags that can be chosen by factions. Each id is rendered client-side as a solid colour square/rectangle. Use a vanilla dye colour name (e.g. red, light_blue) or a 6-digit hex colour (e.g. ff8800).");
        CUSTOM_FLAG_ALLOWLIST = configFile.getStringList("Available Custom Flags", CATEGORY_CLAIMS, CUSTOM_FLAG_ALLOWLIST, "Custom server-side flags allowed from resources/warforge/flags. Use '*' to allow all validated custom flags or list exact ids without extension.");

        // Siege Camp Settings
        ATTACK_STRENGTH_SIEGE_CAMP = configFile.getInt("Siege Camp Attack Strength", CATEGORY_SIEGES, ATTACK_STRENGTH_SIEGE_CAMP, 1, 1024, "How much attack pressure a siege camp exerts on adjacent enemy claims");
        LEECH_PROPORTION_SIEGE_CAMP = configFile.getFloat("Siege Camp Leech Proportion", CATEGORY_SIEGES, LEECH_PROPORTION_SIEGE_CAMP, 0f, 1f, "What proportion of a claim's yields are leeched when a siege camp is set to leech mode");
        MAX_SIEGES = configFile.getInt("Siege Camp Max Count Per Faction", CATEGORY_SIEGES, MAX_SIEGES, 1, 1000, "How many sieges each faction is allowed to have, with any additional siege camps being unable to be placed by members");
        ATTACKER_DESERTION_TIMER = configFile.getInt("Attacker Desertion Timer [s]", CATEGORY_SIEGES, ATTACKER_DESERTION_TIMER, 0, Integer.MAX_VALUE, "The number of seconds a siege can idle with no attackers in it before any action occurs. Setting to 0 results in checks being run every tick.");
        DEFENDER_DESERTION_TIMER = configFile.getInt("Defender Desertion Timer [s]", CATEGORY_SIEGES, DEFENDER_DESERTION_TIMER, 0, Integer.MAX_VALUE, "The number of seconds a siege can be undefended before any action occurs. Setting to 0 results in checks being run every tick.");
        ATTACKER_CONQUERED_CHUNK_PERIOD = configFile.getInt("Attacker Conquered Chunk Grace Period [ms]", CATEGORY_SIEGES, ATTACKER_CONQUERED_CHUNK_PERIOD, 0, Integer.MAX_VALUE, "The number of milliseconds to permit placement within a chunk only by the faction who last won a siege on it. Setting to 0 results in no grace period.");
        DEFENDER_CONQUERED_CHUNK_PERIOD = configFile.getInt("Defender Conquered Chunk Grace Period [ms]", CATEGORY_SIEGES, DEFENDER_CONQUERED_CHUNK_PERIOD, 0, Integer.MAX_VALUE, "The number of milliseconds to deny sieging or claiming in previously sieged chunk in which the siege was won by the defenders. Setting to 0 results in no grace period.");
        COMBAT_LOG_THRESHOLD = configFile.getInt("Time to Combat Log Action [ms]", CATEGORY_SIEGES, COMBAT_LOG_THRESHOLD, 0, Integer.MAX_VALUE, "The number of milliseconds before enforcement action is taken when a player leaves during a siege on any of their claims.");
        LIVE_QUIT_TIMER = configFile.getInt("Time to Live Quit Siege [ms]", CATEGORY_SIEGES, LIVE_QUIT_TIMER, 0, Integer.MAX_VALUE, "The number of milliseconds before a defending team which went offline after a siege against them has begun is considered to have quit, forfeiting the siege.");
        SIEGE_ALLOW_UI_DECLARE = configFile.getBoolean("Allow UI Siege Declaration", CATEGORY_SIEGES, SIEGE_ALLOW_UI_DECLARE, "If enabled, officers can declare a siege directly from the claim map UI (consuming a siege camp block item) without physically placing a siege camp.");
        SIEGE_DECLARE_MAX_RANGE = configFile.getInt("UI Siege Declaration Max Range", CATEGORY_SIEGES, SIEGE_DECLARE_MAX_RANGE, 1, 64, "When declaring a siege from the claim map UI, the maximum chunk distance (chebyshev) allowed between the chosen start-from chunk and the target chunk.");
        SIEGE_DECLARE_REQUIRE_PRESENCE = configFile.getBoolean("UI Siege Requires Attacker Presence", CATEGORY_SIEGES, SIEGE_DECLARE_REQUIRE_PRESENCE, "If enabled, a UI-declared (camp-less) siege fails if no attacking faction member stays within the attacker radius of the start-from chunk for the attacker desertion timer, mirroring physical siege camps.");

        ALLIANCE_TRUCE_DURATION_MINUTES = configFile.getInt("Alliance Truce Duration [min]", CATEGORY_ALLIANCES, ALLIANCE_TRUCE_DURATION_MINUTES, 0, 525600, "When an alliance is broken, both factions enter a truce during which they cannot siege or harm each other. Length in minutes; 0 disables the truce.");
        MAX_ALLIES = configFile.getInt("Max Allies Per Faction", CATEGORY_ALLIANCES, MAX_ALLIES, -1, 1000, "Maximum number of simultaneous alliances a faction may hold. Set to -1 for unlimited.");
        QUITTER_FAIL_TIMER = configFile.getInt("Time to Quitters Failing Offline Siege [ms]", CATEGORY_SIEGES, QUITTER_FAIL_TIMER, 0, Integer.MAX_VALUE, "The number of milliseconds before a defending team which failed a siege defense due to quitting is failed for subsequent sieges, if still offline.");
        MAX_OFFLINE_PLAYER_COUNT_MINIMUM = configFile.getInt("Max Players Before Online Status", CATEGORY_SIEGES, MAX_OFFLINE_PLAYER_COUNT_MINIMUM, Integer.MIN_VALUE, Integer.MAX_VALUE, "A static minimum for the maximum number of players which can have been online at some point during a siege before the faction online player count dropping to 0 indicates a live quit. Negative values override the percent");
        MAX_OFFLINE_PLAYER_PERCENT = configFile.getFloat("Max Player % Before Online Status", CATEGORY_SIEGES, MAX_OFFLINE_PLAYER_PERCENT, 0, 1.0F, "The maximum percent of players in a faction which can be online at some point during a siege before the online count dropping to 0 indicates a live quit.");
        VERTICAL_SIEGE_DIST = configFile.getInt("Maximum Vertical Siege Radius [Inclusive]", CATEGORY_SIEGES, VERTICAL_SIEGE_DIST, 0, Integer.MAX_VALUE, "The number of blocks up or down a siege block can be placed from a potential target, inclusively. Sieges may also only be started on targets within this vertical radius.");
        SIEGE_BATTLE_RADIUS = configFile.getInt("Battle Square Chunk Radius From Siege", CATEGORY_SIEGES, SIEGE_BATTLE_RADIUS, 0, Integer.MAX_VALUE, "Outer 'War' zone: chunks (square radius) from each active siege camp that count as the active battle area for siege progress and siege-zone protections.");
        SIEGE_SIEGED_RADIUS = configFile.getInt("Sieged Square Chunk Radius From Siege", CATEGORY_SIEGES, SIEGE_SIEGED_RADIUS, 0, Integer.MAX_VALUE, "Inner 'Sieged' zone: chunks (square radius) from each active siege camp where chunk protection is fully disabled.");
        SIEGE_COUNT_ALL_ZONE_DEATHS = configFile.getBoolean("Count All Zone Deaths", CATEGORY_SIEGES, SIEGE_COUNT_ALL_ZONE_DEATHS, "If true, ANY death of a participant inside the War/Sieged zone counts toward the siege goal.");
        ENABLE_SIEGE_GRACE_PERIOD = configFile.getBoolean("Enable New Faction Siege Grace", CATEGORY_CLAIMS, ENABLE_SIEGE_GRACE_PERIOD, "If enabled, freshly created factions cannot be sieged for a grace period.");
        SIEGE_GRACE_PERIOD_HOURS = configFile.getInt("New Faction Siege Grace Hours", CATEGORY_CLAIMS, SIEGE_GRACE_PERIOD_HOURS, 0, 8760, "How many hours a newly created faction stays unsiegeable.");
        SIEGE_ATTACKER_RADIUS = configFile.getInt("Attacker Square Chunk Radius From Siege", CATEGORY_SIEGES, SIEGE_ATTACKER_RADIUS, 0, Integer.MAX_VALUE, "The number of chunks in any direction from the siege block that an attacker can be in to prevent siege abandon.");
        SIEGE_DEFENDER_RADIUS = configFile.getInt("Defender Square Chunk Radius From Siege", CATEGORY_SIEGES, SIEGE_DEFENDER_RADIUS, 0, Integer.MAX_VALUE, "The number of chunks in any direction from the siege block that a defender can be in to prevent siege abandon.");

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
        SIEGE_COOLDOWN_FAIL = configFile.getInt("Cooldown between sieges after failure", CATEGORY_SIEGES, SIEGE_COOLDOWN_FAIL, 0, 100000, "Cooldown between sieges, in minutes");
        FLAG_COOLDOWN = configFile.getFloat("Cooldown between Flag move", CATEGORY_SIEGES, FLAG_COOLDOWN, 0, 100000f, "Cooldown between flag moves, in minutes");
        SIEGE_SWING_PER_ATTACKER_FLAG = configFile.getInt("Siege Swing Per Attacker Flag", CATEGORY_SIEGES, SIEGE_SWING_PER_ATTACKER_FLAG, 0, 1024, "How much the siege swings per attacker flag per day");
        SIEGE_DIFF_PER_MEMBER = configFile.getInt("Siege Difficulty Reinforcement Per Defender Flag", CATEGORY_SIEGES, SIEGE_DIFF_PER_MEMBER, 0, 1024, "How much having a defender flag at a base reinforces the difficulty of the siege for the attackers");
        SIEGE_CAPTURE = configFile.getBoolean("Siege Captures", CATEGORY_SIEGES, SIEGE_CAPTURE, "Does a successful siege convert the claim");
        SIEGE_ENABLE_NEW_TIMER = configFile.getBoolean("Enable Per-Siege timer", CATEGORY_SIEGES, SIEGE_ENABLE_NEW_TIMER, "Enable new per siege time system, instead of a central siege day, each siege has its own timer, starting with the siege");

        //New siege stuff
        SIEGE_MOMENTUM_DURATION = configFile.getInt("Siege momentum duration", CATEGORY_SIEGES, 60, 1, Integer.MAX_VALUE, "Time the momentum lasts");
        // Momentum multipliers (define per momentum level)
        String[] defaults = new String[]{
                "0=15:00",
                "1=10:00",
                "2=8:30",
                "3=5:00",
                "4=2:30"
        };
        String[] values = configFile.getStringList("SiegeMomentumMultipliers", CATEGORY_SIEGES, defaults,
                "List of momentum multipliers in the form level=multiplier");

        SIEGE_MOMENTUM_TIME.clear();
        for (String s : values) {
            String[] split = s.split("=");
            if (split.length == 2) {
                try {
                    Time time = Time.fromString(split[1]);
                    byte level = Byte.parseByte(split[0]);
                    SIEGE_MOMENTUM_TIME.put(level, (int) (time.getMs() / 1000));
                } catch (RuntimeException e) {
                    e.printStackTrace();
                }
            }
        }

        // Vault parameters
        VAULT_BLOCK_IDS = configFile.getStringList("Valuable Blocks", Configuration.CATEGORY_GENERAL, VAULT_BLOCK_IDS, "The block IDs that count towards the value of your citadel's vault");

        // Yield parameters
        String qualityText = "The global multiplier for %s quality veins which all veins fall back to if they do not have an override.";
        YIELD_DAY_LENGTH = configFile.getFloat("Yield Day Length", CATEGORY_YIELDS, YIELD_DAY_LENGTH, 0.0001f, 100000f, "The length of time between yields, in real-world hours.");
        POOR_QUAL_MULT = configFile.getFloat("Global Poor Quality Multiplier", CATEGORY_YIELDS, POOR_QUAL_MULT, 0f, 512f, String.format(qualityText, Quality.POOR));
        FAIR_QUAL_MULT = configFile.getFloat("Global Fair Quality Multiplier", CATEGORY_YIELDS, FAIR_QUAL_MULT, 0f, 512f, String.format(qualityText, Quality.FAIR));
        RICH_QUAL_MULT = configFile.getFloat("Global Rich Quality Multiplier", CATEGORY_YIELDS, RICH_QUAL_MULT, 0f, 512f, String.format(qualityText, Quality.RICH));

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
        FACTION_NAME_BANLIST = configFile.getStringList("Faction Name Banlist", Configuration.CATEGORY_GENERAL, FACTION_NAME_BANLIST, "Case-insensitive substrings that disallow faction names from being created or renamed.");
        SHOW_OPPONENT_BORDERS = configFile.getBoolean("Show Opponent Chunk Borders", Configuration.CATEGORY_GENERAL, SHOW_OPPONENT_BORDERS, "Turns the in-world border rendering on/off for opponent chunks");
        SHOW_ALLY_BORDERS = configFile.getBoolean("Show Ally Chunk Borders", Configuration.CATEGORY_GENERAL, SHOW_ALLY_BORDERS, "Turns the in-world border rendering on/off for ally chunks");
        SHOW_YIELD_TIMERS = configFile.getBoolean("Show yield timers", CATEGORY_CLIENT, SHOW_YIELD_TIMERS, "Whether to show a readout of the time until the next yield / siege in top left of your screen");

        FACTION_PREFIX_IN_CHAT = configFile.getBoolean("Faction Prefix In Chat", CATEGORY_DISPLAY, FACTION_PREFIX_IN_CHAT, "If enabled, a player's faction name is shown as a coloured prefix before their name in chat.");
        FACTION_PREFIX_IN_TABLIST = configFile.getBoolean("Faction Prefix In Tab List", CATEGORY_DISPLAY, FACTION_PREFIX_IN_TABLIST, "If enabled, a player's faction name is shown as a coloured prefix before their name in the tab player list.");
        JOURNEYMAP_CLAIM_MODE = parseJourneyMapMode(configFile.getString("JourneyMap Claim Display Mode", CATEGORY_DISPLAY, "DISABLED",
                "Draws a faction-coloured border over claimed chunks on JourneyMap, if it is installed. Server-authoritative.\n"
                        + "DISABLED = no overlay.\n"
                        + "AUTO = every claim is shown to everyone globally, updated live regardless of whether a player has visited the area.\n"
                        + "PLAYER = a chunk's claim only appears (and updates) on a player's map once that player has observed (loaded) that chunk; unobserved claims are never sent to the client.",
                new String[]{"DISABLED", "AUTO", "PLAYER"}));
        JOURNEYMAP_VEIN_MODE = parseJourneyMapMode(configFile.getString("JourneyMap Vein Display Mode", CATEGORY_DISPLAY, "DISABLED",
                "Draws each chunk's ore vein as a small item icon on JourneyMap, if it is installed. Server-authoritative.\n"
                        + "DISABLED = no overlay.\n"
                        + "AUTO = veins for a wide radius around each player are sent automatically as they move; no need to enter each chunk.\n"
                        + "PLAYER = a chunk's vein only appears once the player has walked into and loaded that chunk; unobserved veins are never sent to the client.",
                new String[]{"DISABLED", "AUTO", "PLAYER"}));
        JOURNEYMAP_VEIN_AUTO_RADIUS = configFile.getInt("JourneyMap Vein Auto Radius", CATEGORY_DISPLAY, JOURNEYMAP_VEIN_AUTO_RADIUS, 1, 128,
                "In AUTO vein mode, how many chunks around each player to reveal veins for. Larger values reveal more of the map but cause the server to generate and store veins over a wider area.");
        VEIN_MEMBER_DISPLAY_TIME_MS = configFile.getInt("Vein Member Display Time", CATEGORY_CLIENT, (int) VEIN_MEMBER_DISPLAY_TIME_MS, 100, Integer.MAX_VALUE, "The time in milliseconds for which each member of a vein will be displayed when it is being cycled through, to the precision allowed by the client tick system.");
        MODERN_WARFARE_MODELS = configFile.getBoolean("Enable modern warfare models", Configuration.CATEGORY_CLIENT, MODERN_WARFARE_MODELS, "Enable modern warfare models, instead of medival more vanilla-friendly models");
        HUD_VERT_CUTOFF_PERCENT = configFile.getFloat("HUD Vertical cutoff", CATEGORY_CLIENT, HUD_VERT_CUTOFF_PERCENT, 0.0f, 1.0f, "What percent of the entire screen resolution from the top must certain displays (such as vein info) be before they stop rendering. Set to 0.0 to disable all relevant displays, or 1.0 to turn this off.");

        POS_TIMERS = ScreenPos.fromString(configFile.getString("Yield timer position", CATEGORY_CLIENT, "BOTTOM_RIGHT", "Position of the yield timers"));
        POS_SIEGE = ScreenPos.fromString(configFile.getString("Siege status position", CATEGORY_CLIENT, "TOP", "Position of the siege status"));
        POS_TOAST_INDICATOR = ScreenPos.fromString(configFile.getString("Toast indicator position", CATEGORY_CLIENT, "TOP", "Position of the  toast indicator"));
        POS_VEIN_INDICATOR = ScreenPos.fromString(configFile.getString("Chunk vein indicator position", CATEGORY_CLIENT, "TOP", "Position of the  chunk vein indicator"));

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

        // Graphics controls
        DO_FANCY_RENDERING = configFile.getBoolean("Enable WarForge Fancy Rendering", CATEGORY_CLIENT, DO_FANCY_RENDERING, "Controls whether or not fancy graphics will be enabled for this mod's rendering.");
        RANDOM_BORDER_REDRAW_DENOMINATOR = configFile.getInt("Random Border Redraw Denominator", CATEGORY_CLIENT, RANDOM_BORDER_REDRAW_DENOMINATOR, 1, Integer.MAX_VALUE, "Sets the bound on a random number generated, which when equal to 0 calls the border redraw. Effectively 1/this chance to redraw every frame");
        BORDER_RENDER_DISTANCE = configFile.getInt("Border Render Distance", CATEGORY_CLIENT, BORDER_RENDER_DISTANCE, 0, 256, "Max chunk distance at which claim borders are rendered. 0 = follow client render distance.");

        String botChannelString = configFile.getString("Discord Bot Channel ID", Configuration.CATEGORY_GENERAL, "" + FACTIONS_BOT_CHANNEL_ID, "https://github.com/Chikachi/DiscordIntegration/wiki/IMC-Feature");
        FACTIONS_BOT_CHANNEL_ID = Long.parseLong(botChannelString);

        // Debug / diagnostics
        DEBUG_TRACE_SETBLOCK = configFile.getBoolean("Trace setBlockState In Claimed Chunks", CATEGORY_DEBUG, DEBUG_TRACE_SETBLOCK,
                "DEBUG/diagnostic only. When enabled, logs every server-side setBlockState that lands inside a claimed chunk, " +
                        "together with the position, the owning claim, the block being placed and the calling code. " +
                        "Use this to identify mods that mutate protected terrain outside the explosion-event path " +
                        "(e.g. GregTech Industrial TNT, AE2 Tiny TNT). Very spammy - leave disabled in production.");

        if (configFile.hasChanged())
            configFile.save();
    }

    //New system to deal with that config sync
    public static PacketSyncConfig createConfigSyncPacket() {
        var packet = new PacketSyncConfig();
        var compoundNBT = new NBTTagCompound();
        compoundNBT.setBoolean("enableUpgrades", ENABLE_CITADEL_UPGRADES);
        compoundNBT.setBoolean("newSiegeTimer", SIEGE_ENABLE_NEW_TIMER);
        compoundNBT.setInteger("maxMomentum", SIEGE_MOMENTUM_MAX);
        compoundNBT.setInteger("timeMomentum", SIEGE_MOMENTUM_DURATION);
        compoundNBT.setString("momentumMap", SIEGE_MOMENTUM_TIME.toString());
        compoundNBT.setFloat("poorQualMult", POOR_QUAL_MULT);
        compoundNBT.setFloat("fairQualMult", FAIR_QUAL_MULT);
        compoundNBT.setFloat("richQualMult", RICH_QUAL_MULT);
        compoundNBT.setShort("megachunkLength", VEIN_HANDLER.megachunkLength);
        compoundNBT.setInteger("battleSiegeRadius", SIEGE_BATTLE_RADIUS);
        compoundNBT.setInteger("atkSiegeRadius", SIEGE_ATTACKER_RADIUS);
        compoundNBT.setInteger("defSiegeRadius", SIEGE_DEFENDER_RADIUS);
        compoundNBT.setBoolean("offlineRaidProtection", ENABLE_OFFLINE_RAID_PROTECTION);
        compoundNBT.setInteger("offlineRaidProtectionHours", OFFLINE_RAID_PROTECTION_HOURS);
        compoundNBT.setString("insuranceBlacklist", String.join("\n", INSURANCE_BLACKLIST_IDS));
        compoundNBT.setBoolean("factionPrefixChat", FACTION_PREFIX_IN_CHAT);
        compoundNBT.setBoolean("factionPrefixTab", FACTION_PREFIX_IN_TABLIST);
        compoundNBT.setInteger("islandCollectorSlots", ISLAND_COLLECTOR_SLOTS);
        compoundNBT.setInteger("jmClaimMode", JOURNEYMAP_CLAIM_MODE);
        compoundNBT.setInteger("jmVeinMode", JOURNEYMAP_VEIN_MODE);

        NBTTagCompound zones = new NBTTagCompound();
        zones.setTag("unclaimed", UNCLAIMED.writeProtectionSync());
        zones.setTag("safe", SAFE_ZONE.writeProtectionSync());
        zones.setTag("war", WAR_ZONE.writeProtectionSync());
        zones.setTag("citadelFriend", CITADEL_FRIEND.writeProtectionSync());
        zones.setTag("citadelFoe", CITADEL_FOE.writeProtectionSync());
        zones.setTag("claimFriend", CLAIM_FRIEND.writeProtectionSync());
        zones.setTag("claimAlly", CLAIM_ALLY.writeProtectionSync());
        zones.setTag("claimFoe", CLAIM_FOE.writeProtectionSync());
        zones.setTag("sieger", SIEGECAMP_SIEGER.writeProtectionSync());
        zones.setTag("siegeOther", SIEGECAMP_OTHER.writeProtectionSync());
        zones.setTag("claimDefended", CLAIM_DEFENDED.writeProtectionSync());
        zones.setTag("siegedFriend", SIEGED_FRIEND.writeProtectionSync());
        zones.setTag("siegedFoe", SIEGED_FOE.writeProtectionSync());
        zones.setTag("warFriend", WAR_FRIEND.writeProtectionSync());
        zones.setTag("warFoe", WAR_FOE.writeProtectionSync());
        compoundNBT.setTag("protectionZones", zones);
        compoundNBT.setInteger("siegeSiegedRadius", SIEGE_SIEGED_RADIUS);

        packet.configNBT = compoundNBT.toString();
        return packet;
    }

    public static boolean isInsuranceBlacklisted(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem().getRegistryName() == null) {
            return false;
        }

        String registryName = stack.getItem().getRegistryName().toString();
        String fullName = registryName + ":" + stack.getMetadata();
        for (String pattern : INSURANCE_BLACKLIST_IDS) {
            if (pattern == null || pattern.trim().isEmpty()) {
                continue;
            }
            String trimmed = pattern.trim();
            if (globMatches(trimmed, registryName) || globMatches(trimmed, fullName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean globMatches(String pattern, String value) {
        String regex = Pattern.quote(pattern).replace("\\*", "\\E.*\\Q");
        return value.matches(regex);
    }

    public static boolean isDefaultFlagAvailable(String id) {
        for (String allowed : DEFAULT_FLAG_IDS) {
            if (allowed != null && allowed.trim().equalsIgnoreCase(id)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isCustomFlagAvailable(String id) {
        for (String allowed : CUSTOM_FLAG_ALLOWLIST) {
            if (allowed == null || allowed.trim().isEmpty()) {
                continue;
            }
            if (globMatches(allowed.trim().toLowerCase(Locale.ROOT), id.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public static class ProtectionConfig {
        public final MineTime mineTime = new MineTime();
        private boolean MINETIME_ENABLED = false;
        private String MINETIME_MODE = "MULTIPLIER";
        private double MINETIME_VALUE = 5.0;
        private String[] MINETIME_WHITELIST_IDS = new String[]{};
        private String[] MINETIME_BLACKLIST_IDS = new String[]{};

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

        public Set<Block> BLOCK_PLACE_WHITELIST;
        public Set<Block> BLOCK_BREAK_WHITELIST;
        public Set<Block> BLOCK_INTERACT_WHITELIST;
        public Set<Item> ITEM_USE_WHITELIST;

        public Set<Block> BLOCK_PLACE_BLACKLIST;
        public Set<Block> BLOCK_BREAK_BLACKLIST;
        public Set<Block> BLOCK_INTERACT_BLACKLIST;
        public Set<Item> ITEM_USE_BLACKLIST;

        private String[] BLOCK_PLACE_BLACKLIST_IDS = new String[]{};
        private String[] BLOCK_BREAK_BLACKLIST_IDS = new String[]{};
        private String[] BLOCK_INTERACT_BLACKLIST_IDS = new String[]{};
        private String[] ITEM_USE_BLACKLIST_IDS = new String[]{};

        private String[] BLOCK_PLACE_WHITELIST_IDS = new String[]{"minecraft:torch"};
        private String[] BLOCK_BREAK_WHITELIST_IDS = new String[]{"minecraft:torch", "warforge:siegecampblock"};
        private String[] BLOCK_INTERACT_WHITELIST_IDS = new String[]{"minecraft:ender_chest", "warforge:citadelblock", "warforge:basicclaimblock", "warforge:reinforcedclaimblock", "warforge:siegecampblock"};
        private String[] ITEM_USE_WHITELIST_IDS = new String[]{"minecraft:snowball"};

        private Set<Block> findBlocks(String[] input) {
            Set<Block> output = new HashSet<>(input.length);
            for (String blockID : input) {
                ResourceLocation rl = new ResourceLocation(blockID);
                Block block = ForgeRegistries.BLOCKS.getValue(rl);
                if (block != null) {
                    output.add(block);
                } else {
                    WarForgeMod.LOGGER.warn("Unknown block ID in config: {}", blockID);
                }
            }
            return output;
        }

        private Set<Item> findItems(String[] input) {
            Set<Item> output = new HashSet<>(input.length);
            for (String itemID : input) {
                ResourceLocation rl = new ResourceLocation(itemID);
                Item item = ForgeRegistries.ITEMS.getValue(rl);
                if (item != null) {
                    output.add(item);
                } else {
                    WarForgeMod.LOGGER.warn("Unknown item ID in config: {}", itemID);
                }
            }
            return output;
        }

        public void findBlocks() {
            BLOCK_PLACE_WHITELIST = findBlocks(BLOCK_PLACE_WHITELIST_IDS);
            BLOCK_BREAK_WHITELIST = findBlocks(BLOCK_BREAK_WHITELIST_IDS);
            BLOCK_INTERACT_WHITELIST = findBlocks(BLOCK_INTERACT_WHITELIST_IDS);
            ITEM_USE_WHITELIST = findItems(ITEM_USE_WHITELIST_IDS);

            BLOCK_PLACE_BLACKLIST = findBlocks(BLOCK_PLACE_BLACKLIST_IDS);
            BLOCK_BREAK_BLACKLIST = findBlocks(BLOCK_BREAK_BLACKLIST_IDS);
            BLOCK_INTERACT_BLACKLIST = findBlocks(BLOCK_INTERACT_BLACKLIST_IDS);
            ITEM_USE_BLACKLIST = findItems(ITEM_USE_BLACKLIST_IDS);
        }

        public NBTTagCompound writeProtectionSync() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setBoolean("break", BREAK_BLOCKS);
            tag.setBoolean("removal", BLOCK_REMOVAL);
            tag.setString("bw", String.join("\n", BLOCK_BREAK_WHITELIST_IDS));
            tag.setString("bb", String.join("\n", BLOCK_BREAK_BLACKLIST_IDS));
            tag.setBoolean("place", PLACE_BLOCKS);
            tag.setString("pw", String.join("\n", BLOCK_PLACE_WHITELIST_IDS));
            tag.setString("pb", String.join("\n", BLOCK_PLACE_BLACKLIST_IDS));
            tag.setBoolean("mtEnabled", MINETIME_ENABLED);
            tag.setString("mtMode", MINETIME_MODE);
            tag.setDouble("mtValue", MINETIME_VALUE);
            tag.setString("mtWl", String.join("\n", MINETIME_WHITELIST_IDS));
            tag.setString("mtBl", String.join("\n", MINETIME_BLACKLIST_IDS));
            return tag;
        }

        public void readProtectionSync(NBTTagCompound tag) {
            BREAK_BLOCKS = tag.getBoolean("break");
            BLOCK_REMOVAL = tag.getBoolean("removal");
            String bw = tag.getString("bw");
            String bb = tag.getString("bb");
            BLOCK_BREAK_WHITELIST_IDS = bw.isEmpty() ? new String[0] : bw.split("\n");
            BLOCK_BREAK_BLACKLIST_IDS = bb.isEmpty() ? new String[0] : bb.split("\n");
            BLOCK_BREAK_WHITELIST = findBlocks(BLOCK_BREAK_WHITELIST_IDS);
            BLOCK_BREAK_BLACKLIST = findBlocks(BLOCK_BREAK_BLACKLIST_IDS);

            PLACE_BLOCKS = tag.getBoolean("place");
            String pw = tag.getString("pw");
            String pb = tag.getString("pb");
            BLOCK_PLACE_WHITELIST_IDS = pw.isEmpty() ? new String[0] : pw.split("\n");
            BLOCK_PLACE_BLACKLIST_IDS = pb.isEmpty() ? new String[0] : pb.split("\n");
            BLOCK_PLACE_WHITELIST = findBlocks(BLOCK_PLACE_WHITELIST_IDS);
            BLOCK_PLACE_BLACKLIST = findBlocks(BLOCK_PLACE_BLACKLIST_IDS);

            MINETIME_ENABLED = tag.getBoolean("mtEnabled");
            MINETIME_MODE = tag.getString("mtMode");
            MINETIME_VALUE = tag.getDouble("mtValue");
            String mtWl = tag.getString("mtWl");
            String mtBl = tag.getString("mtBl");
            MINETIME_WHITELIST_IDS = mtWl.isEmpty() ? new String[0] : mtWl.split("\n");
            MINETIME_BLACKLIST_IDS = mtBl.isEmpty() ? new String[0] : mtBl.split("\n");
            mineTime.configure(MINETIME_ENABLED, MINETIME_MODE, MINETIME_VALUE, MINETIME_WHITELIST_IDS, MINETIME_BLACKLIST_IDS);
        }

        public void SyncConfig(String name, String desc) {

            BREAK_BLOCKS = configFile.getBoolean(name + " - Break Blocks", name, BREAK_BLOCKS, "Can players break blocks in " + desc);
            PLACE_BLOCKS = configFile.getBoolean(name + " - Place Blocks", name, PLACE_BLOCKS, "Can players place blocks in " + desc);
            BLOCK_REMOVAL = configFile.getBoolean(name + " - Block Removal", name, BLOCK_REMOVAL, "Can blocks be removed at all in (including from explosions, mobs etc) " + desc);
            EXPLOSION_DAMAGE = configFile.getBoolean(name + " - Explosion Damage", name, EXPLOSION_DAMAGE, "Can explosions damage blocks in " + desc);
            INTERACT = configFile.getBoolean(name + " - Interact", name, INTERACT, "Can players interact with blocks and entities in " + desc);
            USE_ITEM = configFile.getBoolean(name + " - Use Items", name, USE_ITEM, "Can players use items in " + desc);
            PLAYER_TAKE_DAMAGE_FROM_MOB = configFile.getBoolean(name + " - Take Dmg From Mob", name, PLAYER_TAKE_DAMAGE_FROM_MOB, "Can players take mob damage in " + desc);
            PLAYER_TAKE_DAMAGE_FROM_PLAYER = configFile.getBoolean(name + " - Take Dmg From Player", name, PLAYER_TAKE_DAMAGE_FROM_PLAYER, "Can players take damage from other players in " + desc);
            PLAYER_TAKE_DAMAGE_FROM_OTHER = configFile.getBoolean(name + " - Take Any Other Dmg", name, PLAYER_TAKE_DAMAGE_FROM_OTHER, "Can players take damage from any other source in " + desc);
            PLAYER_DEAL_DAMAGE = configFile.getBoolean(name + " - Deal Damage", name, PLAYER_DEAL_DAMAGE, "Can players deal damage in " + desc);

            // Whitelists
            BLOCK_PLACE_WHITELIST_IDS = configFile.getStringList(
                    name + " - Place Whitelist",
                    name,
                    BLOCK_PLACE_WHITELIST_IDS,
                    "Whitelist: block IDs that can still be placed. Has no effect if block placement is allowed anyway"
            );

            BLOCK_BREAK_WHITELIST_IDS = configFile.getStringList(
                    name + " - Break Whitelist",
                    name,
                    BLOCK_BREAK_WHITELIST_IDS,
                    "Whitelist: block IDs that can still be broken. Has no effect if block breaking is allowed anyway"
            );

            BLOCK_INTERACT_WHITELIST_IDS = configFile.getStringList(
                    name + " - Interact Whitelist",
                    name,
                    BLOCK_INTERACT_WHITELIST_IDS,
                    "Whitelist: block IDs that can still be interacted with. Has no effect if interacting is allowed anyway"
            );

            ITEM_USE_WHITELIST_IDS = configFile.getStringList(
                    name + " - Use Whitelist",
                    name,
                    ITEM_USE_WHITELIST_IDS,
                    "Whitelist: item IDs that can still be used. Has no effect if using items is allowed anyway"
            );

// Blacklists
            BLOCK_PLACE_BLACKLIST_IDS = configFile.getStringList(
                    name + " - Place Blacklist",
                    name,
                    BLOCK_PLACE_BLACKLIST_IDS,
                    "Blacklist: block IDs that can never be placed, even if placement is otherwise allowed"
            );

            BLOCK_BREAK_BLACKLIST_IDS = configFile.getStringList(
                    name + " - Break Blacklist",
                    name,
                    BLOCK_BREAK_BLACKLIST_IDS,
                    "Blacklist: block IDs that can never be broken, even if breaking is otherwise allowed"
            );

            BLOCK_INTERACT_BLACKLIST_IDS = configFile.getStringList(
                    name + " - Interact Blacklist",
                    name,
                    BLOCK_INTERACT_BLACKLIST_IDS,
                    "Blacklist: block IDs that can never be interacted with, even if interaction is otherwise allowed"
            );

            ITEM_USE_BLACKLIST_IDS = configFile.getStringList(
                    name + " - Use Blacklist",
                    name,
                    ITEM_USE_BLACKLIST_IDS,
                    "Blacklist: item IDs that can never be used, even if using items is otherwise allowed"
            );


            ALLOW_MOB_SPAWNS = configFile.getBoolean(name + " - Allow Mob Spawns", name, ALLOW_MOB_SPAWNS, "Can mobs spawn in " + desc);
            ALLOW_MOB_ENTRY = configFile.getBoolean(name + " - Allow Mob Entry", name, ALLOW_MOB_ENTRY, "Can mobs enter " + desc);

            ALLOW_DISMOUNT_ENTITY = configFile.getBoolean(name + " - Allow Dismount Entity", name, ALLOW_DISMOUNT_ENTITY, "Can players dismount entities " + desc);
            ALLOW_MOUNT_ENTITY = configFile.getBoolean(name + " - Allow Mount Entity", name, ALLOW_MOUNT_ENTITY, "Can players mount entities " + desc);

            MINETIME_ENABLED = configFile.getBoolean(name + " - MineTime Enabled", name, MINETIME_ENABLED,
                "If enabled, breaking a block this profile would normally block is slowed down rather than cancelled in " + desc + ". Whitelisted per-block values below still apply even when this is false.");
            MINETIME_MODE = configFile.getString(name + " - MineTime Default Mode", name, MINETIME_MODE,
                "Default slow-down mode: MULTIPLIER (break time = natural time x value) or FIXED (break time = value seconds).");
            MINETIME_VALUE = (double) configFile.getFloat(name + " - MineTime Default Value", name, (float) MINETIME_VALUE, 0.0f, 100000.0f,
                "Default value for the chosen mode: a time multiplier for MULTIPLIER, or a break time in seconds for FIXED.");
            MINETIME_WHITELIST_IDS = configFile.getStringList(name + " - MineTime Whitelist", name, MINETIME_WHITELIST_IDS,
                "Blocks that MineTime always slows in this profile (even when disabled above), optionally with a per-entry override. " +
                "Each entry is a pattern, optionally followed by '=' and a value spec. " +
                "Patterns: exact id ('gregtech:steam_macerator'), '*' globs ('gregtech:*', 'minecraft:*_ore'). " +
                "Value specs: 'x10' or '10' = 10x time, '30s' = fixed 30 seconds; omit to use the default mode/value.");
            MINETIME_BLACKLIST_IDS = configFile.getStringList(name + " - MineTime Blacklist", name, MINETIME_BLACKLIST_IDS,
                "Blocks excluded from MineTime in this profile (full protection). Same pattern syntax; no value spec. Whitelist entries win.");
            mineTime.configure(MINETIME_ENABLED, MINETIME_MODE, MINETIME_VALUE, MINETIME_WHITELIST_IDS, MINETIME_BLACKLIST_IDS);

        }
    }
}
