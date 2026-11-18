package com.incuid.chat.health;

/** Pure calculations; no Bukkit calls. */
public final class HealthCalculator {
    private HealthCalculator() { }
    public enum Condition { HEALTHY, INJURED, CRITICAL, DEAD }
    public record Result(double current, double maximum, double percent, int filled, Condition condition) { }
    public static Result calculate(double current, double maximum, double healthyAt, double injuredAt) {
        // Fail closed on invalid values: an all-empty, dead-looking indicator.
        if (!Double.isFinite(current) || !Double.isFinite(maximum) || maximum <= 0) {
            return new Result(0, 0, 0, 0, Condition.DEAD);
        }
        double adjusted = Math.max(0, Math.min(current, maximum));
        double pct = (adjusted / maximum) * 100.0;
        int count = adjusted <= 0 ? 0 : Math.max(1, Math.min(10, (int) Math.ceil(pct / 10.0)));
        Condition condition = adjusted <= 0 ? Condition.DEAD : pct >= healthyAt ? Condition.HEALTHY
                : pct >= injuredAt ? Condition.INJURED : Condition.CRITICAL;
        return new Result(adjusted, maximum, pct, count, condition);
    }
}
