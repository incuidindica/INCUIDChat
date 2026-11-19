package com.incuid.chat.commands;

import com.incuid.chat.IncuidChatPlugin;
import com.incuid.chat.health.HealthStyle;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.List;

public final class IncuidCommand implements BasicCommand {
    private final IncuidChatPlugin plugin;
    public IncuidCommand(IncuidChatPlugin plugin) { this.plugin=plugin; }
    @Override public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender=stack.getSender();
        String sub=args.length==0 ? "help" : args[0].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "help" -> sender.sendMessage(Component.text("INCUID Chat: /ic help, status, version, reload, healthbar, healthstyle [bars|heart], mentions; /incuidmsg, /incuidreply, /incuidignore, /incuidunignore, /incuidignorelist, /incuidmsgtoggle"));
            case "status" -> sender.sendMessage(Component.text("INCUID Chat Phase 2 | Health: " + plugin.settings().healthEnabled()
                    + " | Chat: " + plugin.settings().chatEnabled() + " | Mentions: " + plugin.settings().mentionEnabled()));
            case "version" -> sender.sendMessage(Component.text("INCUID Chat 1.1.1-phase2-viewer-health for Paper 26.3"));
            case "reload" -> {
                if (!sender.hasPermission("incuidchat.reload") && !sender.hasPermission("incuidchat.admin")) { deny(sender); return; }
                plugin.reloadSettings(); sender.sendMessage(Component.text("INCUID Chat configuration reloaded."));
            }
            case "healthbar" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage(Component.text("Players only.")); return; }
                if (!player.hasPermission("incuidchat.health.hide") || !plugin.settings().allowHealthToggle()) { deny(sender); return; }
                boolean visible=plugin.toggleHealth(player.getUniqueId());
                sender.sendMessage(Component.text("Health indicators in your chat are now " + (visible?"visible":"hidden") + "."));
            }
            case "healthstyle" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage(Component.text("Players only.")); return; }
                if (!player.hasPermission("incuidchat.health.style")) { deny(sender); return; }
                if (args.length == 1) {
                    sender.sendMessage(Component.text("Health display in your chat: " + plugin.healthStyle(player.getUniqueId()).settingValue()
                            + ". Choose /ic healthstyle bars or /ic healthstyle heart."));
                    return;
                }
                if (args.length != 2 || HealthStyle.parse(args[1]) == null) {
                    sender.sendMessage(Component.text("Usage: /ic healthstyle <bars|heart>")); return;
                }
                HealthStyle selected = HealthStyle.parse(args[1]);
                boolean saved = plugin.setHealthStyle(player.getUniqueId(), selected);
                sender.sendMessage(Component.text("Health indicators in your chat now use " + selected.settingValue()
                        + ". " + (saved ? "Only your view changes; preference queued for saving." : "Warning: preference change was not accepted.")));
            }
            case "mentions" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage(Component.text("Players only.")); return; }
                if (!player.hasPermission("incuidchat.mentions")) { deny(sender); return; }
                boolean enabled=plugin.toggleMentions(player.getUniqueId());
                sender.sendMessage(Component.text("Mention notifications " + (enabled?"enabled":"disabled") + "."));
            }
            default -> sender.sendMessage(Component.text("Unknown subcommand. Use /ic help."));
        }
    }
    private static void deny(CommandSender sender) { sender.sendMessage(Component.text("You do not have permission.")); }
    @Override public java.util.Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("healthstyle")) {
            if (!stack.getSender().hasPermission("incuidchat.health.style")) return List.of();
            return List.of("bars", "heart").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(java.util.Locale.ROOT))).toList();
        }
        if (args.length != 1) return List.of();
        return List.of("help","status","version","reload","healthbar","healthstyle","mentions").stream()
            .filter(s -> s.startsWith(args[0].toLowerCase(java.util.Locale.ROOT)))
            .filter(s -> !s.equals("reload") || stack.getSender().hasPermission("incuidchat.reload"))
            .filter(s -> !s.equals("healthstyle") || stack.getSender().hasPermission("incuidchat.health.style"))
            .toList();
    }
}
