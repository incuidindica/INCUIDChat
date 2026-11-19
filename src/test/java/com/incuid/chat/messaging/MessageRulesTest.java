package com.incuid.chat.messaging;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MessageRulesTest {
    private static final UUID A=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID B=UUID.fromString("00000000-0000-0000-0000-000000000002");
    @Test void rejectsSelf() { assertEquals("You cannot message yourself.",MessageRules.problem(A,A,"Hello")); }
    @Test void rejectsBlank() { assertNotNull(MessageRules.problem(A,B," \t")); }
    @Test void limitsLength() {
        assertNull(MessageRules.problem(A,B,"x".repeat(512)));
        assertNotNull(MessageRules.problem(A,B,"x".repeat(513)));
    }
    @Test void acceptsNormal() { assertNull(MessageRules.problem(A,B,"Hello!")); }
}
