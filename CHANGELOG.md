# Changelog

All notable changes to PinnacleAFK are documented in this file.

## [26.2-1.1.0] - Unreleased

### Added

- Added this changelog to document release history, fixes, configuration changes, and compatibility updates ([#19](https://github.com/mccreeper1318/PinnacleAFK/issues/19)).
- Added an MIT license ([#20](https://github.com/mccreeper1318/PinnacleAFK/issues/20)).
- Added repository exclusions for Gradle outputs, IDE metadata, local settings, and generated server files ([#21](https://github.com/mccreeper1318/PinnacleAFK/issues/21)).

### Changed

- Updated the build instructions to use the included Gradle wrapper and document the Java 25 requirement on Linux, macOS, and Windows ([#22](https://github.com/mccreeper1318/PinnacleAFK/issues/22)).

### Fixed

- Prevented AFK players from attacking entities, launching projectiles, breaking or placing blocks, using or moving items, and interacting with entities or inventories ([#1](https://github.com/mccreeper1318/PinnacleAFK/issues/1)).
- Blocked teleports and portals while AFK and added safe AFK cleanup if an external world change still succeeds, preventing stale cross-world movement locks ([#2](https://github.com/mccreeper1318/PinnacleAFK/issues/2)).

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
