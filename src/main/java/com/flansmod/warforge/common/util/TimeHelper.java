package com.flansmod.warforge.common.util;

import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.Tags;

public class TimeHelper {
    public TimeHelper() {
    }

    public static long minToMs(int minutes){
        return (long) minutes * 60L * 1000L;
    }

    public static long getSiegeDayLengthMS() {
        return (long) (
                WarForgeConfig.SIEGE_DAY_LENGTH // In hours
                        * 60f // In minutes
                        * 60f // In seconds
                        * 1000f); // In milliseconds
    }

    public static long getYieldDayLengthMs() {
        return (long) (
                WarForgeConfig.YIELD_DAY_LENGTH // In hours
                        * 60f // In minutes
                        * 60f // In seconds
                        * 1000f); // In milliseconds
    }

    public static long getCooldownIntoTicks(float cooldown) {
        // From Minutes
        return (long) (
                cooldown
                        * 60L // Minutes -> Seconds
                        * 20L // Seconds -> Ticks
        );
    }

    public static String formatTime(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        int hours = ((int) minutes) / 60;
        int days = hours / 24;

        // ensure entire time left is not represented in each format, but rather only leftover amounts for that unit

        seconds -= minutes * 60;
        minutes -= hours * 60;
        hours -= days * 24;

        StringBuilder timeBuilder = new StringBuilder();
        if (days > 0) timeBuilder.append(days).append("d ");
        if (hours > 0) timeBuilder.append(hours).append("h ");
        if (minutes > 0) timeBuilder.append(minutes).append("min ");
        if (seconds > 0) timeBuilder.append(seconds).append("s ");
        timeBuilder.append(ms % 1000).append("ms");

        return timeBuilder.toString();
    }

    public long getCooldownRemainingSeconds(float cooldown, long startOfCooldown) {
        long ticks = (long) cooldown; // why did you convert it into ticks if its already in ticks
        long elapsed = startOfCooldown - ticks;

        return (WarForgeMod.serverTick - elapsed) * 20;
    }

    public int getCooldownRemainingMinutes(float cooldown, long startOfCooldown) {
        long ticks = (long) cooldown;
        long elapsed = startOfCooldown - ticks;

        return (int) (
                (WarForgeMod.serverTick - elapsed) * 20 / 60
        );
    }

    public int getCooldownRemainingHours(float cooldown, long startOfCooldown) {
        long ticks = (long) cooldown;
        long elapsed = startOfCooldown - ticks;

        return (int) (
                (WarForgeMod.serverTick - elapsed) * 20 / 60 / 60
        );
    }

    public long getTimeToNextSiegeAdvanceMs() {
        long elapsedMS = System.currentTimeMillis() - WarForgeMod.timestampOfFirstDay;
        long todayElapsedMS = elapsedMS % getSiegeDayLengthMS();

        return getSiegeDayLengthMS() - todayElapsedMS;
    }

    public long getTimeToNextYieldMs() {
        long elapsedMS = System.currentTimeMillis() - WarForgeMod.timestampOfFirstDay;
        long todayElapsedMS = elapsedMS % getYieldDayLengthMs();

        return getYieldDayLengthMs() - todayElapsedMS;
    }

    public static long parseDurationMs(String input) {
        if (input == null || input.isEmpty()) {
            return -1;
        }
        String value = input.trim().toLowerCase();
        long unitMs = 1000L;
        char last = value.charAt(value.length() - 1);
        if (!Character.isDigit(last)) {
            switch (last) {
                case 's': unitMs = 1000L; break;
                case 'm': unitMs = 60L * 1000L; break;
                case 'h': unitMs = 60L * 60L * 1000L; break;
                case 'd': unitMs = 24L * 60L * 60L * 1000L; break;
                default: return -1;
            }
            value = value.substring(0, value.length() - 1);
        }
        try {
            long amount = Long.parseLong(value.trim());
            if (amount < 0) {
                return -1;
            }
            return amount * unitMs;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}