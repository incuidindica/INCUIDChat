package com.incuid.chat.commands;

import com.incuid.chat.IncuidChatPlugin;
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
            case "help" -> sender.sendMessage(Component.text("INCUID Chat: /ic help, status, version, reload, healthbar, mentions"));
            case "status" -> sender.sendMessage(Component.text("INCUID Chat Phase 1 | Health: " + plugin.settings().healthEnabled()
                    + " | Chat: " + plugin.settings().chatEnabled() + " | Mentions: " + plugin.settings().mentionEnabled()));
            case "version" -> sender.sendMessage(Component.text("INCUID Chat 1.0.0-phase1 for Paper 26.3"));
            case "reload" -> {
                if (!sender.hasPermission("incuidchat.reload") && !sender.hasPermission("incuidchat.admin")) { deny(sender); return; }
                plugin.reloadSettings(); sender.sendMessage(Component.text("INCUID Chat configuration reloaded."));
            }
            case "healthbar" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage(Component.text("Players only.")); return; }
                if (!player.hasPermission("incuidchat.health.hide") || !plugin.settings().allowHealthToggle()) { deny(sender); return; }
                boolean visible=plugin.toggleHealth(player.getUniqueId());
                sender.sendMessage(Component.text("Your health bar is now " + (visible?"visible":"hidden") + "."));
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
        if (args.length != 1) return List.of();
        return List.of("help","status","version","reload","healthbar","mentions").stream()
            .filter(s -> s.startsWith(args[0].toLowerCase(java.util.Locale.ROOT)))
            .filter(s -> !s.equals("reload") || stack.getSender().hasPermission("incuidchat.reload"))
            .toList();
    }
}
