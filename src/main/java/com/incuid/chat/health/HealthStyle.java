package com.incuid.chat.health;

import java.util.Locale;

/** Viewer-selected appearance of health indicators in received chat messages. */
public enum HealthStyle {
    BARS, HEART;

    public static HealthStyle parse(String value) {
        if (value == null) return null;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "bars", "bar" -> BARS;
            case "heart" -> HEART;
            default -> null;
        };
    }
    public String settingValue() { return name().toLowerCase(Locale.ROOT); }
}
