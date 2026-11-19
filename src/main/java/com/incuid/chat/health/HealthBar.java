package com.incuid.chat.health;

import com.incuid.chat.config.ChatSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import java.util.Locale;

public final class HealthBar {
    private HealthBar() { }
    public static Component render(String playerName, HealthCalculator.Result hp, ChatSettings settings, HealthStyle style) {
        String fill = settings.hearts() ? "❤" : settings.filledSymbol();
        String empty = settings.hearts() ? "♡" : settings.emptySymbol();
        String selected = switch (hp.condition()) {
            case HEALTHY -> settings.healthyColor();
            case INJURED -> settings.injuredColor();
            case CRITICAL -> settings.criticalColor();
            case DEAD -> settings.deadColor();
        };
        if (style == HealthStyle.HEART) {
            Component heart = Component.text("❤", TextColor.fromHexString(selected));
            return settings.healthHover() ? heart.hoverEvent(HoverEvent.showText(hoverText(playerName, hp))) : heart;
        }
        Component bar = settings.brackets() ? Component.text("[", TextColor.color(0x888888)) : Component.empty();
        for (int i=0; i<10; i++) {
            boolean filled = i < hp.filled();
            bar = bar.append(Component.text(filled ? fill : empty,
                    TextColor.fromHexString(filled ? selected : settings.emptyColor())));
        }
        if (settings.brackets()) bar = bar.append(Component.text("]", TextColor.color(0x888888)));
        if (settings.healthHover()) bar = bar.hoverEvent(HoverEvent.showText(hoverText(playerName, hp)));
        return bar;
    }
    private static Component hoverText(String playerName, HealthCalculator.Result hp) {
        String status = switch (hp.condition()) {
            case HEALTHY -> "Healthy"; case INJURED -> "Injured";
            case CRITICAL -> "Critical"; case DEAD -> "Dead";
        };
        return Component.text(playerName + "\n")
                .append(Component.text(String.format(Locale.ROOT,"Health: %.1f / %.1f\n", hp.current(), hp.maximum())))
                .append(Component.text("Status: " + status + "\n"))
                .append(Component.text(String.format(Locale.ROOT,"%.1f%% Health",hp.percent())));
    }
}
