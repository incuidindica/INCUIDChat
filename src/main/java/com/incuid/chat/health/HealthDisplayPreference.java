package com.incuid.chat.health;

/** A recipient-only view setting; independent of the message sender's preferences. */
public record HealthDisplayPreference(HealthStyle style, boolean visible) {
    public static HealthDisplayPreference resolve(HealthStyle style, boolean toggled,
                                                   boolean allowToggle, boolean defaultVisible) {
        // The saved toggle is relative to the administrator's visibility default.
        boolean visible = allowToggle && toggled ? !defaultVisible : defaultVisible;
        return new HealthDisplayPreference(style == null ? HealthStyle.BARS : style, visible);
    }
}
