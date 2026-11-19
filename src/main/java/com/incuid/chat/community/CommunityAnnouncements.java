package com.incuid.chat.community;

import com.incuid.chat.IncuidChatPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Decorates original event announcements instead of manually rebroadcasting duplicates. */
public final class CommunityAnnouncements implements Listener {
    private final IncuidChatPlugin plugin;
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    public CommunityAnnouncements(IncuidChatPlugin plugin) { this.plugin=plugin; }
    private boolean on(String path) { return plugin.getConfig().getBoolean("announcements."+path+".enabled",true); }
    private Component format(String key,String fallback,Player player) {
        String template=plugin.getConfig().getString("announcements."+key,fallback);
        return MINI.deserialize(template.replace("{player}",MiniMessage.miniMessage().escapeTags(player.getName())));
    }
    @EventHandler(priority=EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        Player p=event.getPlayer();
        if (!on("join") && !on("first-join")) return; // Preserve vanilla if all join modules are off.
        if (!p.hasPlayedBefore() && on("first-join")) {
            event.joinMessage(format("first-join.public-format","<gold>✦ Welcome to INCUID, {player}!</gold>",p));
            if (plugin.getConfig().getBoolean("announcements.first-join.private-welcome",true)) {
                Bukkit.getScheduler().runTask(plugin,()-> {
                    if (p.isOnline()) p.sendMessage(format("first-join.private-format","<gray>Welcome to INCUID! Visit <aqua>playincuid.com</aqua></gray>",p));
                });
            }
        } else if (on("join")) {
            event.joinMessage(format("join.format","<gray>✦ <white>{player}</white> joined the world.</gray>",p));
        }
    }
    @EventHandler(priority=EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        if (on("leave")) event.quitMessage(format("leave.format","<gray>✦ <white>{player}</white> left the world.</gray>",event.getPlayer()));
    }
    @EventHandler(priority=EventPriority.NORMAL)
    public void onDeath(PlayerDeathEvent event) {
        if (!on("death")) return;
        Component message=event.deathMessage();
        if (message!=null) event.deathMessage(MINI.deserialize(plugin.getConfig().getString("announcements.death.prefix","<red>☠ </red>")).append(message));
        if (plugin.getConfig().getBoolean("announcements.death.private-coordinates",true)) {
            Player p=event.getEntity();
            int x=p.getLocation().getBlockX(),y=p.getLocation().getBlockY(),z=p.getLocation().getBlockZ();
            String world=p.getWorld().getName();
            Bukkit.getScheduler().runTask(plugin,()-> {
                if(p.isOnline())p.sendMessage(Component.text("Death location: "+world+" [X: "+x+", Y: "+y+", Z: "+z+"]"));
            });
        }
    }
    @EventHandler(priority=EventPriority.NORMAL)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (!on("advancement")) return;
        // Existing message is null for non-announced advancements, such as recipe unlocks.
        // Keep the vanilla-translated title and hover component intact.
        Component original=event.message();
        if (original!=null) event.message(MINI.deserialize(plugin.getConfig().getString("announcements.advancement.prefix","<gold>✦ </gold>")).append(original));
    }
}
