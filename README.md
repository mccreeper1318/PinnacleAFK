# PinnacleAFK

PaperMC 26.2 plugin that adds `/afk`.

## Features

- `/afk` toggles AFK mode on and off.
- Shows `[AFK]` in the tab menu.
- Shows `[AFK]` above the player's name using a floating text marker.
- Preserves the player's real scoreboard team membership and team-based mechanics while AFK.
- Freezes the player in place until they type `/afk` again.
- Makes the player invincible after a configurable delay in seconds.
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

## Install

1. Build the plugin.
2. Put the JAR into your server's `plugins` folder.
3. Start or restart the Paper server.
4. Edit `plugins/PinnacleAFK/config.yml` if needed.
5. Restart the server or reload the plugin with your preferred plugin manager.

## Config

```yml
invincible-after-seconds: 10

display:
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
  only-player: "&cOnly players can use this command."
```

The `nametag-prefix` and `nametag-suffix` values form a floating marker displayed above the player's normal name tag. PinnacleAFK uses this marker instead of a scoreboard-team prefix, so entering AFK does not change team membership, friendly-fire rules, collision behavior, or team selectors.

## Team preservation in 26.2-1.1.0

Older versions temporarily moved AFK players into plugin-created scoreboard teams. Version 26.2-1.1.0 leaves every player in their real team continuously and uses a nonpersistent text marker for the above-head AFK indicator.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for release history and upcoming changes.

## License

PinnacleAFK is licensed under the [MIT License](LICENSE).
