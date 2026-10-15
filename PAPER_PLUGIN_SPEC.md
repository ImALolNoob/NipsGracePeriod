# Hardcore Grace Period — Paper Plugin Spec

## Overview

A server-side Paper plugin for hardcore Minecraft servers. Each player gets a grace period that starts the first time they join after the event begins. While it lasts they cannot die: instead their inventory and XP are dropped at the death location, they are teleported to their spawn point, and they are fully healed. A boss bar shows each player their remaining grace time.

The event is designed to run for about a month, with players joining at any point. Every player gets the same grace duration from their own first join.

---

## Core Concepts

### Event State
The plugin has two phases:

- **Pre-event** (event not started): every player is grace-protected. No boss bars are shown. This is the default state on a fresh install. Servers typically keep a small lobby world border during this phase.
- **Active event** (event started): each player has a personal grace **end time**. Protection ends when that time passes.

### Grace Timing
- Grace is **real time**: a fixed window of the configured duration, starting at the player's first join after the event starts (or at event start, for players online then).
- The clock keeps running while the player is offline and while the server is down.
- Stored per player UUID as the end time in epoch milliseconds. Remaining time = `end - now`.
- Saved to disk whenever it changes (new player, admin command, reset) and on plugin disable.

---

## Configuration (`config.json`)

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

- `gracePeriodHours` + `gracePeriodMinutes` together define each player's grace duration.
- Read once on startup. Changing it requires a server restart. Settings missing from an existing file are added with their defaults.
- If the file can't be read, a copy is saved as `config.json.broken-<timestamp>`, a warning is logged, and defaults are used.

---

## Data Persistence (`grace_period_data.json`)

Stored in the plugin's data folder. Structure:

```json
{
  "eventStarted": true,
  "players": {
    "<uuid>": {
      "graceEndMillis": 1791762718827
    }
  }
}
```

- Loaded on plugin enable. Older files that stored `remainingTime` in ticks are converted to an end time counted from load.
- Written atomically (temp file, then move) so a crash can't leave a half-written file.
- If the file can't be read, a copy is saved as `grace_period_data.json.broken-<timestamp>`, a warning is logged, and the plugin starts with empty data instead of failing to enable.
- Deleted entirely on `/graceperiod reset`.

---

## Death Interception

This is the most critical behaviour. Paper makes it straightforward with events; no mixins required.

### How it works
Listen to `EntityDamageEvent`. When the event fires for a player, check whether the damage would be lethal (`current health - final damage <= 0`) and whether the player has grace. If both are true:

1. If the player holds a Totem of Undying in either hand and the damage type is not tagged `bypasses_invulnerability` (void, `/kill`), do nothing and let vanilla use the totem.
2. Otherwise cancel the damage event (player's health stays above 0) and perform the "grace death" sequence:
   - Drop all inventory items at the player's current location.
   - Drop XP orbs at the player's current location (capped at 100 XP).
   - Clear the player's inventory and all active potion effects.
   - Clear fire ticks, fall distance and freeze ticks.
   - Teleport the player to their respawn location (see Respawn Location Logic).
   - Restore full health (max health), hunger (20) and saturation (20).
   - Broadcast to all players: `"<name> was saved by the grace period!"` in yellow.

A `PlayerDeathEvent` + `PlayerRespawnEvent` fallback handles deaths that skip the damage event. It suppresses the death message, auto-respawns the player at the same respawn location, and restores health on the next tick.

### Permanent Deaths
When a player with no grace left dies (`PlayerDeathEvent`, not cancelled), every online player sees a title and hears a sound:
- Title `deathTitle`, subtitle `deathSubtitle` (`%player%` = name, `§` colour codes), with `deathTitleFadeIn` / `deathTitleStay` / `deathTitleFadeOut` in ticks.
- `deathSound` at `deathSoundVolume` / `deathSoundPitch`, played at each listener's own position; empty for none.
- Turned off with `deathTitleEnabled: false`. Grace saves never trigger it.

### Respawn Location Logic
1. **Bed / Respawn Anchor**: the player's vanilla respawn location (`Player#getRespawnLocation()`), if they have a valid one.
2. **World spawn**: otherwise the overworld spawn point.

For the chosen point, use it as-is if it's safe; otherwise use the closest safe spot within 3 blocks horizontally and 2 vertically. If neither point has a safe spot, use the highest block at world spawn.

A position is safe when the block below is solid and not harmful (magma, campfire, soul campfire, cactus), and the blocks at feet and head level are air (`Material#isAir()`, which includes cave air).

---

## Boss Bar

- One boss bar per player, visible only to that player.
- Title format: `"Grace Period: Xh Ym Zs"`; omit the hours segment if 0, and the minutes segment if 0.
- Progress: `remaining / configured duration`, clamped to [0.0, 1.0] (a player given more than the default shows a full bar).
- Color thresholds:
  - **Green** when progress > 50%
  - **Yellow** when progress is 25–50%
  - **Red** when progress < 25%
- Shown only while the player still has grace left: created on event start or join, removed when grace ends, on quit, and on `/graceperiod reset`.
- Can be turned off with `enableBossBar: false`.

### On Grace Expiry
When a player's grace ends while they're online (whether or not boss bars are enabled):
1. Remove their boss bar.
2. Send them a title `"Grace Period Expired!"` (red, bold) with subtitle `"You are no longer protected from death"` (yellow).
3. Broadcast to all players: `"<name>'s grace period has ended!"` in red.

Players whose grace ends while they're offline are not notified.

---

## Commands

All subcommands require permission node `graceperiod.admin` (default: OP).

### `/graceperiod start`
- Errors if the event is already running: `"Grace period event is already running!"`
- Errors if a countdown is already running: `"The countdown is already running!"`
- Begins a 10-second countdown:
  - Broadcasts `"Grace Period Starting in 10 seconds..."` (bold gold) to chat.
  - Each second shows a title with the countdown number (yellow, bold) to all players and plays a note block hat sound.
  - At 10 seconds, fires the event start sequence.
- **Event start sequence:**
  1. Set `eventStarted = true`.
  2. Set overworld time to day (0).
  3. For every online player: give them their grace window and boss bar, heal them (max health, hunger 20, saturation 20), and apply Regeneration X + Saturation X for 10 seconds (200 ticks).
  4. Reset the overworld world border to the vanilla default.
  5. Broadcast `"Grace Period Event Started! You have <duration> of protection."` (green, bold).
  6. Save data to disk.
- Sends executor feedback: `"Starting grace period countdown..."` in yellow.

### `/graceperiod reset`
- Cancels a running countdown.
- Sets `eventStarted = false` and clears all stored grace end times.
- Removes all boss bars.
- Deletes the data file.
- Sends feedback: `"Grace period event reset!"` in yellow.

### `/graceperiod check <player>`
- Works for online players and offline players who have joined the server before.
- Displays `"<name> has <time> remaining"`, `"<name>'s grace period has ended"`, or (during the event) that the player hasn't joined since it started.
- Before the event starts, shows the configured duration.

### `/graceperiod set <player> <hours> [minutes]`
- Works for online and offline players (who have joined before).
- `hours`: integer 0–24. `minutes`: optional integer 0–59, defaults to 0. Anything else is rejected with a message.
- Sets the player's grace to end that long **from now** (0 ends it immediately).
- Feedback: `"Set <name>'s grace period to Xh Ym from now"` in green.

### `/graceperiod resetplayer <player>`
- Works for online and offline players (who have joined before).
- Gives the player the configured duration again, starting now.
- Feedback: `"Reset <name>'s grace period to <duration> from now"` in green.

---

## Player Lifecycle

### On Player Join
- If the event has started and the player has no stored end time: set it to now + configured duration and save.
- If they still have grace left, show their boss bar.

### On Player Quit
- Remove the player's boss bar.
- Keep their end time; their grace keeps running while they're offline.

---

## Tick Loop

A repeating Bukkit task runs once a second (every 20 ticks). On each run:

1. Skip if the event has not started.
2. For each online player with a stored end time:
   - If they still have grace left: update their boss bar progress, colour and title.
   - If their grace has just run out: trigger the expiry sequence (once).

---

## Time Formatting

Consistent format used in the boss bar, command feedback and broadcasts:

```
hours > 0:    "Xh Ym Zs"
minutes > 0:  "Ym Zs"
else:         "Zs"
```

---

## Plugin Lifecycle

- **`onEnable`**: load config → load data file (restore event state and all end times) → register listeners → register commands → start the once-a-second task.
- **`onDisable`**: save data immediately.

---

## Differences from the Fabric Mod

| Fabric | Paper |
|--------|-------|
| `ServerPlayerEntityMixin` intercepts `onDeath()` | `EntityDamageEvent` cancelled before death occurs |
| `AFTER_RESPAWN` fallback handler | `PlayerDeathEvent` + `PlayerRespawnEvent` fallback |
| Timer counted down only while online | Real-time end time, keeps running offline |
| Custom network packets sync timers to clients | Not needed: boss bar is managed server-side by Paper |
| `OverheadRenderer` (client-side, disabled) | Dropped entirely |
| `enableOverheadDisplay` config option | Dropped |
| Custom tick scheduler using `server.execute()` recursion | Standard `BukkitScheduler.runTaskTimer()` |
