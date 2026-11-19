package com.incuid.chat.health;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealthDisplayPreferenceTest {
    @Test void defaultsAreBarsAndVisible() {
        assertEquals(new HealthDisplayPreference(HealthStyle.BARS,true),
                HealthDisplayPreference.resolve(HealthStyle.BARS,false,true,true));
    }
    @Test void twoViewersCanSeeDifferentStylesOfSameSender() {
        var first = HealthDisplayPreference.resolve(HealthStyle.HEART,false,true,true);
        var second = HealthDisplayPreference.resolve(HealthStyle.BARS,false,true,true);
        assertEquals(HealthStyle.HEART,first.style());
        assertEquals(HealthStyle.BARS,second.style());
        assertTrue(first.visible());
        assertTrue(second.visible());
    }
    @Test void oneViewerHidingDoesNotAffectOthers() {
        var first = HealthDisplayPreference.resolve(HealthStyle.HEART,true,true,true);
        var second = HealthDisplayPreference.resolve(HealthStyle.BARS,false,true,true);
        assertFalse(first.visible());
        assertTrue(second.visible());
    }
    @Test void lockedVisibilityUsesServerDefault() {
        assertTrue(HealthDisplayPreference.resolve(HealthStyle.HEART,true,false,true).visible());
        assertFalse(HealthDisplayPreference.resolve(HealthStyle.HEART,true,false,false).visible());
    }
    @Test void hiddenByDefaultCanBeShownByViewer() {
        assertFalse(HealthDisplayPreference.resolve(HealthStyle.BARS,false,true,false).visible());
        assertTrue(HealthDisplayPreference.resolve(HealthStyle.HEART,true,true,false).visible());
    }
}
