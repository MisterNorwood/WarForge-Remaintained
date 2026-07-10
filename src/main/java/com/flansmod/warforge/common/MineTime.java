package com.flansmod.warforge.common;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class MineTime {
    public enum Mode {
        MULTIPLIER,
        FIXED
    }

    public static class Rule {
        public final Mode mode;
        public final double value;

        public Rule(Mode mode, double value) {
            this.mode = mode;
            this.value = value;
        }
    }

    private static class Entry {
        final BlockPattern pattern;
        final Rule rule;

        Entry(BlockPattern p, Rule r) {
            this.pattern = p;
            this.rule = r;
        }
    }

    private boolean enabled = false;
    private Mode defaultMode = Mode.MULTIPLIER;
    private double defaultValue = 5.0;
    private List<Entry> whitelist = new ArrayList<>();
    private List<BlockPattern> blacklist = new ArrayList<>();

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

    public Rule resolve(Block block) {
        for (Entry entry : whitelist) {
            if (entry.pattern.matches(block)) {
                return entry.rule != null ? entry.rule : new Rule(defaultMode, defaultValue);
            }
        }
        if (!enabled) return null;
        for (BlockPattern pattern : blacklist) {
            if (pattern.matches(block)) return null;
        }
        return new Rule(defaultMode, defaultValue);
    }

    public static float applySpeed(Rule rule, float baseSpeed, IBlockState state, World level, BlockPos pos, EntityPlayer player) {
        switch (rule.mode) {
            case MULTIPLIER: {
                double multiplier = rule.value;
                if (multiplier <= 0) return baseSpeed;
                return baseSpeed / (float) multiplier;
            }
            case FIXED: {
                double seconds = rule.value;
                if (seconds <= 0) return baseSpeed;
                float hardness = state.getBlockHardness(level, pos);
                if (hardness <= 0) return baseSpeed;
                int factor = ForgeHooks.canHarvestBlock(state.getBlock(), player, level, pos) ? 30 : 100;
                float speed = (float) (hardness * factor / (20.0 * seconds));
                return Math.max(speed, 1.0e-4F);
            }
            default:
                return baseSpeed;
        }
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
                WarForgeMod.LOGGER.warn("MineTime: block tag patterns are not supported in 1.12.2, ignoring '{}'", raw);
                return null;
            }

            if (pattern.contains("*")) {
                String regex = Pattern.quote(pattern).replace("\\*", "\\E.*\\Q");
                Pattern compiled = Pattern.compile(regex);
                return block -> {
                    net.minecraft.util.ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
                    return id != null && compiled.matcher(id.toString()).matches();
                };
            }

            net.minecraft.util.ResourceLocation exact;
            try {
                exact = new net.minecraft.util.ResourceLocation(pattern);
            } catch (Exception e) {
                WarForgeMod.LOGGER.warn("MineTime: invalid block pattern '{}'", raw);
                return null;
            }
            return block -> exact.equals(ForgeRegistries.BLOCKS.getKey(block));
        }
    }
}
