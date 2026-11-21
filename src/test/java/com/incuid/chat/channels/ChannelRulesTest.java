package com.incuid.chat.channels;

import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

final class ChannelRulesTest {
    @Test void publicMentionsMustNotChangePublicRecipients() {
        UUID sender=UUID.randomUUID(),mentioned=UUID.randomUUID(),other=UUID.randomUUID();
        Set<UUID> publicViewers=Set.of(sender,mentioned,other);
        Set<UUID> noChannelRestriction=ChannelRules.intersect(publicViewers,publicViewers);
        assertEquals(publicViewers,noChannelRestriction);
        assertTrue(noChannelRestriction.contains(other),"Non-mentioned public viewer must still receive the message");
    }
    @Test void channelFilteringOnlyRemovesRecipients() {
        UUID sender=UUID.randomUUID(),near=UUID.randomUUID(),distant=UUID.randomUUID(),hidden=UUID.randomUUID();
        Set<UUID> existing=Set.of(sender,near,distant);
        Set<UUID> eligible=Set.of(sender,near,hidden);
        Set<UUID> result=ChannelRules.intersect(existing,eligible);
        assertEquals(Set.of(sender,near),result);
        assertFalse(result.contains(hidden),"Never re-add a vanished / externally excluded recipient");
    }
    @Test void proximityIs3dAndWorldScoped() {
        assertTrue(ChannelRules.inRange("overworld",0,64,0,"overworld",60,64,80,100));
        assertFalse(ChannelRules.inRange("overworld",0,64,0,"nether",0,64,0,100));
        assertFalse(ChannelRules.inRange("overworld",0,64,0,"overworld",101,64,0,100));
        assertFalse(ChannelRules.inRange("overworld",0,64,0,"overworld",0,64,0,-5));
    }
    @Test void closedChannelsFallBackToGlobal() {
        assertEquals("global",ChannelRules.normalize("staff",true,true,false));
        assertEquals("global",ChannelRules.normalize("local",false,true,false));
        assertEquals("local",ChannelRules.normalize("LOCAL",true,true,false));
        assertEquals("staff",ChannelRules.normalize("staff",true,true,true));
        assertEquals("global",ChannelRules.normalize("nonsense",true,true,true));
    }
}
