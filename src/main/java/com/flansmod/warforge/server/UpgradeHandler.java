package com.flansmod.warforge.server;

import com.flansmod.warforge.common.WarForgeMod;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UpgradeHandler {
    public static final String STUB = """
            levels:
              - level: 0
                claim_limit: 5
                insurance_slots: 0
                loaded_chunks: 4
                requirements: []

              - level: 1
                claim_limit: 10
                insurance_slots: 9
                loaded_chunks: 8
                requirements:
                  - type: ore
                    id: ingotIron
                    count: 64
                  - type: item
                    id: minecraft:diamond
                    count: 1

              - level: 2
                claim_limit: 15
                insurance_slots: 18
                loaded_chunks: 16
                requirements:
                  - type: item
                    id: modid:custom_item:3
                    count: 1
            """;

    protected HashMap<StackComparable, Integer>[] LEVELS;
    protected int[] LIMITS;
    protected int[] INSURANCE_SLOTS;
    protected int[] LOADED_CHUNKS;

    public UpgradeHandler() {
        LEVELS = new HashMap[0];
        LIMITS = new int[0];
        INSURANCE_SLOTS = new int[0];
        LOADED_CHUNKS = new int[0];
    }

    public int[] getLIMITS() {
        return LIMITS;
    }

    public HashMap<StackComparable, Integer>[] getLEVELS() {
        return LEVELS;
    }

    public int[] getINSURANCE_SLOTS() {
        return INSURANCE_SLOTS;
    }

    public int[] getLOADED_CHUNKS() {
        return LOADED_CHUNKS;
    }

    public void setLevelAndLimits(int level, HashMap<StackComparable, Integer> requirements, int limit, int insuranceSlots, int loadedChunks) {
        if (level >= LEVELS.length) {
            int newSize = Math.max(level + 1, Math.max(LEVELS.length * 2, 1));
            LEVELS = Arrays.copyOf(LEVELS, newSize);
            LIMITS = Arrays.copyOf(LIMITS, newSize);
            INSURANCE_SLOTS = Arrays.copyOf(INSURANCE_SLOTS, newSize);
            LOADED_CHUNKS = Arrays.copyOf(LOADED_CHUNKS, newSize);
        }
        LEVELS[level] = requirements;
        LIMITS[level] = limit;
        INSURANCE_SLOTS[level] = insuranceSlots;
        LOADED_CHUNKS[level] = loadedChunks;
    }

    public int getLoadedChunksForLevel(int level) {
        if (level < 0 || level >= LOADED_CHUNKS.length) {
            return 0;
        }
        return LOADED_CHUNKS[level];
    }

    public static void migrateLegacyConfigIfNeeded(Path legacyCfg, Path yamlPath) throws IOException {
        if (Files.exists(yamlPath) || !Files.exists(legacyCfg)) {
            return;
        }

        LegacyConfigData migrated = parseLegacyConfig(legacyCfg);
        writeYamlConfig(yamlPath, migrated.levels, migrated.claims, migrated.insuranceSlots, migrated.loadedChunks);
        Files.move(legacyCfg, legacyCfg.resolveSibling(legacyCfg.getFileName() + ".migrated"), StandardCopyOption.REPLACE_EXISTING);
    }

    public static void writeStubIfEmpty(Path filePath) throws IOException {
        if (Files.notExists(filePath) || Files.size(filePath) == 0) {
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, STUB.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        }
    }

    public static void parseConfig(Path path) throws IOException {
        Yaml yaml = new Yaml();
        Object loaded;
        try (Reader reader = Files.newBufferedReader(path)) {
            loaded = yaml.load(reader);
        }
        if (!(loaded instanceof Map<?, ?> root)) {
            throw new IllegalStateException("Upgrade config must be a YAML object");
        }

        Object rawLevels = root.get("levels");
        if (!(rawLevels instanceof List<?> rawLevelList)) {
            throw new IllegalStateException("Upgrade config must contain a 'levels' list");
        }

        List<Map<StackComparable, Integer>> levels = new ArrayList<>();
        List<Integer> claims = new ArrayList<>();
        List<Integer> insuranceSlots = new ArrayList<>();
        List<Integer> loadedChunks = new ArrayList<>();

        for (Object rawLevel : rawLevelList) {
            if (!(rawLevel instanceof Map<?, ?> levelMap)) {
                throw new IllegalArgumentException("Each level entry must be a map");
            }

            int level = readRequiredInt(levelMap, "level");
            int claimLimit = readRequiredInt(levelMap, "claim_limit");
            int insurance = readOptionalInt(levelMap, "insurance_slots", 0);
            int loadedChunkVal = readOptionalInt(levelMap, "loaded_chunks", 0);
            if (claimLimit != -1 && claimLimit <= 0) {
                throw new IllegalArgumentException("Claim limit must be > 0 or -1");
            }
            if (insurance < 0) {
                throw new IllegalArgumentException("Insurance slots must be >= 0");
            }

            while (levels.size() <= level) {
                levels.add(new HashMap<>());
                claims.add(-1);
                insuranceSlots.add(0);
                loadedChunks.add(0);
            }

            HashMap<StackComparable, Integer> requirements = new HashMap<>();
            Object rawRequirements = levelMap.get("requirements");
            if (rawRequirements instanceof List<?> requirementList) {
                for (Object rawRequirement : requirementList) {
                    if (!(rawRequirement instanceof Map<?, ?> requirementMap)) {
                        throw new IllegalArgumentException("Requirement entries must be maps");
                    }
                    String type = String.valueOf(requirementMap.get("type")).toLowerCase(Locale.ROOT);
                    String id = String.valueOf(requirementMap.get("id"));
                    int count = readOptionalInt(requirementMap, "count", 1);

                    StackComparable stackComparable;
                    if ("ore".equals(type)) {
                        stackComparable = new StackComparable().toOredict(id);
                    } else if ("item".equals(type)) {
                        String[] parts = id.split(":");
                        if (parts.length == 2) {
                            stackComparable = new StackComparable(parts[0] + ":" + parts[1]);
                        } else if (parts.length == 3) {
                            stackComparable = new StackComparable(parts[0] + ":" + parts[1], Integer.parseInt(parts[2]));
                        } else {
                            throw new IllegalArgumentException("Invalid item id format: " + id);
                        }

                        ResourceLocation resourceLocation = new ResourceLocation(stackComparable.getRegistryName());
                        if (!ForgeRegistries.ITEMS.containsKey(resourceLocation)) {
                            WarForgeMod.LOGGER.warn("UpgradeHandler config: Item {} does not exist. Level {} may become inaccessible.", resourceLocation, level);
                        }
                    } else {
                        throw new IllegalArgumentException("Unknown requirement type: " + type);
                    }
                    requirements.put(stackComparable, count);
                }
            }

            levels.set(level, requirements);
            claims.set(level, claimLimit);
            insuranceSlots.set(level, insurance);
            loadedChunks.set(level, loadedChunkVal);
        }

        validateMonotonicClaims(claims);
        applyParsedData(levels, claims, insuranceSlots, loadedChunks);
    }

    public HashMap<StackComparable, Integer> getRequirementsFor(int level) {
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

    private static void applyParsedData(List<Map<StackComparable, Integer>> levels, List<Integer> claims, List<Integer> insuranceSlots, List<Integer> loadedChunks) {
        int size = levels.size();
        WarForgeMod.UPGRADE_HANDLER.LEVELS = new HashMap[size];
        WarForgeMod.UPGRADE_HANDLER.LIMITS = new int[size];
        WarForgeMod.UPGRADE_HANDLER.INSURANCE_SLOTS = new int[size];
        WarForgeMod.UPGRADE_HANDLER.LOADED_CHUNKS = new int[size];
        for (int i = 0; i < size; i++) {
            WarForgeMod.UPGRADE_HANDLER.LEVELS[i] = new HashMap<>(levels.get(i));
            WarForgeMod.UPGRADE_HANDLER.LIMITS[i] = claims.get(i);
            WarForgeMod.UPGRADE_HANDLER.INSURANCE_SLOTS[i] = insuranceSlots.get(i);
            WarForgeMod.UPGRADE_HANDLER.LOADED_CHUNKS[i] = loadedChunks.get(i);
        }
    }

    private static void validateMonotonicClaims(List<Integer> claims) {
        for (int i = 1; i < claims.size(); i++) {
            if (claims.get(i) != -1 && claims.get(i - 1) != -1 && claims.get(i) < claims.get(i - 1)) {
                throw new IllegalStateException("Claim limit at level " + i + " is less than previous level");
            }
        }
    }

    private static int readRequiredInt(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required key: " + key);
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static int readOptionalInt(Map<?, ?> map, String key, int fallback) {
        Object value = map.get(key);
        return value == null ? fallback : Integer.parseInt(String.valueOf(value));
    }

    private static void writeYamlConfig(Path path, List<Map<StackComparable, Integer>> levels, List<Integer> claims, List<Integer> insuranceSlots, List<Integer> loadedChunks) throws IOException {
        List<Map<String, Object>> yamlLevels = new ArrayList<>();
        for (int i = 0; i < levels.size(); i++) {
            Map<String, Object> levelMap = new LinkedHashMap<>();
            levelMap.put("level", i);
            levelMap.put("claim_limit", claims.get(i));
            levelMap.put("insurance_slots", insuranceSlots.get(i));
            levelMap.put("loaded_chunks", loadedChunks.get(i));

            List<Map<String, Object>> requirements = new ArrayList<>();
            for (Map.Entry<StackComparable, Integer> entry : levels.get(i).entrySet()) {
                Map<String, Object> requirementMap = new LinkedHashMap<>();
                if (entry.getKey().getOredict() != null) {
                    requirementMap.put("type", "ore");
                    requirementMap.put("id", entry.getKey().getOredict());
                } else {
                    requirementMap.put("type", "item");
                    String id = entry.getKey().getRegistryName();
                    if (entry.getKey().getMeta() != -1) {
                        id = id + ":" + entry.getKey().getMeta();
                    }
                    requirementMap.put("id", id);
                }
                requirementMap.put("count", entry.getValue());
                requirements.add(requirementMap);
            }
            levelMap.put("requirements", requirements);
            yamlLevels.add(levelMap);
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("levels", yamlLevels);
        Files.createDirectories(path.getParent());
        try (Writer writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            new Yaml().dump(root, writer);
        }
    }

    private static LegacyConfigData parseLegacyConfig(Path path) throws IOException {
        List<Map<StackComparable, Integer>> levels = new ArrayList<>();
        List<Integer> claims = new ArrayList<>();
        List<Integer> insuranceSlots = new ArrayList<>();
        List<Integer> loadedChunks = new ArrayList<>();

        levels.add(new HashMap<>());
        claims.add(-1);
        insuranceSlots.add(0);
        loadedChunks.add(0);

        Map<StackComparable, Integer> current = null;
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

            StackComparable stackComparable;
            if ("ore".equals(type)) {
                stackComparable = new StackComparable().toOredict(rawEntry);
            } else {
                String[] parts = rawEntry.split(":");
                if (parts.length == 2) {
                    stackComparable = new StackComparable(parts[0] + ":" + parts[1]);
                } else if (parts.length == 3) {
                    stackComparable = new StackComparable(parts[0] + ":" + parts[1], Integer.parseInt(parts[2]));
                } else {
                    throw new IllegalArgumentException("Invalid item format: " + rawEntry);
                }
            }
            current.put(stackComparable, count);
        }

        validateMonotonicClaims(claims);
        return new LegacyConfigData(levels, claims, insuranceSlots, loadedChunks);
    }

    private static final class LegacyConfigData {
        private final List<Map<StackComparable, Integer>> levels;
        private final List<Integer> claims;
        private final List<Integer> insuranceSlots;
        private final List<Integer> loadedChunks;

        private LegacyConfigData(List<Map<StackComparable, Integer>> levels, List<Integer> claims, List<Integer> insuranceSlots, List<Integer> loadedChunks) {
            this.levels = levels;
            this.claims = claims;
            this.insuranceSlots = insuranceSlots;
            this.loadedChunks = loadedChunks;
        }
    }
}
