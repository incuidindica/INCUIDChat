package com.incuid.chat.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;

/** Add only missing YAML configuration keys. Existing lines, comments and values remain unchanged.
 * A validated atomic replacement prevents a partial config write on upgrade. */
public final class ConfigMigrator {
    private ConfigMigrator() { }
    public static void migrate(JavaPlugin plugin) {
        Path target=new File(plugin.getDataFolder(),"config.yml").toPath();
        if (!Files.isRegularFile(target)) return;
        try (InputStream in=plugin.getResource("config.yml")) {
            if (in==null) return;
            String original=Files.readString(target,StandardCharsets.UTF_8);
            String stock=new String(in.readAllBytes(),StandardCharsets.UTF_8);
            YamlConfiguration saved=YamlConfiguration.loadConfiguration(new StringReader(original));
            YamlConfiguration defaults=YamlConfiguration.loadConfiguration(new StringReader(stock));
            String result=original;
            for (String root:defaults.getKeys(false)) {
                if (!saved.contains(root)) {
                    String block=sectionText(stock,root,0);
                    if (!block.isBlank()) result += "\n" + block + "\n";
                } else if (defaults.isConfigurationSection(root)) {
                    result=mergeSection(saved,defaults,stock,result,root,root,0);
                }
            }
            if (result.equals(original)) return;
            // Validate before replacing an administrator's configuration.
            YamlConfiguration validate=new YamlConfiguration();
            validate.loadFromString(result);
            Path tmp=target.resolveSibling("config.yml.migration.tmp");
            Files.writeString(tmp,result,StandardCharsets.UTF_8);
            try { Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException e) { Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING); }
            plugin.getLogger().info("Added missing INCUID Chat configuration settings without overwriting existing values.");
        } catch(Exception ex) { plugin.getLogger().warning("Config migration skipped: "+ex.getMessage()); }
    }
    private static String mergeSection(YamlConfiguration saved,YamlConfiguration defaults,String stock,
                                       String result,String path,String root,int depth) {
        var section=defaults.getConfigurationSection(path);
        if (section==null) return result;
        for (String key:section.getKeys(false)) {
            String child=path+"."+key;
            if (!saved.contains(child)) {
                String block=sectionText(stock,root,0);
                String[] parts=child.split("\\.");
                for (int i=1;i<parts.length;i++) block=sectionText(block,parts[i],i*2);
                if (!block.isBlank()) result=insertUnder(result,path,block);
                saved.set(child,defaults.get(child));
            } else if (defaults.isConfigurationSection(child)) {
                result=mergeSection(saved,defaults,stock,result,child,root,depth+1);
            }
        }
        return result;
    }
    private static String sectionText(String text,String name,int indentation) {
        String[] lines=text.split("\n",-1);
        String mark=" ".repeat(indentation)+name+":";
        StringBuilder block=new StringBuilder();boolean found=false;
        for (String line:lines) {
            if (!found) {
                if (line.equals(mark) || line.startsWith(mark+" ")) { found=true;block.append(line).append('\n'); }
            } else {
                if (!line.isBlank() && !line.stripLeading().startsWith("#") && leading(line)<=indentation) break;
                block.append(line).append('\n');
            }
        }
        return block.toString().stripTrailing();
    }
    private static int leading(String s) { int n=0;while(n<s.length() && s.charAt(n)==' ')n++;return n; }
    private static String insertUnder(String original,String root,String child) {
        int depth=root.split("\\.").length-1;
        String mark="  ".repeat(depth)+root.substring(root.lastIndexOf('.')+1)+":";
        String[] lines=original.split("\n",-1);
        StringBuilder out=new StringBuilder();boolean placed=false;
        for (String line:lines) {
            out.append(line).append('\n');
            if (!placed && (line.equals(mark) || line.startsWith(mark+" "))) {
                out.append(child).append('\n'); placed=true;
            }
        }
        return placed ? out.toString().stripTrailing()+"\n" : original;
    }
}
