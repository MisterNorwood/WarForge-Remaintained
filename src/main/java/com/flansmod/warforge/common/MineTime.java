package com.flansmod.warforge.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * MineTime soft protection. Where territory rules would otherwise cancel a block break, MineTime
 * lets the break happen but slows it down — either by a time multiplier or to a fixed break time.
 *
 * <p>Each {@link com.flansmod.warforge.common.WarForgeConfig.ProtectionConfig} profile owns its own
 * MineTime instance, so the system can be enabled (and tuned) per zone rather than globally.
 *
 * <p>A blacklist excludes blocks from the system, and a whitelist both opts blocks in and lets each
 * entry carry its own value override that applies even when the profile has MineTime disabled. Entries
 * accept exact ids ({@code gregtech:steam_macerator}), {@code *} globs ({@code gregtech:*},
 * {@code minecraft:*_ore}) and block tags ({@code #forge:ores}) so a single line can catch every
 * machine from a mod.
 */
public final class MineTime {
    public enum Mode {
        /** Result break time = natural time × value. */
        MULTIPLIER,
        /** Result break time = value seconds, regardless of hardness/tool. */
        FIXED
    }

    /** A resolved slow-down to apply to one block. */
    public record Rule(Mode mode, double value) {
    }

    private record Entry(BlockPattern pattern, Rule rule) {
    }

    private boolean enabled = false;
    private Mode defaultMode = Mode.MULTIPLIER;
    private double defaultValue = 5.0;

    private List<Entry> whitelist = List.of();
    private List<BlockPattern> blacklist = List.of();

    public void configure(boolean enabled, String modeName, double defaultValue, String[] whitelist, String[] blacklist) {
        this.enabled = enabled;
        this.defaultMode = parseMode(modeName, Mode.MULTIPLIER);
        this.defaultValue = defaultValue;

        List<Entry> parsedWhitelist = new ArrayList<>();
        for (String raw : whitelist) {
            if (raw == null || raw.trim().isEmpty()) continue;
            String patternStr = raw;
            Rule rule = null;
            int eq = raw.indexOf('=');
            if (eq >= 0) {
                patternStr = raw.substring(0, eq);
                rule = parseValueSpec(raw.substring(eq + 1));
            }
            BlockPattern pattern = BlockPattern.parse(patternStr);
            if (pattern != null) parsedWhitelist.add(new Entry(pattern, rule));
        }
        this.whitelist = parsedWhitelist;

        List<BlockPattern> parsedBlacklist = new ArrayList<>();
        for (String raw : blacklist) {
            if (raw == null || raw.trim().isEmpty()) continue;
            BlockPattern pattern = BlockPattern.parse(raw);
            if (pattern != null) parsedBlacklist.add(pattern);
        }
        this.blacklist = parsedBlacklist;
    }

    /**
     * Resolves how a block that territory rules would deny breaking should be handled.
     *
     * @return the slow-down to apply, or {@code null} to keep the hard cancel (full protection).
     */
    public Rule resolve(Block block) {
        for (Entry entry : whitelist) {
            if (entry.pattern().matches(block)) {
                return entry.rule() != null ? entry.rule() : new Rule(defaultMode, defaultValue);
            }
        }
        if (!enabled) return null;
        for (BlockPattern pattern : blacklist) {
            if (pattern.matches(block)) return null;
        }
        return new Rule(defaultMode, defaultValue);
    }

    /** Computes the BreakSpeed the player should get under a resolved rule. */
    public static float applySpeed(Rule rule, float baseSpeed, BlockState state, Level level, BlockPos pos, Player player) {
        switch (rule.mode()) {
            case MULTIPLIER -> {
                double multiplier = rule.value();
                if (multiplier <= 0) return baseSpeed;
                return baseSpeed / (float) multiplier;
            }
            case FIXED -> {
                double seconds = rule.value();
                if (seconds <= 0) return baseSpeed;
                float hardness = state.getDestroySpeed(level, pos);
                // Instant-break (0) and unbreakable (-1) blocks have no hardness to pace against.
                if (hardness <= 0) return baseSpeed;
                // Vanilla progress per tick = digSpeed / hardness / factor, breaking at 1.0. To hit
                // 20*seconds ticks, invert: digSpeed = hardness * factor / (20*seconds).
                int factor = player.hasCorrectToolForDrops(state) ? 30 : 100;
                float speed = (float) (hardness * factor / (20.0 * seconds));
                return Math.max(speed, 1.0e-4F);
            }
        }
        return baseSpeed;
    }

    private static Mode parseMode(String name, Mode fallback) {
        if (name == null) return fallback;
        try {
            return Mode.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            WarForgeMod.LOGGER.warn("MineTime: unknown mode '{}', defaulting to {}", name, fallback);
            return fallback;
        }
    }

    // Value specs: "x2.5" or bare "2.5" => MULTIPLIER 2.5; "5s" => FIXED 5 seconds. Empty => default.
    private static Rule parseValueSpec(String spec) {
        if (spec == null) return null;
        String trimmed = spec.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) return null;
        try {
            if (trimmed.endsWith("s")) {
                return new Rule(Mode.FIXED, Double.parseDouble(trimmed.substring(0, trimmed.length() - 1).trim()));
            }
            if (trimmed.startsWith("x")) {
                return new Rule(Mode.MULTIPLIER, Double.parseDouble(trimmed.substring(1).trim()));
            }
            return new Rule(Mode.MULTIPLIER, Double.parseDouble(trimmed));
        } catch (NumberFormatException e) {
            WarForgeMod.LOGGER.warn("MineTime: could not parse value spec '{}'; using profile default", spec);
            return null;
        }
    }

    private interface BlockPattern {
        boolean matches(Block block);

        static BlockPattern parse(String raw) {
            String pattern = raw.trim();
            if (pattern.isEmpty()) return null;

            if (pattern.startsWith("#")) {
                ResourceLocation tagId = ResourceLocation.tryParse(pattern.substring(1).trim());
                if (tagId == null) {
                    WarForgeMod.LOGGER.warn("MineTime: invalid tag pattern '{}'", raw);
                    return null;
                }
                TagKey<Block> tag = TagKey.create(Registries.BLOCK, tagId);
                return block -> block.builtInRegistryHolder().is(tag);
            }

            if (pattern.contains("*")) {
                String regex = Pattern.quote(pattern).replace("\\*", "\\E.*\\Q");
                Pattern compiled = Pattern.compile(regex);
                return block -> {
                    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
                    return id != null && compiled.matcher(id.toString()).matches();
                };
            }

            ResourceLocation exact = ResourceLocation.tryParse(pattern);
            if (exact == null) {
                WarForgeMod.LOGGER.warn("MineTime: invalid block pattern '{}'", raw);
                return null;
            }
            return block -> exact.equals(BuiltInRegistries.BLOCK.getKey(block));
        }
    }
}
