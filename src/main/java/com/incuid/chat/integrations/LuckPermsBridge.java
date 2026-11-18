package com.incuid.chat.integrations;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Only called on the primary server thread. */
public final class LuckPermsBridge {
    private LuckPermsBridge() { }
    public static PrefixSuffix capture(Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) return new PrefixSuffix("", "");
        try {
            LuckPerms api = LuckPermsProvider.get();
            var meta = api.getPlayerAdapter(Player.class).getMetaData(player);
            return new PrefixSuffix(meta.getPrefix() == null ? "" : meta.getPrefix(),
                    meta.getSuffix() == null ? "" : meta.getSuffix());
        } catch (RuntimeException | LinkageError ignored) {
            return new PrefixSuffix("", "");
        }
    }
    public record PrefixSuffix(String prefix,String suffix) { }
}
