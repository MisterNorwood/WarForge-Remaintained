package com.flansmod.warforge.common;

import com.flansmod.warforge.api.Time;
import com.flansmod.warforge.api.vein.Quality;
import com.flansmod.warforge.common.network.PacketSyncConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.ForgeRegistries;

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

    // General
    public static final String CATEGORY_GENERAL = "General";

    // Config spec
    public static final ForgeConfigSpec SPEC;
    public static boolean DO_FANCY_RENDERING = true;
    public static boolean SHOW_OPPONENT_BORDERS = true;
    public static boolean SHOW_ALLY_BORDERS = true;
    public static float HUD_VERT_CUTOFF_PERCENT = 0.40f;

    // Yields/ Vein
    public static int YIELD_DAY_LENGTH = 3600; // In real-world seconds
    public static boolean TICK_YIELDS = false;
    public static boolean TICK_SIEGES = false;
    public static boolean TICK_MOMENTUM = false;
    public static boolean TICK_OFFLINE_PROTECTION = false;
    public static boolean TICK_SIEGE_GRACE = false;
    public static boolean TICK_CITADEL_MOVE = false;
    public static boolean TICK_TRUCES = false;
    public static long VEIN_MEMBER_DISPLAY_TIME_MS = 1000;
    public static float POOR_QUAL_MULT = 0.5f;
    public static float FAIR_QUAL_MULT = 1f;
    public static float RICH_QUAL_MULT = 2f;

    // Claims
    public static boolean ENABLE_CITADEL_UPGRADES = true;
    public static String[] CLAIM_DIM_WHITELIST = new String[]{"minecraft:overworld"};
    public static int CLAIM_STRENGTH_CITADEL = 15;
    public static int CLAIM_STRENGTH_REINFORCED = 10;
    public static int CLAIM_STRENGTH_BASIC = 5;
    public static int SUPPORT_STRENGTH_CITADEL = 3;

    public static int SUPPORT_STRENGTH_REINFORCED = 2;
    public static int SUPPORT_STRENGTH_BASIC = 1;
    public static int FORCE_LOADED_CHUNKS_TOTAL = 8;
    public static int MAX_CLAIMS_PER_FACTION = -1;
    public static int MAX_FOBS = 3;
    public static int FOB_TICKET_LIMIT = 5;
    public static int FOB_TICKET_REGEN_PER_SIEGE_TICK = 1;
    public static int FOB_WARP_TICKS = 200;
    public static boolean FOB_BLOCK_BREAKABLE = false;
    public static int FOB_CLAIM_EXCLUSION_RADIUS = 1;
    public static Map<String, Integer> FOB_VEHICLE_TICKET_COST = new HashMap<>();
    public static int MIN_DISTANCE_BETWEEN_FACTIONS = 1;
    public static int CLAIM_MANAGER_RADIUS = 4;
    public static int ISLAND_COLLECTOR_SLOTS = 100;
    public static boolean ENABLE_OFFLINE_RAID_PROTECTION = true;
    public static int OFFLINE_RAID_PROTECTION_HOURS = 24;
    public static boolean ENABLE_SIEGE_GRACE_PERIOD = true;
    public static int SIEGE_GRACE_PERIOD_HOURS = 24;
    public static int ATTACK_STRENGTH_SIEGE_CAMP = 1;
    public static float LEECH_PROPORTION_SIEGE_CAMP = 0.25f;
    public static boolean ENABLE_ISOLATED_CLAIMS = true;
    // Stop cross-claim-border griefing: foreign liquid flowing in (lavacasts) and pistons pushing in.
    public static boolean BLOCK_FOREIGN_FLUID_INFLOW = true;
    public static boolean BLOCK_FOREIGN_PISTON_PUSH = true;
    public static String[] INSURANCE_BLACKLIST_IDS = new String[]{"minecraft:*shulker_box", "appliedenergistics2:*cell*"};
    public static String[] DEFAULT_FLAG_IDS = new String[]{
            "white", "light_gray", "gray", "black", "red", "orange", "yellow", "lime",
            "green", "cyan", "light_blue", "blue", "purple", "magenta", "pink", "brown"
    };
    public static String[] CUSTOM_FLAG_ALLOWLIST = new String[]{"*"};
    public static String[] FLAG_WHITELIST = new String[]{};
    public static boolean UNIQUE_FLAGS = true;
    private static final Map<String, Set<UUID>> FLAG_WHITELIST_MAP = new HashMap<>();

    // Sieges
    public static boolean SIEGE_ENABLE_NEW_TIMER = true;
    public static int SIEGE_DEFENCE_THRESHOLD = 5;
    public static boolean SIEGE_END_ON_GOAL_REACHED = true;
    public static int SIEGE_STALL_ESCALATION_THRESHOLD = 10;
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
    public static int SIEGE_BATTLE_RADIUS = 2; // outer "War" zone radius: kills count, foes cannot break
    public static int SIEGE_SIEGED_RADIUS = 1; // inner "Sieged" zone radius: chunk protection disabled
    public static SiegeKillDetectionMode SIEGE_KILL_DETECTION_MODE = SiegeKillDetectionMode.PRECISE;

    public enum SiegeKillDetectionMode {
        PRECISE,
        SIMPLE
    }
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

    // Wealth - Vault blocks
    public static String[] VAULT_BLOCK_IDS = new String[]{"minecraft:gold_block"};
    public static ArrayList<Block> VAULT_BLOCKS = new ArrayList<Block>();
    public static float SHOW_NEW_AREA_TIMER = 200.0f;
    public static int INVITE_DECAY_TIME = 5;
    public static int RANDOM_BORDER_REDRAW_DENOMINATOR = 5;
    public static int BORDER_RENDER_DISTANCE = 0; // in chunks; claim borders farther than this from the camera are not rendered. 0 = follow the client's render distance
    public static final int BORDER_SYNC_MAX_RADIUS = 48; // hard cap (chunks) on the sparse border-outline sync radius the server will serve
    public static int FACTION_NAME_LENGTH_MAX = 32;
    public static String[] FACTION_NAME_BANLIST = new String[]{"admin", "mod", "staff"};
    public static boolean BLOCK_ENDER_CHEST = false;
    public static boolean SHOW_YIELD_TIMERS = true;
    public static int CITADEL_MOVE_COOLDOWN_SECONDS = 25200; // In real-world seconds
    public static int CITADEL_MIN_Y = 63;

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
    public static boolean SPAWN_AT_CITADEL = false;
    public static boolean ALLOW_BED_SPAWN_IN_CLAIMS = false;
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
    public static ProtectionConfig CLAIM_DEFENDED = new ProtectionConfig();
    // Inner "Sieged" zone (protection disabled, kills count) and outer "War" zone (kills count, foes
    // cannot break) around an active siege. Friend = member of the defending (besieged) faction.
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

        //WarForgeMod.VEIN_MAP.defaultReturnValue(null);

        CLAIM_DEFENDED.BREAK_BLOCKS = true;

        // Sieged zone: chunk protection is disabled here. Defender and foe alike may breach, place and
        // blow up blocks; PVP is on (kills count). This is where a base is physically broken into.
        SIEGED_FRIEND.BREAK_BLOCKS = true;
        SIEGED_FRIEND.PLACE_BLOCKS = true;
        SIEGED_FRIEND.INTERACT = true;
        SIEGED_FRIEND.USE_ITEM = true;
        SIEGED_FRIEND.BLOCK_REMOVAL = true;
        SIEGED_FRIEND.EXPLOSION_DAMAGE = true;
        SIEGED_FOE.BREAK_BLOCKS = true;
        SIEGED_FOE.PLACE_BLOCKS = true;
        SIEGED_FOE.INTERACT = true;
        SIEGED_FOE.USE_ITEM = true;
        SIEGED_FOE.BLOCK_REMOVAL = true;
        SIEGED_FOE.EXPLOSION_DAMAGE = true;

        // War zone: kills count, but foes cannot break/place. The defending faction may still fortify.
        WAR_FRIEND.BREAK_BLOCKS = true;
        WAR_FRIEND.PLACE_BLOCKS = true;
        WAR_FRIEND.INTERACT = true;
        WAR_FRIEND.USE_ITEM = true;
        WAR_FRIEND.BLOCK_REMOVAL = true;
        WAR_FOE.BREAK_BLOCKS = false;
        WAR_FOE.PLACE_BLOCKS = false;
        WAR_FOE.INTERACT = false;
        WAR_FOE.USE_ITEM = true;
        WAR_FOE.EXPLOSION_DAMAGE = false;

        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        define(builder);
        SPEC = builder.build();
    }

    // ---------------------------------------------------------------------
    // ForgeConfigSpec backing values. Each ConfigValue is read in bake() and
    // copied into the matching static field so the hundreds of callers keep
    // reading WarForgeConfig.FIELD unchanged.
    // ---------------------------------------------------------------------

    // Claims
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> CLAIM_DIM_WHITELIST_V;
    private static ForgeConfigSpec.IntValue CLAIM_STRENGTH_CITADEL_V;
    private static ForgeConfigSpec.IntValue CLAIM_STRENGTH_REINFORCED_V;
    private static ForgeConfigSpec.IntValue CLAIM_STRENGTH_BASIC_V;
    private static ForgeConfigSpec.IntValue SUPPORT_STRENGTH_CITADEL_V;
    private static ForgeConfigSpec.IntValue SUPPORT_STRENGTH_REINFORCED_V;
    private static ForgeConfigSpec.IntValue SUPPORT_STRENGTH_BASIC_V;
    private static ForgeConfigSpec.IntValue FORCE_LOADED_CHUNKS_TOTAL_V;
    private static ForgeConfigSpec.IntValue MAX_CLAIMS_PER_FACTION_V;
    private static ForgeConfigSpec.IntValue MAX_FOBS_V;
    private static ForgeConfigSpec.IntValue FOB_TICKET_LIMIT_V;
    private static ForgeConfigSpec.IntValue FOB_TICKET_REGEN_PER_SIEGE_TICK_V;
    private static ForgeConfigSpec.IntValue FOB_WARP_TICKS_V;
    private static ForgeConfigSpec.BooleanValue FOB_BLOCK_BREAKABLE_V;
    private static ForgeConfigSpec.IntValue FOB_CLAIM_EXCLUSION_RADIUS_V;
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> FOB_VEHICLE_TICKET_COST_V;
    private static ForgeConfigSpec.IntValue MIN_DISTANCE_BETWEEN_FACTIONS_V;
    private static ForgeConfigSpec.IntValue CLAIM_MANAGER_RADIUS_V;
    private static ForgeConfigSpec.IntValue ISLAND_COLLECTOR_SLOTS_V;
    private static ForgeConfigSpec.BooleanValue ENABLE_OFFLINE_RAID_PROTECTION_V;
    private static ForgeConfigSpec.IntValue OFFLINE_RAID_PROTECTION_HOURS_V;
    private static ForgeConfigSpec.BooleanValue ENABLE_SIEGE_GRACE_PERIOD_V;
    private static ForgeConfigSpec.IntValue SIEGE_GRACE_PERIOD_HOURS_V;
    private static ForgeConfigSpec.IntValue CITADEL_MOVE_COOLDOWN_SECONDS_V;
    private static ForgeConfigSpec.IntValue CITADEL_MIN_Y_V;
    private static ForgeConfigSpec.BooleanValue ENABLE_CITADEL_UPGRADES_V;
    private static ForgeConfigSpec.BooleanValue ENABLE_ISOLATED_CLAIMS_V;
    private static ForgeConfigSpec.BooleanValue BLOCK_FOREIGN_FLUID_INFLOW_V;
    private static ForgeConfigSpec.BooleanValue BLOCK_FOREIGN_PISTON_PUSH_V;
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> INSURANCE_BLACKLIST_IDS_V;
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> DEFAULT_FLAG_IDS_V;
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> CUSTOM_FLAG_ALLOWLIST_V;
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> FLAG_WHITELIST_V;
    private static ForgeConfigSpec.BooleanValue UNIQUE_FLAGS_V;

    // Sieges
    private static ForgeConfigSpec.IntValue ATTACK_STRENGTH_SIEGE_CAMP_V;
    private static ForgeConfigSpec.DoubleValue LEECH_PROPORTION_SIEGE_CAMP_V;
    private static ForgeConfigSpec.IntValue MAX_SIEGES_V;
    private static ForgeConfigSpec.IntValue ATTACKER_DESERTION_TIMER_V;
    private static ForgeConfigSpec.IntValue DEFENDER_DESERTION_TIMER_V;
    private static ForgeConfigSpec.IntValue ATTACKER_CONQUERED_CHUNK_PERIOD_V;
    private static ForgeConfigSpec.IntValue DEFENDER_CONQUERED_CHUNK_PERIOD_V;
    private static ForgeConfigSpec.IntValue COMBAT_LOG_THRESHOLD_V;
    private static ForgeConfigSpec.IntValue LIVE_QUIT_TIMER_V;
    private static ForgeConfigSpec.BooleanValue SIEGE_ALLOW_UI_DECLARE_V;
    private static ForgeConfigSpec.IntValue SIEGE_DECLARE_MAX_RANGE_V;
    private static ForgeConfigSpec.BooleanValue SIEGE_DECLARE_REQUIRE_PRESENCE_V;
    private static ForgeConfigSpec.IntValue QUITTER_FAIL_TIMER_V;
    private static ForgeConfigSpec.IntValue MAX_OFFLINE_PLAYER_COUNT_MINIMUM_V;
    private static ForgeConfigSpec.DoubleValue MAX_OFFLINE_PLAYER_PERCENT_V;
    private static ForgeConfigSpec.IntValue VERTICAL_SIEGE_DIST_V;
    private static ForgeConfigSpec.IntValue SIEGE_BATTLE_RADIUS_V;
    private static ForgeConfigSpec.IntValue SIEGE_SIEGED_RADIUS_V;
    private static ForgeConfigSpec.EnumValue<SiegeKillDetectionMode> SIEGE_KILL_DETECTION_MODE_V;
    private static ForgeConfigSpec.IntValue SIEGE_ATTACKER_RADIUS_V;
    private static ForgeConfigSpec.IntValue SIEGE_DEFENDER_RADIUS_V;
    private static ForgeConfigSpec.IntValue SIEGE_SWING_PER_DEFENDER_DEATH_V;
    private static ForgeConfigSpec.IntValue SIEGE_SWING_PER_ATTACKER_DEATH_V;
    private static ForgeConfigSpec.IntValue SIEGE_STALL_ESCALATION_THRESHOLD_V;
    private static ForgeConfigSpec.IntValue SIEGE_SWING_PER_DAY_ELAPSED_BASE_V;
    private static ForgeConfigSpec.IntValue SIEGE_SWING_PER_DAY_ELAPSED_NO_ATTACKER_LOGINS_V;
    private static ForgeConfigSpec.IntValue SIEGE_SWING_PER_DAY_ELAPSED_NO_DEFENDER_LOGINS_V;
    private static ForgeConfigSpec.DoubleValue SIEGE_DAY_LENGTH_V;
    private static ForgeConfigSpec.IntValue SIEGE_DEFENCE_THRESHOLD_V;
    private static ForgeConfigSpec.BooleanValue SIEGE_END_ON_GOAL_REACHED_V;
    private static ForgeConfigSpec.DoubleValue SIEGE_INFO_RADIUS_V;
    private static ForgeConfigSpec.IntValue SIEGE_SWING_PER_DEFENDER_FLAG_V;
    private static ForgeConfigSpec.IntValue SIEGE_COOLDOWN_FAIL_V;
    private static ForgeConfigSpec.IntValue SIEGE_SWING_PER_ATTACKER_FLAG_V;
    private static ForgeConfigSpec.IntValue SIEGE_DIFF_PER_MEMBER_V;
    private static ForgeConfigSpec.BooleanValue SIEGE_CAPTURE_V;
    private static ForgeConfigSpec.BooleanValue SIEGE_ENABLE_NEW_TIMER_V;
    private static ForgeConfigSpec.IntValue SIEGE_MOMENTUM_DURATION_V;
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> SIEGE_MOMENTUM_MULTIPLIERS_V;

    // Alliances
    private static ForgeConfigSpec.IntValue ALLIANCE_TRUCE_DURATION_MINUTES_V;
    private static ForgeConfigSpec.IntValue MAX_ALLIES_V;

    // Vault
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> VAULT_BLOCK_IDS_V;

    // Yields
    private static ForgeConfigSpec.IntValue YIELD_DAY_LENGTH_V;
    private static ForgeConfigSpec.BooleanValue TICK_YIELDS_V;
    private static ForgeConfigSpec.BooleanValue TICK_SIEGES_V;
    private static ForgeConfigSpec.BooleanValue TICK_MOMENTUM_V;
    private static ForgeConfigSpec.BooleanValue TICK_OFFLINE_PROTECTION_V;
    private static ForgeConfigSpec.BooleanValue TICK_SIEGE_GRACE_V;
    private static ForgeConfigSpec.BooleanValue TICK_CITADEL_MOVE_V;
    private static ForgeConfigSpec.BooleanValue TICK_TRUCES_V;
    private static ForgeConfigSpec.DoubleValue POOR_QUAL_MULT_V;
    private static ForgeConfigSpec.DoubleValue FAIR_QUAL_MULT_V;
    private static ForgeConfigSpec.DoubleValue RICH_QUAL_MULT_V;

    // Notoriety
    private static ForgeConfigSpec.IntValue NOTORIETY_PER_PLAYER_KILL_V;
    private static ForgeConfigSpec.IntValue NOTORIETY_PER_SIEGE_ATTACK_SUCCESS_V;
    private static ForgeConfigSpec.IntValue NOTORIETY_PER_SIEGE_DEFEND_SUCCESS_V;
    private static ForgeConfigSpec.IntValue NOTORIETY_KILL_CAP_PER_PLAYER_V;

    // Legacy
    private static ForgeConfigSpec.IntValue LEGACY_PER_DAY_V;
    private static ForgeConfigSpec.BooleanValue LEGACY_USES_YIELD_TIMER_V;

    // Visual / Client
    private static ForgeConfigSpec.DoubleValue SHOW_NEW_AREA_TIMER_V;
    private static ForgeConfigSpec.IntValue FACTION_NAME_LENGTH_MAX_V;
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> FACTION_NAME_BANLIST_V;
    private static ForgeConfigSpec.BooleanValue SHOW_OPPONENT_BORDERS_V;
    private static ForgeConfigSpec.BooleanValue SHOW_ALLY_BORDERS_V;
    private static ForgeConfigSpec.BooleanValue SHOW_YIELD_TIMERS_V;
    private static ForgeConfigSpec.BooleanValue FACTION_PREFIX_IN_CHAT_V;
    private static ForgeConfigSpec.BooleanValue FACTION_PREFIX_IN_TABLIST_V;
    private static ForgeConfigSpec.ConfigValue<String> JOURNEYMAP_CLAIM_MODE_V;
    private static ForgeConfigSpec.ConfigValue<String> JOURNEYMAP_VEIN_MODE_V;
    private static ForgeConfigSpec.IntValue JOURNEYMAP_VEIN_AUTO_RADIUS_V;
    private static ForgeConfigSpec.IntValue VEIN_MEMBER_DISPLAY_TIME_MS_V;
    private static ForgeConfigSpec.DoubleValue HUD_VERT_CUTOFF_PERCENT_V;
    private static ForgeConfigSpec.ConfigValue<String> POS_TIMERS_V;
    private static ForgeConfigSpec.ConfigValue<String> POS_SIEGE_V;
    private static ForgeConfigSpec.ConfigValue<String> POS_TOAST_INDICATOR_V;
    private static ForgeConfigSpec.ConfigValue<String> POS_VEIN_INDICATOR_V;
    private static ForgeConfigSpec.BooleanValue DO_FANCY_RENDERING_V;
    private static ForgeConfigSpec.IntValue RANDOM_BORDER_REDRAW_DENOMINATOR_V;
    private static ForgeConfigSpec.IntValue BORDER_RENDER_DISTANCE_V;

    // General
    private static ForgeConfigSpec.BooleanValue BLOCK_ENDER_CHEST_V;
    private static ForgeConfigSpec.BooleanValue ENABLE_TPA_POTIONS_V;
    private static ForgeConfigSpec.ConfigValue<String> FACTIONS_BOT_CHANNEL_ID_V;

    // Warps
    private static ForgeConfigSpec.BooleanValue ENABLE_F_HOME_COMMAND_V;
    private static ForgeConfigSpec.BooleanValue ENABLE_F_HOME_POTION_EFFECT_V;
    private static ForgeConfigSpec.BooleanValue ALLOW_F_HOME_BETWEEN_DIMENSIONS_V;
    private static ForgeConfigSpec.BooleanValue ENABLE_SPAWN_COMMAND_V;
    private static ForgeConfigSpec.BooleanValue ENABLE_SPAWN_POTION_EFFECT_V;
    private static ForgeConfigSpec.BooleanValue ALLOW_SPAWN_BETWEEN_DIMENSIONS_V;
    private static ForgeConfigSpec.BooleanValue SPAWN_AT_CITADEL_V;
    private static ForgeConfigSpec.BooleanValue ALLOW_BED_SPAWN_IN_CLAIMS_V;
    private static ForgeConfigSpec.IntValue NUM_TICKS_FOR_WARP_COMMANDS_V;

    // Debug
    private static ForgeConfigSpec.BooleanValue DEBUG_TRACE_SETBLOCK_V;

    private static List<String> asList(String[] arr) {
        return new ArrayList<>(Arrays.asList(arr));
    }

    private static void define(ForgeConfigSpec.Builder cfg) {
        // Protections
        UNCLAIMED.define(cfg, "Unclaimed", "Unclaimed Chunks");
        SAFE_ZONE.define(cfg, "SafeZone", "Safe Zone");
        WAR_ZONE.define(cfg, "WarZone", "War Zone");
        CITADEL_FRIEND.define(cfg, "CitadelFriend", "Citadels of their Faction");
        CITADEL_FOE.define(cfg, "CitadelFoe", "Citadels of other Factions");
        CLAIM_FRIEND.define(cfg, "ClaimFriend", "Claims of their Faction");
        CLAIM_ALLY.define(cfg, "ClaimAlly", "Claims of allied Factions (only applies when the owning faction enables ally interaction)");
        CLAIM_FOE.define(cfg, "ClaimFoe", "Claims of other Factions");
        CLAIM_DEFENDED.define(cfg, "ClaimDefended", "Claims of a faction under siege that are outside the War/Sieged zones");
        SIEGED_FRIEND.define(cfg, "SiegedFriend", "Inner sieged zone, member of the defending faction");
        SIEGED_FOE.define(cfg, "SiegedFoe", "Inner sieged zone, attacker or other faction (chunk protection disabled)");
        WAR_FRIEND.define(cfg, "WarFriend", "Outer war zone, member of the defending faction");
        WAR_FOE.define(cfg, "WarFoe", "Outer war zone, attacker or other faction (kills count, cannot break blocks)");

        // Claim Settings
        cfg.push(CATEGORY_CLAIMS);
        CLAIM_DIM_WHITELIST_V = cfg.comment("In which dimensions should player be able to claim chunks")
                .defineList("Claim Dimension Whitelist", java.util.Arrays.asList(CLAIM_DIM_WHITELIST), o -> o instanceof String);
        CLAIM_STRENGTH_CITADEL_V = cfg.comment("The strength of citadel claims").defineInRange("Citadel Claim Strength", CLAIM_STRENGTH_CITADEL, 1, 1024);
        CLAIM_STRENGTH_REINFORCED_V = cfg.comment("The strength of reinforced claims").defineInRange("Reinforced Claim Strength", CLAIM_STRENGTH_REINFORCED, 1, 1024);
        CLAIM_STRENGTH_BASIC_V = cfg.comment("The strength of basic claims").defineInRange("Basic Claim Strength", CLAIM_STRENGTH_BASIC, 1, 1024);
        SUPPORT_STRENGTH_CITADEL_V = cfg.comment("The support strength a citadel gives to adjacent claims").defineInRange("Citadel Support Strength", SUPPORT_STRENGTH_CITADEL, 1, 1024);
        SUPPORT_STRENGTH_REINFORCED_V = cfg.comment("The support strength a reinforced claim gives to adjacent claims").defineInRange("Reinforced Support Strength", SUPPORT_STRENGTH_REINFORCED, 1, 1024);
        SUPPORT_STRENGTH_BASIC_V = cfg.comment("The support strength a basic claim gives to adjacent claims").defineInRange("Basic Support Strength", SUPPORT_STRENGTH_BASIC, 1, 1024);
        FORCE_LOADED_CHUNKS_TOTAL_V = cfg.comment("Total chunks each faction can force-load. Ignored when the citadel upgrade system is enabled; the per-level 'loaded_chunks' value from upgrade_levels.toml is used instead.").defineInRange("Force-loaded Chunks Total", FORCE_LOADED_CHUNKS_TOTAL, 0, 1024);
        MAX_CLAIMS_PER_FACTION_V = cfg.comment("Maximum number of chunks a single faction may claim. Set to -1 for unlimited. When the citadel upgrade system is enabled, the per-level limit is applied in addition to this cap.").defineInRange("Max Claims Per Faction", MAX_CLAIMS_PER_FACTION, -1, 1000000);
        MAX_FOBS_V = cfg.comment("Maximum number of forward operating bases (FOBs) a single faction may establish. Ignored when the citadel upgrade system is enabled; the per-level 'max_fobs' value from upgrade_levels.toml is used instead.").defineInRange("Max FOBs Per Faction", MAX_FOBS, 0, 1024);
        FOB_TICKET_LIMIT_V = cfg.comment("Maximum warp tickets a FOB can hold. Ignored when the citadel upgrade system is enabled; the per-level 'fob_ticket_limit' value from upgrade_levels.toml is used instead.").defineInRange("FOB Ticket Limit", FOB_TICKET_LIMIT, 0, 1024);
        FOB_TICKET_REGEN_PER_SIEGE_TICK_V = cfg.comment("Warp tickets restored to a FOB each time a siege timer resets. Ignored when the citadel upgrade system is enabled; the per-level 'fob_ticket_regen' value from upgrade_levels.toml is used instead.").defineInRange("FOB Ticket Regen Per Siege Tick", FOB_TICKET_REGEN_PER_SIEGE_TICK, 0, 1024);
        FOB_WARP_TICKS_V = cfg.comment("Number of ticks a player must wait before a FOB warp fires.").defineInRange("FOB Warp Ticks", FOB_WARP_TICKS, 0, 1000000);
        FOB_BLOCK_BREAKABLE_V = cfg.comment("If true, the central FOB block can be mined (very slowly, at a fixed rate independent of tool tier); otherwise it is only removed programmatically.").define("FOB Block Breakable", FOB_BLOCK_BREAKABLE);
        FOB_CLAIM_EXCLUSION_RADIUS_V = cfg.comment("Buffer, in chunks, that must separate any new claim (including a founding citadel) from an active (faction-owned) FOB. A value of N forbids claiming within N chunks (square/Chebyshev radius) of any established FOB. This applies to everyone, including the FOB's own faction. Set to 0 to forbid only the exact FOB chunk.").defineInRange("FOB Claim Exclusion Radius", FOB_CLAIM_EXCLUSION_RADIUS, 0, 64);
        FOB_VEHICLE_TICKET_COST_V = cfg.comment("Extra warp ticket cost per vehicle entity, in the form 'namespace:path=extraCost'. A warp with a vehicle costs 1 plus this value.").defineList("FOB Vehicle Ticket Cost", asList(new String[]{}), o -> o instanceof String);
        MIN_DISTANCE_BETWEEN_FACTIONS_V = cfg.comment("Minimum gap, in chunks, that must separate a new claim from an opposing (non-allied) faction's claims. A value of N forbids claiming within N chunks (square radius) of an opposing faction; allied factions are exempt. Set to 0 to disable.").defineInRange("Minimum Distance Between Opposing Factions", MIN_DISTANCE_BETWEEN_FACTIONS, 0, 64);
        CLAIM_MANAGER_RADIUS_V = cfg.comment("Square radius in chunks shown in the claim manager UI.").defineInRange("Claim Manager Radius", CLAIM_MANAGER_RADIUS, 1, 12);
        ISLAND_COLLECTOR_SLOTS_V = cfg.comment("Number of pull-only storage slots in the faction yield collector block. Shrinking this on an existing world relocates any items that no longer fit into remaining slots.").defineInRange("Island Collector Slot Count", ISLAND_COLLECTOR_SLOTS, 1, 1024);
        ENABLE_OFFLINE_RAID_PROTECTION_V = cfg.comment("If enabled, factions cannot be sieged for a limited period after all their members go offline.").define("Enable Offline Raid Protection", ENABLE_OFFLINE_RAID_PROTECTION);
        OFFLINE_RAID_PROTECTION_HOURS_V = cfg.comment("How many hours a faction remains protected from new sieges after the last member goes offline.").defineInRange("Offline Raid Protection Hours", OFFLINE_RAID_PROTECTION_HOURS, 0, 168);
        ENABLE_SIEGE_GRACE_PERIOD_V = cfg.comment("If enabled, freshly created factions cannot be sieged for a grace period. If a graced faction starts a siege of its own, it forfeits its grace instantly.").define("Enable New Faction Siege Grace", ENABLE_SIEGE_GRACE_PERIOD);
        SIEGE_GRACE_PERIOD_HOURS_V = cfg.comment("How many hours a newly created faction stays unsiegeable. Disabling the feature above removes grace from all existing factions immediately.").defineInRange("New Faction Siege Grace Hours", SIEGE_GRACE_PERIOD_HOURS, 0, 8760);
        CITADEL_MOVE_COOLDOWN_SECONDS_V = cfg.comment("How many real-world seconds a faction has to wait before moving their citadel across chunks again.").defineInRange("Citadel Move Cooldown Seconds", CITADEL_MOVE_COOLDOWN_SECONDS, 0, Integer.MAX_VALUE);
        CITADEL_MIN_Y_V = cfg.comment("The minimum Y level a citadel can be placed or moved to. Placement below this is rejected. Defaults to sea level (63). Set at or below the world floor to disable.").defineInRange("Citadel Minimum Y", CITADEL_MIN_Y, -2048, 2048);
        ENABLE_CITADEL_UPGRADES_V = cfg.comment("Applies claim limits that require upgrading to extend your faction's claim limit").define("Enable Citadel Upgrade System", ENABLE_CITADEL_UPGRADES);
        ENABLE_ISOLATED_CLAIMS_V = cfg.comment("If true, forces all newly placed claim blocks, excluding siege blocks and citadels, to be directly adjacent to a pre-existing claim.").define("Enabled Isolated Claims", ENABLE_ISOLATED_CLAIMS);
        BLOCK_FOREIGN_FLUID_INFLOW_V = cfg.comment("If true, liquids cannot flow from a chunk into a differently-claimed chunk (stops lavacast/water griefing across claim borders).").define("Block Foreign Fluid Inflow", BLOCK_FOREIGN_FLUID_INFLOW);
        BLOCK_FOREIGN_PISTON_PUSH_V = cfg.comment("If true, pistons cannot push or pull blocks across a claim border into/out of a differently-claimed chunk.").define("Block Foreign Piston Push", BLOCK_FOREIGN_PISTON_PUSH);
        INSURANCE_BLACKLIST_IDS_V = cfg.comment("Registry-id patterns blocked from the faction insurance stash. Supports '*' wildcards, for example 'minecraft:*shulker_box' or 'appliedenergistics2:*cell*'.").defineList("Insurance Blacklist", asList(INSURANCE_BLACKLIST_IDS), o -> o instanceof String);
        DEFAULT_FLAG_IDS_V = cfg.comment("Default built-in flags that can be chosen by factions. Each id is rendered client-side as a solid colour square/rectangle. Use a vanilla dye colour name (e.g. red, light_blue) or a 6-digit hex colour (e.g. ff8800).").defineList("Available Default Flags", asList(DEFAULT_FLAG_IDS), o -> o instanceof String);
        CUSTOM_FLAG_ALLOWLIST_V = cfg.comment("Custom server-side flags allowed from resources/warforge/flags. Use '*' to allow all validated custom flags or list exact ids without extension.").defineList("Available Custom Flags", asList(CUSTOM_FLAG_ALLOWLIST), o -> o instanceof String);
        FLAG_WHITELIST_V = cfg.comment("Restrict specific flags to specific players so factions can have exclusive flags. Each entry is 'flagId=uuid1,uuid2' where flagId matches the ids used elsewhere (e.g. 'default:red' or 'custom:myflag') and each uuid is a player's UUID. A flag listed here can only be selected by the listed players; flags not listed stay available to everyone. Multiple UUIDs per flag are supported and duplicates are ignored.").defineList("Flag Whitelist", asList(FLAG_WHITELIST), o -> o instanceof String);
        UNIQUE_FLAGS_V = cfg.comment("If true, each flag can only be used by one faction at a time: a faction cannot select a flag another faction has already taken. Admin flag overrides ('/f flag <faction> set <flagId>') bypass this check.").define("Unique Flags", UNIQUE_FLAGS);
        cfg.pop();

        // Siege Camp Settings
        cfg.push(CATEGORY_SIEGES);
        ATTACK_STRENGTH_SIEGE_CAMP_V = cfg.comment("How much attack pressure a siege camp exerts on adjacent enemy claims").defineInRange("Siege Camp Attack Strength", ATTACK_STRENGTH_SIEGE_CAMP, 1, 1024);
        LEECH_PROPORTION_SIEGE_CAMP_V = cfg.comment("What proportion of a claim's yields are leeched when a siege camp is set to leech mode").defineInRange("Siege Camp Leech Proportion", (double) LEECH_PROPORTION_SIEGE_CAMP, 0d, 1d);
        MAX_SIEGES_V = cfg.comment("How many sieges each faction is allowed to have, with any additional siege camps being unable to be placed by members").defineInRange("Siege Camp Max Count Per Faction", MAX_SIEGES, 1, 1000);
        ATTACKER_DESERTION_TIMER_V = cfg.comment("The number of seconds a siege can idle with no attackers in it before any action occurs. Setting to 0 results in checks being run every tick.").defineInRange("Attacker Desertion Timer [s]", ATTACKER_DESERTION_TIMER, 0, Integer.MAX_VALUE);
        DEFENDER_DESERTION_TIMER_V = cfg.comment("The number of seconds a siege can be undefended before any action occurs. Setting to 0 results in checks being run every tick.").defineInRange("Defender Desertion Timer [s]", DEFENDER_DESERTION_TIMER, 0, Integer.MAX_VALUE);
        ATTACKER_CONQUERED_CHUNK_PERIOD_V = cfg.comment("Milliseconds a chunk stays conquered no-man's-land after attackers win a siege on it: unclaimable by everyone and unprotected (free-build wilderness) until it reverts to normal wilderness. Setting to 0 makes won chunks immediately claimable.").defineInRange("Attacker Conquered Chunk Grace Period [ms]", ATTACKER_CONQUERED_CHUNK_PERIOD, 0, Integer.MAX_VALUE);
        DEFENDER_CONQUERED_CHUNK_PERIOD_V = cfg.comment("The number of milliseconds to deny sieging or claiming in previously sieged chunk in which the siege was won by the defenders. Setting to 0 results in no grace period.").defineInRange("Defender Conquered Chunk Grace Period [ms]", DEFENDER_CONQUERED_CHUNK_PERIOD, 0, Integer.MAX_VALUE);
        COMBAT_LOG_THRESHOLD_V = cfg.comment("The number of milliseconds before enforcement action is taken when a player leaves during a siege on any of their claims.").defineInRange("Time to Combat Log Action [ms]", COMBAT_LOG_THRESHOLD, 0, Integer.MAX_VALUE);
        LIVE_QUIT_TIMER_V = cfg.comment("The number of milliseconds before a defending team which went offline after a siege against them has begun is considered to have quit, forfeiting the siege.").defineInRange("Time to Live Quit Siege [ms]", LIVE_QUIT_TIMER, 0, Integer.MAX_VALUE);
        SIEGE_ALLOW_UI_DECLARE_V = cfg.comment("If enabled, officers can declare a siege directly from the claim map UI (consuming a siege camp block item) without physically placing a siege camp.").define("Allow UI Siege Declaration", SIEGE_ALLOW_UI_DECLARE);
        SIEGE_DECLARE_MAX_RANGE_V = cfg.comment("When declaring a siege from the claim map UI, the maximum chunk distance (chebyshev) allowed between the chosen start-from chunk and the target chunk.").defineInRange("UI Siege Declaration Max Range", SIEGE_DECLARE_MAX_RANGE, 1, 64);
        SIEGE_DECLARE_REQUIRE_PRESENCE_V = cfg.comment("If enabled, a UI-declared (camp-less) siege fails if no attacking faction member stays within the attacker radius of the start-from chunk for the attacker desertion timer, mirroring physical siege camps.").define("UI Siege Requires Attacker Presence", SIEGE_DECLARE_REQUIRE_PRESENCE);
        QUITTER_FAIL_TIMER_V = cfg.comment("The number of milliseconds before a defending team which failed a siege defense due to quitting is failed for subsequent sieges, if still offline.").defineInRange("Time to Quitters Failing Offline Siege [ms]", QUITTER_FAIL_TIMER, 0, Integer.MAX_VALUE);
        MAX_OFFLINE_PLAYER_COUNT_MINIMUM_V = cfg.comment("A static minimum for the maximum number of players which can have been online at some point during a siege before the faction online player count dropping to 0 indicates a live quit. Negative values override the percent").defineInRange("Max Players Before Online Status", MAX_OFFLINE_PLAYER_COUNT_MINIMUM, Integer.MIN_VALUE, Integer.MAX_VALUE);
        MAX_OFFLINE_PLAYER_PERCENT_V = cfg.comment("The maximum percent of players in a faction which can be online at some point during a siege before the online count dropping to 0 indicates a live quit.").defineInRange("Max Player % Before Online Status", (double) MAX_OFFLINE_PLAYER_PERCENT, 0d, 1.0d);
        VERTICAL_SIEGE_DIST_V = cfg.comment("The number of blocks up or down a siege block can be placed from a potential target, inclusively. Sieges may also only be started on targets within this vertical radius.").defineInRange("Maximum Vertical Siege Radius [Inclusive]", VERTICAL_SIEGE_DIST, 0, Integer.MAX_VALUE);
        SIEGE_BATTLE_RADIUS_V = cfg.comment("Outer 'War' zone: chunks (square radius) from each active siege camp where kills count toward the siege and foes cannot break/place blocks (WarFriend/WarFoe profiles). Should be >= the Sieged radius.").defineInRange("Battle Square Chunk Radius From Siege", SIEGE_BATTLE_RADIUS, 0, Integer.MAX_VALUE);
        SIEGE_SIEGED_RADIUS_V = cfg.comment("Inner 'Sieged' zone: chunks (square radius) from each active siege camp where chunk protection is fully disabled - anyone may breach (SiegedFriend/SiegedFoe profiles). Kills also count here. Typically smaller than the War radius.").defineInRange("Sieged Square Chunk Radius From Siege", SIEGE_SIEGED_RADIUS, 0, Integer.MAX_VALUE);
        SIEGE_KILL_DETECTION_MODE_V = cfg.comment("How siege kills are detected. PRECISE (default): only a confirmed kill by an opposing player inside the War/Sieged zone counts (an attacker must kill a defender, or a defender an attacker). SIMPLE: any participant death inside the zone counts toward the siege, including environmental/mob/fall deaths - e.g. a defender dying to fall damage awards the attackers a point.").defineEnum("Kill Detection Mode", SIEGE_KILL_DETECTION_MODE);
        SIEGE_ATTACKER_RADIUS_V = cfg.comment("The number of chunks in any direction from the siege block that an attacker can be in to prevent siege abandon.").defineInRange("Attacker Square Chunk Radius From Siege", SIEGE_ATTACKER_RADIUS, 0, Integer.MAX_VALUE);
        SIEGE_DEFENDER_RADIUS_V = cfg.comment("The number of chunks in any direction from the siege block that a defender can be in to prevent siege abandon.").defineInRange("Defender Square Chunk Radius From Siege", SIEGE_DEFENDER_RADIUS, 0, Integer.MAX_VALUE);
        SIEGE_SWING_PER_DEFENDER_DEATH_V = cfg.comment("How much a siege progress swings when a defender dies in the siege").defineInRange("Siege Swing Per Defender Death", SIEGE_SWING_PER_DEFENDER_DEATH, 0, 1024);
        SIEGE_STALL_ESCALATION_THRESHOLD_V = cfg.comment("Anti-stall: once the defenders' lead exceeds this many points, each consecutive siege-timer tick with no kills doubles the attackers' swing (reset by any kill). Only reachable when Siege Defence Threshold is higher than this. Set very high to effectively disable.").defineInRange("Siege Stall Escalation Threshold", SIEGE_STALL_ESCALATION_THRESHOLD, 0, 1024);
        SIEGE_SWING_PER_ATTACKER_DEATH_V = cfg.comment("How much a siege progress swings when an attacker dies in the siege").defineInRange("Siege Swing Per Attacker Death", SIEGE_SWING_PER_ATTACKER_DEATH, 0, 1024);
        SIEGE_SWING_PER_DAY_ELAPSED_BASE_V = cfg.comment("How much a siege progress swings each day (see below). This happens regardless of logins").defineInRange("Siege Swing Per Day", SIEGE_SWING_PER_DAY_ELAPSED_BASE, 0, 1024);
        SIEGE_SWING_PER_DAY_ELAPSED_NO_ATTACKER_LOGINS_V = cfg.comment("How much a siege progress swings when no attackers have logged on for a day (see below)").defineInRange("Siege Swing Per Day Without Attacker Logins", SIEGE_SWING_PER_DAY_ELAPSED_NO_ATTACKER_LOGINS, 0, 1024);
        SIEGE_SWING_PER_DAY_ELAPSED_NO_DEFENDER_LOGINS_V = cfg.comment("How much a siege progress swings when no defenders have logged on for a day (see below)").defineInRange("Siege Swing Per Day Without Defender Logins", SIEGE_SWING_PER_DAY_ELAPSED_NO_DEFENDER_LOGINS, 0, 1024);
        SIEGE_DAY_LENGTH_V = cfg.comment("The length of a day for siege login purposes, in real-world hours.").defineInRange("Siege Day Length", (double) SIEGE_DAY_LENGTH, 0.0001d, 100000d);
        SIEGE_DEFENCE_THRESHOLD_V = cfg.comment("How many points the defenders must swing the siege in their favour to repel it (attackers win at their own difficulty threshold).").defineInRange("Siege Defence Threshold", SIEGE_DEFENCE_THRESHOLD, 1, 1024);
        SIEGE_END_ON_GOAL_REACHED_V = cfg.comment("If true, a siege concludes the instant a side reaches its point goal (e.g. via a kill), even mid-timer. If false, reaching the goal does not end the siege early; it only concludes when the siege timer next elapses.").define("Siege Ends On Goal Reached", SIEGE_END_ON_GOAL_REACHED);
        SIEGE_INFO_RADIUS_V = cfg.comment("The range at which you see siege information. (Capped by the server setting)").defineInRange("Siege Info Radius", (double) SIEGE_INFO_RADIUS, 1d, 1000d);
        SIEGE_SWING_PER_DEFENDER_FLAG_V = cfg.comment("How much the siege swings per defender flag per day").defineInRange("Siege Swing Per Defender Flag", SIEGE_SWING_PER_DEFENDER_FLAG, 0, 1024);
        SIEGE_COOLDOWN_FAIL_V = cfg.comment("Cooldown between sieges, in minutes").defineInRange("Cooldown between sieges after failure", SIEGE_COOLDOWN_FAIL, 0, 100000);
        SIEGE_SWING_PER_ATTACKER_FLAG_V = cfg.comment("How much the siege swings per attacker flag per day").defineInRange("Siege Swing Per Attacker Flag", SIEGE_SWING_PER_ATTACKER_FLAG, 0, 1024);
        SIEGE_DIFF_PER_MEMBER_V = cfg.comment("How much having a defender flag at a base reinforces the difficulty of the siege for the attackers").defineInRange("Siege Difficulty Reinforcement Per Defender Flag", SIEGE_DIFF_PER_MEMBER, 0, 1024);
        SIEGE_CAPTURE_V = cfg.comment("Does a successful siege convert the claim").define("Siege Captures", SIEGE_CAPTURE);
        SIEGE_ENABLE_NEW_TIMER_V = cfg.comment("Enable new per siege time system, instead of a central siege day, each siege has its own timer, starting with the siege").define("Enable Per-Siege timer", SIEGE_ENABLE_NEW_TIMER);
        SIEGE_MOMENTUM_DURATION_V = cfg.comment("Time the momentum lasts").defineInRange("Siege momentum duration", 60, 1, Integer.MAX_VALUE);
        SIEGE_MOMENTUM_MULTIPLIERS_V = cfg.comment("List of momentum multipliers in the form level=multiplier").defineList("SiegeMomentumMultipliers",
                asList(new String[]{"0=15:00", "1=10:00", "2=8:30", "3=5:00", "4=2:30"}), o -> o instanceof String);
        cfg.pop();

        // Alliances
        cfg.push(CATEGORY_ALLIANCES);
        ALLIANCE_TRUCE_DURATION_MINUTES_V = cfg.comment("When an alliance is broken, both factions enter a truce during which they cannot siege or harm each other. Length in minutes; 0 disables the truce.").defineInRange("Alliance Truce Duration [min]", ALLIANCE_TRUCE_DURATION_MINUTES, 0, 525600);
        MAX_ALLIES_V = cfg.comment("Maximum number of simultaneous alliances a faction may hold. Set to -1 for unlimited.").defineInRange("Max Allies Per Faction", MAX_ALLIES, -1, 1000);
        cfg.pop();

        // Vault parameters
        cfg.push(CATEGORY_GENERAL);
        VAULT_BLOCK_IDS_V = cfg.comment("The block IDs that count towards the value of your citadel's vault").defineList("Valuable Blocks", asList(VAULT_BLOCK_IDS), o -> o instanceof String);
        FACTION_NAME_LENGTH_MAX_V = cfg.comment("How many characters long can a faction name be.").defineInRange("Max Faction Name Length", FACTION_NAME_LENGTH_MAX, 3, 128);
        FACTION_NAME_BANLIST_V = cfg.comment("Case-insensitive substrings that disallow faction names from being created or renamed.").defineList("Faction Name Banlist", asList(FACTION_NAME_BANLIST), o -> o instanceof String);
        SHOW_OPPONENT_BORDERS_V = cfg.comment("Turns the in-world border rendering on/off for opponent chunks").define("Show Opponent Chunk Borders", SHOW_OPPONENT_BORDERS);
        SHOW_ALLY_BORDERS_V = cfg.comment("Turns the in-world border rendering on/off for ally chunks").define("Show Ally Chunk Borders", SHOW_ALLY_BORDERS);
        BLOCK_ENDER_CHEST_V = cfg.comment("Prevent players from opening ender chests").define("Disable Ender Chest", BLOCK_ENDER_CHEST);
        ENABLE_TPA_POTIONS_V = cfg.comment("Allow players to craft and consume /tpa and /tpaccept style potions").define("Enable TPA Potions", ENABLE_TPA_POTIONS);
        FACTIONS_BOT_CHANNEL_ID_V = cfg.comment("https://github.com/Chikachi/DiscordIntegration/wiki/IMC-Feature").define("Discord Bot Channel ID", Long.toString(FACTIONS_BOT_CHANNEL_ID));
        cfg.pop();

        // Yield parameters
        cfg.push(CATEGORY_YIELDS);
        String qualityText = "The global multiplier for %s quality veins which all veins fall back to if they do not have an override.";
        YIELD_DAY_LENGTH_V = cfg.comment("The length of time between yields, in real-world seconds.").defineInRange("Yield Day Length", YIELD_DAY_LENGTH, 1, Integer.MAX_VALUE);
        String tickComment = "If true, this timer advances on server ticks (game time that pauses while the server is stopped or not ticking) instead of real-world wall-clock time. Each timer is independent. Do not change on an existing world that has active factions; stored timestamps are not converted.";
        TICK_YIELDS_V = cfg.comment("Passive yield cycle. " + tickComment).define("Tick-Based Yield Timer", TICK_YIELDS);
        TICK_SIEGES_V = cfg.comment("Siege day advance, siege end-timer countdown, siege start cooldown, and conquered-chunk revert. " + tickComment).define("Tick-Based Siege Timers", TICK_SIEGES);
        TICK_MOMENTUM_V = cfg.comment("Siege momentum expiry. " + tickComment).define("Tick-Based Siege Momentum", TICK_MOMENTUM);
        TICK_OFFLINE_PROTECTION_V = cfg.comment("Offline raid protection window. " + tickComment).define("Tick-Based Offline Raid Protection", TICK_OFFLINE_PROTECTION);
        TICK_SIEGE_GRACE_V = cfg.comment("New-faction siege grace period. " + tickComment).define("Tick-Based Siege Grace", TICK_SIEGE_GRACE);
        TICK_CITADEL_MOVE_V = cfg.comment("Citadel move cooldown. " + tickComment).define("Tick-Based Citadel Move Cooldown", TICK_CITADEL_MOVE);
        TICK_TRUCES_V = cfg.comment("Truce durations. " + tickComment).define("Tick-Based Truces", TICK_TRUCES);
        POOR_QUAL_MULT_V = cfg.comment(String.format(qualityText, Quality.POOR)).defineInRange("Global Poor Quality Multiplier", (double) POOR_QUAL_MULT, 0d, 512d);
        FAIR_QUAL_MULT_V = cfg.comment(String.format(qualityText, Quality.FAIR)).defineInRange("Global Fair Quality Multiplier", (double) FAIR_QUAL_MULT, 0d, 512d);
        RICH_QUAL_MULT_V = cfg.comment(String.format(qualityText, Quality.RICH)).defineInRange("Global Rich Quality Multiplier", (double) RICH_QUAL_MULT, 0d, 512d);
        cfg.pop();

        // Notoriety
        cfg.push(CATEGORY_NOTORIETY);
        NOTORIETY_PER_PLAYER_KILL_V = cfg.comment("How much notoriety a player earns for their faction when killing another player").defineInRange("Notoriety gain per PVP kill", NOTORIETY_PER_PLAYER_KILL, 0, 1024);
        NOTORIETY_PER_SIEGE_ATTACK_SUCCESS_V = cfg.comment("How much notoriety a faction earns when successfully winning an siege as attacker").defineInRange("Notoriety gain per siege attack win", NOTORIETY_PER_SIEGE_ATTACK_SUCCESS, 0, 1024);
        NOTORIETY_PER_SIEGE_DEFEND_SUCCESS_V = cfg.comment("How much notoriety a faction earns when successfully defending a siege").defineInRange("Notoriety gain per siege defend win", NOTORIETY_PER_SIEGE_DEFEND_SUCCESS, 0, 1024);
        NOTORIETY_KILL_CAP_PER_PLAYER_V = cfg.comment("How many times a faction can kill the same player and still get points").defineInRange("Max # kills per player", NOTORIETY_KILL_CAP_PER_PLAYER, 0, 1024);
        cfg.pop();

        // Legacy
        cfg.push(CATEGORY_LEGACY);
        LEGACY_PER_DAY_V = cfg.comment("How much legacy a faction gets for having at least one player on").defineInRange("Legacy gain per day", LEGACY_PER_DAY, 0, 1024);
        LEGACY_USES_YIELD_TIMER_V = cfg.comment("If true, legacy triggers every yield timer. Otherwise, every siege timer").define("Legacy uses yield timer", LEGACY_USES_YIELD_TIMER);
        cfg.pop();

        // Client / Visual
        cfg.push(CATEGORY_CLIENT);
        SHOW_NEW_AREA_TIMER_V = cfg.comment("How many in-game ticks to show the 'You have entered {faction}' message for.").defineInRange("New Area Timer", (double) SHOW_NEW_AREA_TIMER, 0.0d, 1000d);
        SHOW_YIELD_TIMERS_V = cfg.comment("Whether to show a readout of the time until the next yield / siege in top left of your screen").define("Show yield timers", SHOW_YIELD_TIMERS);
        VEIN_MEMBER_DISPLAY_TIME_MS_V = cfg.comment("The time in milliseconds for which each member of a vein will be displayed when it is being cycled through, to the precision allowed by the client tick system.").defineInRange("Vein Member Display Time", (int) VEIN_MEMBER_DISPLAY_TIME_MS, 100, Integer.MAX_VALUE);
        HUD_VERT_CUTOFF_PERCENT_V = cfg.comment("What percent of the entire screen resolution from the top must certain displays (such as vein info) be before they stop rendering. Set to 0.0 to disable all relevant displays, or 1.0 to turn this off.").defineInRange("HUD Vertical cutoff", (double) HUD_VERT_CUTOFF_PERCENT, 0.0d, 1.0d);
        POS_TIMERS_V = cfg.comment("Position of the yield timers").define("Yield timer position", "BOTTOM_RIGHT");
        POS_SIEGE_V = cfg.comment("Position of the siege status").define("Siege status position", "TOP");
        POS_TOAST_INDICATOR_V = cfg.comment("Position of the  toast indicator").define("Toast indicator position", "TOP");
        POS_VEIN_INDICATOR_V = cfg.comment("Position of the  chunk vein indicator").define("Chunk vein indicator position", "TOP");
        DO_FANCY_RENDERING_V = cfg.comment("Controls whether or not fancy graphics will be enabled for this mod's rendering.").define("Enable WarForge Fancy Rendering", DO_FANCY_RENDERING);
        RANDOM_BORDER_REDRAW_DENOMINATOR_V = cfg.comment("Sets the bound on a random number generated, which when equal to 0 calls the border redraw. Effectively 1/this chance to redraw every frame").defineInRange("Random Border Redraw Denominator", RANDOM_BORDER_REDRAW_DENOMINATOR, 1, Integer.MAX_VALUE);
        BORDER_RENDER_DISTANCE_V = cfg.comment("Maximum distance in chunks from the camera at which claim borders are rendered. 0 (default) follows the client's render distance so borders never extend into unloaded terrain; a positive value overrides it.").defineInRange("Border Render Distance", BORDER_RENDER_DISTANCE, 0, 256);
        cfg.pop();

        // Display
        cfg.push(CATEGORY_DISPLAY);
        FACTION_PREFIX_IN_CHAT_V = cfg.comment("If enabled, a player's faction name is shown as a coloured prefix before their name in chat.").define("Faction Prefix In Chat", FACTION_PREFIX_IN_CHAT);
        FACTION_PREFIX_IN_TABLIST_V = cfg.comment("If enabled, a player's faction name is shown as a coloured prefix before their name in the tab player list.").define("Faction Prefix In Tab List", FACTION_PREFIX_IN_TABLIST);
        JOURNEYMAP_CLAIM_MODE_V = cfg.comment("Draws a faction-coloured border over claimed chunks on JourneyMap, if it is installed. Server-authoritative.\n"
                        + "DISABLED = no overlay.\n"
                        + "AUTO = every claim is shown to everyone globally, updated live regardless of whether a player has visited the area.\n"
                        + "PLAYER = a chunk's claim only appears (and updates) on a player's map once that player has observed (loaded) that chunk; unobserved claims are never sent to the client.")
                .define("JourneyMap Claim Display Mode", "DISABLED", o -> isJourneyMapMode(o));
        JOURNEYMAP_VEIN_MODE_V = cfg.comment("Draws each chunk's ore vein as a small item icon on JourneyMap, if it is installed. Server-authoritative.\n"
                        + "DISABLED = no overlay.\n"
                        + "AUTO = veins for a wide radius around each player are sent automatically as they move; no need to enter each chunk.\n"
                        + "PLAYER = a chunk's vein only appears once the player has walked into and loaded that chunk; unobserved veins are never sent to the client.")
                .define("JourneyMap Vein Display Mode", "DISABLED", o -> isJourneyMapMode(o));
        JOURNEYMAP_VEIN_AUTO_RADIUS_V = cfg.comment("In AUTO vein mode, how many chunks around each player to reveal veins for. Larger values reveal more of the map but cause the server to generate and store veins over a wider area.").defineInRange("JourneyMap Vein Auto Radius", JOURNEYMAP_VEIN_AUTO_RADIUS, 1, 128);
        cfg.pop();

        // Warps
        cfg.push(CATEGORY_WARPS);
        ENABLE_F_HOME_COMMAND_V = cfg.comment("Allow players to use /f home to teleport to their citadel").define("Enable /f home Command", ENABLE_F_HOME_COMMAND);
        ENABLE_F_HOME_POTION_EFFECT_V = cfg.comment("Allow players to craft a potion that takes them to their citadel").define("Enable /f home Potion", ENABLE_F_HOME_POTION_EFFECT);
        ALLOW_F_HOME_BETWEEN_DIMENSIONS_V = cfg.comment("Allow players to use /f home when in a different dimension to their citadel").define("Allow /f home across dimensions", ALLOW_F_HOME_BETWEEN_DIMENSIONS);
        ENABLE_SPAWN_COMMAND_V = cfg.comment("Allow players to use /spawn to teleport to the world spawn").define("Enable /spawn Command", ENABLE_SPAWN_COMMAND);
        ENABLE_SPAWN_POTION_EFFECT_V = cfg.comment("Allow players to craft a potion that takes them to the world spawn").define("Enable /spawn Potion", ENABLE_SPAWN_POTION_EFFECT);
        ALLOW_SPAWN_BETWEEN_DIMENSIONS_V = cfg.comment("Allow players to use /spawn when in a different dimension to the world spawn").define("Allow /spawn across dimensions", ALLOW_SPAWN_BETWEEN_DIMENSIONS);
        SPAWN_AT_CITADEL_V = cfg.comment("If enabled, bed and respawn-anchor spawn points are disabled and players respawn in their faction citadel's chunk, at the citadel's Y level. Players not in a faction fall back to the world spawn.").define("Respawn At Citadel", SPAWN_AT_CITADEL);
        ALLOW_BED_SPAWN_IN_CLAIMS_V = cfg.comment("Only used when Respawn At Citadel is enabled. If enabled, players may set bed and respawn-anchor spawn points, but only inside chunks claimed by their own faction. They then respawn at that spawn point; if the bed is broken or the chunk is no longer claimed by their faction, they respawn at their citadel instead.").define("Allow Bed Spawn In Claims", ALLOW_BED_SPAWN_IN_CLAIMS);
        NUM_TICKS_FOR_WARP_COMMANDS_V = cfg.comment("How many ticks must the player stand still for a warp command to take effect").defineInRange("Num Ticks for Warps", NUM_TICKS_FOR_WARP_COMMANDS, 0, 20 * 60 * 5);
        cfg.pop();

        // Debug / diagnostics
        cfg.push(CATEGORY_DEBUG);
        DEBUG_TRACE_SETBLOCK_V = cfg.comment("DEBUG/diagnostic only. When enabled, logs every server-side setBlockState that lands inside a claimed chunk, " +
                "together with the position, the owning claim, the block being placed and the calling code. " +
                "Use this to identify mods that mutate protected terrain outside the explosion-event path " +
                "(e.g. GregTech Industrial TNT, AE2 Tiny TNT). Very spammy - leave disabled in production.").define("Trace setBlockState In Claimed Chunks", DEBUG_TRACE_SETBLOCK);
        cfg.pop();
    }

    private static boolean isJourneyMapMode(Object o) {
        return o instanceof String s && (s.equals("DISABLED") || s.equals("AUTO") || s.equals("PLAYER"));
    }

    private static List<Integer> intList(int[] arr) {
        ArrayList<Integer> out = new ArrayList<>(arr.length);
        for (int i : arr) out.add(i);
        return out;
    }

    private static int[] toIntArray(List<? extends Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i);
        return out;
    }

    private static String[] toStringArray(List<? extends String> list) {
        return list.toArray(new String[0]);
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC);
    }

    // Copies spec values into the static fields. Invoked on ModConfigEvent.Loading / Reloading.
    public static void bake() {
        // Protections
        UNCLAIMED.bake();
        SAFE_ZONE.bake();
        WAR_ZONE.bake();
        CITADEL_FRIEND.bake();
        CITADEL_FOE.bake();
        CLAIM_FRIEND.bake();
        CLAIM_ALLY.bake();
        CLAIM_FOE.bake();
        CLAIM_DEFENDED.bake();
        SIEGED_FRIEND.bake();
        SIEGED_FOE.bake();
        WAR_FRIEND.bake();
        WAR_FOE.bake();

        // Claims
        CLAIM_DIM_WHITELIST = CLAIM_DIM_WHITELIST_V.get().toArray(new String[0]);
        CLAIM_STRENGTH_CITADEL = CLAIM_STRENGTH_CITADEL_V.get();
        CLAIM_STRENGTH_REINFORCED = CLAIM_STRENGTH_REINFORCED_V.get();
        CLAIM_STRENGTH_BASIC = CLAIM_STRENGTH_BASIC_V.get();
        SUPPORT_STRENGTH_CITADEL = SUPPORT_STRENGTH_CITADEL_V.get();
        SUPPORT_STRENGTH_REINFORCED = SUPPORT_STRENGTH_REINFORCED_V.get();
        SUPPORT_STRENGTH_BASIC = SUPPORT_STRENGTH_BASIC_V.get();
        FORCE_LOADED_CHUNKS_TOTAL = FORCE_LOADED_CHUNKS_TOTAL_V.get();
        MAX_CLAIMS_PER_FACTION = MAX_CLAIMS_PER_FACTION_V.get();
        MAX_FOBS = MAX_FOBS_V.get();
        FOB_TICKET_LIMIT = FOB_TICKET_LIMIT_V.get();
        FOB_TICKET_REGEN_PER_SIEGE_TICK = FOB_TICKET_REGEN_PER_SIEGE_TICK_V.get();
        FOB_WARP_TICKS = FOB_WARP_TICKS_V.get();
        FOB_BLOCK_BREAKABLE = FOB_BLOCK_BREAKABLE_V.get();
        FOB_CLAIM_EXCLUSION_RADIUS = FOB_CLAIM_EXCLUSION_RADIUS_V.get();

        FOB_VEHICLE_TICKET_COST.clear();
        for (String s : FOB_VEHICLE_TICKET_COST_V.get()) {
            String[] split = s.split("=");
            if (split.length == 2) {
                try {
                    FOB_VEHICLE_TICKET_COST.put(split[0].trim(), Integer.parseInt(split[1].trim()));
                } catch (RuntimeException e) {
                    e.printStackTrace();
                }
            }
        }
        MIN_DISTANCE_BETWEEN_FACTIONS = MIN_DISTANCE_BETWEEN_FACTIONS_V.get();
        CLAIM_MANAGER_RADIUS = CLAIM_MANAGER_RADIUS_V.get();
        ISLAND_COLLECTOR_SLOTS = ISLAND_COLLECTOR_SLOTS_V.get();
        ENABLE_OFFLINE_RAID_PROTECTION = ENABLE_OFFLINE_RAID_PROTECTION_V.get();
        OFFLINE_RAID_PROTECTION_HOURS = OFFLINE_RAID_PROTECTION_HOURS_V.get();
        ENABLE_SIEGE_GRACE_PERIOD = ENABLE_SIEGE_GRACE_PERIOD_V.get();
        SIEGE_GRACE_PERIOD_HOURS = SIEGE_GRACE_PERIOD_HOURS_V.get();
        CITADEL_MOVE_COOLDOWN_SECONDS = CITADEL_MOVE_COOLDOWN_SECONDS_V.get();
        CITADEL_MIN_Y = CITADEL_MIN_Y_V.get();
        ENABLE_CITADEL_UPGRADES = ENABLE_CITADEL_UPGRADES_V.get();
        ENABLE_ISOLATED_CLAIMS = ENABLE_ISOLATED_CLAIMS_V.get();
        BLOCK_FOREIGN_FLUID_INFLOW = BLOCK_FOREIGN_FLUID_INFLOW_V.get();
        BLOCK_FOREIGN_PISTON_PUSH = BLOCK_FOREIGN_PISTON_PUSH_V.get();
        INSURANCE_BLACKLIST_IDS = toStringArray(INSURANCE_BLACKLIST_IDS_V.get());
        DEFAULT_FLAG_IDS = toStringArray(DEFAULT_FLAG_IDS_V.get());
        CUSTOM_FLAG_ALLOWLIST = toStringArray(CUSTOM_FLAG_ALLOWLIST_V.get());
        FLAG_WHITELIST = toStringArray(FLAG_WHITELIST_V.get());
        UNIQUE_FLAGS = UNIQUE_FLAGS_V.get();
        rebuildFlagWhitelist();

        // Sieges
        ATTACK_STRENGTH_SIEGE_CAMP = ATTACK_STRENGTH_SIEGE_CAMP_V.get();
        LEECH_PROPORTION_SIEGE_CAMP = LEECH_PROPORTION_SIEGE_CAMP_V.get().floatValue();
        MAX_SIEGES = MAX_SIEGES_V.get();
        ATTACKER_DESERTION_TIMER = ATTACKER_DESERTION_TIMER_V.get();
        DEFENDER_DESERTION_TIMER = DEFENDER_DESERTION_TIMER_V.get();
        ATTACKER_CONQUERED_CHUNK_PERIOD = ATTACKER_CONQUERED_CHUNK_PERIOD_V.get();
        DEFENDER_CONQUERED_CHUNK_PERIOD = DEFENDER_CONQUERED_CHUNK_PERIOD_V.get();
        COMBAT_LOG_THRESHOLD = COMBAT_LOG_THRESHOLD_V.get();
        LIVE_QUIT_TIMER = LIVE_QUIT_TIMER_V.get();
        SIEGE_ALLOW_UI_DECLARE = SIEGE_ALLOW_UI_DECLARE_V.get();
        SIEGE_DECLARE_MAX_RANGE = SIEGE_DECLARE_MAX_RANGE_V.get();
        SIEGE_DECLARE_REQUIRE_PRESENCE = SIEGE_DECLARE_REQUIRE_PRESENCE_V.get();
        QUITTER_FAIL_TIMER = QUITTER_FAIL_TIMER_V.get();
        MAX_OFFLINE_PLAYER_COUNT_MINIMUM = MAX_OFFLINE_PLAYER_COUNT_MINIMUM_V.get();
        MAX_OFFLINE_PLAYER_PERCENT = MAX_OFFLINE_PLAYER_PERCENT_V.get().floatValue();
        VERTICAL_SIEGE_DIST = VERTICAL_SIEGE_DIST_V.get();
        SIEGE_BATTLE_RADIUS = SIEGE_BATTLE_RADIUS_V.get();
        SIEGE_SIEGED_RADIUS = SIEGE_SIEGED_RADIUS_V.get();
        SIEGE_KILL_DETECTION_MODE = SIEGE_KILL_DETECTION_MODE_V.get();
        SIEGE_STALL_ESCALATION_THRESHOLD = SIEGE_STALL_ESCALATION_THRESHOLD_V.get();
        SIEGE_ATTACKER_RADIUS = SIEGE_ATTACKER_RADIUS_V.get();
        SIEGE_DEFENDER_RADIUS = SIEGE_DEFENDER_RADIUS_V.get();
        SIEGE_SWING_PER_DEFENDER_DEATH = SIEGE_SWING_PER_DEFENDER_DEATH_V.get();
        SIEGE_SWING_PER_ATTACKER_DEATH = SIEGE_SWING_PER_ATTACKER_DEATH_V.get();
        SIEGE_SWING_PER_DAY_ELAPSED_BASE = SIEGE_SWING_PER_DAY_ELAPSED_BASE_V.get();
        SIEGE_SWING_PER_DAY_ELAPSED_NO_ATTACKER_LOGINS = SIEGE_SWING_PER_DAY_ELAPSED_NO_ATTACKER_LOGINS_V.get();
        SIEGE_SWING_PER_DAY_ELAPSED_NO_DEFENDER_LOGINS = SIEGE_SWING_PER_DAY_ELAPSED_NO_DEFENDER_LOGINS_V.get();
        SIEGE_DAY_LENGTH = SIEGE_DAY_LENGTH_V.get().floatValue();
        SIEGE_DEFENCE_THRESHOLD = SIEGE_DEFENCE_THRESHOLD_V.get();
        SIEGE_END_ON_GOAL_REACHED = SIEGE_END_ON_GOAL_REACHED_V.get();
        SIEGE_INFO_RADIUS = SIEGE_INFO_RADIUS_V.get().floatValue();
        SIEGE_SWING_PER_DEFENDER_FLAG = SIEGE_SWING_PER_DEFENDER_FLAG_V.get();
        SIEGE_COOLDOWN_FAIL = SIEGE_COOLDOWN_FAIL_V.get();
        SIEGE_SWING_PER_ATTACKER_FLAG = SIEGE_SWING_PER_ATTACKER_FLAG_V.get();
        SIEGE_DIFF_PER_MEMBER = SIEGE_DIFF_PER_MEMBER_V.get();
        SIEGE_CAPTURE = SIEGE_CAPTURE_V.get();
        SIEGE_ENABLE_NEW_TIMER = SIEGE_ENABLE_NEW_TIMER_V.get();
        SIEGE_MOMENTUM_DURATION = SIEGE_MOMENTUM_DURATION_V.get();

        SIEGE_MOMENTUM_TIME.clear();
        for (String s : SIEGE_MOMENTUM_MULTIPLIERS_V.get()) {
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

        // Alliances
        ALLIANCE_TRUCE_DURATION_MINUTES = ALLIANCE_TRUCE_DURATION_MINUTES_V.get();
        MAX_ALLIES = MAX_ALLIES_V.get();

        // Vault
        VAULT_BLOCK_IDS = toStringArray(VAULT_BLOCK_IDS_V.get());

        // Yields
        YIELD_DAY_LENGTH = YIELD_DAY_LENGTH_V.get();
        TICK_YIELDS = TICK_YIELDS_V.get();
        TICK_SIEGES = TICK_SIEGES_V.get();
        TICK_MOMENTUM = TICK_MOMENTUM_V.get();
        TICK_OFFLINE_PROTECTION = TICK_OFFLINE_PROTECTION_V.get();
        TICK_SIEGE_GRACE = TICK_SIEGE_GRACE_V.get();
        TICK_CITADEL_MOVE = TICK_CITADEL_MOVE_V.get();
        TICK_TRUCES = TICK_TRUCES_V.get();
        POOR_QUAL_MULT = POOR_QUAL_MULT_V.get().floatValue();
        FAIR_QUAL_MULT = FAIR_QUAL_MULT_V.get().floatValue();
        RICH_QUAL_MULT = RICH_QUAL_MULT_V.get().floatValue();

        // Notoriety
        NOTORIETY_PER_PLAYER_KILL = NOTORIETY_PER_PLAYER_KILL_V.get();
        NOTORIETY_PER_SIEGE_ATTACK_SUCCESS = NOTORIETY_PER_SIEGE_ATTACK_SUCCESS_V.get();
        NOTORIETY_PER_SIEGE_DEFEND_SUCCESS = NOTORIETY_PER_SIEGE_DEFEND_SUCCESS_V.get();
        NOTORIETY_KILL_CAP_PER_PLAYER = NOTORIETY_KILL_CAP_PER_PLAYER_V.get();

        // Legacy
        LEGACY_PER_DAY = LEGACY_PER_DAY_V.get();
        LEGACY_USES_YIELD_TIMER = LEGACY_USES_YIELD_TIMER_V.get();

        // Visual / General
        SHOW_NEW_AREA_TIMER = SHOW_NEW_AREA_TIMER_V.get().floatValue();
        FACTION_NAME_LENGTH_MAX = FACTION_NAME_LENGTH_MAX_V.get();
        FACTION_NAME_BANLIST = toStringArray(FACTION_NAME_BANLIST_V.get());
        SHOW_OPPONENT_BORDERS = SHOW_OPPONENT_BORDERS_V.get();
        SHOW_ALLY_BORDERS = SHOW_ALLY_BORDERS_V.get();
        SHOW_YIELD_TIMERS = SHOW_YIELD_TIMERS_V.get();

        FACTION_PREFIX_IN_CHAT = FACTION_PREFIX_IN_CHAT_V.get();
        FACTION_PREFIX_IN_TABLIST = FACTION_PREFIX_IN_TABLIST_V.get();
        JOURNEYMAP_CLAIM_MODE = parseJourneyMapMode(JOURNEYMAP_CLAIM_MODE_V.get());
        JOURNEYMAP_VEIN_MODE = parseJourneyMapMode(JOURNEYMAP_VEIN_MODE_V.get());
        JOURNEYMAP_VEIN_AUTO_RADIUS = JOURNEYMAP_VEIN_AUTO_RADIUS_V.get();
        VEIN_MEMBER_DISPLAY_TIME_MS = VEIN_MEMBER_DISPLAY_TIME_MS_V.get();
        HUD_VERT_CUTOFF_PERCENT = HUD_VERT_CUTOFF_PERCENT_V.get().floatValue();

        POS_TIMERS = ScreenPos.fromString(POS_TIMERS_V.get());
        POS_SIEGE = ScreenPos.fromString(POS_SIEGE_V.get());
        POS_TOAST_INDICATOR = ScreenPos.fromString(POS_TOAST_INDICATOR_V.get());
        POS_VEIN_INDICATOR = ScreenPos.fromString(POS_VEIN_INDICATOR_V.get());

        // Other permissions
        BLOCK_ENDER_CHEST = BLOCK_ENDER_CHEST_V.get();
        ENABLE_TPA_POTIONS = ENABLE_TPA_POTIONS_V.get();

        // Warps
        ENABLE_F_HOME_COMMAND = ENABLE_F_HOME_COMMAND_V.get();
        ENABLE_F_HOME_POTION_EFFECT = ENABLE_F_HOME_POTION_EFFECT_V.get();
        ALLOW_F_HOME_BETWEEN_DIMENSIONS = ALLOW_F_HOME_BETWEEN_DIMENSIONS_V.get();
        ENABLE_SPAWN_COMMAND = ENABLE_SPAWN_COMMAND_V.get();
        ENABLE_SPAWN_POTION_EFFECT = ENABLE_SPAWN_POTION_EFFECT_V.get();
        ALLOW_SPAWN_BETWEEN_DIMENSIONS = ALLOW_SPAWN_BETWEEN_DIMENSIONS_V.get();
        SPAWN_AT_CITADEL = SPAWN_AT_CITADEL_V.get();
        ALLOW_BED_SPAWN_IN_CLAIMS = ALLOW_BED_SPAWN_IN_CLAIMS_V.get();
        NUM_TICKS_FOR_WARP_COMMANDS = NUM_TICKS_FOR_WARP_COMMANDS_V.get();

        // Graphics controls
        DO_FANCY_RENDERING = DO_FANCY_RENDERING_V.get();
        RANDOM_BORDER_REDRAW_DENOMINATOR = RANDOM_BORDER_REDRAW_DENOMINATOR_V.get();
        BORDER_RENDER_DISTANCE = BORDER_RENDER_DISTANCE_V.get();

        FACTIONS_BOT_CHANNEL_ID = Long.parseLong(FACTIONS_BOT_CHANNEL_ID_V.get());

        // Debug / diagnostics
        DEBUG_TRACE_SETBLOCK = DEBUG_TRACE_SETBLOCK_V.get();
    }

    //New system to deal with that config sync
    public static PacketSyncConfig createConfigSyncPacket() {
        var packet = new PacketSyncConfig();
        var compoundNBT = new CompoundTag();
        compoundNBT.putBoolean("enableUpgrades", ENABLE_CITADEL_UPGRADES);
        compoundNBT.putBoolean("newSiegeTimer", SIEGE_ENABLE_NEW_TIMER);
        compoundNBT.putInt("defenceThreshold", SIEGE_DEFENCE_THRESHOLD);
        compoundNBT.putInt("maxMomentum", SIEGE_MOMENTUM_MAX);
        compoundNBT.putInt("timeMomentum", SIEGE_MOMENTUM_DURATION);
        compoundNBT.putString("momentumMap", SIEGE_MOMENTUM_TIME.toString());
        compoundNBT.putFloat("poorQualMult", POOR_QUAL_MULT);
        compoundNBT.putFloat("fairQualMult", FAIR_QUAL_MULT);
        compoundNBT.putFloat("richQualMult", RICH_QUAL_MULT);
        compoundNBT.putShort("megachunkLength", VEIN_HANDLER.megachunkLength);
        compoundNBT.putInt("battleSiegeRadius", SIEGE_BATTLE_RADIUS);
        compoundNBT.putInt("atkSiegeRadius", SIEGE_ATTACKER_RADIUS);
        compoundNBT.putInt("defSiegeRadius", SIEGE_DEFENDER_RADIUS);
        compoundNBT.putBoolean("offlineRaidProtection", ENABLE_OFFLINE_RAID_PROTECTION);
        compoundNBT.putInt("offlineRaidProtectionHours", OFFLINE_RAID_PROTECTION_HOURS);
        compoundNBT.putString("insuranceBlacklist", String.join("\n", INSURANCE_BLACKLIST_IDS));
        compoundNBT.putBoolean("factionPrefixChat", FACTION_PREFIX_IN_CHAT);
        compoundNBT.putBoolean("factionPrefixTab", FACTION_PREFIX_IN_TABLIST);
        compoundNBT.putInt("islandCollectorSlots", ISLAND_COLLECTOR_SLOTS);
        compoundNBT.putInt("jmClaimMode", JOURNEYMAP_CLAIM_MODE);
        compoundNBT.putInt("jmVeinMode", JOURNEYMAP_VEIN_MODE);

        // The break/place rules + per-zone MineTime settings of the client-determinable zones
        CompoundTag zones = new CompoundTag();
        zones.put("unclaimed", UNCLAIMED.writeProtectionSync());
        zones.put("safe", SAFE_ZONE.writeProtectionSync());
        zones.put("war", WAR_ZONE.writeProtectionSync());
        zones.put("citadelFriend", CITADEL_FRIEND.writeProtectionSync());
        zones.put("citadelFoe", CITADEL_FOE.writeProtectionSync());
        zones.put("claimFriend", CLAIM_FRIEND.writeProtectionSync());
        zones.put("claimFoe", CLAIM_FOE.writeProtectionSync());
        zones.put("siegedFriend", SIEGED_FRIEND.writeProtectionSync());
        zones.put("siegedFoe", SIEGED_FOE.writeProtectionSync());
        zones.put("warFriend", WAR_FRIEND.writeProtectionSync());
        zones.put("warFoe", WAR_FOE.writeProtectionSync());
        compoundNBT.put("protectionZones", zones);

        packet.configNBT = compoundNBT.toString();
        return packet;
    }

    public static boolean isInsuranceBlacklisted(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        ResourceLocation registry = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (registry == null) {
            return false;
        }

        String registryName = registry.toString();
        for (String pattern : INSURANCE_BLACKLIST_IDS) {
            if (pattern == null || pattern.trim().isEmpty()) {
                continue;
            }
            String trimmed = pattern.trim();
            if (globMatches(trimmed, registryName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean globMatches(String pattern, String value) {
        String regex = Pattern.quote(pattern).replace("*", "\\E.*\\Q");
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

    private static void rebuildFlagWhitelist() {
        FLAG_WHITELIST_MAP.clear();
        for (String entry : FLAG_WHITELIST) {
            if (entry == null) {
                continue;
            }
            int eq = entry.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String flagId = entry.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            if (flagId.isEmpty()) {
                continue;
            }
            Set<UUID> uuids = FLAG_WHITELIST_MAP.computeIfAbsent(flagId, k -> new HashSet<UUID>());
            for (String raw : entry.substring(eq + 1).split(",")) {
                String trimmed = raw.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    uuids.add(UUID.fromString(trimmed));
                } catch (IllegalArgumentException e) {
                    WarForgeMod.LOGGER.warn("Ignoring invalid UUID '{}' in flag whitelist entry for {}", trimmed, flagId);
                }
            }
        }
    }

    public static boolean isFlagRestricted(String flagId) {
        return flagId != null && FLAG_WHITELIST_MAP.containsKey(flagId.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean isFlagAllowedForPlayer(String flagId, UUID player) {
        if (flagId == null) {
            return false;
        }
        Set<UUID> allowed = FLAG_WHITELIST_MAP.get(flagId.trim().toLowerCase(Locale.ROOT));
        if (allowed == null) {
            return true;
        }
        return player != null && allowed.contains(player);
    }

    public static class ProtectionConfig {
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

        // MineTime soft protection, scoped to this profile: slows a break this profile would deny
        // instead of cancelling it. Disabled by default so behaviour matches a hard cancel out of the box.
        public final MineTime mineTime = new MineTime();
        private boolean MINETIME_ENABLED = false;
        private String MINETIME_MODE = "MULTIPLIER";
        private double MINETIME_VALUE = 5.0;
        private String[] MINETIME_WHITELIST_IDS = new String[]{};
        private String[] MINETIME_BLACKLIST_IDS = new String[]{};

        // ForgeConfigSpec backing values for this protection category.
        private ForgeConfigSpec.BooleanValue BREAK_BLOCKS_V;
        private ForgeConfigSpec.BooleanValue PLACE_BLOCKS_V;
        private ForgeConfigSpec.BooleanValue BLOCK_REMOVAL_V;
        private ForgeConfigSpec.BooleanValue EXPLOSION_DAMAGE_V;
        private ForgeConfigSpec.BooleanValue INTERACT_V;
        private ForgeConfigSpec.BooleanValue USE_ITEM_V;
        private ForgeConfigSpec.BooleanValue PLAYER_TAKE_DAMAGE_FROM_MOB_V;
        private ForgeConfigSpec.BooleanValue PLAYER_TAKE_DAMAGE_FROM_PLAYER_V;
        private ForgeConfigSpec.BooleanValue PLAYER_TAKE_DAMAGE_FROM_OTHER_V;
        private ForgeConfigSpec.BooleanValue PLAYER_DEAL_DAMAGE_V;
        private ForgeConfigSpec.ConfigValue<List<? extends String>> BLOCK_PLACE_WHITELIST_V;
        private ForgeConfigSpec.ConfigValue<List<? extends String>> BLOCK_BREAK_WHITELIST_V;
        private ForgeConfigSpec.ConfigValue<List<? extends String>> BLOCK_INTERACT_WHITELIST_V;
        private ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_USE_WHITELIST_V;
        private ForgeConfigSpec.ConfigValue<List<? extends String>> BLOCK_PLACE_BLACKLIST_V;
        private ForgeConfigSpec.ConfigValue<List<? extends String>> BLOCK_BREAK_BLACKLIST_V;
        private ForgeConfigSpec.ConfigValue<List<? extends String>> BLOCK_INTERACT_BLACKLIST_V;
        private ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_USE_BLACKLIST_V;
        private ForgeConfigSpec.BooleanValue ALLOW_MOB_SPAWNS_V;
        private ForgeConfigSpec.BooleanValue ALLOW_MOB_ENTRY_V;
        private ForgeConfigSpec.BooleanValue ALLOW_DISMOUNT_ENTITY_V;
        private ForgeConfigSpec.BooleanValue ALLOW_MOUNT_ENTITY_V;
        private ForgeConfigSpec.BooleanValue MINETIME_ENABLED_V;
        private ForgeConfigSpec.ConfigValue<String> MINETIME_MODE_V;
        private ForgeConfigSpec.DoubleValue MINETIME_VALUE_V;
        private ForgeConfigSpec.ConfigValue<List<? extends String>> MINETIME_WHITELIST_V;
        private ForgeConfigSpec.ConfigValue<List<? extends String>> MINETIME_BLACKLIST_V;

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

        // Serialises the break/place-relevant subset for the config-sync packet, so the client can run
        // the same breakDenied()/placeDenied() checks locally and predict MineTime slow-downs and denied
        // placements without rubber-banding.
        public CompoundTag writeProtectionSync() {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("break", BREAK_BLOCKS);
            tag.putBoolean("removal", BLOCK_REMOVAL);
            tag.putString("bw", String.join("\n", BLOCK_BREAK_WHITELIST_IDS));
            tag.putString("bb", String.join("\n", BLOCK_BREAK_BLACKLIST_IDS));
            tag.putBoolean("place", PLACE_BLOCKS);
            tag.putString("pw", String.join("\n", BLOCK_PLACE_WHITELIST_IDS));
            tag.putString("pb", String.join("\n", BLOCK_PLACE_BLACKLIST_IDS));
            tag.putBoolean("mtEnabled", MINETIME_ENABLED);
            tag.putString("mtMode", MINETIME_MODE);
            tag.putDouble("mtValue", MINETIME_VALUE);
            tag.putString("mtWl", String.join("\n", MINETIME_WHITELIST_IDS));
            tag.putString("mtBl", String.join("\n", MINETIME_BLACKLIST_IDS));
            return tag;
        }

        public void readProtectionSync(CompoundTag tag) {
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

        public void define(ForgeConfigSpec.Builder cfg, String name, String desc) {
            cfg.push(name);

            BREAK_BLOCKS_V = cfg.comment("Can players break blocks in " + desc).define(name + " - Break Blocks", BREAK_BLOCKS);
            PLACE_BLOCKS_V = cfg.comment("Can players place blocks in " + desc).define(name + " - Place Blocks", PLACE_BLOCKS);
            BLOCK_REMOVAL_V = cfg.comment("Can blocks be removed at all in (including from explosions, mobs etc) " + desc).define(name + " - Block Removal", BLOCK_REMOVAL);
            EXPLOSION_DAMAGE_V = cfg.comment("Can explosions damage blocks in " + desc).define(name + " - Explosion Damage", EXPLOSION_DAMAGE);
            INTERACT_V = cfg.comment("Can players interact with blocks and entities in " + desc).define(name + " - Interact", INTERACT);
            USE_ITEM_V = cfg.comment("Can players use items in " + desc).define(name + " - Use Items", USE_ITEM);
            PLAYER_TAKE_DAMAGE_FROM_MOB_V = cfg.comment("Can players take mob damage in " + desc).define(name + " - Take Dmg From Mob", PLAYER_TAKE_DAMAGE_FROM_MOB);
            PLAYER_TAKE_DAMAGE_FROM_PLAYER_V = cfg.comment("Can players take damage from other players in " + desc).define(name + " - Take Dmg From Player", PLAYER_TAKE_DAMAGE_FROM_PLAYER);
            PLAYER_TAKE_DAMAGE_FROM_OTHER_V = cfg.comment("Can players take damage from any other source in " + desc).define(name + " - Take Any Other Dmg", PLAYER_TAKE_DAMAGE_FROM_OTHER);
            PLAYER_DEAL_DAMAGE_V = cfg.comment("Can players deal damage in " + desc).define(name + " - Deal Damage", PLAYER_DEAL_DAMAGE);

            // Whitelists
            BLOCK_PLACE_WHITELIST_V = cfg.comment("Whitelist: block IDs that can still be placed. Has no effect if block placement is allowed anyway")
                    .defineList(name + " - Place Whitelist", asList(BLOCK_PLACE_WHITELIST_IDS), o -> o instanceof String);
            BLOCK_BREAK_WHITELIST_V = cfg.comment("Whitelist: block IDs that can still be broken. Has no effect if block breaking is allowed anyway")
                    .defineList(name + " - Break Whitelist", asList(BLOCK_BREAK_WHITELIST_IDS), o -> o instanceof String);
            BLOCK_INTERACT_WHITELIST_V = cfg.comment("Whitelist: block IDs that can still be interacted with. Has no effect if interacting is allowed anyway")
                    .defineList(name + " - Interact Whitelist", asList(BLOCK_INTERACT_WHITELIST_IDS), o -> o instanceof String);
            ITEM_USE_WHITELIST_V = cfg.comment("Whitelist: item IDs that can still be used. Has no effect if using items is allowed anyway")
                    .defineList(name + " - Use Whitelist", asList(ITEM_USE_WHITELIST_IDS), o -> o instanceof String);

            // Blacklists
            BLOCK_PLACE_BLACKLIST_V = cfg.comment("Blacklist: block IDs that can never be placed, even if placement is otherwise allowed")
                    .defineList(name + " - Place Blacklist", asList(BLOCK_PLACE_BLACKLIST_IDS), o -> o instanceof String);
            BLOCK_BREAK_BLACKLIST_V = cfg.comment("Blacklist: block IDs that can never be broken, even if breaking is otherwise allowed")
                    .defineList(name + " - Break Blacklist", asList(BLOCK_BREAK_BLACKLIST_IDS), o -> o instanceof String);
            BLOCK_INTERACT_BLACKLIST_V = cfg.comment("Blacklist: block IDs that can never be interacted with, even if interaction is otherwise allowed")
                    .defineList(name + " - Interact Blacklist", asList(BLOCK_INTERACT_BLACKLIST_IDS), o -> o instanceof String);
            ITEM_USE_BLACKLIST_V = cfg.comment("Blacklist: item IDs that can never be used, even if using items is otherwise allowed")
                    .defineList(name + " - Use Blacklist", asList(ITEM_USE_BLACKLIST_IDS), o -> o instanceof String);

            ALLOW_MOB_SPAWNS_V = cfg.comment("Can mobs spawn in " + desc).define(name + " - Allow Mob Spawns", ALLOW_MOB_SPAWNS);
            ALLOW_MOB_ENTRY_V = cfg.comment("Can mobs enter " + desc).define(name + " - Allow Mob Entry", ALLOW_MOB_ENTRY);

            ALLOW_DISMOUNT_ENTITY_V = cfg.comment("Can players dismount entities " + desc).define(name + " - Allow Dismount Entity", ALLOW_DISMOUNT_ENTITY);
            ALLOW_MOUNT_ENTITY_V = cfg.comment("Can players mount entities " + desc).define(name + " - Allow Mount Entity", ALLOW_MOUNT_ENTITY);

            // MineTime soft protection for this profile.
            cfg.push("MineTime");
            MINETIME_ENABLED_V = cfg.comment("If enabled, breaking a block this profile would normally block is slowed down rather than cancelled in " + desc + ". Whitelisted per-block values below still apply even when this is false.").define(name + " - MineTime Enabled", MINETIME_ENABLED);
            MINETIME_MODE_V = cfg.comment("Default slow-down mode: MULTIPLIER (break time = natural time x value) or FIXED (break time = value seconds).").define(name + " - MineTime Default Mode", MINETIME_MODE);
            MINETIME_VALUE_V = cfg.comment("Default value for the chosen mode: a time multiplier for MULTIPLIER, or a break time in seconds for FIXED.").defineInRange(name + " - MineTime Default Value", MINETIME_VALUE, 0.0, 100000.0);
            MINETIME_WHITELIST_V = cfg.comment(
                            "Blocks that MineTime always slows in this profile (even when disabled above), optionally with a per-entry override. " +
                            "Each entry is a pattern, optionally followed by '=' and a value spec. " +
                            "Patterns: exact id ('gregtech:steam_macerator'), '*' globs ('gregtech:*', 'minecraft:*_ore') or block tags ('#forge:ores'). " +
                            "Value specs: 'x10' or '10' = 10x time, '30s' = fixed 30 seconds; omit to use the default mode/value. " +
                            "Example: 'gregtech:*=x20' slows every GregTech block to 20x break time.")
                    .defineList(name + " - MineTime Whitelist", asList(MINETIME_WHITELIST_IDS), o -> o instanceof String);
            MINETIME_BLACKLIST_V = cfg.comment("Blocks excluded from MineTime in this profile (they keep full, uncancellable protection when the system is enabled). Same pattern syntax as the whitelist; no value spec. Whitelist entries win over the blacklist.")
                    .defineList(name + " - MineTime Blacklist", asList(MINETIME_BLACKLIST_IDS), o -> o instanceof String);
            cfg.pop();

            cfg.pop();
        }

        public void bake() {
            BREAK_BLOCKS = BREAK_BLOCKS_V.get();
            PLACE_BLOCKS = PLACE_BLOCKS_V.get();
            BLOCK_REMOVAL = BLOCK_REMOVAL_V.get();
            EXPLOSION_DAMAGE = EXPLOSION_DAMAGE_V.get();
            INTERACT = INTERACT_V.get();
            USE_ITEM = USE_ITEM_V.get();
            PLAYER_TAKE_DAMAGE_FROM_MOB = PLAYER_TAKE_DAMAGE_FROM_MOB_V.get();
            PLAYER_TAKE_DAMAGE_FROM_PLAYER = PLAYER_TAKE_DAMAGE_FROM_PLAYER_V.get();
            PLAYER_TAKE_DAMAGE_FROM_OTHER = PLAYER_TAKE_DAMAGE_FROM_OTHER_V.get();
            PLAYER_DEAL_DAMAGE = PLAYER_DEAL_DAMAGE_V.get();

            BLOCK_PLACE_WHITELIST_IDS = toStringArray(BLOCK_PLACE_WHITELIST_V.get());
            BLOCK_BREAK_WHITELIST_IDS = toStringArray(BLOCK_BREAK_WHITELIST_V.get());
            BLOCK_INTERACT_WHITELIST_IDS = toStringArray(BLOCK_INTERACT_WHITELIST_V.get());
            ITEM_USE_WHITELIST_IDS = toStringArray(ITEM_USE_WHITELIST_V.get());

            BLOCK_PLACE_BLACKLIST_IDS = toStringArray(BLOCK_PLACE_BLACKLIST_V.get());
            BLOCK_BREAK_BLACKLIST_IDS = toStringArray(BLOCK_BREAK_BLACKLIST_V.get());
            BLOCK_INTERACT_BLACKLIST_IDS = toStringArray(BLOCK_INTERACT_BLACKLIST_V.get());
            ITEM_USE_BLACKLIST_IDS = toStringArray(ITEM_USE_BLACKLIST_V.get());

            ALLOW_MOB_SPAWNS = ALLOW_MOB_SPAWNS_V.get();
            ALLOW_MOB_ENTRY = ALLOW_MOB_ENTRY_V.get();
            ALLOW_DISMOUNT_ENTITY = ALLOW_DISMOUNT_ENTITY_V.get();
            ALLOW_MOUNT_ENTITY = ALLOW_MOUNT_ENTITY_V.get();

            MINETIME_ENABLED = MINETIME_ENABLED_V.get();
            MINETIME_MODE = MINETIME_MODE_V.get();
            MINETIME_VALUE = MINETIME_VALUE_V.get();
            MINETIME_WHITELIST_IDS = toStringArray(MINETIME_WHITELIST_V.get());
            MINETIME_BLACKLIST_IDS = toStringArray(MINETIME_BLACKLIST_V.get());
            mineTime.configure(MINETIME_ENABLED, MINETIME_MODE, MINETIME_VALUE, MINETIME_WHITELIST_IDS, MINETIME_BLACKLIST_IDS);
        }
    }
}
