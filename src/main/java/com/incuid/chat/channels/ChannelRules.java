package com.incuid.chat.channels;

import java.util.*;

/** Pure recipient intersection: never add viewers removed by another plugin. */
public final class ChannelRules {
    private ChannelRules() { }
    public static Set<UUID> intersect(Set<UUID> alreadyAllowed, Set<UUID> channelEligible) {
        Set<UUID> output = new HashSet<>(alreadyAllowed);
        output.retainAll(channelEligible);
        return Set.copyOf(output);
    }
    public static boolean inRange(String sourceWorld, double sx, double sy, double sz,
                                  String viewerWorld, double x, double y, double z, double radius) {
        if (sourceWorld == null || !sourceWorld.equals(viewerWorld) || !Double.isFinite(radius) || radius < 0) return false;
        double dx=x-sx, dy=y-sy, dz=z-sz;
        return dx*dx+dy*dy+dz*dz <= radius*radius;
    }
    /** Operators can always access staff chat. Non-operators need an explicitly granted permission. */
    public static boolean staffAllowed(boolean operator, boolean explicitPermission) {
        return operator || explicitPermission;
    }
    public static String normalize(String candidate, boolean localEnabled, boolean staffEnabled, boolean staffPermission) {
        if ("local".equalsIgnoreCase(candidate) && localEnabled) return "local";
        if ("staff".equalsIgnoreCase(candidate) && staffEnabled && staffPermission) return "staff";
        return "global";
    }
}
