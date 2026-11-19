# INCUID Chat — Phase 2 — viewer-local health and white brackets (v1.1.2-phase2)

Minecraft **Paper 26.3**, **Java 25**, Gradle 9.2.1, Adventure API. No required companion plugins. LuckPerms optional.

## Included

**New in v1.1.2:** Ten-segment health bar brackets (`[` and `]`) are always white (`#FFFFFF`). The filled segments still use the configurable green/yellow/red/gray health color, and unfilled segments retain the configured empty color. Single-heart display, hover details, per-viewer settings, and all Phase 2 features are unchanged.

**Retained from Phase 1.1:** Adventure chat renderer, exact ten-segment health bars, 1-heart alternative (`/ic healthstyle heart`), health hover, mentions, optional LuckPerms prefix/suffix, and the `/ic` management commands.

**Viewer-local health display fix (retained from v1.1.1):** `/ic healthstyle bars|heart` changes how **you see every player's health** in chat, without changing other players' displays. `/ic healthbar` toggles whether **you see** health indicators in chat. Player A can see hearts while Player B sees bars or nothing for the very same chat message. The sender's HP is still captured once per message, and the viewer's preference is read by UUID during per-recipient rendering. Console output uses server defaults. The preferences remain in the existing `players.yml` format, so no reset is necessary. Styles selected before this update will now apply to the viewers who chose them (not to their own outgoing indicator for everybody).

**New in Phase 2:**

- `[item]`: renders the held item with its actual Minecraft item hover preview, including supported metadata. Max 1 occurrence per message by default; rejects metadata larger than 8 KiB. Empty hand shows `[empty hand]`.
- `[coords]`: renders integer block coordinates with exact location/world hover and a **click-to-suggest** coordinate string. This safely populates chat input (it does **not** directly copy to the clipboard).
- UUID-based player preferences persisted to `plugins/INCUIDChat/players.yml`: health style, health visibility, mention notification toggle, private message toggle, and ignore lists.
- `/incuidmsg <player> <message>`, `/incuidreply <message>`, `/incuidignore <online player>`, `/incuidunignore <online player>`, `/incuidignorelist`, `/incuidmsgtoggle`.
- Existing `messages.yml`, `formats.yml`, `filters.yml` are left untouched during restart. Their initial defaults are written only when absent.

**Messaging aliases:** To avoid conflicts with EssentialsX or vanilla commands, short aliases (`/msg`, `/reply`, `/ignore`, `/unignore`, `/ignorelist`, `/msgtoggle`) are **disabled by default**. Set `private-messages.register-short-aliases: true` before startup only if your server does not use another messaging plugin. The full `/incuid...` commands always work when messaging is enabled. Aliases and command enable/disable are determined at startup and require a restart; formatting settings support `/ic reload`.

**Current limitations:** Ignore/unignore can target online players only; their UUIDs persist after they go offline. Ignore list shows UUIDs for offline players. Reply partner is held in memory during the current online session; it's cleared when someone disconnects. Messaging does not federate across proxy servers. PlaceholderAPI is a soft dependency only and no expansion is registered. Phase 3 join/death/advancement messages and Phase 4 moderation are not included.

## Install / upgrade

1. Back up `plugins/INCUIDChat/` and stop Paper.
2. Build this project using GitHub Actions or `gradle clean test build` on Java 25 with Gradle 9.2.1.
3. Download the **INCUIDChat-Phase2-JAR** artifact from the successful workflow and extract the `.jar`.
4. Remove the old `INCUIDChat-1.0.0-phase1.jar` from your server's `plugins` folder. Upload **only** `INCUIDChat-1.1.2-phase2.jar`.
5. Restart Paper and look for `INCUID Chat Phase 2 viewer-local health enabled` in console.
6. Leave existing YAML files in place: plugin will reuse them. Add any new `interactive:` or `private-messages:` keys yourself to `config.yml` if you want to change their defaults. Missing keys are handled using defaults.
7. Try `Hello [item]` while holding something, `Village at [coords]`, `/ic healthstyle heart`, `/incuidmsg Friend Hello`.

## Permission nodes

| Permission | Default | Purpose |
| --- | --- | --- |
| `incuidchat.admin` | op | Admin |
| `incuidchat.reload` | op | Reload config |
| `incuidchat.health` | true | Existing health functionality |
| `incuidchat.health.hide` | true | Toggle health indicator |
| `incuidchat.health.style` | true | Select bars/heart |
| `incuidchat.mentions` | true | Mention notification preference |
| `incuidchat.mentions.everyone` | op | `@everyone` |
| `incuidchat.chat.color` | op | Reserved cosmetic permission |
| `incuidchat.items` | true | Share `[item]` |
| `incuidchat.coordinates` | true | Share `[coords]` |
| `incuidchat.pm` | true | All personal messaging and ignore commands |
| `incuidchat.pm.bypass` | op | Bypass user ignore and incoming-message toggle (staff only) |

## Runtime, safety and compatibility

- `AsyncChatEvent`: Bukkit entity data is captured once on the main thread (or inline for synchronous events). `ChatRenderer` receives immutable components; the signed original chat event content is **not rewritten**. Interactive tag decoration/mention highlights are a rendering transformation, not a cryptographic re-sign of modified text.
- Held-item hover details and coordinate snapshots are captured on the main thread; no item references or `Player` references are retained in plugin state. Item metadata is size-limited before constructing hover events. Item snapshot is only generated if `[item]` appears in the message.
- Ordinary chat processing does no disk writes. Player preference changes are saved via a single-thread executor; all outstanding writes are flushed on plugin shutdown (up to a bounded wait).
- UUID-based reply state is ephemeral; persisted ignored UUIDs and player toggles are loaded once during enable.
- Only one chat formatter should control final formatting. Disable EssentialsXChat/VentureChat formatting if you want INCUID Chat to own the output.
- `players.yml` Phase 1.1 health-style values under `health-styles` are automatically read for migration. A preference change writes the new `players:` format and removes legacy storage; back up the file before upgrading.

## Build and tests

`gradle --no-daemon clean test build` on Java 25. The `.jar` appears in `build/libs/`. GitHub Actions uploads the output under **INCUIDChat-Phase2-JAR** after passing tests. The job also uploads test XML and reports on failure.

The code has been statically inspected, but **no Gradle/Java 25 environment is available in this workspace**. Final compilation, test execution and server integration must be verified by the GitHub workflow and Paper console before declaring this build production-ready.

## Known constraints

Short command alias registration occurs on plugin startup. Some servers already register `/msg` or `/reply`, so do not enable aliases indiscriminately. Operators receive `incuidchat.pm.bypass` by default; revoke it if staff should not bypass user messaging preferences. Avoid putting sensitive coordinates in public chat; the `[coords]` feature shares your real world location in-game to everyone who sees the message.

## Test for viewer-local health

Use two logged-in players. Player A: `/ic healthstyle heart`; Player B: `/ic healthstyle bars`. Have Player B send a chat message. A should see a heart before B's name, while B sees the ten-segment bar. Next A runs `/ic healthbar` and B sends another message: A sees no health icon, B continues seeing bars. Restart Paper and repeat to verify persistence. The two choices are viewing preferences; they do not alter health values, hover contents, sender messages, or others' settings.

---

## Phase 3 — Community announcements and AFK (1.2.0-phase3)

Phase 3 extends all earlier Phase 1/2 functionality. The existing viewer-local bars/hearts, white health brackets, `[item]`, `[coords]`, private messaging, and saved preferences remain present.

### /afk

Players with `incuidchat.afk` (default: everybody) can use `/afk` to toggle away status. Their Tab-list name becomes **[AFK] PlayerName**, using **white `[` and `]` brackets** and **red `AFK` letters**. Their pre-AFK Tab-list component is restored upon returning. AFK state is in-memory, and is cleared on logout/restart; it is not a persistent player preference. Typing in regular chat also clears AFK by default; set `afk.clear-on-chat: false` if you want strictly manual toggling. Optional `afk.broadcast` is disabled by default to avoid chat spam.

When a private message is successfully delivered to an AFK recipient, its sender receives a short `[AFK] PlayerName is currently away and may not respond.` notification. The message still reaches the recipient. This applies to `/incuidmsg` and `/incuidreply`, and to short aliases if enabled. If another plugin owns `/msg`, its messages do **not** automatically integrate with INCUID AFK notifications.

### Announcements

The `announcements` section of `config.yml` controls join, first join, quit, death, and advancement modules independently. Formats use MiniMessage. Join/quit messages replace event messages; deaths and vanilla-eligible advancements decorate the existing event Component to preserve underlying vanilla information and hover behavior. Recipe/root advancements that Minecraft does not announce are left unannounced. Death coordinates are sent **privately** only to the deceased player when enabled; they are never globally broadcast by this module. The original death message (and gamerule visibility) is respected.

**Existing servers:** Keep `plugins/INCUIDChat/players.yml` and your other existing configuration files. `saveDefaultConfig()` preserves your existing config; if you want to customize Phase 3 options, copy the new `announcements` and `afk` YAML sections into your existing `config.yml`. If absent, Phase 3 features use the documented defaults. Run `/ic reload` after editing config. The new `/afk` command requires a full restart after updating the JAR.

**Compatibility:** Other plugins that change Tab-list names while a player is AFK may compete with the saved/restored component; if you run a Tab formatting plugin, check integration behavior in-game. Other join/death/advancement formatters may also override event messages, so avoid enabling competing modules twice.

**Build:** `gradle clean test build` with Java 25 and Gradle 9.2.1, or use GitHub Actions. A successfully compiled JAR appears in `build/libs/` and is downloadable as the `INCUIDChat-Phase3-JAR` workflow artifact. A green build does not replace in-game verification.
