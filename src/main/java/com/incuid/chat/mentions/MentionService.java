package com.incuid.chat.mentions;

import com.incuid.chat.config.ChatSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.util.*;
import java.util.regex.Pattern;

/** Pure tokenization. Match longest player names first; don't match @Name inside other words. */
public final class MentionService {
    private MentionService() { }
    public record Output(Component component, Set<UUID> mentioned, boolean everyone) { }
    public record Target(UUID uuid, String name) { }
    public static Output highlight(Component original, List<Target> online, boolean permitEveryone, ChatSettings cfg) {
        String source = PlainTextComponentSerializer.plainText().serialize(original);
        Map<String, Target> byName = new HashMap<>();
        for (Target player : online) byName.put(player.name().toLowerCase(Locale.ROOT), player);
        List<String> names = new ArrayList<>(byName.keySet());
        names.sort(Comparator.comparingInt(String::length).reversed());
        String alternatives = String.join("|", names.stream().map(Pattern::quote).toList());
        String regex = "(?<![A-Za-z0-9_])@(" + (permitEveryone ? "everyone" + (alternatives.isEmpty()? "" : "|") : "")
                + alternatives + ")(?![A-Za-z0-9_])";
        if (alternatives.isEmpty() && !permitEveryone) return new Output(original, Set.of(),false);
        var matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(source);
        Component highlighted = Component.empty();
        Set<UUID> recipients = new HashSet<>();
        boolean all = false;
        int last = 0;
        while (matcher.find()) {
            highlighted = highlighted.append(Component.text(source.substring(last,matcher.start())));
            String found = matcher.group(1);
            if (found.equalsIgnoreCase("everyone") && permitEveryone) all = true;
            else {
                Target target = byName.get(found.toLowerCase(Locale.ROOT));
                if (target != null) recipients.add(target.uuid());
            }
            highlighted = highlighted.append(Component.text(matcher.group(),TextColor.fromHexString(cfg.mentionColor())));
            last=matcher.end();
        }
        if (last==0) return new Output(original,Set.of(),false);
        return new Output(highlighted.append(Component.text(source.substring(last))),Set.copyOf(recipients),all);
    }
}
