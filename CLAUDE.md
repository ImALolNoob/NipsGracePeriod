# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Hardcore Grace Period** is a Paper plugin for Minecraft 26.3 hardcore servers. Each player gets a real-time grace period (default 3 hours) from their first join after the event starts. While it lasts, lethal damage is cancelled and the player is sent back to their spawn instead of dying.

**Stack**: Java 25, Gradle, Paper API 26.3. Design reference: `PAPER_PLUGIN_SPEC.md`.

## Common Commands

```bash
./gradlew build    # build/libs/hardcore-grace-period-1.0-SNAPSHOT.jar
./gradlew clean
```

## Architecture

- **`Hardcore_grace_period`**: plugin entry point. Creates the manager, registers the command and listener; saves data on disable.
- **`GracePeriodManager`**: all grace state.
  - `graceEnds`: per-player grace **end time** in epoch milliseconds (real time; keeps running offline and across restarts). Remaining = `end - now`.
  - A player gets `now + configured duration` the first time they're seen after the event starts (`initializePlayer`, called on join and at event start).
  - `tick()` runs **once a second**: updates boss bars and announces expiry (title + broadcast) for players online when their grace ends. `protectedOnline` makes sure expiry is announced once.
  - `hasGracePeriod(uuid)` is true for everyone before the event starts.
  - `startEvent()` heals online players, sets time to day and resets the overworld border to the vanilla default.
  - Data is saved when it changes (join, set, reset) and on disable, not every tick.
- **`GracePeriodCommands`**: `/graceperiod start|reset|check|set|resetplayer`. Tracks countdown tasks so `start` can't be run twice and `reset` cancels a running countdown. `check`/`set`/`resetplayer` accept offline players via `Bukkit.getOfflinePlayerIfCached`.
- **`PlayerListener`**:
  - `EntityDamageEvent` (HIGHEST): if the hit would be lethal and the player has grace, cancel it and run the grace save (drop items/XP, clear effects, fire, fall distance and freezing, teleport, heal). If they hold a Totem of Undying and the damage isn't `bypasses_invulnerability`, vanilla handles it instead.
  - `PlayerDeathEvent` + `PlayerRespawnEvent`: fallback for deaths that skip the damage event.
  - Respawn location: the player's vanilla respawn point (bed/anchor) if safe, else the closest safe spot within 3 blocks, else the overworld spawn, else the highest block at spawn. "Safe" = solid, non-harmful ground with air (any air, including cave air) at feet and head.
- **`ModConfig`**: `config.json` (Gson). Duration helpers in milliseconds.
- **`GracePeriodData`**: `grace_period_data.json` with `eventStarted` and `players.<uuid>.graceEndMillis`. Old files with `remainingTime` (ticks) are migrated on load.
- **`JsonFiles`**: atomic writes (temp file + move) and backup/warning for unreadable JSON files.

## Development Notes

- All logic runs on the main server thread (Bukkit scheduler); no concurrency handling needed.
- The overworld is assumed to be named `world`.
- Config is read once on enable; restart to apply changes.
