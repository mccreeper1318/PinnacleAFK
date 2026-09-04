# Changelog

All notable changes to PinnacleAFK are documented in this file.

## [26.2-1.1.3] - Unreleased

### Fixed

- Prevented mounted players from bypassing the AFK movement lock by dismounting them before the lock position is captured, correcting any later mounted/displaced AFK state, and treating vehicle or otherwise observed mounted movement as activity for automatic AFK detection ([#33](https://github.com/mccreeper1318/PinnacleAFK/issues/33)).
- Refused manual and automatic AFK entry when a player remains mounted after both normal and fallback dismount attempts, preventing invalid AFK locks or protection and reporting the rejection to the player ([#37](https://github.com/mccreeper1318/PinnacleAFK/issues/37)).

## [26.2-1.1.2] - 8/28/26

### Hotfix

- Reworked the AFK movement lock to cancel movement packets against the saved AFK location and clear player velocity, preventing continuous walking or stop/start movement from drifting away before correction.

## [26.2-1.1.1] - 8/28/26

### Hotfix

- Fully locked AFK players to their saved position and view direction, preventing walking, drifting, or looking around while AFK.
- Fixed tab-list restoration after leaving AFK so players using normal scoreboard team formatting return to their team prefix/color (such as `<NM>` or `<FM>`) instead of being left with a plain custom player-list name.
- Changed the AFK tab display so the player's name is gray to match the gray `[AFK]` indicator while AFK.

## [26.2-1.1.0] - 8/27/26

### Added

- Added the permission-protected `/pafk reload` command, which rejects malformed YAML and safely reapplies validated runtime, display, protection, and automatic-AFK settings ([#13](https://github.com/mccreeper1318/PinnacleAFK/issues/13)).
- Added an automated JUnit regression suite covering configuration migration and validation, elapsed-time deadlines, cooldowns, administrator command parsing, world policies, and ownership-safe display restoration ([#14](https://github.com/mccreeper1318/PinnacleAFK/issues/14)).
- Added GitHub Actions validation that builds and tests the plugin on every push and pull request, verifies the packaged descriptor and resources, and uploads the JAR with its checksum ([#15](https://github.com/mccreeper1318/PinnacleAFK/issues/15)).
- Added an automated release workflow that validates release tags, builds with Java 25, and attaches the versioned plugin JAR and SHA-256 checksum to published GitHub releases ([#18](https://github.com/mccreeper1318/PinnacleAFK/issues/18)).
- Added this changelog to document release history, fixes, configuration changes, and compatibility updates ([#19](https://github.com/mccreeper1318/PinnacleAFK/issues/19)).
- Added an MIT license ([#20](https://github.com/mccreeper1318/PinnacleAFK/issues/20)).
- Added repository exclusions for Gradle outputs, IDE metadata, local settings, and generated server files ([#21](https://github.com/mccreeper1318/PinnacleAFK/issues/21)).
- Added `/pafk list`, `/pafk status <player>`, and `/pafk remove <player>` for permission-protected AFK administration and troubleshooting ([#23](https://github.com/mccreeper1318/PinnacleAFK/issues/23)).
- Added disabled-by-default automatic AFK detection with a configurable inactivity duration and activity tracking for movement, commands, chat, and ordinary player actions ([#24](https://github.com/mccreeper1318/PinnacleAFK/issues/24)).
- Added configuration controls for broadcasts, AFK-enabled worlds, protection enablement, world and permission eligibility, and independent tab-list and above-head indicators ([#25](https://github.com/mccreeper1318/PinnacleAFK/issues/25)).

### Changed

- Made the Gradle project version the single source for the JAR name and generated `plugin.yml` version, preventing release metadata from drifting between files ([#16](https://github.com/mccreeper1318/PinnacleAFK/issues/16)).
- Pinned the Paper API dependency to `26.2.build.62-beta` so builds resolve the same API version every time ([#17](https://github.com/mccreeper1318/PinnacleAFK/issues/17)).
- Updated the build instructions to use the included Gradle wrapper and document the Java 25 requirement on Linux, macOS, and Windows ([#22](https://github.com/mccreeper1318/PinnacleAFK/issues/22)).

### Fixed

- Prevented AFK players from attacking entities, launching projectiles, breaking or placing blocks, using or moving items, and interacting with entities or inventories ([#1](https://github.com/mccreeper1318/PinnacleAFK/issues/1)).
- Blocked teleports and portals while AFK and added safe AFK cleanup if an external world change still succeeds, preventing stale cross-world movement locks ([#2](https://github.com/mccreeper1318/PinnacleAFK/issues/2)).
- Replaced temporary AFK scoreboard teams with a floating above-head marker and direct tab formatting, preserving real team membership, friendly-fire rules, collision behavior, selectors, and other team mechanics ([#3](https://github.com/mccreeper1318/PinnacleAFK/issues/3)).
- Prevented abrupt shutdowns from stranding players in generated AFK teams by eliminating temporary teams and reconciling legacy shared and per-player AFK teams during startup ([#4](https://github.com/mccreeper1318/PinnacleAFK/issues/4)).
- Cleared AFK state on death with a respawn failsafe, cancelling delayed protection, removing the marker, restoring the tab-list name, and preventing stale death-location locks ([#5](https://github.com/mccreeper1318/PinnacleAFK/issues/5)).
- Preserved team changes made by rank and scoreboard plugins while players are AFK by removing AFK ownership and stale restoration of scoreboard-team membership ([#6](https://github.com/mccreeper1318/PinnacleAFK/issues/6)).
- Made AFK indicators independent of main, per-player, and viewer-specific scoreboards by using a floating display and direct tab-list formatting, preventing custom scoreboard plugins from hiding or misformatting AFK status ([#7](https://github.com/mccreeper1318/PinnacleAFK/issues/7)).
- Preserved custom tab-list components when applying the AFK indicator, reconciled formatting changes made by other plugins while AFK, and avoided restoring stale values ([#8](https://github.com/mccreeper1318/PinnacleAFK/issues/8)).
- Merged newly bundled configuration defaults into existing files and used bundled message defaults for missing settings, preventing blank messages after upgrades ([#9](https://github.com/mccreeper1318/PinnacleAFK/issues/9)).
- Added a configurable real-time cooldown for repeated `/afk` toggles, preventing chat floods and unnecessary display churn ([#10](https://github.com/mccreeper1318/PinnacleAFK/issues/10)).
- Measured delayed protection against a monotonic real-time deadline instead of server ticks, preventing low TPS from proportionally extending the vulnerable period ([#11](https://github.com/mccreeper1318/PinnacleAFK/issues/11)).
- Validated protection and cooldown durations during startup, logging and replacing negative, fractional, nonnumeric, or out-of-range values with safe defaults instead of silently granting instant protection ([#12](https://github.com/mccreeper1318/PinnacleAFK/issues/12)).

## [1.0.2] - 2026-06-26

### Added

- Added server-wide announcements when a player enters or leaves AFK mode.
- Added configurable AFK broadcast messages.

## [1.0.1]

### Added

- Added a configuration option controlling whether PinnacleAFK overrides player-list names.

### Changed

- Updated the AFK name display to better preserve existing scoreboard team prefixes and suffixes.
- Improved cleanup of temporary AFK scoreboard teams.

### Fixed

- Fixed original scoreboard team prefixes not being restored after leaving AFK mode.

## [1.0.0]

### Added

- Added the `/afk` command to toggle AFK mode.
- Added AFK indicators in the tab list and above player name tags.
- Added a movement lock while AFK.
- Added configurable delayed invincibility and damage protection.
- Added cleanup when players leave the server or the plugin is disabled.
