# Hardcore Grace Period

A Paper plugin for Minecraft 26.3 hardcore servers. Every player gets a grace period (default 3 hours) that starts the first time they join after the event begins. While it lasts they can't die: a lethal hit drops their items and XP, sends them back to their bed or spawn, and heals them.

## Features

- **Real-time grace**: each player's grace runs for a fixed time from their first join, and keeps running while they're offline. Late joiners get the same amount.
- **Boss bar** showing each player their remaining time (green → yellow → red).
- **Death protection**: lethal damage is cancelled; items and XP drop where they "died" and the player is sent to their bed/anchor or the world spawn.
- **Totems still work**: a held Totem of Undying saves the player the vanilla way (except against void and `/kill`).
- **Event start**: 10-second countdown, everyone healed, time set to day, world border reset to the vanilla default (so a small lobby border can be used before the event).
- **Death title**: when a player dies for good (no grace left), everyone sees a title (default "☠ <name> / has been lost to the 7 Seas") and hears a quiet sound. Grace saves never trigger it.
- **Safe files**: data is written atomically; an unreadable config or data file is backed up and replaced with defaults instead of disabling the plugin.

## Commands

All need the `graceperiod.admin` permission (default: op).

| Command | Does |
|---|---|
| `/graceperiod start` | Starts the 10-second countdown, then the event |
| `/graceperiod reset` | Cancels a running countdown, ends the event and clears all player data |
| `/graceperiod check <player>` | Shows a player's remaining grace (online or offline) |
| `/graceperiod set <player> <hours 0-24> [minutes 0-59]` | Sets a player's grace to end that long from now |
| `/graceperiod resetplayer <player>` | Gives a player the default grace again, starting now |

`check`, `set` and `resetplayer` work for offline players who have joined the server before.

## Configuration

`plugins/HardcoreGracePeriod/config.json`:

```json
{
  "gracePeriodHours": 3,
  "gracePeriodMinutes": 0,
  "enableBossBar": true,
  "debugMode": false,
  "deathTitleEnabled": true,
  "deathTitle": "§4☠ §6%player%",
  "deathSubtitle": "§7§ohas been lost to the 7 Seas",
  "deathTitleFadeIn": 20,
  "deathTitleStay": 80,
  "deathTitleFadeOut": 30,
  "deathSound": "minecraft:block.conduit.deactivate",
  "deathSoundVolume": 0.6,
  "deathSoundPitch": 1.0
}
```

`%player%` is the dead player's name, colours use `§` codes, and the title times are in ticks (20 = 1 second). Set `deathSound` to `""` for no sound. New settings are added to an existing `config.json` on startup.

Read once at startup; restart to apply changes.

## Building

```bash
./gradlew build
```

Output: `build/libs/hardcore-grace-period-1.0-SNAPSHOT.jar`. Needs Java 25. Built against `paper-api` 26.3.
