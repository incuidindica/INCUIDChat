package com.incuid.chat.moderation;

import com.incuid.chat.IncuidChatPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/** Thread-safe chat gating, lazy-expiring mutes and async serialized file writes. */
public final class ModerationService implements Listener {
    private record Mute(long until, String reason, String moderator) { }
    private record Recent(long last, String normalized, long windowStart, int inWindow) { }
    private record Config(boolean enabled, boolean cooldownEnabled, long cooldownMs, boolean duplicates,
                          long duplicateMs, boolean repeats, int repeatLimit, boolean flood, long floodWindowMs,
                          int floodMaximum, boolean contentEnabled, String action, String replacement,
                          List<Pattern> patterns, boolean muteEnabled, boolean notifyStaff, int maxLength) { }
    private final IncuidChatPlugin plugin;
    private final ConcurrentMap<UUID,Mute> mutes = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID,Recent> activity = new ConcurrentHashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {Thread t=new Thread(r,"INCUIDChat-moderation-io"); t.setDaemon(true); return t;});
    private final File muteFile, logFile;
    private volatile Config config;
    public ModerationService(IncuidChatPlugin plugin) {
        this.plugin=plugin;
        muteFile=new File(plugin.getDataFolder(),"mutes.yml");
        logFile=new File(plugin.getDataFolder(),"moderation.log");
        load(); reload();
    }
    public void reload() {
        var c=plugin.getConfig();
        File filtersFile=new File(plugin.getDataFolder(),"filters.yml");
        var filters=YamlConfiguration.loadConfiguration(filtersFile);
        List<Pattern> patterns=new ArrayList<>();
        for (String word:filters.getStringList("blocked-words")) {
            try { if (patterns.size() >= 200) break; patterns.add(ModerationRules.wordPattern(word)); }
            catch (IllegalArgumentException ex) { plugin.getLogger().warning("Ignoring invalid filter entry."); }
        }
        String action=c.getString("moderation.filter.action","cancel").toLowerCase(Locale.ROOT);
        if (!Set.of("cancel","replace","notify").contains(action)) action="cancel";
        String replacement=c.getString("moderation.filter.replacement","***");
        if (replacement==null || replacement.isBlank() || replacement.length()>32) replacement="***";
        config=new Config(c.getBoolean("moderation.enabled",true),c.getBoolean("moderation.spam.cooldown.enabled",true),
           bounded(c.getLong("moderation.spam.cooldown.milliseconds",1100),0,60000),
           c.getBoolean("moderation.spam.duplicate.enabled",true),bounded(c.getLong("moderation.spam.duplicate.window-seconds",15),1,300)*1000,
           c.getBoolean("moderation.spam.repeated-characters.enabled",true), (int)bounded(c.getInt("moderation.spam.repeated-characters.limit",12),3,100),
           c.getBoolean("moderation.spam.flood.enabled",true),bounded(c.getLong("moderation.spam.flood.window-seconds",10),1,300)*1000,
           (int)bounded(c.getInt("moderation.spam.flood.max-messages",6),2,100),c.getBoolean("moderation.filter.enabled",false),
           action,replacement,List.copyOf(patterns),c.getBoolean("moderation.mutes.enabled",true),
           c.getBoolean("moderation.filter.notify-staff",true),(int)bounded(c.getInt("moderation.spam.max-length",512),32,10000));
    }
    private static long bounded(long n,long min,long max) { return Math.max(min,Math.min(max,n)); }
    private void load() {
        if (!muteFile.isFile()) return;
        YamlConfiguration y=YamlConfiguration.loadConfiguration(muteFile);
        var section=y.getConfigurationSection("mutes");
        if (section == null) return;
        long now=System.currentTimeMillis();
        for (String key:section.getKeys(false)) try {
            UUID id=UUID.fromString(key);
            long until=y.getLong("mutes."+key+".until",0);
            if (until==0 || until>now) mutes.put(id,new Mute(until,y.getString("mutes."+key+".reason","No reason"),y.getString("mutes."+key+".moderator","Console")));
        } catch (IllegalArgumentException ignored) { plugin.getLogger().warning("Skipping invalid mute UUID: "+key); }
    }
    private void saveAsync() {
        // Each write captures its own immutable values; serial executor preserves command order.
        Map<UUID,Mute> copy=Map.copyOf(mutes);
        io.execute(() -> {
            YamlConfiguration y=new YamlConfiguration();
            copy.forEach((id,m)-> {String path="mutes."+id; y.set(path+".until",m.until());y.set(path+".reason",m.reason());y.set(path+".moderator",m.moderator());});
            try {
                Files.createDirectories(muteFile.toPath().getParent());
                File tmp=new File(muteFile.getParentFile(),"mutes.yml.tmp");
                y.save(tmp);
                try {Files.move(tmp.toPath(),muteFile.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
                catch (java.nio.file.AtomicMoveNotSupportedException ex) {Files.move(tmp.toPath(),muteFile.toPath(),StandardCopyOption.REPLACE_EXISTING);}
            } catch (IOException ex) {plugin.getLogger().warning("Unable to save mutes.yml: "+ex.getMessage());}
        });
    }
    private void log(String entry) {
        String sanitized=entry.replace('\n',' ').replace('\r',' ');
        io.execute(() -> {try {
            Files.writeString(logFile.toPath(),java.time.Instant.now()+" "+sanitized+System.lineSeparator(),
                    java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND);
        } catch(IOException ex) {plugin.getLogger().warning("Unable to write moderation log: "+ex.getMessage());}});
    }
    public String muteReason(UUID id) {
        Config c=config;
        if (c==null || !c.enabled() || !c.muteEnabled()) return null;
        Mute m=mutes.get(id);
        if (m==null) return null;
        if (m.until()!=0 && m.until()<=System.currentTimeMillis()) {
            if (mutes.remove(id,m)) saveAsync();
            return null;
        }
        return m.reason();
    }
    public void mute(UUID id,String name,long durationMs,String reason,String moderator) {
        long until=durationMs==0?0:System.currentTimeMillis()+durationMs;
        mutes.put(id,new Mute(until,reason,moderator));saveAsync();
        log("MUTE target="+id+" name="+name+" actor="+moderator+" durationMs="+durationMs+" reason="+reason);
    }
    public boolean unmute(UUID id,String name,String moderator) {
        boolean changed=mutes.remove(id)!=null;
        if(changed) {saveAsync();log("UNMUTE target="+id+" name="+name+" actor="+moderator);}
        return changed;
    }
    public void logClear(String moderator) { log("CLEAR actor="+moderator); }
    public void forget(UUID id) { activity.remove(id); }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void onChat(AsyncChatEvent event) {
        Config cfg=config;
        if (!cfg.enabled()) return;
        Player player=event.getPlayer(); UUID id=player.getUniqueId();
        boolean bypass=hasBypass(player,event.isAsynchronous());
        String muted=muteReason(id);
        if (muted!=null && !bypass) {
            reject(event,"You are muted: "+muted);return;
        }
        String message=PlainTextComponentSerializer.plainText().serialize(event.message());
        if (message.length()>cfg.maxLength()) { reject(event,"Message is too long.");return; }
        if (!bypass) {
            String norm=ModerationRules.normalized(message);
            long now=System.currentTimeMillis();
            String[] reason={null};
            activity.compute(id,(key,previous)-> {
                long windowStart=previous==null || now-previous.windowStart()>cfg.floodWindowMs()?now:previous.windowStart();
                int count=previous==null || windowStart==now?1:previous.inWindow()+1;
                if (previous!=null && cfg.cooldownEnabled() && now-previous.last()<cfg.cooldownMs()) reason[0]="Please slow down.";
                else if (previous!=null && cfg.duplicates() && norm.equals(previous.normalized()) && now-previous.last()<=cfg.duplicateMs()) reason[0]="Please don't repeat the same message.";
                else if (cfg.repeats() && ModerationRules.repeatedRun(message,cfg.repeatLimit())) reason[0]="Please avoid excessive repeated characters.";
                else if (cfg.flood() && count>cfg.floodMaximum()) reason[0]="You're sending too many messages.";
                return new Recent(now,norm,windowStart,count);
            });
            if(reason[0]!=null) {reject(event,reason[0]);return;}
        }
        if (cfg.contentEnabled() && ModerationRules.contains(message,cfg.patterns())) {
            switch(cfg.action()) {
                case "cancel" -> {reject(event,"Your message contains a blocked word.");return;}
                case "replace" -> {
                    // Explicit message replacement can invalidate signed-chat verification on clients.
                    event.message(Component.text(ModerationRules.replace(message,cfg.patterns(),cfg.replacement())));
                }
                case "notify" -> { /* Leave the author's message untouched. */ }
                default -> { }
            }
            if (cfg.notifyStaff()) {
                String excerpt=message.length()>120?message.substring(0,120)+"…":message;
                notifyStaff("[INCUID Moderation] Filter match by "+player.getName()+": "+excerpt);
            }
        }
    }
    private boolean hasBypass(Player player,boolean async) {
        if (!async || Bukkit.isPrimaryThread()) return player.hasPermission("incuidchat.moderation.bypass");
        try {return Bukkit.getScheduler().callSyncMethod(plugin,()->player.hasPermission("incuidchat.moderation.bypass"))
                    .get(2,TimeUnit.SECONDS);
        } catch (InterruptedException ex) {Thread.currentThread().interrupt();return false;}
          catch (ExecutionException | TimeoutException | IllegalStateException ex) {
            plugin.getLogger().warning("Unable to resolve moderation bypass: "+ex.getMessage()); return false;
        }
    }
    private void reject(AsyncChatEvent e,String reason) {
        e.setCancelled(true);
        notifyPlayer(e.getPlayer().getUniqueId(),reason);
    }
    private void notifyPlayer(UUID id,String message) {
        Runnable r=()-> {Player p=Bukkit.getPlayer(id);if(p!=null)p.sendMessage(Component.text("[INCUID Chat] "+message));};
        if(Bukkit.isPrimaryThread())r.run();else Bukkit.getScheduler().runTask(plugin,r);
    }
    private void notifyStaff(String message) {
        Runnable r=()-> {for (Player p:Bukkit.getOnlinePlayers()) if(p.hasPermission("incuidchat.moderation.notify")) p.sendMessage(Component.text(message));};
        if(Bukkit.isPrimaryThread())r.run();else Bukkit.getScheduler().runTask(plugin,r);
    }
    public void close() {
        activity.clear();
        saveAsync();
        io.shutdown();
        try {if (!io.awaitTermination(3,TimeUnit.SECONDS)) plugin.getLogger().warning("Moderation writes still pending at shutdown.");}
        catch(InterruptedException ex) {Thread.currentThread().interrupt();}
    }
}
