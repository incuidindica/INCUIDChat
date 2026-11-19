package com.incuid.chat.messaging;

import java.util.UUID;

/** Pure validation shared by private-message commands. */
public final class MessageRules {
    private MessageRules() { }
    public static String problem(UUID sender,UUID recipient,String text) {
        if (sender.equals(recipient)) return "You cannot message yourself.";
        if (text == null || text.isBlank()) return "Message cannot be empty.";
        if (text.length()>512) return "Message is too long (maximum 512 characters).";
        return null;
    }
}
