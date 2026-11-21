package com.incuid.chat.community;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AfkRulesTest {
    @Test void manualAfkClearsByDefault() {
        assertTrue(AfkRules.shouldClearOnActivity(false,true,true));
    }
    @Test void automaticAfkClearsByDefault() {
        assertTrue(AfkRules.shouldClearOnActivity(true,true,true));
    }
    @Test void preferencesApplyToTheirOwnMode() {
        assertFalse(AfkRules.shouldClearOnActivity(false,true,false));
        assertFalse(AfkRules.shouldClearOnActivity(true,false,true));
        assertTrue(AfkRules.shouldClearOnActivity(false,false,true));
        assertTrue(AfkRules.shouldClearOnActivity(true,true,false));
    }
}
