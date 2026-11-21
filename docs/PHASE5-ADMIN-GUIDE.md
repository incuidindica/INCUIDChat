# INCUID Chat v1.4.0 — Administration and Release Notes

## Upgrade

Back up the entire `plugins/INCUIDChat/` directory. Replace only the JAR, and restart Paper 26.3 using Java 25. `config.yml` migration appends missing options while preserving existing configured values and comments. If validation fails, the original file is retained and a warning is written to the log. Player preferences are read from UUID-keyed `players.yml`, and existing ignore lists and health display styles are preserved.

## Channel configuration

```yaml
channels:
  enabled: false
  register-shortcuts: true
  show-labels: true
  local:
    enabled: false
    radius: 100
  staff:
    enabled: false
```

Global is always available. Local uses 3D Euclidean distance and world identity, captured on the primary server thread. Staff requires `incuidchat.staffchat` for both sending and receiving. Local requires `incuidchat.channels.local` to select. Channel recipient filtering **only removes** viewers from `AsyncChatEvent.viewers()`; never adds recipients who another plugin removed. Player name hover and per-viewer health display happen in the renderer and do not alter the audience. Nonplayer audiences are omitted in local/staff channels. Normal public/global chat retains all existing audience entries, including nonplayer audiences provided by Paper.

## Diagnostics and mention checks

Set `diagnostics.recipients.enabled: true` to log the count of recipients before and after INCUID's channel filter. No player messages or UUID lists are logged. This is useful for identifying when another chat plugin changes recipients. This does not prove which plugin modified an earlier event's audience. Mention sounds only target the selected channel's current eligible audience. We do *not* indiscriminately broadcast public mentions in order to protect vanish and other plugins' recipient restrictions.

## AFK

```yaml
afk:
  enabled: true
  clear-on-chat: true
  broadcast: false
  manual:
    clear-on-activity: true
  auto:
    enabled: true
    idle-seconds: 300
    clear-on-activity: true
```

The repeating inactivity check runs every 20 seconds and uses lightweight UUID timestamp tracking, not a repeating location scan. Player movement activity updates only on block-coordinate changes, reducing event overhead.

## Commands, conflicts, tests

- `/chat`, `/g`, `/l`, `/sc` register only when channels are enabled at startup (shortcuts also require `channels.register-shortcuts: true`). Restart after enabling channels or changing shortcut registration. Check for conflicts with other plugins before adopting shortcuts.
- Private commands remain `/incuidmsg`, `/incuidreply`, `/incuidignore`, `/incuidunignore`, `/incuidignorelist`, `/incuidmsgtoggle`; shorter aliases are controlled by `private-messages.register-short-aliases` and require restart.
- `/ic mute`, `/ic unmute`, `/ic clear` and all original features remain.
- Staff chat permission: `incuidchat.staffchat` (operator default); local chat: `incuidchat.channels.local` (everyone default).
- Unit tests cover channel intersection and radius, channel choice normalization, moderation rules, and health calculations. GitHub Actions runs Gradle with Java 25 and uploads the JAR only if compilation and tests succeed.

**Compatibility:** The combined release cannot guarantee compatibility with other chat-formatter, vanish, or moderation plugins without integration tests. Commands invoked with `/g`, `/l`, `/sc` use the server-side chat API, not client-signed chat, and may be treated differently by external moderation plugins.

### AFK activity behavior (v1.4.0 update)

Automatic AFK detection is enabled by default after 300 seconds of inactivity. Both manual `/afk` and automatically detected AFK now clear on player activity by default (`afk.manual.clear-on-activity: true` and `afk.auto.clear-on-activity: true`). Moving into a different block, interacting, or executing a non-AFK command counts as activity. `/afk` itself remains an explicit toggle. Existing `config.yml` values are preserved during migration, so servers whose earlier `afk.auto.enabled` value is `false` must change it to `true` manually if they wish to adopt the new default.
