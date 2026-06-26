# PinnacleAFK

PaperMC 26.2 plugin that adds `/afk`.

## Features

- `/afk` toggles AFK mode on and off.
- Shows `[AFK]` in the tab menu.
- Shows `[AFK]` next to the player's name tag above their head using a scoreboard team prefix.
- Preserves existing scoreboard team prefixes/suffixes by using a temporary per-player AFK team.
- Freezes the player in place until they type `/afk` again.
- Makes the player invincible after a configurable delay in seconds.
- Announces to the server when a player goes AFK or returns from AFK.
- Cancels all normal Bukkit/Paper damage events while protected.

## Build

```bash
gradle build
```

The jar will be in:

```text
build/libs/PinnacleAFK-1.0.2.jar
```

## Install

1. Build the plugin.
2. Put the jar into your server's `plugins` folder.
3. Start or restart the Paper server.
4. Edit `plugins/PinnacleAFK/config.yml` if needed.
5. Restart the server or reload the plugin with your preferred plugin manager.

## Config

```yml
invincible-after-seconds: 10

display:
  use-player-list-name: false
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

For the name tag above the player, Minecraft's normal API only supports adding text before/after the real username through scoreboard teams. That is why the config uses `nametag-prefix` and `nametag-suffix` instead of a full `%player%` format.


## Prefix preservation fix in 1.0.1

Older versions used one shared `pinnacleafk` scoreboard team. That worked for showing `[AFK]`, but it could replace a player's real team and leave their old prefix missing after toggling AFK off.

Version 1.0.1 now creates a temporary per-player AFK team, copies the player's current team settings, adds the AFK prefix/suffix, and then restores the player to their original team when `/afk` is toggled off.

If a player was already affected by the older version, restart the server with this version installed. If their original team membership was already lost before the update, re-add them to the original team once after the restart.
