package com.incuid.chat.health;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealthStyleTest {
    @Test void commandsAndDefaults() {
        assertEquals(HealthStyle.BARS, HealthStyle.parse("bars"));
        assertEquals(HealthStyle.BARS, HealthStyle.parse("BAR"));
        assertEquals(HealthStyle.HEART, HealthStyle.parse("heart"));
        assertEquals(HealthStyle.HEART, HealthStyle.parse("HEART"));
        assertNull(HealthStyle.parse("hearts"));
        assertNull(HealthStyle.parse(null));
        assertEquals("heart", HealthStyle.HEART.settingValue());
    }
}
