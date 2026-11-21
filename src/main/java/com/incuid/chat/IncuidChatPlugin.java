package com.incuid.chat;

import com.incuid.chat.chat.ChatFormatter;
import com.incuid.chat.channels.ChannelService;
import com.incuid.chat.config.ConfigMigrator;
import com.incuid.chat.community.AfkService;
import com.incuid.chat.community.CommunityAnnouncements;
import com.incuid.chat.chat.PlayerSnapshot;
import com.incuid.chat.commands.IncuidCommand;
import com.incuid.chat.config.ChatSettings;
import com.incuid.chat.mentions.MentionService;
import com.incuid.chat.health.HealthStyle;
import com.incuid.chat.storage.PlayerPreferences;
import com.incuid.chat.interactive.InteractiveTags;
import com.incuid.chat.interactive.ChatPipeline;
import com.incuid.chat.messaging.MessageService;
import com.incuid.chat.moderation.ModerationService;
import com.incuid.chat.moderation.ModerationCommands;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.concurrent.*;

public final class IncuidChatPlugin extends JavaPlugin implements Listener {
    private volatile ChatSettings settings;
    private volatile InteractiveConfig interactiveConfig = new InteractiveConfig(true,true,1,1,8192);
    private record InteractiveConfig(boolean items,boolean coords,int maxItems,int maxCoords,int maxBytes) { }
    private PlayerPreferences preferences;
    private MessageService messages;
    private AfkService afk;
    private ModerationService moderation;
    private ChannelService channels;
    private record PendingChannel(String channel,String text) { }
    private final ConcurrentMap<UUID,PendingChannel> nextChannelMessage=new ConcurrentHashMap<>();
    private final ConcurrentMap<AsyncChatEvent,PendingDelivery> pendingDelivery=new ConcurrentHashMap<>();
    private record PendingDelivery(UUID sender,Set<UUID> mentions,boolean everyone,ChatSettings settings,
                                   String channel,int initialRecipients,int afterIncuid) { }
    private ModerationCommands moderationCommands;
    private final ConcurrentMap<UUID,Long> mentionCooldown = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID,Long> everyoneCooldown = new ConcurrentHashMap<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        ConfigMigrator.migrate(this);
        // Migrate the legacy stock near-white text; do not change any other custom color.
        if ("#EEEEEE".equalsIgnoreCase(getConfig().getString("chat.message-color",""))) {
            getConfig().set("chat.message-color","#B8C5D1");
            // Preserve comments and other administrator edits: change only the exact old stock line.
            java.nio.file.Path configFile = new java.io.File(getDataFolder(),"config.yml").toPath();
            try {
                String original = java.nio.file.Files.readString(configFile);
                String updated = original.replace("message-color: '#EEEEEE'", "message-color: '#B8C5D1'")
                        .replace("message-color: \"#EEEEEE\"", "message-color: \"#B8C5D1\"");
                if (!updated.equals(original)) java.nio.file.Files.writeString(configFile,updated);
            } catch (java.io.IOException ex) {
                getLogger().warning("Legacy chat color updated in memory, but could not persist config.yml: " + ex.getMessage());
            }
            getLogger().info("Updated legacy default chat text color to muted blue-gray (#B8C5D1).");
        }
        for (String path : List.of("messages.yml","formats.yml","filters.yml")) {
            if (!new java.io.File(getDataFolder(),path).isFile()) saveResource(path,false);
        }
        reloadSettings();
        preferences = new PlayerPreferences(this);
        channels = new ChannelService(this);
        afk = new AfkService(this);
        moderation = new ModerationService(this);
        moderationCommands = new ModerationCommands(this,moderation);
        messages = new MessageService(this,preferences,afk);
        Bukkit.getPluginManager().registerEvents(moderation,this);
        for (String action : List.of("mute","unmute","clear")) {
            registerCommand("incuid"+action,"INCUID moderation: "+action,List.of(),new BasicCommand() {
                @Override public void execute(CommandSourceStack stack,String[] args) {
                    moderationCommands.execute(stack.getSender(),action,args);
                }
            });
        }
        Bukkit.getPluginManager().registerEvents(afk,this);
        afk.start();
        Bukkit.getPluginManager().registerEvents(new CommunityAnnouncements(this),this);
        registerCommand("afk","Toggle away-from-keyboard status",List.of(),afk);
        if (channels.settings().enabled()) {
            registerCommand("chat","Select INCUID chat channel",List.of(),channels.command("chat"));
            if (getConfig().getBoolean("channels.register-shortcuts",true)) {
                registerCommand("g","Send one global message",List.of(),channels.command("g"));
                registerCommand("l","Send one local message",List.of(),channels.command("l"));
                registerCommand("sc","Send one staff message",List.of(),channels.command("sc"));
            }
        }
        Bukkit.getPluginManager().registerEvents(this,this);
        registerCommand("incuidchat", "INCUID Chat management", List.of("ic"), new IncuidCommand(this));
        if (getConfig().getBoolean("private-messages.enabled", true)) {
            registerCommand("incuidmsg","INCUID private message",shortAliases("msg"),messages.command("msg"));
            registerCommand("incuidreply","Reply to private message",shortAliases("reply"),messages.command("reply"));
            registerCommand("incuidignore","Ignore player",shortAliases("ignore"),messages.command("ignore"));
            registerCommand("incuidunignore","Unignore player",shortAliases("unignore"),messages.command("unignore"));
            registerCommand("incuidignorelist","List ignored players",shortAliases("ignorelist"),messages.command("ignorelist"));
            registerCommand("incuidmsgtoggle","Toggle messages",shortAliases("msgtoggle"),messages.command("msgtoggle"));
        }
        getLogger().info("INCUID Chat Phase 5 enabled (moderation, community, interactive chat)");
    }
    public void reloadSettings() {
        reloadConfig();
        settings = ChatSettings.load(getConfig());
        if (moderation != null) moderation.reload();
        if (channels != null) channels.reload();
        if (afk != null) afk.start();
        if (afk != null && !getConfig().getBoolean("afk.enabled",true)) afk.shutdown();
        interactiveConfig = new InteractiveConfig(
                getConfig().getBoolean("interactive.items.enabled",true),
                getConfig().getBoolean("interactive.coordinates.enabled",true),
                bound(getConfig().getInt("interactive.items.max-tags",1),0,3),
                bound(getConfig().getInt("interactive.coordinates.max-tags",1),0,3),
                bound(getConfig().getInt("interactive.items.max-hover-bytes",8192),1024,16384));
        if (getConfig().getInt("health.segments",10)!=10) getLogger().warning("Only 10 health segments are supported; configured value ignored.");
    }
    private static int bound(int val,int min,int max) { return Math.max(min,Math.min(max,val)); }
    public ChatSettings settings() { return settings; }
    private List<String> shortAliases(String name) {
        return getConfig().getBoolean("private-messages.register-short-aliases",false) ? List.of(name) : List.of();
    }
    public HealthStyle healthStyle(UUID id) { return preferences.get(id).style(); }
    public boolean setHealthStyle(UUID id,HealthStyle style) { preferences.style(id,style);return true; }
    public boolean toggleHealth(UUID id) {
        preferences.toggleHealth(id);
        return com.incuid.chat.health.HealthDisplayPreference.resolve(
                preferences.get(id).style(), preferences.get(id).hideHealth(),
                settings.allowHealthToggle(), settings.healthDefaultVisible()).visible();
    }
    public boolean isHealthHidden(UUID id) { return preferences.get(id).hideHealth(); }
    public boolean toggleMentions(UUID id) { return preferences.toggleMentions(id); }
    public PlayerPreferences preferences() { return preferences; }
    public ModerationService moderation() { return moderation; }
    /** Invokes the normal Bukkit/Paper chat pipeline, including mutes and other plugins.
     * The command-generated message is NOT a client-signed chat message. */
    public void sendChannelCommand(Player player,String channel,String message) {
        if (message.isBlank() || message.length()>512 || message.startsWith("/")) { player.sendMessage("Invalid channel message.");return; }
        PendingChannel pending = new PendingChannel(channel,message);
        if (nextChannelMessage.putIfAbsent(player.getUniqueId(),pending)!=null) {
            player.sendMessage("Previous channel message is still being processed.");return;
        }
        // The chat event may run asynchronously after player.chat() returns. Retain a short-lived
        // exact-message override and discard it if no matching event arrives.
        Bukkit.getScheduler().runTaskLater(this,() -> nextChannelMessage.remove(player.getUniqueId(),pending),40L);
        try { player.chat(message); }
        catch (RuntimeException ex) { nextChannelMessage.remove(player.getUniqueId(),pending);throw ex; }
    }
    public ModerationCommands moderationCommands() { return moderationCommands; }

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onChat(AsyncChatEvent event) {
        ChatSettings cfg = settings;
        if (!cfg.chatEnabled()) return;
        if (Bukkit.isPrimaryThread()) afk.onChat(event.getPlayer());
        else Bukkit.getScheduler().runTask(this, () -> afk.onChat(event.getPlayer()));
        final UUID sourceId = event.getPlayer().getUniqueId();
        String originalText=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.message());
        PendingChannel pending=nextChannelMessage.get(sourceId);
        String forced=null;
        if (pending!=null && pending.text().equals(originalText) && nextChannelMessage.remove(sourceId,pending)) {
            forced=pending.channel();
        }
        final String requestedChannel=forced;
        String plainMessage = originalText.toLowerCase(java.util.Locale.ROOT);
        boolean wantsItem = plainMessage.contains("[item]");
        boolean wantsCoords = plainMessage.contains("[coords]");
        // Bukkit entity state is captured on the server thread. For async chat, wait on the chat worker only.
        // A synchronous chat event must capture inline to avoid deadlocking the server thread.
        final Capture capture;
        try {
            if (!event.isAsynchronous() || Bukkit.isPrimaryThread()) {
                capture = capture(event.getPlayer(),cfg,wantsItem,wantsCoords,requestedChannel);
            } else {
                capture = Bukkit.getScheduler().callSyncMethod(this, () -> capture(event.getPlayer(),cfg,wantsItem,wantsCoords,requestedChannel))
                        .get(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            getLogger().warning("Chat snapshot interrupted; leaving vanilla chat unchanged.");
            return;
        } catch (ExecutionException | TimeoutException | IllegalStateException ex) {
            getLogger().warning("Unable to capture chat safely; leaving original renderer unchanged: " + ex.getMessage());
            return;
        }
        // Channel routing intersects the existing event audience. Never expand recipients removed
        // by another plugin or Paper; this also preserves vanish and privacy restrictions.
        int before=event.viewers().size();
        if (!"global".equals(capture.channel().channel())) {
            event.viewers().removeIf(viewer -> !capture.channel().accepts(viewer));
        }
        int after=event.viewers().size();
        var output = ChatPipeline.render(event.message(),capture.tags(),capture.online(),capture.everyoneAllowed(),cfg,
                interactiveConfig.maxItems(),interactiveConfig.maxCoords());
        // Set only renderer, not event.message(): signed content is preserved.
        // Per-recipient rendering: a viewer's settings never change what anyone else sees.
        // UUID-only preference lookups are thread-safe; no world/entity state is queried here.
        event.renderer((source, sourceName, msg, viewer) -> {
            PlayerPreferences.State viewerSettings = viewer instanceof Player recipient
                    ? preferences.get(recipient.getUniqueId()) : PlayerPreferences.State.defaults();
            return ChatFormatter.format(capture.player(), output.component(), cfg, viewerSettings,
                    channels.label(capture.channel().channel()),capture.afk());
        });
        if (channels.settings().diagnostics() || (cfg.mentionEnabled() && (!output.mentioned().isEmpty() || output.everyone()))) {
            pendingDelivery.put(event,new PendingDelivery(sourceId,output.mentioned(),output.everyone(),cfg,
                    capture.channel().channel(),before,after));
        }
    }

    /** Inspect the final audience after normal channel routing and moderation have executed.
     * The event is removed from the temporary map even if a later plugin cancelled it. */
    @EventHandler(priority=EventPriority.MONITOR)
    public void onChatFinished(AsyncChatEvent event) {
        PendingDelivery pending=pendingDelivery.remove(event);
        if (pending==null) return;
        if (channels.settings().diagnostics()) {
            getLogger().info("Chat recipient diagnostics channel="+pending.channel()
                    +" initially="+pending.initialRecipients()+" after-incuid="+pending.afterIncuid()
                    +" at-monitor="+event.viewers().size()+" cancelled="+event.isCancelled());
        }
        if (event.isCancelled()) return;
        Set<UUID> allowed=new HashSet<>();
        for (Audience viewer:event.viewers()) if (viewer instanceof Player p) allowed.add(p.getUniqueId());
        Runnable notify=() -> {
            if (!Bukkit.getPluginManager().isPluginEnabled(this)) return;
            long now=System.currentTimeMillis();
            Set<UUID> targets=new HashSet<>(pending.mentions());
            ChatSettings cfg=pending.settings();
            if (pending.everyone() && cfg.everyoneEnabled()
                    && allow(everyoneCooldown,pending.sender(),now,cfg.everyoneCooldown())) targets.addAll(allowed);
            targets.retainAll(allowed);
            for(UUID id:targets) {
                if (id.equals(pending.sender()) || preferences.get(id).silentMentions()
                        || !allow(mentionCooldown,id,now,cfg.mentionCooldown())) continue;
                Player p=Bukkit.getPlayer(id);
                if(p==null || !p.isOnline()) continue;
                try { p.playSound(p.getLocation(),Sound.valueOf(cfg.mentionSound()),cfg.mentionVolume(),cfg.mentionPitch()); }
                catch (IllegalArgumentException ex) { getLogger().warning("Invalid mention sound: " + cfg.mentionSound()); }
            }
        };
        if(Bukkit.isPrimaryThread()) notify.run(); else Bukkit.getScheduler().runTask(this,notify);
    }
    private boolean allow(ConcurrentMap<UUID,Long> history,UUID id,long now,int seconds) {
        final long interval = seconds * 1000L;
        final boolean[] allowed = {false};
        history.compute(id,(key,previous)-> {
            if (previous == null || now - previous >= interval) { allowed[0] = true; return now; }
            return previous;
        });
        return allowed[0];
    }
    private Capture capture(Player player,ChatSettings cfg, boolean wantsItem,boolean wantsCoords,String forced) {
        PlayerSnapshot snapshot = PlayerSnapshot.capture(player,cfg.healthyAt(),cfg.injuredAt());
        List<MentionService.Target> online = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) online.add(new MentionService.Target(p.getUniqueId(),p.getName()));
        InteractiveConfig options = interactiveConfig;
        InteractiveTags.Snapshot tags=InteractiveTags.capture(player,options.items() && wantsItem,options.coords() && wantsCoords,options.maxBytes());
        return new Capture(snapshot,List.copyOf(online),player.hasPermission("incuidchat.mentions.everyone"),tags,
                channels.capture(player,forced),afk.isAfk(player.getUniqueId()));
    }
    private record Capture(PlayerSnapshot player,List<MentionService.Target> online,boolean everyoneAllowed,
                           InteractiveTags.Snapshot tags,ChannelService.Snapshot channel,boolean afk) { }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID id=event.getPlayer().getUniqueId();
        mentionCooldown.remove(id);
        everyoneCooldown.remove(id);
        messages.quit(id);
        nextChannelMessage.remove(id);
        if (moderation!=null) moderation.forget(id);
    }
    @Override public void onDisable() {
        mentionCooldown.clear(); everyoneCooldown.clear(); nextChannelMessage.clear();pendingDelivery.clear();
        if (afk != null) afk.shutdown();
        if (messages != null) messages.close();
        if (preferences != null) preferences.close();
        if (moderation != null) moderation.close();
    }
}
