# Release: INCUID Chat v1.4.0 (Phase 4.1 + Phase 5)

## Included

- Phase 4.1: public mentions stay in the original chat audience. Recipient filtering never expands audiences. At MONITOR priority, mention sounds are limited to non-cancelled event recipients. Optional count-only diagnostics show initial, post-INCUID and near-final audience sizes. This supports diagnosis of conflicts with other plugins but does **not** prove that an outside plugin was the cause of an earlier report.
- Phase 5: global, local (same world, 3D radius) and staff channels with persisted player selection; optional single-message `/g`, `/l`, `/sc`; configurable `[G]`, `[L]`, `[Staff]` labels; richer player-name hovers; optional timed automatic AFK detection; additive YAML configuration migration.
- Existing features retained: ten-segment bars with white brackets, per-viewer heart mode/visibility, hover health stats, LuckPerms prefixes, `[item]`, `[coords]`, player mentions, PMs/ignore, AFK Tab labels, first joins/leaves/death/advancement announcements, spam filtering/mutes/clear/logs, muted blue-gray chat.

## Build and test status

The project can be built using the included GitHub Actions workflow with Java 25 / Gradle 9.2.1. The project has **not** been fully compiled or integration-tested against Paper 26.3 in this environment. Available Java 21 pure-logic smoke tests for channel selection/radius/intersection, health and moderation normalization passed. YAML parsing and ZIP integrity checks passed. JUnit sources include new `ChannelRulesTest` (must run on GitHub).

## Install

1. Back up `plugins/INCUIDChat` (especially `config.yml`, `players.yml`, `mutes.yml`).
2. Upload this ZIP's extracted **contents** to the root of your GitHub repository, including `.github/workflows/build.yml` (use GitHub's editor for hidden folders if needed).
3. Commit and inspect GitHub Actions. Download `INCUIDChat-v1.4.0-JAR` only after a successful run.
4. Stop Paper, replace your old INCUIDChat JAR, and restart. Leave your existing YAML data in place.
5. Set `channels.enabled: true` (and enable `local`/`staff` as desired) then restart to register `/chat`, `/g`, `/l`, `/sc` commands.

## Verification checklist

- With three players, ordinary public chat and `Hey @Alex` must both reach all eligible public recipients; only Alex receives mention sound.
- Change `/ic healthstyle heart` and `/ic healthbar` separately on two clients; viewer-local choices must not affect one another.
- Test `/chat local` with two players inside and one outside the configured radius, plus a player in another world.
- Check staff chat invisible to non-staff, including console/nonplayer audiences.
- Restart and verify saved channel, health and ignore preferences.
- Enable AFK automatic mode and test inactivity and return movement; manually set AFK should not be reset by block movement unless auto-set.
- Verify old mutes, `[item]`, `[coords]`, PMs, death/advancement announcements and chat moderation still work.
- Confirm configuration upgrades keep custom comments and settings. On unexpected behavior, restore the backup and inspect logs.
