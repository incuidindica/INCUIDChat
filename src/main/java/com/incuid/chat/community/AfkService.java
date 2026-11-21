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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

/** Online session AFK state, with each player's original tab component restored on return. */
public final class AfkService implements Listener, BasicCommand {
    private final IncuidChatPlugin plugin;
    private final Set<UUID> afk = new HashSet<>();
    private final Map<UUID,Component> originalNames = new HashMap<>();
    private final Map<UUID,Long> lastActivity = new HashMap<>();
    private final Set<UUID> autoMarked = new HashSet<>();
    private BukkitTask autoTask;
    public void start() {
        if (autoTask != null) autoTask.cancel();
        autoTask=Bukkit.getScheduler().runTaskTimer(plugin,() -> {
            if (!enabled() || !plugin.getConfig().getBoolean("afk.auto.enabled",true)) return;
            long timeout=Math.max(60,plugin.getConfig().getLong("afk.auto.idle-seconds",300))*1000L;
            long now=System.currentTimeMillis();
            for (Player player:Bukkit.getOnlinePlayers()) {
                UUID id=player.getUniqueId();
                if (!isAfk(id) && now-lastActivity.computeIfAbsent(id,k->now)>=timeout) { setAfk(player,true);autoMarked.add(id); }
            }
        },400L,400L);
    }
    private void active(Player p) {
        UUID id=p.getUniqueId();
        lastActivity.put(id,System.currentTimeMillis());
        if (isAfk(id) && AfkRules.shouldClearOnActivity(
                autoMarked.contains(id),
                plugin.getConfig().getBoolean("afk.auto.clear-on-activity",true),
                plugin.getConfig().getBoolean("afk.manual.clear-on-activity",true))) {
            setAfk(p,false);
        }
    }
    @EventHandler public void onJoin(PlayerJoinEvent e) { lastActivity.put(e.getPlayer().getUniqueId(),System.currentTimeMillis()); }
    @EventHandler public void onMove(PlayerMoveEvent e) {
        if (e.getTo()!=null && (e.getFrom().getBlockX()!=e.getTo().getBlockX() || e.getFrom().getBlockY()!=e.getTo().getBlockY() || e.getFrom().getBlockZ()!=e.getTo().getBlockZ())) active(e.getPlayer());
    }
    @EventHandler public void onInteract(PlayerInteractEvent e) { active(e.getPlayer()); }
    @EventHandler public void onCommand(PlayerCommandPreprocessEvent e) {
        // /afk must toggle the current state, not clear it first and then toggle it back on.
        String command=e.getMessage().trim().split("\\s+",2)[0].toLowerCase(Locale.ROOT);
        if (command.equals("/afk") || command.equals("/incuidchat:afk")) return;
        active(e.getPlayer());
    }
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
        if (!value) autoMarked.remove(id);
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
        lastActivity.put(p.getUniqueId(),System.currentTimeMillis());
        boolean value=setAfk(p,!isAfk(p.getUniqueId()));
        p.sendMessage(Component.text(value?"You are now AFK. Use /afk again to return.":"You are no longer AFK."));
        if (plugin.getConfig().getBoolean("afk.broadcast",false)) Bukkit.broadcast(Component.text(p.getName()+(value?" is now AFK.":" is back."),TextColor.color(0xAAAAAA)));
    }
    public void onChat(Player p) {
        lastActivity.put(p.getUniqueId(),System.currentTimeMillis());
        if (enabled() && plugin.getConfig().getBoolean("afk.clear-on-chat",true) && isAfk(p.getUniqueId())) setAfk(p,false);
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { setAfk(event.getPlayer(),false);lastActivity.remove(event.getPlayer().getUniqueId()); }
    public void shutdown() {
        if (autoTask!=null) { autoTask.cancel();autoTask=null; }
        lastActivity.clear();autoMarked.clear();
        for (UUID id:List.copyOf(afk)) { Player p=Bukkit.getPlayer(id); if(p!=null)setAfk(p,false); }
        afk.clear();originalNames.clear();
    }
}
