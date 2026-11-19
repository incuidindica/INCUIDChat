package com.incuid.chat.community;

import com.incuid.chat.IncuidChatPlugin;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.*;

/** Online session AFK state, with each player's original tab component restored on return. */
public final class AfkService implements Listener, BasicCommand {
    private final IncuidChatPlugin plugin;
    private final Set<UUID> afk = new HashSet<>();
    private final Map<UUID,Component> originalNames = new HashMap<>();
    public AfkService(IncuidChatPlugin plugin) { this.plugin = plugin; }
    public boolean isAfk(UUID id) { return afk.contains(id); }
    public boolean enabled() { return plugin.getConfig().getBoolean("afk.enabled",true); }
    public static Component label() {
        return Component.text("[",TextColor.color(0xFFFFFF))
            .append(Component.text("AFK",TextColor.color(0xFF5555)))
            .append(Component.text("]",TextColor.color(0xFFFFFF)));
    }
    public boolean setAfk(Player player, boolean value) {
        UUID id=player.getUniqueId();
        if (value == afk.contains(id)) return value;
        if (value) {
            Component current=player.playerListName();
            originalNames.put(id,current);
            afk.add(id);
            player.playerListName(label().append(Component.space()).append(current));
        } else {
            afk.remove(id);
            Component old=originalNames.remove(id);
            if (old!=null) player.playerListName(old);
        }
        return value;
    }
    @Override public void execute(CommandSourceStack stack,String[] args) {
        if (!(stack.getSender() instanceof Player p)) { stack.getSender().sendMessage(Component.text("Players only."));return; }
        if (!enabled() || !p.hasPermission("incuidchat.afk")) { p.sendMessage(Component.text("AFK is unavailable."));return; }
        boolean value=setAfk(p,!isAfk(p.getUniqueId()));
        p.sendMessage(Component.text(value?"You are now AFK. Use /afk again to return.":"You are no longer AFK."));
        if (plugin.getConfig().getBoolean("afk.broadcast",false)) Bukkit.broadcast(Component.text(p.getName()+(value?" is now AFK.":" is back."),TextColor.color(0xAAAAAA)));
    }
    public void onChat(Player p) {
        if (enabled() && plugin.getConfig().getBoolean("afk.clear-on-chat",true) && isAfk(p.getUniqueId())) setAfk(p,false);
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { setAfk(event.getPlayer(),false); }
    public void shutdown() {
        for (UUID id:List.copyOf(afk)) { Player p=Bukkit.getPlayer(id); if(p!=null)setAfk(p,false); }
        afk.clear();originalNames.clear();
    }
}
