#!/usr/bin/env python3
"""Verify the health bar's white bracket styling at source level."""
from pathlib import Path
source = (Path(__file__).resolve().parents[1] / "src/main/java/com/incuid/chat/health/HealthBar.java").read_text()
assert 'Component.text("[", TextColor.color(0xFFFFFF))' in source
assert 'Component.text("]", TextColor.color(0xFFFFFF))' in source
assert 'TextColor.color(0x888888)' not in source
assert 'filled ? selected : settings.emptyColor()' in source
assert 'style == HealthStyle.HEART' in source
assert 'bar.hoverEvent(HoverEvent.showText(hoverText(playerName, hp)))' in source
print("Health bar colors and existing hover/heart code checks passed")
