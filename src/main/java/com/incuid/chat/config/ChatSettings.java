package com.incuid.chat.config;

import org.bukkit.configuration.file.FileConfiguration;
import java.util.Locale;

/** Immutable, validated snapshot published atomically on reload. */
public record ChatSettings(boolean chatEnabled, boolean healthEnabled, String filledSymbol, String emptySymbol,
        boolean brackets, boolean healthHover, boolean hearts, double healthyAt, double injuredAt,
        String healthyColor, String injuredColor, String criticalColor, String deadColor, String emptyColor,
        String separator, String playerColor, String messageColor, boolean nameHover, boolean clickNames,
        boolean mentionEnabled, String mentionColor, String mentionSound, int mentionCooldown,
        boolean everyoneEnabled, int everyoneCooldown, float mentionVolume, float mentionPitch,
        boolean allowHealthToggle, boolean healthDefaultVisible, boolean luckPrefix, boolean luckSuffix) {

    public static ChatSettings load(FileConfiguration c) {
        double healthy = finiteThreshold(c.getDouble("health.thresholds.healthy", 75), 75);
        double injured = finiteThreshold(c.getDouble("health.thresholds.injured", 50), 50);
        if (injured >= healthy) { injured = 50; healthy = 75; }
        return new ChatSettings(c.getBoolean("chat.enabled",true), c.getBoolean("health.enabled",true),
                symbol(c.getString("health.filled-symbol"), "|"), symbol(c.getString("health.empty-symbol"), "."),
                c.getBoolean("health.show-brackets",true), c.getBoolean("health.hover.enabled",true),
                c.getBoolean("health.hearts", false), healthy, injured,
                color(c.getString("health.colors.healthy"),"#55FF55"), color(c.getString("health.colors.injured"),"#FFDD55"),
                color(c.getString("health.colors.critical"),"#FF5555"), color(c.getString("health.colors.dead"),"#555555"),
                color(c.getString("health.colors.empty"),"#555555"),
                c.getString("chat.separator", " » "), color(c.getString("chat.player-color"),"#FFFFFF"),
                color(c.getString("chat.message-color"),"#EEEEEE"), c.getBoolean("chat.name-hover",true),
                c.getBoolean("chat.name-click-suggest-msg",true), c.getBoolean("mentions.enabled",true),
                color(c.getString("mentions.highlight-color"),"#FFAA00"),
                c.getString("mentions.sound", "BLOCK_NOTE_BLOCK_PLING"), Math.max(0,c.getInt("mentions.cooldown-seconds",10)),
                c.getBoolean("mentions.everyone-enabled",true), Math.max(0,c.getInt("mentions.everyone-cooldown-seconds",60)),
                (float)Math.max(0,Math.min(2,c.getDouble("mentions.sound-volume",0.6))),
                (float)Math.max(0,Math.min(2,c.getDouble("mentions.sound-pitch",1.25))),
                c.getBoolean("health.visibility.allow-player-toggle",true), c.getBoolean("health.visibility.default-visible",true),
                c.getBoolean("integrations.luckperms-prefix",true), c.getBoolean("integrations.luckperms-suffix",true));
    }
    private static double finiteThreshold(double value, double fallback) {
        return Double.isFinite(value) && value >= 0 && value <= 100 ? value : fallback;
    }
    private static String symbol(String input,String fallback) {
        return input == null || input.isEmpty() || input.codePointCount(0,input.length()) > 2 ? fallback : input;
    }
    private static String color(String input,String fallback) {
        return input != null && input.matches("#[0-9a-fA-F]{6}") ? input.toUpperCase(Locale.ROOT) : fallback;
    }
}
