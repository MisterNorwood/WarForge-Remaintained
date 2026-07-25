package com.flansmod.warforge.server;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import com.flansmod.warforge.common.WarForgeMod;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UpgradeHandler {
    public static final String STUB = """
            # WarForge citadel progression. Each faction starts at level 0 and spends the listed
            # materials at its citadel to reach the next level, unlocking more claims, force-loaded
            # chunks, insurance slots and FOBs. Requirements use vanilla items and Forge tags so the
            # tree works on a plain install; edit freely to fit your pack.

            [[levels]]
            level = 0
            claim_limit = 5
            insurance_slots = 0
            loaded_chunks = 4
            fob_ticket_limit = 5
            fob_ticket_regen = 1
            max_fobs = 1
            extra_claim_cost = []
            requirements = []

            [[levels]]
            level = 1
            claim_limit = 8
            insurance_slots = 9
            loaded_chunks = 6
            fob_ticket_limit = 5
            fob_ticket_regen = 1
            max_fobs = 2
            extra_claim_cost = []
            requirements = [
                { type = "ore", id = "forge:ingots/iron", count = 32 },
                { type = "ore", id = "forge:ingots/copper", count = 16 },
            ]

            [[levels]]
            level = 2
            claim_limit = 12
            insurance_slots = 18
            loaded_chunks = 8
            fob_ticket_limit = 5
            fob_ticket_regen = 1
            max_fobs = 2
            extra_claim_cost = []
            requirements = [
                { type = "ore", id = "forge:ingots/gold", count = 32 },
                { type = "ore", id = "forge:gems/diamond", count = 8 },
            ]

            [[levels]]
            level = 3
            claim_limit = 16
            insurance_slots = 27
            loaded_chunks = 12
            fob_ticket_limit = 5
            fob_ticket_regen = 1
            max_fobs = 3
            extra_claim_cost = []
            requirements = [
                { type = "ore", id = "forge:gems/diamond", count = 16 },
                { type = "item", id = "minecraft:netherite_ingot", count = 1 },
            ]
            """;

    protected HashMap<ItemMatcher, Integer>[] LEVELS;
    protected int[] LIMITS;
    protected int[] INSURANCE_SLOTS;
    protected int[] LOADED_CHUNKS;
    protected int[] FOB_TICKET_LIMIT;
    protected int[] FOB_TICKET_REGEN;
    protected int[] MAX_FOBS;
    protected HashMap<ItemMatcher, Integer>[] EXTRA_CLAIM_COST;

    public UpgradeHandler() {
        LEVELS = new HashMap[0];
        LIMITS = new int[0];
        INSURANCE_SLOTS = new int[0];
        LOADED_CHUNKS = new int[0];
        FOB_TICKET_LIMIT = new int[0];
        FOB_TICKET_REGEN = new int[0];
        MAX_FOBS = new int[0];
        EXTRA_CLAIM_COST = new HashMap[0];
    }

    public int[] getLIMITS() {
        return LIMITS;
    }

    public HashMap<ItemMatcher, Integer>[] getLEVELS() {
        return LEVELS;
    }

    public int[] getINSURANCE_SLOTS() {
        return INSURANCE_SLOTS;
    }

    public int[] getLOADED_CHUNKS() {
        return LOADED_CHUNKS;
    }

    public void setLevelAndLimits(int level, HashMap<ItemMatcher, Integer> requirements, int limit, int insuranceSlots, int loadedChunks) {
        if (level >= LEVELS.length) {
            int newSize = Math.max(level + 1, Math.max(LEVELS.length * 2, 1));
            LEVELS = Arrays.copyOf(LEVELS, newSize);
            LIMITS = Arrays.copyOf(LIMITS, newSize);
            INSURANCE_SLOTS = Arrays.copyOf(INSURANCE_SLOTS, newSize);
            LOADED_CHUNKS = Arrays.copyOf(LOADED_CHUNKS, newSize);
            FOB_TICKET_LIMIT = Arrays.copyOf(FOB_TICKET_LIMIT, newSize);
            FOB_TICKET_REGEN = Arrays.copyOf(FOB_TICKET_REGEN, newSize);
            MAX_FOBS = Arrays.copyOf(MAX_FOBS, newSize);
        }
        LEVELS[level] = requirements;
        LIMITS[level] = limit;
        INSURANCE_SLOTS[level] = insuranceSlots;
        LOADED_CHUNKS[level] = loadedChunks;
    }

    public static void writeStubIfEmpty(Path filePath) throws IOException {
        if (Files.notExists(filePath) || Files.size(filePath) == 0) {
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, STUB.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        }
    }

    public static void parseConfig(Path path) throws IOException {
        Config root;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            root = new TomlParser().parse(reader);
        }

        Object rawLevels = root.get("levels");
        if (!(rawLevels instanceof List<?> rawLevelList)) {
            throw new IllegalStateException("Upgrade config must contain a 'levels' array of tables");
        }

        List<Map<ItemMatcher, Integer>> levels = new ArrayList<>();
        List<Integer> claims = new ArrayList<>();
        List<Integer> insuranceSlots = new ArrayList<>();
        List<Integer> loadedChunks = new ArrayList<>();
        List<Integer> fobTicketLimits = new ArrayList<>();
        List<Integer> fobTicketRegens = new ArrayList<>();
        List<Integer> maxFobs = new ArrayList<>();
        List<Map<ItemMatcher, Integer>> extraCosts = new ArrayList<>();

        for (Object rawLevel : rawLevelList) {
            if (!(rawLevel instanceof Config levelMap)) {
                throw new IllegalArgumentException("Each level entry must be a table");
            }

            int level = readRequiredInt(levelMap, "level");
            int claimLimit = readRequiredInt(levelMap, "claim_limit");
            int insurance = readOptionalInt(levelMap, "insurance_slots", 0);
            int loaded = readOptionalInt(levelMap, "loaded_chunks", 0);
            int fobTicketLimit = readOptionalInt(levelMap, "fob_ticket_limit", 0);
            int fobTicketRegen = readOptionalInt(levelMap, "fob_ticket_regen", 0);
            int fobCap = readOptionalInt(levelMap, "max_fobs", 0);
            if (claimLimit != -1 && claimLimit <= 0) {
                throw new IllegalArgumentException("Claim limit must be > 0 or -1");
            }
            if (insurance < 0) {
                throw new IllegalArgumentException("Insurance slots must be >= 0");
            }
            if (loaded < 0) {
                throw new IllegalArgumentException("Loaded chunks must be >= 0");
            }

            while (levels.size() <= level) {
                levels.add(new HashMap<>());
                claims.add(-1);
                insuranceSlots.add(0);
                loadedChunks.add(0);
                fobTicketLimits.add(0);
                fobTicketRegens.add(0);
                maxFobs.add(0);
                extraCosts.add(new HashMap<>());
            }

            HashMap<ItemMatcher, Integer> requirements = new HashMap<>();
            Object rawRequirements = levelMap.get("requirements");
            if (rawRequirements instanceof List<?> requirementList) {
                for (Object rawRequirement : requirementList) {
                    if (!(rawRequirement instanceof Config requirementMap)) {
                        throw new IllegalArgumentException("Requirement entries must be tables");
                    }
                    String type = coerceString(requirementMap.get("type")).toLowerCase(Locale.ROOT);
                    String id = coerceString(requirementMap.get("id"));
                    int count = readOptionalInt(requirementMap, "count", 1);

                    ItemMatcher matcher;
                    if ("ore".equals(type)) {
                        matcher = ItemMatcher.ofTag(id);
                    } else if ("item".equals(type)) {
                        String[] parts = id.split(":");
                        if (parts.length != 2 && parts.length != 3) {
                            throw new IllegalArgumentException("Invalid item id format: " + id);
                        }
                        matcher = ItemMatcher.ofItem(id);
                    } else {
                        throw new IllegalArgumentException("Unknown requirement type: " + type);
                    }
                    if (matcher == null) {
                        WarForgeMod.LOGGER.warn("UpgradeHandler config: requirement '{}' (type {}) at level {} does not resolve to a known item/tag; skipping.", id, type, level);
                        continue;
                    }
                    requirements.put(matcher, count);
                }
            }

            HashMap<ItemMatcher, Integer> extraCost = new HashMap<>();
            Object rawExtraCost = levelMap.get("extra_claim_cost");
            if (rawExtraCost instanceof List<?> extraCostList) {
                for (Object rawEntry : extraCostList) {
                    if (!(rawEntry instanceof Config costMap)) {
                        throw new IllegalArgumentException("extra_claim_cost entries must be tables");
                    }
                    int cnt = readOptionalInt(costMap, "count", 1);
                    if (cnt <= 0) {
                        continue;
                    }
                    String type = coerceString(costMap.get("type")).toLowerCase(Locale.ROOT);
                    String id = coerceString(costMap.get("id"));
                    ItemMatcher matcher = parseCostMatcher(type, id, level);
                    if (matcher != null) {
                        extraCost.put(matcher, cnt);
                    }
                }
            }

            levels.set(level, requirements);
            claims.set(level, claimLimit);
            insuranceSlots.set(level, insurance);
            loadedChunks.set(level, loaded);
            fobTicketLimits.set(level, fobTicketLimit);
            fobTicketRegens.set(level, fobTicketRegen);
            maxFobs.set(level, fobCap);
            extraCosts.set(level, extraCost);
        }

        validateMonotonicClaims(claims);
        applyParsedData(levels, claims, insuranceSlots, loadedChunks, fobTicketLimits, fobTicketRegens, maxFobs, extraCosts);
    }

    private static ItemMatcher parseCostMatcher(String type, String id, int level) {
        ItemMatcher matcher;
        if ("ore".equals(type)) {
            matcher = ItemMatcher.ofTag(id);
        } else if ("item".equals(type)) {
            String[] parts = id.split(":");
            if (parts.length != 2 && parts.length != 3) {
                throw new IllegalArgumentException("Invalid item id format: " + id);
            }
            matcher = ItemMatcher.ofItem(id);
        } else {
            throw new IllegalArgumentException("Unknown extra_claim_cost type: " + type);
        }
        if (matcher == null) {
            WarForgeMod.LOGGER.warn("UpgradeHandler config: extra_claim_cost '{}' (type {}) at level {} does not resolve; no extra cost applied.", id, type, level);
        }
        return matcher;
    }

    public HashMap<ItemMatcher, Integer> getRequirementsFor(int level) {
        if (level >= LEVELS.length) {
            return null;
        }
        return LEVELS[level];
    }

    public int getClaimLimitForLevel(int level) {
        if (level >= LIMITS.length || level < 0) {
            return -1;
        }
        return LIMITS[level];
    }

    public int getInsuranceSlotsForLevel(int level) {
        if (level >= INSURANCE_SLOTS.length || level < 0) {
            return 0;
        }
        return INSURANCE_SLOTS[level];
    }

    public int getLoadedChunksForLevel(int level) {
        if (level >= LOADED_CHUNKS.length || level < 0) {
            return 0;
        }
        return LOADED_CHUNKS[level];
    }

    public int getFobTicketLimitForLevel(int level) {
        if (level >= FOB_TICKET_LIMIT.length || level < 0) {
            return 0;
        }
        return FOB_TICKET_LIMIT[level];
    }

    public int getFobRegenForLevel(int level) {
        if (level >= FOB_TICKET_REGEN.length || level < 0) {
            return 0;
        }
        return FOB_TICKET_REGEN[level];
    }

    public int getMaxFobsForLevel(int level) {
        if (level >= MAX_FOBS.length || level < 0) {
            return 0;
        }
        return MAX_FOBS[level];
    }

    public HashMap<ItemMatcher, Integer> getExtraClaimCostForLevel(int level) {
        if (level < 0 || level >= EXTRA_CLAIM_COST.length) {
            return null;
        }
        return EXTRA_CLAIM_COST[level];
    }

    private static void applyParsedData(List<Map<ItemMatcher, Integer>> levels, List<Integer> claims, List<Integer> insuranceSlots, List<Integer> loadedChunks, List<Integer> fobTicketLimits, List<Integer> fobTicketRegens, List<Integer> maxFobs, List<Map<ItemMatcher, Integer>> extraCosts) {
        int size = levels.size();
        WarForgeMod.UPGRADE_HANDLER.LEVELS = new HashMap[size];
        WarForgeMod.UPGRADE_HANDLER.LIMITS = new int[size];
        WarForgeMod.UPGRADE_HANDLER.INSURANCE_SLOTS = new int[size];
        WarForgeMod.UPGRADE_HANDLER.LOADED_CHUNKS = new int[size];
        WarForgeMod.UPGRADE_HANDLER.FOB_TICKET_LIMIT = new int[size];
        WarForgeMod.UPGRADE_HANDLER.FOB_TICKET_REGEN = new int[size];
        WarForgeMod.UPGRADE_HANDLER.MAX_FOBS = new int[size];
        WarForgeMod.UPGRADE_HANDLER.EXTRA_CLAIM_COST = new HashMap[size];
        for (int i = 0; i < size; i++) {
            WarForgeMod.UPGRADE_HANDLER.LEVELS[i] = new HashMap<>(levels.get(i));
            WarForgeMod.UPGRADE_HANDLER.LIMITS[i] = claims.get(i);
            WarForgeMod.UPGRADE_HANDLER.INSURANCE_SLOTS[i] = insuranceSlots.get(i);
            WarForgeMod.UPGRADE_HANDLER.LOADED_CHUNKS[i] = loadedChunks.get(i);
            WarForgeMod.UPGRADE_HANDLER.FOB_TICKET_LIMIT[i] = fobTicketLimits.get(i);
            WarForgeMod.UPGRADE_HANDLER.FOB_TICKET_REGEN[i] = fobTicketRegens.get(i);
            WarForgeMod.UPGRADE_HANDLER.MAX_FOBS[i] = maxFobs.get(i);
            WarForgeMod.UPGRADE_HANDLER.EXTRA_CLAIM_COST[i] = new HashMap<>(extraCosts.get(i));
        }
    }

    private static void validateMonotonicClaims(List<Integer> claims) {
        for (int i = 1; i < claims.size(); i++) {
            if (claims.get(i) != -1 && claims.get(i - 1) != -1 && claims.get(i) < claims.get(i - 1)) {
                throw new IllegalStateException("Claim limit at level " + i + " is less than previous level");
            }
        }
    }

    private static int readRequiredInt(Config map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required key: " + key);
        }
        return coerceInt(value);
    }

    private static int readOptionalInt(Config map, String key, int fallback) {
        Object value = map.get(key);
        return value == null ? fallback : coerceInt(value);
    }

    // night-config returns TOML integers as Number and strings as String/char[] depending on version;
    // coerce defensively so a type mismatch can never throw a raw ClassCastException at parse time.
    private static int coerceInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(coerceString(value).trim());
    }

    private static String coerceString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof char[] chars) {
            return new String(chars);
        }
        return value.toString();
    }

    private static void writeTomlConfig(Path path, List<Map<ItemMatcher, Integer>> levels, List<Integer> claims, List<Integer> insuranceSlots, List<Integer> loadedChunks) throws IOException {
        List<Config> tomlLevels = new ArrayList<>();
        for (int i = 0; i < levels.size(); i++) {
            Config levelMap = TomlFormat.newConfig();
            levelMap.set("level", i);
            levelMap.set("claim_limit", claims.get(i));
            levelMap.set("insurance_slots", insuranceSlots.get(i));
            levelMap.set("loaded_chunks", loadedChunks.get(i));

            List<Config> requirements = new ArrayList<>();
            for (Map.Entry<ItemMatcher, Integer> entry : levels.get(i).entrySet()) {
                Config requirementMap = TomlFormat.newConfig();
                requirementMap.set("type", entry.getKey().isTag() ? "ore" : "item");
                requirementMap.set("id", entry.getKey().id());
                requirementMap.set("count", entry.getValue());
                requirements.add(requirementMap);
            }
            levelMap.set("requirements", requirements);
            tomlLevels.add(levelMap);
        }

        Config root = TomlFormat.newConfig();
        root.set("levels", tomlLevels);
        Files.createDirectories(path.getParent());
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            new TomlWriter().write(root, writer);
        }
    }

    private static LegacyConfigData parseLegacyConfig(Path path) throws IOException {
        List<Map<ItemMatcher, Integer>> levels = new ArrayList<>();
        List<Integer> claims = new ArrayList<>();
        List<Integer> insuranceSlots = new ArrayList<>();
        List<Integer> loadedChunks = new ArrayList<>();

        levels.add(new HashMap<>());
        claims.add(-1);
        insuranceSlots.add(0);
        loadedChunks.add(0);

        Map<ItemMatcher, Integer> current = null;
        int currentLevel = 0;

        for (String line : Files.readAllLines(path)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            if (line.startsWith("level:")) {
                String levelSpec = line.substring(6).trim();
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)\\[(\\d+|-1)](?:\\[(\\d+)])?(?:\\[(\\d+)])?").matcher(levelSpec);
                if (!matcher.matches()) {
                    throw new IllegalArgumentException("Invalid level format: " + line);
                }

                currentLevel = Integer.parseInt(matcher.group(1));
                int claimLimit = Integer.parseInt(matcher.group(2));
                int insurance = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
                int loaded = matcher.group(4) == null ? 0 : Integer.parseInt(matcher.group(4));

                while (levels.size() <= currentLevel) {
                    levels.add(new HashMap<>());
                    claims.add(-1);
                    insuranceSlots.add(0);
                    loadedChunks.add(0);
                }

                claims.set(currentLevel, claimLimit);
                insuranceSlots.set(currentLevel, insurance);
                loadedChunks.set(currentLevel, loaded);
                current = levels.get(currentLevel);
                continue;
            }

            if (current == null) {
                throw new IllegalStateException("Item defined before any level");
            }

            String type;
            if (line.startsWith("item:")) {
                type = "item";
            } else if (line.startsWith("ore:")) {
                type = "ore";
            } else {
                throw new IllegalArgumentException("Unknown entry type: " + line);
            }

            String content = line.substring(type.length() + 1).trim();
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(.+?)(\\[(\\d+)])?$").matcher(content);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Invalid format for line: " + line);
            }

            String rawEntry = matcher.group(1);
            int count = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 1;

            ItemMatcher itemMatcher;
            if ("ore".equals(type)) {
                itemMatcher = ItemMatcher.ofTag(rawEntry);
            } else {
                String[] parts = rawEntry.split(":");
                if (parts.length != 2 && parts.length != 3) {
                    throw new IllegalArgumentException("Invalid item format: " + rawEntry);
                }
                itemMatcher = ItemMatcher.ofItem(rawEntry);
            }
            if (itemMatcher == null) {
                WarForgeMod.LOGGER.warn("UpgradeHandler legacy config: '{}' did not resolve to a known item/tag; skipping.", rawEntry);
                continue;
            }
            current.put(itemMatcher, count);
        }

        validateMonotonicClaims(claims);
        return new LegacyConfigData(levels, claims, insuranceSlots, loadedChunks);
    }

    private static final class LegacyConfigData {
        private final List<Map<ItemMatcher, Integer>> levels;
        private final List<Integer> claims;
        private final List<Integer> insuranceSlots;
        private final List<Integer> loadedChunks;

        private LegacyConfigData(List<Map<ItemMatcher, Integer>> levels, List<Integer> claims, List<Integer> insuranceSlots, List<Integer> loadedChunks) {
            this.levels = levels;
            this.claims = claims;
            this.insuranceSlots = insuranceSlots;
            this.loadedChunks = loadedChunks;
        }
    }
}
