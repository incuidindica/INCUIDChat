package com.incuid.chat.channels;

import com.incuid.chat.IncuidChatPlugin;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import java.util.*;

/** Thread-safe channel configuration. World positions and permissions captured on server thread. */
public final class ChannelService {
    public record Settings(boolean enabled, boolean local, boolean staff, double radius, boolean labels, boolean diagnostics) { }
    public record Snapshot(String channel, Set<UUID> eligible, int online) {
        public Snapshot { eligible = Set.copyOf(eligible); }
        public boolean accepts(Audience viewer) { return viewer instanceof Player p && eligible.contains(p.getUniqueId()); }
    }
    private final IncuidChatPlugin plugin;
    private volatile Settings settings;
    public ChannelService(IncuidChatPlugin plugin) { this.plugin=plugin; reload(); }
    public void reload() {
        FileConfiguration c=plugin.getConfig();
        settings=new Settings(c.getBoolean("channels.enabled",false),c.getBoolean("channels.local.enabled",true),
            c.getBoolean("channels.staff.enabled",true),Math.max(1,Math.min(1000,c.getDouble("channels.local.radius",100))),
            c.getBoolean("channels.show-labels",true),c.getBoolean("diagnostics.recipients.enabled",false));
    }
    public Settings settings() { return settings; }
    private boolean staff(Player p) { return ChannelRules.staffAllowed(p.isOp(),p.hasPermission("incuidchat.staffchat")); }
    public String channel(Player sender) {
        Settings s=settings;
        if (!s.enabled()) return "global";
        return ChannelRules.normalize(plugin.preferences().get(sender.getUniqueId()).channel(),s.local(),s.staff(),staff(sender));
    }
    public Snapshot capture(Player sender,String override) {
        Settings s=settings;
        String choice=s.enabled()?ChannelRules.normalize(override==null?channel(sender):override,s.local(),s.staff(),staff(sender)):"global";
        Set<UUID> eligible=new HashSet<>();
        var source=sender.getLocation();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if ("staff".equals(choice)) {
                if (staff(player)) eligible.add(player.getUniqueId());
            } else if ("local".equals(choice)) {
                var loc=player.getLocation();
                if (ChannelRules.inRange(source.getWorld().getUID().toString(),source.getX(),source.getY(),source.getZ(),
                        loc.getWorld().getUID().toString(),loc.getX(),loc.getY(),loc.getZ(),s.radius())) eligible.add(player.getUniqueId());
            } else eligible.add(player.getUniqueId());
        }
        return new Snapshot(choice,eligible,Bukkit.getOnlinePlayers().size());
    }
    public Component label(String channel) {
        if (!settings.enabled() || !settings.labels()) return Component.empty();
        return switch(channel) {
            case "local" -> Component.text("[L] ",TextColor.color(0x9FC8AA));
            case "staff" -> Component.text("[Staff] ",TextColor.color(0xC7A5DC));
            default -> Component.text("[G] ",TextColor.color(0x95BBD3));
        };
    }
    public BasicCommand command(String variant) {
        return new BasicCommand() {
            @Override public void execute(CommandSourceStack stack,String[] args) {
                if (!(stack.getSender() instanceof Player p)) { stack.getSender().sendMessage("Players only."); return; }
                if (!settings.enabled()) { p.sendMessage("Chat channels are disabled."); return; }
                if (variant.equals("chat")) {
                    if (args.length==0) { p.sendMessage("Current channel: " + channel(p) + ". Use /chat <global|local|staff>.");return; }
                    String selected=ChannelRules.normalize(args[0],settings.local(),settings.staff(),staff(p));
                    if (!args[0].equalsIgnoreCase(selected)) { p.sendMessage("That channel is disabled or you lack staff access. Ask an admin to enable local/staff in config.yml.");return; }
                    plugin.preferences().channel(p.getUniqueId(),selected);
                    p.sendMessage("Your active chat channel is now " + selected + ".");
                } else {
                    String requested=switch(variant) { case "g" -> "global"; case "l" -> "local"; default -> "staff"; };
                    if (!requested.equals(ChannelRules.normalize(requested,settings.local(),settings.staff(),staff(p)))) {
                        p.sendMessage("That channel is unavailable or you lack permission.");return;
                    }
                    if (args.length==0) { p.sendMessage("Usage: /"+variant+" <message>");return; }
                    // Shortcuts publish an explicit channel audience; never call Bukkit.broadcast or bypass moderation.
                    plugin.sendChannelCommand(p,requested,String.join(" ",args));
                }
            }
            @Override public Collection<String> suggest(CommandSourceStack stack,String[] args) {
                if (!variant.equals("chat") || args.length!=1) return List.of();
                List<String> list=new ArrayList<>();list.add("global");
                if (settings.local()) list.add("local");
                if (settings.staff() && (stack.getSender() instanceof Player p) && staff(p)) list.add("staff");
                return list.stream().filter(n->n.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
            }
        };
    }
}
