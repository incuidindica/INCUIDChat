package com.incuid.chat.chat;

import com.incuid.chat.config.ChatSettings;
import com.incuid.chat.health.HealthBar;
import com.incuid.chat.health.HealthDisplayPreference;
import com.incuid.chat.storage.PlayerPreferences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** The source message remains an Adventure component. Never run player text through MiniMessage. */
public final class ChatFormatter {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private ChatFormatter() { }
    public static Component format(PlayerSnapshot p, Component message, ChatSettings s,
                                   PlayerPreferences.State viewerSettings) {
        Component result = Component.empty();
        var display = HealthDisplayPreference.resolve(viewerSettings.style(), viewerSettings.hideHealth(),
                s.allowHealthToggle(), s.healthDefaultVisible());
        if (s.healthEnabled() && display.visible()) {
            result = result.append(HealthBar.render(p.username(), p.health(), s, display.style()))
                    .append(Component.space());
        }
        if (s.luckPrefix()) result = result.append(safeMeta(p.prefix()));
        Component name = Component.text(p.username(), TextColor.fromHexString(s.playerColor()));
        if (s.nameHover()) name = name.hoverEvent(HoverEvent.showText(Component.text("Player: " + p.username())));
        if (s.clickNames()) name = name.clickEvent(ClickEvent.suggestCommand("/msg " + p.username() + " "));
        result = result.append(name);
        if (s.luckSuffix()) result = result.append(safeMeta(p.suffix()));
        // Text fragments in the pipeline may have child components. Adventure inheritance
        // applies this parent color to uncolored descendants without changing mention/item accents.
        Component coloredMessage=Component.empty().color(TextColor.fromHexString(s.messageColor())).append(message);
        return result.append(Component.text(s.separator(),TextColor.color(0x89949D))).append(coloredMessage);
    }
    private static Component safeMeta(String value) {
        // LuckPerms metadata is administrator-managed. No click/hover tags are parsed here.
        if (value == null || value.isEmpty()) return Component.empty();
        // Legacy prefixes like &7 are deliberately printed plainly until converted by admin.
        return MM.deserialize(MM.escapeTags(value), TagResolver.empty());
    }
}
