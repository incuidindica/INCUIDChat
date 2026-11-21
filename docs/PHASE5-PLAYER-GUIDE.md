# INCUID Chat v1.4.0 — Channels and Player Features

INCUID Chat is designed to behave like ordinary Minecraft public chat until the server administrator enables channels. The health indicator and mention behavior remain the same.

## Chat channels

By default `channels.enabled: false`, so messages are public as before. When an administrator enables `channels.enabled: true`, local and staff channel modules are enabled by default on new installations. Local is available to every player; staff is restricted to operators and players explicitly granted `incuidchat.staffchat`. Existing installations that saved `channels.local.enabled: false` or `channels.staff.enabled: false` must set them to `true` manually because upgrades preserve saved settings.

- `/chat` — shows your active channel.
- `/chat global` — make ordinary messages global. Everyone permitted to receive public chat sees them.
- `/chat local` — choose local chat (if enabled). Only players in the same world within the configured 3D radius see these messages.
- `/chat staff` — choose staff chat (if enabled and permitted). Other players cannot see staff messages.
- `/g Hello` — send one message in Global without changing your selected channel.
- `/l Hello` — send one local message.
- `/sc Hello` — send one staff message, if permitted.

Your selection survives reconnects and restarts. If the selected channel becomes unavailable, messages fall back safely to global. Channel labels (`[G]`, `[L]`, `[Staff]`) can be disabled.

**Important:** `/g`, `/l`, and `/sc` are commands. Their generated chat text is server-originated, not an original cryptographically signed client chat packet. It still goes through the normal chat pipeline using the Paper `Player.chat` API, including INCUID moderation and other event listeners.

## Public mentions versus private messages

Typing `Hey @Alex` in regular chat stays in the selected chat channel. It does *not* become private. An optional mention sound is delivered only to an eligible recipient of that message. Use `/msg Alex Hello` for a private message if short aliases are enabled, or `/incuidmsg Alex Hello` otherwise.

## Player name hovers

Hover over a name in chat to see their username, rank prefix where present, current/max health and AFK state. Click a name to suggest `/msg PlayerName` (depending on the server's commands).

## AFK

- `/afk` — toggle your AFK status.
- Tab displays white `[ ]` brackets with red `AFK` inside.
- When messaging an AFK player with INCUID's private-messaging service, the sender receives an AFK notice.
- If enabled by staff, inactivity automatically activates AFK after `afk.auto.idle-seconds` (default 300). Normal movement, interaction, chat and commands reset the timer.

## Health and interactive tags

- `/ic healthstyle bars` — ten-segment health indicator for *your* view.
- `/ic healthstyle heart` — single colored heart for *your* view.
- `/ic healthbar` — toggle indicators for *your* view.
- `[item]` — hold an item in your main hand; open chat and type `Look at this [item]`.
- `[coords]` — open chat and type `Meet here [coords]` to share coordinates.

All prior Phase 1–4 moderation and announcement features are retained.
