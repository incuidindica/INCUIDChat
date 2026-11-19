package com.incuid.chat;

import com.incuid.chat.chat.ChatFormatter;
import com.incuid.chat.chat.PlayerSnapshot;
import com.incuid.chat.commands.IncuidCommand;
import com.incuid.chat.config.ChatSettings;
import com.incuid.chat.mentions.MentionService;
import com.incuid.chat.health.HealthStyle;
import com.incuid.chat.storage.PlayerPreferences;
import com.incuid.chat.interactive.InteractiveTags;
import com.incuid.chat.interactive.ChatPipeline;
import com.incuid.chat.messaging.MessageService;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
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
    private final ConcurrentMap<UUID,Long> mentionCooldown = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID,Long> everyoneCooldown = new ConcurrentHashMap<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        for (String path : List.of("messages.yml","formats.yml","filters.yml")) {
            if (!new java.io.File(getDataFolder(),path).isFile()) saveResource(path,false);
        }
        reloadSettings();
        preferences = new PlayerPreferences(this);
        messages = new MessageService(this,preferences);
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
        getLogger().info("INCUID Chat Phase 2 viewer-local health enabled (Paper 26.3, interactive tags and private messages)");
    }
    public void reloadSettings() {
        reloadConfig();
        settings = ChatSettings.load(getConfig());
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

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onChat(AsyncChatEvent event) {
        ChatSettings cfg = settings;
        if (!cfg.chatEnabled()) return;
        final UUID sourceId = event.getPlayer().getUniqueId();
        String plainMessage = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.message()).toLowerCase(java.util.Locale.ROOT);
        boolean wantsItem = plainMessage.contains("[item]");
        boolean wantsCoords = plainMessage.contains("[coords]");
        // Bukkit entity state is captured on the server thread. For async chat, wait on the chat worker only.
        // A synchronous chat event must capture inline to avoid deadlocking the server thread.
        final Capture capture;
        try {
            if (!event.isAsynchronous() || Bukkit.isPrimaryThread()) {
                capture = capture(event.getPlayer(),cfg,wantsItem,wantsCoords);
            } else {
                capture = Bukkit.getScheduler().callSyncMethod(this, () -> capture(event.getPlayer(),cfg,wantsItem,wantsCoords))
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
        var output = ChatPipeline.render(event.message(),capture.tags(),capture.online(),capture.everyoneAllowed(),cfg,
                interactiveConfig.maxItems(),interactiveConfig.maxCoords());
        // Set only renderer, not event.message(): signed content is preserved.
        // Per-recipient rendering: a viewer's settings never change what anyone else sees.
        // UUID-only preference lookups are thread-safe; no world/entity state is queried here.
        event.renderer((source, sourceName, msg, viewer) -> {
            PlayerPreferences.State viewerSettings = viewer instanceof Player recipient
                    ? preferences.get(recipient.getUniqueId()) : PlayerPreferences.State.defaults();
            return ChatFormatter.format(capture.player(), output.component(), cfg, viewerSettings);
        });
        if (cfg.mentionEnabled() && (!output.mentioned().isEmpty() || output.everyone())) {
            Runnable notify = () -> {
                if (!Bukkit.getPluginManager().isPluginEnabled(this)) return;
                long now = System.currentTimeMillis();
                Set<UUID> ids = new HashSet<>(output.mentioned());
                if (output.everyone() && cfg.everyoneEnabled() && allow(everyoneCooldown,sourceId,now,cfg.everyoneCooldown())) {
                    for (Player online : Bukkit.getOnlinePlayers()) ids.add(online.getUniqueId());
                }
                for (UUID id : ids) {
                    if (id.equals(sourceId) || preferences.get(id).silentMentions() || !allow(mentionCooldown,id,now,cfg.mentionCooldown())) continue;
                    Player target = Bukkit.getPlayer(id);
                    if (target != null && target.isOnline()) {
                        try { target.playSound(target.getLocation(),Sound.valueOf(cfg.mentionSound()),cfg.mentionVolume(),cfg.mentionPitch()); }
                        catch (IllegalArgumentException ex) { getLogger().warning("Invalid mentions.sound in config.yml: " + cfg.mentionSound()); }
                    }
                }
            };
            if (Bukkit.isPrimaryThread()) notify.run(); else Bukkit.getScheduler().runTask(this,notify);
        }
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
    private Capture capture(Player player,ChatSettings cfg, boolean wantsItem,boolean wantsCoords) {
        PlayerSnapshot snapshot = PlayerSnapshot.capture(player,cfg.healthyAt(),cfg.injuredAt());
        List<MentionService.Target> online = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) online.add(new MentionService.Target(p.getUniqueId(),p.getName()));
        InteractiveConfig options = interactiveConfig;
        InteractiveTags.Snapshot tags=InteractiveTags.capture(player,options.items() && wantsItem,options.coords() && wantsCoords,options.maxBytes());
        return new Capture(snapshot,List.copyOf(online),player.hasPermission("incuidchat.mentions.everyone"),tags);
    }
    private record Capture(PlayerSnapshot player,List<MentionService.Target> online,boolean everyoneAllowed,
                           InteractiveTags.Snapshot tags) { }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID id=event.getPlayer().getUniqueId();
        mentionCooldown.remove(id);
        everyoneCooldown.remove(id);
        messages.quit(id);
    }
    @Override public void onDisable() {
        mentionCooldown.clear(); everyoneCooldown.clear();
        if (messages != null) messages.close();
        if (preferences != null) preferences.close();
    }
}
