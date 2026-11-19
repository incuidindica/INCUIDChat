package com.incuid.chat.storage;

import com.incuid.chat.health.HealthStyle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;

/** Thread-safe UUID-only preferences. File operations run only on a dedicated background writer. */
public final class PlayerPreferences {
    public record State(HealthStyle style, boolean hideHealth, boolean silentMentions,
                        boolean messagesDisabled, Set<UUID> ignored) {
        public State {
            ignored = Set.copyOf(ignored);
        }
        public static State defaults() { return new State(HealthStyle.BARS,false,false,false,Set.of()); }
    }
    private final ConcurrentMap<UUID, State> states = new ConcurrentHashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "INCUIDChat-preferences"); thread.setDaemon(true); return thread;
    });
    private final JavaPlugin plugin;
    private final File file;
    private final Object writeLock = new Object();
    private volatile boolean closing;

    public PlayerPreferences(JavaPlugin plugin) {
        this.plugin=plugin;
        this.file=new File(plugin.getDataFolder(),"players.yml");
        load(); // Only once during plugin enable; never during chat handling.
    }
    public State get(UUID id) { return states.getOrDefault(id, State.defaults()); }
    private void modify(UUID id, java.util.function.UnaryOperator<State> fn) {
        if (closing) return;
        states.compute(id, (key, old) -> fn.apply(old == null ? State.defaults() : old));
        persist();
    }
    public void style(UUID id, HealthStyle style) {
        modify(id, s -> new State(style,s.hideHealth(),s.silentMentions(),s.messagesDisabled(),s.ignored()));
    }
    public boolean toggleHealth(UUID id) {
        modify(id,s->new State(s.style(),!s.hideHealth(),s.silentMentions(),s.messagesDisabled(),s.ignored()));
        return !get(id).hideHealth();
    }
    public boolean toggleMentions(UUID id) {
        modify(id,s->new State(s.style(),s.hideHealth(),!s.silentMentions(),s.messagesDisabled(),s.ignored()));
        return !get(id).silentMentions();
    }
    public boolean toggleMessages(UUID id) {
        modify(id,s->new State(s.style(),s.hideHealth(),s.silentMentions(),!s.messagesDisabled(),s.ignored()));
        return !get(id).messagesDisabled();
    }
    public boolean ignore(UUID id, UUID target, boolean add) {
        Set<UUID> prior = get(id).ignored();
        if (prior.contains(target)==add) return false;
        modify(id,s->{
            Set<UUID> values = new HashSet<>(s.ignored());
            if (add) values.add(target); else values.remove(target);
            return new State(s.style(),s.hideHealth(),s.silentMentions(),s.messagesDisabled(),values);
        });
        return true;
    }
    private void load() {
        if (!file.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("players");
        if (root != null) for (String key : root.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                ConfigurationSection c = root.getConfigurationSection(key);
                if (c == null) continue;
                Set<UUID> ignored = new HashSet<>();
                for (String value : c.getStringList("ignored")) {
                    try { ignored.add(UUID.fromString(value)); }
                    catch (IllegalArgumentException ex) { plugin.getLogger().warning("Skipping invalid ignored UUID in players.yml"); }
                }
                HealthStyle style = HealthStyle.parse(c.getString("style", "bars"));
                if (style == null) style = HealthStyle.BARS;
                states.put(id,new State(style,c.getBoolean("hide-health"),c.getBoolean("silent-mentions"),
                        c.getBoolean("disable-messages"),ignored));
            } catch (IllegalArgumentException ex) { plugin.getLogger().warning("Skipping invalid player UUID: " + key); }
        }
        // Migrates Phase 1.1 health-styles without overwriting player options.
        ConfigurationSection legacy = yaml.getConfigurationSection("health-styles");
        if (legacy != null) for (String key : legacy.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                HealthStyle style = HealthStyle.parse(legacy.getString(key));
                if (style == HealthStyle.HEART && !states.containsKey(id)) states.compute(id,(k,old)-> {
                    State s = old == null ? State.defaults() : old;
                    return new State(style,s.hideHealth(),s.silentMentions(),s.messagesDisabled(),s.ignored());
                });
            } catch (IllegalArgumentException ex) { plugin.getLogger().warning("Skipping invalid legacy player UUID: " + key); }
        }
    }
    private void persist() {
        Map<UUID,State> snapshot = Map.copyOf(states);
        writer.submit(() -> write(snapshot));
    }
    private void write(Map<UUID,State> snapshot) {
        synchronized (writeLock) {
            YamlConfiguration yaml = new YamlConfiguration();
            for (var entry : snapshot.entrySet()) {
                State s = entry.getValue(); String key = "players." + entry.getKey() + ".";
                yaml.set(key+"style", s.style().settingValue());
                yaml.set(key+"hide-health", s.hideHealth());
                yaml.set(key+"silent-mentions",s.silentMentions());
                yaml.set(key+"disable-messages",s.messagesDisabled());
                yaml.set(key+"ignored",s.ignored().stream().map(UUID::toString).sorted().toList());
            }
            try { yaml.save(file); }
            catch (IOException ex) { plugin.getLogger().log(Level.SEVERE,"Unable to save players.yml",ex); }
        }
    }
    public void close() {
        closing=true;
        Map<UUID, State> snapshot=Map.copyOf(states);
        writer.submit(() -> write(snapshot));
        writer.shutdown();
        try { if (!writer.awaitTermination(10,TimeUnit.SECONDS)) plugin.getLogger().severe("Preference writes timed out on shutdown."); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); plugin.getLogger().severe("Preference writes interrupted on shutdown."); }
    }
}
