package com.incuid.chat.messaging;

import com.incuid.chat.IncuidChatPlugin;
import com.incuid.chat.community.AfkService;
import com.incuid.chat.storage.PlayerPreferences;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Messages and reply state are in-memory; ignore and message settings are persisted by UUID. */
public final class MessageService {
    private final IncuidChatPlugin plugin;
    private final PlayerPreferences prefs;
    private final AfkService afk;
    private final Map<UUID,UUID> lastPartner = new ConcurrentHashMap<>();
    public MessageService(IncuidChatPlugin plugin,PlayerPreferences prefs,AfkService afk) { this.plugin=plugin;this.prefs=prefs;this.afk=afk; }
    private static void say(CommandSender sender,String message) { sender.sendMessage(Component.text(message)); }
    private static Player player(CommandSourceStack stack) {
        if (stack.getSender() instanceof Player p) return p;
        say(stack.getSender(),"This command is only available to players."); return null;
    }
    private Player findOnline(String name) {
        for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().equalsIgnoreCase(name)) return p;
        return null;
    }
    private static String text(String[] args,int from) { return String.join(" ",Arrays.copyOfRange(args,from,args.length)); }
    private void send(Player source,Player target,String content) {
        if (!source.canSee(target)) { say(source,"Player is not available.");return; }
        String problem = MessageRules.problem(source.getUniqueId(),target.getUniqueId(),content);
        if (problem != null) { say(source,problem);return; }
        if (!source.hasPermission("incuidchat.pm.bypass")) {
            if (prefs.get(source.getUniqueId()).ignored().contains(target.getUniqueId())) { say(source,"You are ignoring that player.");return; }
            if (prefs.get(target.getUniqueId()).ignored().contains(source.getUniqueId())) { say(source,"That player is not accepting your messages.");return; }
            if (prefs.get(target.getUniqueId()).messagesDisabled()) { say(source,"That player has private messages disabled.");return; }
        }
        // Display user input as literal text, not MiniMessage.
        source.sendMessage(Component.text("[To "+target.getName()+"] ",TextColor.color(0xAAAAAA))
                .append(Component.text(content,TextColor.color(0xEEEEEE))));
        target.sendMessage(Component.text("[From "+source.getName()+"] ",TextColor.color(0xFFCC66))
                .append(Component.text(content,TextColor.color(0xEEEEEE))));
        if (afk.enabled() && afk.isAfk(target.getUniqueId())) {
            source.sendMessage(AfkService.label().append(Component.text(" "+target.getName()+" is currently away and may not respond.",TextColor.color(0xAAAAAA))));
        }
        lastPartner.put(source.getUniqueId(),target.getUniqueId());
        lastPartner.put(target.getUniqueId(),source.getUniqueId());
        if (plugin.getConfig().getBoolean("private-messages.sound.enabled",false)) {
            // Sound is deliberately optional and bounded; no unsafe enum lookup.
            target.playSound(target.getLocation(),"minecraft:block.note_block.pling",0.5f,1.3f);
        }
    }
    public BasicCommand command(String type) { return new BasicCommand() {
        @Override public void execute(CommandSourceStack stack,String[] args) {
            Player player=player(stack); if(player==null)return;
            if(!player.hasPermission("incuidchat.pm")) { say(player,"You do not have permission.");return; }
            switch(type) {
                case "msg" -> {
                    if(args.length<2) {say(player,"Usage: /incuidmsg <player> <message>");return;}
                    Player target=findOnline(args[0]);
                    if(target==null || !player.canSee(target)) {say(player,"Player is not online.");return;}
                    send(player,target,text(args,1));
                }
                case "reply" -> {
                    if(args.length<1) {say(player,"Usage: /incuidreply <message>");return;}
                    UUID id=lastPartner.get(player.getUniqueId());
                    Player target=id==null?null:Bukkit.getPlayer(id);
                    if(target==null) {say(player,"No recent online conversation partner.");return;}
                    send(player,target,text(args,0));
                }
                case "ignore", "unignore" -> {
                    if(args.length!=1) {say(player,"Usage: /incuid"+type+" <online player>");return;}
                    Player target=findOnline(args[0]);
                    if(target==null || !player.canSee(target)) {say(player,"Player must be online to change their ignore status.");return;}
                    if(target.getUniqueId().equals(player.getUniqueId())) {say(player,"You cannot ignore yourself.");return;}
                    boolean changed=prefs.ignore(player.getUniqueId(),target.getUniqueId(),type.equals("ignore"));
                    say(player,changed ? (type.equals("ignore")?"Ignoring ":"No longer ignoring ")+target.getName() : "Ignore list unchanged.");
                }
                case "ignorelist" -> {
                    Set<UUID> ignored=prefs.get(player.getUniqueId()).ignored();
                    if(ignored.isEmpty()) {say(player,"Your ignore list is empty.");return;}
                    List<String> names=new ArrayList<>();
                    for(UUID id:ignored) {
                        Player online=Bukkit.getPlayer(id);
                        names.add(online==null?id.toString():online.getName());
                    }
                    say(player,"Ignored players: "+String.join(", ",names));
                }
                case "msgtoggle" -> say(player,"Incoming private messages "+(prefs.toggleMessages(player.getUniqueId())?"enabled":"disabled")+".");
                default -> say(player,"Unknown messaging command.");
            }
        }
        @Override public Collection<String> suggest(CommandSourceStack stack,String[] args) {
            if(args.length!=1 || !(stack.getSender() instanceof Player))return List.of();
            if(!(type.equals("msg")||type.equals("ignore")||type.equals("unignore")))return List.of();
            Player p=(Player)stack.getSender();
            return Bukkit.getOnlinePlayers().stream().filter(p::canSee).map(Player::getName)
                .filter(s->s.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
    }; }
    public void quit(UUID id) { lastPartner.remove(id);lastPartner.values().removeIf(id::equals); }
    public void close() { lastPartner.clear(); }
}
