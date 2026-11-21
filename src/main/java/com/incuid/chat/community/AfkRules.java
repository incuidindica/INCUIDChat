package com.incuid.chat.community;

/** Pure AFK policy, kept independent of Bukkit for regression tests. */
public final class AfkRules {
    private AfkRules() {}
    public static boolean shouldClearOnActivity(boolean automaticallyMarked, boolean autoClear, boolean manualClear) {
        return automaticallyMarked ? autoClear : manualClear;
    }
}
