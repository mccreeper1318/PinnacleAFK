# PinnacleAFK

PaperMC 26.2 plugin that adds `/afk`.

## Features

- `/afk` toggles AFK mode on and off.
- Shows `[AFK]` in the tab menu.
- Shows `[AFK]` above the player's name using a floating text marker.
- Preserves the player's real scoreboard team membership and team-based mechanics while AFK.
- Freezes the player in place until they type `/afk` again.
- Makes the player invincible after a configurable real-time delay that is not extended by low TPS.
- Rate-limits repeated `/afk` toggles with a configurable cooldown.
- Optionally detects inactivity and marks players AFK automatically.
- Provides administrator commands for reloads, AFK lists, status checks, and removals.
- Controls broadcasts, allowed worlds, protection eligibility, and each display independently.
- Announces to the server when a player goes AFK or returns from AFK.
- Cancels all normal Bukkit/Paper damage events while protected.

## Requirements

- Java Development Kit (JDK) 25
- PaperMC 26.2

Make sure `java -version` reports Java 25 before building.

## Build

The repository includes the Gradle wrapper, so a separate Gradle installation is not required.

Linux or macOS:

```bash
./gradlew build
```

Windows:

```powershell
.\gradlew.bat build
```

The versioned JAR will be created in:

```text
build/libs/PinnacleAFK-<version>.jar
```

Every push and pull request is also built and tested by GitHub Actions. The regression suite covers configuration migration and validation, elapsed-time deadlines, cooldowns, administrator command parsing, world policies, and ownership-safe display restoration. Successful workflow runs provide the verified plugin JAR and its SHA-256 checksum as downloadable artifacts.

## Automated releases

Publishing a GitHub release with a tag matching the Gradle project version automatically builds the tagged source and attaches the versioned plugin JAR and its SHA-256 checksum. The release workflow accepts tags with or without a leading `v` and validates prerelease tags against the GitHub release type.

## Install

1. Build the plugin or download a JAR from a successful workflow or GitHub release.
2. Put the JAR into your server's `plugins` folder.
3. Start or restart the Paper server.
4. Edit `plugins/PinnacleAFK/config.yml` if needed.
5. Run `/pafk reload` or restart the server.

## Commands

- `/afk` — toggles your own AFK status. Permission: `pinnacleafk.use` (granted by default).
- `/pafk reload` — validates and safely reloads `config.yml`.
- `/pafk list` — lists online AFK players.
- `/pafk status <player>` — checks an online player's AFK status.
- `/pafk remove <player>` — removes an online player's AFK status.

All `/pafk` commands require `pinnacleafk.admin`, which defaults to server operators.

## Config

```yml
invincible-after-seconds: 10
toggle-cooldown-seconds: 3
allowed-worlds: []

broadcasts:
  enabled: true

protection:
  enabled: true
  require-permission: false
  allowed-worlds: []

automatic-afk:
  enabled: false
  after-seconds: 300

display:
  tab-enabled: true
  nametag-enabled: true
  tab-format: "&7[AFK] &f%player%"
  nametag-prefix: "&7[AFK] &f"
  nametag-suffix: ""

messages:
  now-afk: "&7You are now AFK. You cannot move. Type &f/afk &7again to return."
  no-longer-afk: "&aYou are no longer AFK."
  broadcast-now-afk: "&7%player% is now AFK."
  broadcast-no-longer-afk: "&a%player% is no longer AFK."
  invincible-now: "&bYou are now protected from all damage while AFK."
  already-protected-delay: "&7You will become damage-proof in &f%seconds%s&7."
  toggle-cooldown: "&cPlease wait &f%seconds%s &cbefore toggling AFK again."
  world-not-allowed: "&cAFK mode is not allowed in this world."
  afk-cleared-world: "&cYour AFK status was cleared because this world is no longer allowed."
  only-player: "&cOnly players can use this command."
  no-permission: "&cYou do not have permission to use this command."
  reload-success: "&aPinnacleAFK configuration reloaded."
  reload-failed: "&cPinnacleAFK could not reload config.yml. Check the server log."
  admin-usage: "&7Usage: &f/pafk <reload|list|status|remove> [player]"
  admin-list-empty: "&7No players are currently AFK."
  admin-list: "&7AFK players (&f%count%&7): &f%players%"
  admin-status-afk: "&f%player% &7is currently AFK."
  admin-status-not-afk: "&f%player% &7is not AFK."
  admin-removed: "&aRemoved AFK status from &f%player%&a."
  player-not-found: "&cPlayer &f%player% &cis not online."
```

Existing configuration files automatically receive newly bundled settings during startup and safe reloads. Duration settings accept nonnegative whole seconds: `invincible-after-seconds: 0` grants immediate protection, while `toggle-cooldown-seconds: 0` disables rate limiting. Invalid values and malformed reload files are rejected or replaced safely without partially applying settings.

Protection deadlines use real elapsed time rather than a number of server ticks, so low TPS does not proportionally extend the configured exposure period.

`allowed-worlds` controls where AFK mode may be entered. `protection.allowed-worlds` independently limits damage protection. When `protection.require-permission` is enabled, eligible players must also receive `pinnacleafk.protection`. An empty list or `["*"]` allows every world. World matching is case-insensitive.

Automatic detection is disabled by default. Set `automatic-afk.enabled: true` and choose `automatic-afk.after-seconds` to enable it. Movement, commands, chat, and ordinary player actions reset the inactivity clock.

Set `broadcasts.enabled`, `protection.enabled`, `display.tab-enabled`, or `display.nametag-enabled` to `false` to disable that behavior independently.

In `display.tab-format`, `%player%` inserts the player's current formatted tab-list component rather than just their username. PinnacleAFK reconciles changes made by rank or tab-list plugins while the player is AFK and restores the latest external value only when it still owns the displayed name.

The `nametag-prefix` and `nametag-suffix` values form a floating marker displayed above the player's normal name tag. PinnacleAFK uses this marker instead of a scoreboard-team prefix, so entering AFK does not change team membership, friendly-fire rules, collision behavior, or team selectors.

## Team preservation in 26.2-1.1.0

Older versions temporarily moved AFK players into plugin-created scoreboard teams. Version 26.2-1.1.0 leaves every player in their real team continuously and uses a nonpersistent text marker for the above-head AFK indicator.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for release history and upcoming changes.

## License

PinnacleAFK is licensed under the [MIT License](LICENSE).
