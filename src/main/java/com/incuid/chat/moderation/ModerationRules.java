package com.incuid.chat.moderation;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Pure, bounded moderation rules. No Bukkit objects or file access. */
public final class ModerationRules {
    private ModerationRules() {}
    public static String normalized(String message) {
        return message.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
    public static boolean repeatedRun(String text, int limit) {
        if (limit < 2) return false;
        int run = 0, last = -1;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i); i += Character.charCount(cp);
            run = cp == last ? run + 1 : 1;
            if (run >= limit) return true;
            last = cp;
        }
        return false;
    }
    public static Pattern wordPattern(String word) {
        if (word == null || word.isBlank() || word.length() > 64) throw new IllegalArgumentException("Invalid blocked word");
        return Pattern.compile("(?iu)(?<![\\p{L}\\p{N}_])" + Pattern.quote(word.trim()) + "(?![\\p{L}\\p{N}_])");
    }
    public static String replace(String text, List<Pattern> patterns, String token) {
        String result = text;
        for (Pattern pattern:patterns) result = pattern.matcher(result).replaceAll(java.util.regex.Matcher.quoteReplacement(token));
        return result;
    }
    public static boolean contains(String text, List<Pattern> patterns) {
        for (Pattern pattern:patterns) if (pattern.matcher(text).find()) return true;
        return false;
    }
    public static long durationMillis(String raw) {
        if (raw == null || raw.isBlank()) return -1;
        String value=raw.toLowerCase(Locale.ROOT);
        if (!value.matches("[1-9][0-9]{0,5}[smhd]")) return -1;
        long count=Long.parseLong(value.substring(0,value.length()-1));
        long multiplier = switch(value.charAt(value.length()-1)) {case 's' -> 1000L;case 'm' -> 60000L;case 'h' -> 3600000L;default -> 86400000L;};
        return count * multiplier;
    }
}
