# INCUID Chat — Phase 1 developer build

**Target**: Minecraft/Paper 26.3, Java 25, Gradle Kotlin DSL, plugin version `1.0.0-phase1`.

This is a **Phase 1 source release**, NOT the completed five-phase plugin. It is **not build-verified** in the creation environment (Java 25/Gradle unavailable). Do not deploy to production without successfully compiling and smoke-testing against your Paper 26.3 server.

## Features included

- Paper `AsyncChatEvent` renderer using Adventure components.
- Always exactly ten health positions, ceiling-filled, with configurable glyphs, thresholds and colors.
- Player health hovering shows current/max health, percentage and condition.
- Server-thread health and metadata capture at chat time (no repeating health polling tasks).
- Optional LuckPerms prefix/suffix through LuckPerms API, gracefully absent if not installed.
- Click player name to suggest `/msg Player ` and hover their username.
- Online-player `@mentions` and permission-restricted `@everyone`, with sound throttling and toggles.
- `/ic help`, `/ic status`, `/ic version`, `/ic reload`, `/ic healthbar`, `/ic mentions`.
- YAML settings with reload, validated health thresholds and hex colors.

## Not included yet

Phase 2 item/coords tags, private messages, persisted preferences; Phase 3 join/death/advancement announcements; Phase 4 moderation; Phase 5 channels and extras. PlaceholderAPI is declared as optional metadata only and is **not integrated yet**. The sample `filters.yml`, `formats.yml`, and `messages.yml` are reserved for future phases; their content is not yet used to drive rendering. Player toggles are currently **session-only**, not persisted.

## Build

Install JDK 25 and Gradle 9.2+ (or install a compatible Gradle wrapper in the project). From the project root:

```sh
gradle clean test build
```

Compiled JAR: `build/libs/INCUIDChat-1.0.0-phase1.jar`.

Copy the JAR into `plugins/` on a **Paper 26.3** server running **Java 25**, restart, and configure `plugins/INCUIDChat/config.yml`. Use `/ic reload` for configuration changes.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/incuidchat help` or `/ic help` | Command list | everyone |
| `/ic status` | Enabled module status | everyone |
| `/ic version` | Version info | everyone |
| `/ic reload` | Reload config | `incuidchat.reload` or `incuidchat.admin` |
| `/ic healthbar` | Toggle your health bar | `incuidchat.health.hide` |
| `/ic mentions` | Toggle sounds for your mentions | `incuidchat.mentions` |

Permission defaults: `incuidchat.admin`, `incuidchat.reload`, `incuidchat.mentions.everyone`, `incuidchat.chat.color` => op; `incuidchat.health`, `incuidchat.health.hide`, `incuidchat.mentions` => everyone. `incuidchat.chat.color` is reserved and does not activate player formatting in Phase 1.

## Concurrency and signed chat

The async event captures a minimal immutable record via a main-thread scheduled callable. The waiting chat worker times out after two seconds and, if capture fails, leaves the original chat renderer untouched. No Bukkit player health, attributes, coordinates, online-list or LuckPerms player metadata are queried from the async chat worker. The renderer uses only snapshots. Mentions are sounded on the primary thread. No database or disk access is performed during normal chat.

The original `event.message()` and signed user-authored content are **not replaced**. Highlighting mentions changes only the rendered decoration, but the simple mention highlighter can flatten nested text-component styling when a mention is present. This is a compatibility limitation to improve before production release. In particular, messages transformed for appearance should not be described as new, cryptographically signed text. Other chat formatters may override this renderer depending on event priority. Avoid installing multiple competing chat formatters.

## Configuration reference

- `health.enabled`: global toggle
- `health.segments`: must be 10; other values are ignored with a warning
- `health.filled-symbol`, `health.empty-symbol`, `health.hearts`: display glyphs; 10 positions
- `health.thresholds.healthy/injured`: unrounded percentage boundaries (75/50 defaults)
- `health.colors.*`: strict `#RRGGBB`; invalid values get defaults
- `health.hover.enabled`: health details tooltip
- `health.visibility.allow-player-toggle/default-visible`: health display preferences (session only)
- `chat.enabled`: enable renderer
- `chat.separator`, `chat.player-color`, `chat.message-color`: cosmetic formatting
- `chat.name-hover`, `chat.name-click-suggest-msg`: player name interactions
- `mentions.enabled`, `mentions.highlight-color`, `mentions.sound`: player mentions
- `mentions.cooldown-seconds`, `mentions.everyone-cooldown-seconds`: notification limits
- `mentions.everyone-enabled`: enable staff mentions of everyone
- `integrations.luckperms-prefix/suffix`: add metadata (admin-defined values escaped)

## Troubleshooting

- **Unsupported API version**: server is not Paper 26.3 or newer.
- **Unsupported class file**: start Paper using Java 25.
- **Gradle dependency resolution fails**: check access to Paper's Maven repository and Maven Central. The dependency is pinned to `26.3.build.252-stable` and may require adjustment if it is unavailable.
- **No ranks**: install/configure LuckPerms and verify prefix/suffix metadata; no LuckPerms is required for normal formatting.
- **No mention sounds**: verify sound enum name, `mentions.enabled`, player preferences and cooldowns.
- **Another plugin replaces chat**: remove competing chat formatters or manage event ordering carefully.
- **Chat prints normally**: snapshot may have timed out; check console warnings and main-thread load.

## Health fallback policy

Non-finite input, zero or negative max-health => zero filled positions, dead/gray. Current HP is clamped to [0,max]. Ceil(percentage*10/100) fills at least one bar for positive health. Colors use actual unrounded percentages.

## Build on GitHub Actions (no local Java or Gradle needed)

1. Create an empty repository at https://github.com/new (suggested name: `INCUIDChat`, default branch `main`).
2. Unzip `INCUIDChat-GitHub-ready.zip`. Upload **the contents of the extracted `INCUIDChat` folder**, including the hidden `.github` directory, to the **repository root**. The root should contain `build.gradle.kts`, `settings.gradle.kts`, `src/` and `.github/workflows/build.yml`.
3. Commit the files to the `main` branch. The **Build INCUID Chat** workflow starts automatically. You can also go to **Actions → Build INCUID Chat → Run workflow**.
4. Open the workflow run. If all tests/build steps pass, download the `INCUIDChat-Phase1-JAR` artifact. Unzip the artifact to get the JAR from `build/libs/`.

The workflow does **not** publish a GitHub Release or deploy to a server. If the build fails, open the red step in Actions and inspect its log. This Phase 1 source has not yet been build-verified; a workflow file cannot guarantee compilation succeeds.
