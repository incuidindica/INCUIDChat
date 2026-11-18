package com.incuid.chat;

import com.incuid.chat.chat.ChatFormatter;
import com.incuid.chat.chat.PlayerSnapshot;
import com.incuid.chat.commands.IncuidCommand;
import com.incuid.chat.config.ChatSettings;
import com.incuid.chat.mentions.MentionService;
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
    private final Set<UUID> hiddenHealth = ConcurrentHashMap.newKeySet();
    private final Set<UUID> silentMentions = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<UUID,Long> mentionCooldown = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID,Long> everyoneCooldown = new ConcurrentHashMap<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        for (String path : List.of("messages.yml","formats.yml","filters.yml")) saveResource(path,false);
        reloadSettings();
        Bukkit.getPluginManager().registerEvents(this,this);
        registerCommand("incuidchat", "INCUID Chat management", List.of("ic"), new IncuidCommand(this));
        getLogger().info("INCUID Chat Phase 1 enabled (Paper 26.3, ten-segment health bars)");
    }
    public void reloadSettings() {
        reloadConfig();
        settings = ChatSettings.load(getConfig());
        if (getConfig().getInt("health.segments",10)!=10) getLogger().warning("Only 10 health segments are supported; configured value ignored.");
    }
    public ChatSettings settings() { return settings; }
    public boolean toggleHealth(UUID id) { if (!hiddenHealth.add(id)) { hiddenHealth.remove(id); return true; } return false; }
    public boolean isHealthHidden(UUID id) { return hiddenHealth.contains(id); }
    public boolean toggleMentions(UUID id) { if (!silentMentions.add(id)) { silentMentions.remove(id); return true; } return false; }

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onChat(AsyncChatEvent event) {
        ChatSettings cfg = settings;
        if (!cfg.chatEnabled()) return;
        final UUID sourceId = event.getPlayer().getUniqueId();
        // Bukkit entity state is captured on the server thread. For async chat, wait on the chat worker only.
        // A synchronous chat event must capture inline to avoid deadlocking the server thread.
        final Capture capture;
        try {
            if (!event.isAsynchronous() || Bukkit.isPrimaryThread()) {
                capture = capture(event.getPlayer(),cfg);
            } else {
                capture = Bukkit.getScheduler().callSyncMethod(this, () -> capture(event.getPlayer(),cfg))
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
        var output = cfg.mentionEnabled()
                ? MentionService.highlight(event.message(),capture.online(),capture.everyoneAllowed(),cfg)
                : new MentionService.Output(event.message(),Set.of(),false);
        // Set only renderer, not event.message(): signed content is preserved.
        event.renderer((source, sourceName, msg, viewer) -> ChatFormatter.format(capture.player(),output.component(),cfg));
        if (cfg.mentionEnabled() && (!output.mentioned().isEmpty() || output.everyone())) {
            Runnable notify = () -> {
                if (!Bukkit.getPluginManager().isPluginEnabled(this)) return;
                long now = System.currentTimeMillis();
                Set<UUID> ids = new HashSet<>(output.mentioned());
                if (output.everyone() && cfg.everyoneEnabled() && allow(everyoneCooldown,sourceId,now,cfg.everyoneCooldown())) {
                    for (Player online : Bukkit.getOnlinePlayers()) ids.add(online.getUniqueId());
                }
                for (UUID id : ids) {
                    if (id.equals(sourceId) || silentMentions.contains(id) || !allow(mentionCooldown,id,now,cfg.mentionCooldown())) continue;
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
    private Capture capture(Player player,ChatSettings cfg) {
        PlayerSnapshot snapshot = PlayerSnapshot.capture(player,cfg.healthyAt(),cfg.injuredAt(),
                (cfg.allowHealthToggle() && hiddenHealth.contains(player.getUniqueId()))
                || (!cfg.healthDefaultVisible() && !hiddenHealth.contains(player.getUniqueId())));
        List<MentionService.Target> online = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) online.add(new MentionService.Target(p.getUniqueId(),p.getName()));
        return new Capture(snapshot,List.copyOf(online),player.hasPermission("incuidchat.mentions.everyone"));
    }
    private record Capture(PlayerSnapshot player,List<MentionService.Target> online,boolean everyoneAllowed) { }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID id=event.getPlayer().getUniqueId();
        mentionCooldown.remove(id);
        everyoneCooldown.remove(id);
    }
    @Override public void onDisable() { mentionCooldown.clear(); everyoneCooldown.clear(); }
}
