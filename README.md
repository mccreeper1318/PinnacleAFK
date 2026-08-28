# PinnacleAFK

PinnacleAFK is a PaperMC 26.2 plugin that gives players a simple `/afk` command with clear AFK indicators, a complete movement/action lock, optional automatic AFK detection, and configurable delayed damage protection.

## Features

- Toggle AFK mode with `/afk`.
- Shows a gray `[AFK]` indicator in the player list.
- Displays `[AFK]` above the player's normal name tag using a floating text marker.
- Keeps the player's real scoreboard team intact while AFK.
- Preserves rank/team prefixes, suffixes, colors, friendly-fire rules, collision settings, and selectors.
- Restores normal team/rank formatting when the player leaves AFK.
- Completely freezes AFK players at the location and view direction where they entered AFK.
- Blocks normal gameplay actions while AFK, including block interaction, inventory interaction, item movement, combat, projectile use, and teleports/portals.
- Optionally makes AFK players invincible after a configurable real-time delay.
- Optionally marks inactive players AFK automatically.
- Can announce when players enter or leave AFK mode.
- Provides administrator commands for reloading configuration and managing online AFK players.
- Supports configurable AFK worlds, protection worlds, permissions, displays, messages, and toggle cooldowns.

## Requirements

- PaperMC 26.2
- Java 25

## Installation

1. Download the PinnacleAFK JAR from a GitHub release.
2. Place the JAR in your server's `plugins` folder.
3. Start or restart the Paper server.
4. Edit `plugins/PinnacleAFK/config.yml` if you want to change the defaults.
5. Run `/pafk reload` after configuration changes, or restart the server.

> Installing or updating server plugins should be done with a normal server restart rather than a plugin hot-loader.

## Player Usage

### `/afk`

Toggles your AFK status.

When you enter AFK mode:

- Your position and view direction are locked.
- `[AFK]` appears in the tab list and above your character if those displays are enabled.
- Your tab-list name is shown in gray while AFK.
- Normal gameplay actions are blocked.
- Damage protection begins after the configured delay if protection is enabled and you are eligible for it.

Run `/afk` again to return from AFK. Your normal tab-list/team formatting is restored automatically.

## Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/afk` | Toggle your own AFK status. | `pinnacleafk.use` |
| `/pafk reload` | Validate and reload `config.yml`. | `pinnacleafk.admin` |
| `/pafk list` | List online AFK players. | `pinnacleafk.admin` |
| `/pafk status <player>` | Check an online player's AFK status. | `pinnacleafk.admin` |
| `/pafk remove <player>` | Remove an online player's AFK status. | `pinnacleafk.admin` |

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `pinnacleafk.use` | Everyone | Allows use of `/afk`. |
| `pinnacleafk.admin` | Operators | Allows `/pafk` administration commands. |
| `pinnacleafk.protection` | Nobody | Allows AFK damage protection when `protection.require-permission` is enabled. |

## Configuration

The configuration file is created at:

```text
plugins/PinnacleAFK/config.yml
```

Default configuration:

```yml
# How many real elapsed seconds after typing /afk before the player becomes invincible.
# Set to 0 for instant invincibility.
invincible-after-seconds: 10

# Minimum real elapsed seconds between a player's /afk toggles.
# Set to 0 to disable the cooldown.
toggle-cooldown-seconds: 3

# Worlds where players may enter AFK mode.
# Leave empty or use ["*"] to allow every world.
allowed-worlds: []

broadcasts:
  enabled: true

protection:
  enabled: true
  require-permission: false
  # Leave empty or use ["*"] to allow protection in every AFK-enabled world.
  allowed-worlds: []

automatic-afk:
  enabled: false
  after-seconds: 300

display:
  tab-enabled: true
  nametag-enabled: true
  tab-format: "&7[AFK] %player%"
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

### AFK Worlds

`allowed-worlds` controls which worlds allow players to enter AFK mode.

```yml
allowed-worlds: []
```

An empty list or `["*"]` allows every world. To restrict AFK mode, list the permitted world names:

```yml
allowed-worlds:
  - world
  - world_nether
```

World-name matching is case-insensitive.

### Damage Protection

```yml
invincible-after-seconds: 10

protection:
  enabled: true
  require-permission: false
  allowed-worlds: []
```

`invincible-after-seconds` controls how long an eligible AFK player must remain AFK before becoming protected from damage. Set it to `0` for immediate protection.

The delay uses real elapsed time, so server lag or low TPS does not extend it proportionally.

`protection.allowed-worlds` is independent from the main `allowed-worlds` list. An empty list or `["*"]` allows protection anywhere AFK mode itself is allowed.

Set `protection.require-permission: true` if only selected players should receive AFK protection. Those players must then have:

```text
pinnacleafk.protection
```

### Automatic AFK

Automatic AFK detection is disabled by default.

```yml
automatic-afk:
  enabled: true
  after-seconds: 300
```

When enabled, players are automatically marked AFK after the configured period of inactivity. Movement, commands, chat, and normal gameplay actions reset the inactivity timer while the player is active.

### Toggle Cooldown

```yml
toggle-cooldown-seconds: 3
```

This prevents players from repeatedly toggling `/afk` in rapid succession. Set it to `0` to disable the cooldown.

### Broadcasts

```yml
broadcasts:
  enabled: true
```

Set this to `false` if you do not want server-wide messages when players enter or leave AFK.

### Tab and Name-Tag Displays

```yml
display:
  tab-enabled: true
  nametag-enabled: true
  tab-format: "&7[AFK] %player%"
  nametag-prefix: "&7[AFK] &f"
  nametag-suffix: ""
```

`tab-enabled` controls the AFK indicator in the player list. While AFK, the player's displayed tab component is recolored gray so colored rank/tab components do not remain visible in a different color.

`%player%` represents the player's existing tab-list component rather than only their raw username. This allows PinnacleAFK to work with rank and tab-list plugins while preserving their formatting for restoration afterward.

When AFK ends, players using normal scoreboard-team formatting return to their normal team prefix, suffix, and color rather than being left with a custom plain player-list name.

`nametag-enabled` controls the floating `[AFK]` marker above the player. PinnacleAFK does not move players into a temporary scoreboard team to create this marker, so existing team membership and team mechanics remain untouched.

### Configuration Reloads

After editing `config.yml`, use:

```text
/pafk reload
```

PinnacleAFK validates the configuration before applying it. Existing configuration files also receive newly added bundled settings when the plugin starts or reloads.

## Compatibility with Rank and Scoreboard Plugins

PinnacleAFK is designed to avoid taking ownership of a player's real scoreboard team. This means AFK mode should not alter:

- Rank prefixes or suffixes
- Team colors
- Friendly-fire settings
- Collision settings
- Team selectors
- Other mechanics tied to scoreboard membership

If another plugin changes a player's tab-list component while that player is AFK, PinnacleAFK can reconcile the updated component while keeping the AFK display gray. When AFK ends, the appropriate external or normal team-rendered value is restored.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for release history.

## License

PinnacleAFK is licensed under the [MIT License](LICENSE).
