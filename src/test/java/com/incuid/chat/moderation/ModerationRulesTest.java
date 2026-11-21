package com.incuid.chat.moderation;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class ModerationRulesTest {
    @Test void normalizesRepeatedWhitespace() {
        assertEquals("hello world",ModerationRules.normalized("  HELLO  \t WORLD  "));
    }
    @Test void detectsExcessiveCharacters() {
        assertTrue(ModerationRules.repeatedRun("heyyyyyyyyyyyy",12));
        assertFalse(ModerationRules.repeatedRun("hello everyone",12));
        assertTrue(ModerationRules.repeatedRun("🔥🔥🔥",3));
    }
    @Test void matchesWholeWordsOnly() {
        List<Pattern> rules=List.of(ModerationRules.wordPattern("bad"));
        assertTrue(ModerationRules.contains("BAD word",rules));
        assertFalse(ModerationRules.contains("badminton",rules));
        assertEquals("That is ***.",ModerationRules.replace("That is bad.",rules,"***"));
    }
    @Test void quotesRegexMetacharacters() {
        var rules=List.of(ModerationRules.wordPattern("a+b"));
        assertTrue(ModerationRules.contains("a+b",rules));
        assertFalse(ModerationRules.contains("aaab",rules));
        assertThrows(IllegalArgumentException.class,()->ModerationRules.wordPattern(" "));
    }
    @Test void parsesMuteDuration() {
        assertEquals(30000,ModerationRules.durationMillis("30s"));
        assertEquals(1800000,ModerationRules.durationMillis("30m"));
        assertEquals(7200000,ModerationRules.durationMillis("2h"));
        assertEquals(86400000,ModerationRules.durationMillis("1d"));
        assertEquals(-1,ModerationRules.durationMillis("0m"));
        assertEquals(-1,ModerationRules.durationMillis("for-ever"));
    }
}
