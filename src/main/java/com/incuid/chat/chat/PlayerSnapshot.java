package com.incuid.chat.chat;

import com.incuid.chat.health.HealthCalculator;
import com.incuid.chat.integrations.LuckPermsBridge;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import java.util.UUID;

/** Captured only on the Bukkit primary thread. Never retains Player. */
public record PlayerSnapshot(UUID id, String username, HealthCalculator.Result health,
        String prefix, String suffix) {
    public static PlayerSnapshot capture(Player player, double healthy, double injured) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHp = max == null ? 0 : max.getValue();
        var meta = LuckPermsBridge.capture(player);
        return new PlayerSnapshot(player.getUniqueId(), player.getName(),
                HealthCalculator.calculate(player.getHealth(), maxHp, healthy, injured),
                meta.prefix(), meta.suffix());
    }
}
