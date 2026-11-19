#!/usr/bin/env python3
"""Lightweight structural checks (does not replace Gradle build or in-game tests)."""
from pathlib import Path
import re
r=Path(__file__).resolve().parents[1]
base=r/'src/main/java/com/incuid/chat'
a=(base/'community/AfkService.java').read_text()
c=(base/'community/CommunityAnnouncements.java').read_text()
p=(base/'IncuidChatPlugin.java').read_text()
m=(base/'messaging/MessageService.java').read_text()
config=(r/'src/main/resources/config.yml').read_text()
assert 'Component.text("[",TextColor.color(0xFFFFFF))' in a
assert 'Component.text("AFK",TextColor.color(0xFF5555))' in a
assert 'Component.text("]",TextColor.color(0xFFFFFF))' in a
assert 'player.playerListName(old)' in a
assert 'registerCommand("afk"' in p
assert 'afk.isAfk(target.getUniqueId())' in m
assert 'source.sendMessage(AfkService.label()' in m
assert 'event.message(' in c and 'if (original!=null)' in c
assert 'event.joinMessage(' in c and 'event.quitMessage(' in c
assert 'event.deathMessage(' in c
assert 'afk:' in config and 'announcements:' in config
assert 'version: 1.2.0-phase3' in (r/'src/main/resources/plugin.yml').read_text()
print('Phase 3 structural checks passed')
