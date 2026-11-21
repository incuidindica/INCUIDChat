# INCUID Chat Phase 4 — Admin Quick Reference

## New look
Player names remain white; regular chat text now defaults to muted blue-gray `#B8C5D1`. The delimiter is neutral gray and 10-segment health bar brackets remain white. Existing `chat.message-color: '#EEEEEE'` is changed to the new default while preserving other settings. If you previously selected any other hex color, it stays as configured.

## Commands

| Command | Permission | Details |
|---|---|---|
| `/ic mute Steve 30m Spamming chat` | `incuidchat.moderation.mute` | Temporarily mute for 30 minutes |
| `/ic mute Steve permanent Repeated disruption` | `incuidchat.moderation.mute` | Permanently mute; the duration keyword is optional |
| `/ic unmute Steve` | `incuidchat.moderation.unmute` | Unmute a player |
| `/ic clear` | `incuidchat.moderation.clear` | Push existing chat messages out of view for online users |
| `/ic reload` | `incuidchat.reload` | Reload YAML configuration and blocked-word list |

`/incuidmute`, `/incuidunmute`, `/incuidclear` are equivalent namespaced alternatives; all permissions default to OP. Durations support `s`, `m`, `h`, `d` suffixes, up to six digits. `/ic mute` and `/ic unmute` support known offline players. A permanent mute is the default if no duration is given.

## Configuration

- `moderation.enabled` controls moderation features.
- `moderation.spam.cooldown.*`: time between messages.
- `moderation.spam.duplicate.*`: duplicate suppression.
- `moderation.spam.repeated-characters.*`: repeated-character protection.
- `moderation.spam.flood.*`: maximum number of messages per time window.
- `moderation.filter.enabled`: enables block list; **false by default**.
- `moderation.filter.action`: `cancel`, `replace`, or `notify`.
- `moderation.filter.notify-staff`: staff reports require `incuidchat.moderation.notify`.
- Add literal blocked words to `plugins/INCUIDChat/filters.yml` under `blocked-words:`.
- `incuidchat.moderation.bypass` exempts staff from chat spam and mute restrictions.

Sample blocklist:

```yaml
blocked-words:
  - exampleword
  - unwanted phrase
```

Filtering `replace` **changes player-authored text** and may affect signed chat verification. Prefer the default `cancel` action if signed-chat integrity is important.

## Persistence and limitations

`mutes.yml` stores UUID-based mute records. `moderation.log` records moderator mute/unmute/clear actions asynchronously. Chat spam tracking is transient and resets on restart. Blocked-word filtering applies to public chat; INCUID private messages honor mutes but not the public chat filter/spam rules. External `/msg` commands are outside INCUID control. Chat clearing is visual, not erasure of player history or server logs. Other moderation/chat plugins may intercept messages; test compatibility before production deployment.

**Test plan:** send two rapid messages, repeat a previous message, mute/unmute with a second player, restart to confirm mute persistence, try the optional content filter, check that old health bar and heart display preferences remain viewer-local.
