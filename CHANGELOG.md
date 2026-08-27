# Changelog

All notable changes to PinnacleAFK are documented in this file.

## [26.2-1.1.0] - Unreleased

### Added

- Added GitHub Actions validation that builds and tests the plugin on every push and pull request, verifies the packaged descriptor and resources, and uploads the JAR with its checksum ([#15](https://github.com/mccreeper1318/PinnacleAFK/issues/15)).
- Added an automated release workflow that validates release tags, builds with Java 25, and attaches the versioned plugin JAR and SHA-256 checksum to published GitHub releases ([#18](https://github.com/mccreeper1318/PinnacleAFK/issues/18)).
- Added this changelog to document release history, fixes, configuration changes, and compatibility updates ([#19](https://github.com/mccreeper1318/PinnacleAFK/issues/19)).
- Added an MIT license ([#20](https://github.com/mccreeper1318/PinnacleAFK/issues/20)).
- Added repository exclusions for Gradle outputs, IDE metadata, local settings, and generated server files ([#21](https://github.com/mccreeper1318/PinnacleAFK/issues/21)).

### Changed

- Made the Gradle project version the single source for the JAR name and generated `plugin.yml` version, preventing release metadata from drifting between files ([#16](https://github.com/mccreeper1318/PinnacleAFK/issues/16)).
- Pinned the Paper API dependency to `26.2.build.62-beta` so builds resolve the same API version every time ([#17](https://github.com/mccreeper1318/PinnacleAFK/issues/17)).
- Updated the build instructions to use the included Gradle wrapper and document the Java 25 requirement on Linux, macOS, and Windows ([#22](https://github.com/mccreeper1318/PinnacleAFK/issues/22)).

### Fixed

- Prevented AFK players from attacking entities, launching projectiles, breaking or placing blocks, using or moving items, and interacting with entities or inventories ([#1](https://github.com/mccreeper1318/PinnacleAFK/issues/1)).
- Blocked teleports and portals while AFK and added safe AFK cleanup if an external world change still succeeds, preventing stale cross-world movement locks ([#2](https://github.com/mccreeper1318/PinnacleAFK/issues/2)).
- Replaced temporary AFK scoreboard teams with a floating above-head marker and direct tab formatting, preserving real team membership, friendly-fire rules, collision behavior, selectors, and other team mechanics ([#3](https://github.com/mccreeper1318/PinnacleAFK/issues/3)).
- Prevented abrupt shutdowns from stranding players in generated AFK teams by eliminating temporary teams and reconciling legacy shared and per-player AFK teams during startup ([#4](https://github.com/mccreeper1318/PinnacleAFK/issues/4)).

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
