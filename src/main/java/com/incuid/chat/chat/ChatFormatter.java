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
        return result.append(Component.text(s.separator())).append(message.colorIfAbsent(TextColor.fromHexString(s.messageColor())));
    }
    private static Component safeMeta(String value) {
        // LuckPerms metadata is administrator-managed. No click/hover tags are parsed here.
        if (value == null || value.isEmpty()) return Component.empty();
        // Legacy prefixes like &7 are deliberately printed plainly until converted by admin.
        return MM.deserialize(MM.escapeTags(value), TagResolver.empty());
    }
}
