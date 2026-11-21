package com.incuid.chat.moderation;

import com.incuid.chat.IncuidChatPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.*;

/** Shared implementation for /ic moderation subcommands and dedicated commands. */
public final class ModerationCommands {
    private final IncuidChatPlugin plugin;
    private final ModerationService moderation;
    public ModerationCommands(IncuidChatPlugin plugin,ModerationService moderation) {this.plugin=plugin;this.moderation=moderation;}
    private void reply(CommandSender sender,String text) {sender.sendMessage(Component.text("[INCUID Chat] "+text));}
    public void execute(CommandSender sender,String action,String[] args) {
        if (!plugin.getConfig().getBoolean("moderation.enabled",true)) {reply(sender,"Moderation is disabled.");return;}
        switch(action.toLowerCase(Locale.ROOT)) {
            case "mute" -> mute(sender,args);
            case "unmute" -> unmute(sender,args);
            case "clear" -> clear(sender);
            default -> reply(sender,"Usage: /ic <mute|unmute|clear>");
        }
    }
    private boolean allowed(CommandSender sender,String node) {
        if(sender.hasPermission("incuidchat.admin")||sender.hasPermission(node))return true;
        reply(sender,"You do not have permission.");return false;
    }
    private OfflinePlayer find(String name) {
        Player online=Bukkit.getPlayerExact(name);
        if(online!=null)return online;
        // Bukkit's cached offline lookup is only used for previously known users; no online profile request.
        for (OfflinePlayer p:Bukkit.getOfflinePlayers()) if(p.getName()!=null && p.getName().equalsIgnoreCase(name))return p;
        return null;
    }
    private void mute(CommandSender sender,String[] args) {
        if(!allowed(sender,"incuidchat.moderation.mute"))return;
        if(args.length<1) {reply(sender,"Usage: /ic mute <player> [30m|2h|1d|permanent] [reason]");return;}
        OfflinePlayer target=find(args[0]);
        if(target==null) {reply(sender,"Player not found in known server profiles.");return;}
        long duration=0;int start=1;
        if(args.length>1 && (args[1].equalsIgnoreCase("permanent")||args[1].equalsIgnoreCase("perm"))) start=2;
        else if(args.length>1 && args[1].matches("(?i)[0-9]+[smhd]")) {
            duration=ModerationRules.durationMillis(args[1]);
            if(duration<0) {reply(sender,"Invalid duration. Use examples like 30m, 2h, 1d (maximum 999999 units).");return;}
            start=2;
        }
        String reason=start<args.length?String.join(" ",Arrays.copyOfRange(args,start,args.length)):"No reason specified";
        if(reason.length()>160) {reply(sender,"Reason must be at most 160 characters.");return;}
        moderation.mute(target.getUniqueId(),args[0],duration,reason,sender.getName());
        reply(sender,"Muted "+args[0]+(duration==0?" permanently.":" for "+args[1]+"."));
        if (target.isOnline() && target.getPlayer()!=null) target.getPlayer().sendMessage(Component.text("[INCUID Chat] You have been muted: "+reason));
    }
    private void unmute(CommandSender sender,String[] args) {
        if(!allowed(sender,"incuidchat.moderation.unmute"))return;
        if(args.length!=1) {reply(sender,"Usage: /ic unmute <player>");return;}
        OfflinePlayer target=find(args[0]);
        if(target==null) {reply(sender,"Player not found in known server profiles.");return;}
        reply(sender,moderation.unmute(target.getUniqueId(),args[0],sender.getName())?"Unmuted "+args[0]+".":"That player is not muted.");
    }
    private void clear(CommandSender sender) {
        if(!allowed(sender,"incuidchat.moderation.clear"))return;
        if(!plugin.getConfig().getBoolean("moderation.clear.enabled",true)) {reply(sender,"Chat clearing is disabled.");return;}
        // Client chat can be scrolled back; this is visual clearing, not deletion.
        Component blank=Component.text("\n".repeat(80));
        for(Player p:Bukkit.getOnlinePlayers()) {
            p.sendMessage(blank);
            p.sendMessage(Component.text("[INCUID Chat] Chat was cleared by staff."));
        }
        moderation.logClear(sender.getName());
        reply(sender,"Cleared visible chat for online players.");
    }
}
