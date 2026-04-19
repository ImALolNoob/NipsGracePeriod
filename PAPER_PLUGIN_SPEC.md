# Hardcore Grace Period — Paper Plugin Spec

## Overview

A server-side Paper plugin for hardcore Minecraft servers. Players within their grace period cannot die. Instead of dying, their inventory and XP are dropped at the death location, they are teleported to their spawn point, and they are fully healed. A boss bar shows each player their remaining grace time. The grace period only counts down while a player is online.

---

## Core Concepts

### Event State
The plugin has two phases:

- **Pre-event** (event not started): Every player is considered grace-protected regardless of any stored timer. No boss bars are shown yet. This is the default state on a fresh install.
- **Active event** (event started): Each player has an individual timer in ticks. The timer counts down only while the player is online. When the timer hits 0, protection ends for that player.

### Timer Storage
- Stored as ticks (20 ticks = 1 second) per player UUID.
- Loaded from disk on startup, saved every second (20 ticks) if any timers changed, and saved immediately on any manual change.
- Timers are initialized from the config default when a player is first seen after the event starts.

---

## Configuration (`config.yml`)

```yaml
grace-period-hours: 3
grace-period-minutes: 0
enable-boss-bar: true
debug-mode: false
```

- `grace-period-hours` + `grace-period-minutes` together define the default duration for each player.
- Config is read once on startup. Changing it requires a server restart.

---

## Data Persistence (`grace_period_data.json`)

Stored in the plugin's data folder. Structure:

```json
{
  "eventStarted": true,
  "eventStartTime": 12345678,
  "players": {
    "<uuid>": {
      "remainingTicks": 216000
    }
  }
}
```

- Loaded on plugin enable.
- Saved every 20 ticks if any timer changed, and on plugin disable/server shutdown.
- Deleted entirely on `/graceperiod reset`.

---

## Death Interception

This is the most critical behavior. Paper makes this straightforward with events — no mixins required.

### How it works
Listen to `EntityDamageEvent`. When the event fires for a player, check if the damage would be lethal (`current health - final damage <= 0`) and if the player has grace. If both are true:

1. Cancel the damage event (player's health stays above 0).
2. On the next tick (1-tick delayed task), perform the "grace death" sequence:
   - Drop all inventory items at the player's current location.
   - Drop XP orbs at the player's current location (cap at 100 XP orbs worth).
   - Clear the player's inventory and all active potion effects.
   - Teleport the player to their respawn location (see Respawn Location Logic).
   - Restore full health (max health), hunger (20), and saturation (20).
   - Broadcast to all players: `"<name> was saved by the grace period!"` in yellow.

> Cancelling `EntityDamageEvent` before death avoids the death screen entirely and does not need to fight Minecraft's respawn flow. Do not use `PlayerDeathEvent`.

### Respawn Location Logic
Priority order:
1. **Bed / Respawn Anchor**: If the player has a set spawn point, verify the block is still a valid bed (overworld only) or a charged respawn anchor (nether only, charges > 0). If valid, use that location and dimension.
2. **World spawn**: Fall back to the overworld spawn point.

After determining the target XZ and dimension, find a safe Y position:
- Search from Y=120 down to Y=64 at the target XZ.
- A position is safe when: the block below is solid and not lava/water/fire (has collision shape), and the 2 blocks at feet and head level are air.
- If no safe position is found in that range, fall back to Y=80.
- Teleport to the center of the block (X+0.5, Z+0.5).

---

## Boss Bar

- One boss bar per player, visible only to that player.
- Title format: `"Grace Period: Xh Ym Zs"` — omit the hours segment if 0, omit minutes if 0.
- Progress: `remaining / configDefaultTicks`, clamped to [0.0, 1.0].
- Color thresholds:
  - **Green** when progress > 50%
  - **Yellow** when progress is 25–50%
  - **Red** when progress < 25%
- Created when the player's timer is initialized (on event start, or on join if the event is already running).
- Removed and destroyed when the timer hits 0 or on `/graceperiod reset`.
- Hidden on player disconnect, recreated on reconnect.

### On Timer Expiry
When a player's timer reaches 0:
1. Remove and destroy their boss bar.
2. Send them a title `"Grace Period Expired!"` (red, bold) with subtitle `"You are no longer protected from death"` (yellow).
3. Broadcast to all players: `"<name>'s grace period has ended!"` in red.

---

## Commands

All subcommands require permission node `graceperiod.admin` (default: OP).

### `/graceperiod start`
- Errors if the event is already running: `"Grace period event is already running!"`
- Begins a 10-second countdown:
  - Broadcasts `"Grace Period Starting in 10 seconds..."` (bold gold) to chat.
  - Each second shows a title with the countdown number (yellow, bold) to all players and plays a note block hat sound.
  - At 10 seconds, fires the event start sequence.
- **Event start sequence:**
  1. Set `eventStarted = true`, record `eventStartTime`.
  2. Set overworld time to day (0).
  3. For every online player: initialize their timer and boss bar, then heal them (max health, hunger 20, saturation 20), and apply Regeneration X + Saturation X for 10 seconds (200 ticks).
  4. Set world border to 30,000,000 blocks.
  5. Broadcast `"Grace Period Event Started! You have <duration> of protection."` (green, bold).
  6. Save data to disk.
- Sends executor feedback: `"Starting grace period countdown..."` in yellow.

### `/graceperiod reset`
- Sets `eventStarted = false`, clears `eventStartTime`.
- Clears all stored player timers.
- Removes and destroys all boss bars.
- Deletes the data file.
- Sends feedback: `"Grace period event reset!"` in yellow.

### `/graceperiod check <player>`
- Target player must be online.
- Displays `"<name> has <time> remaining"` in aqua to the command sender.
- If the event hasn't started, shows the config default duration.

### `/graceperiod set <player> <hours> [minutes]`
- Target player must be online.
- `hours`: integer 0–24. `minutes`: optional integer 0–59, defaults to 0.
- Converts to ticks and updates the player's stored timer immediately.
- Feedback: `"Set <name>'s grace period to Xh Ym"` in green.

### `/graceperiod resetplayer <player>`
- Target player must be online.
- Resets that player's timer to the config default ticks.
- Feedback: `"Reset <name>'s grace period to <formatted duration>"` in green.

---

## Player Lifecycle

### On Player Join
- If the event has started and the player has no stored timer: set their timer to the config default and save. Then create and show their boss bar.
- If the event has started and the player already has a stored timer: just recreate their boss bar (timer was persisted from last session).

### On Player Quit
- Hide and remove the player's boss bar reference.
- Do not delete their timer — it persists and resumes on reconnect.

---

## Tick Loop

Register a repeating BukkitTask at period 1 tick. On each tick:

1. Skip if the event has not started.
2. For each online player with a stored timer:
   - If `remaining > 0`: decrement by 1, mark data dirty, update boss bar progress/color/title.
   - If `remaining` just reached 0: trigger the expiry sequence (remove boss bar, send title, broadcast).
3. Every 20 ticks: if data is dirty, save to disk and clear the dirty flag.

---

## Time Formatting

Consistent format used in boss bar, command feedback, and broadcasts:

```
hours > 0:    "Xh Ym Zs"
minutes > 0:  "Ym Zs"
else:         "Zs"
```

---

## Plugin Lifecycle

- **`onEnable`**: Load config → load data file (restore event state and all player timers) → register event listeners → register commands → start tick scheduler.
- **`onDisable`**: Force-save data immediately to flush any unsaved timer changes before shutdown.

---

## Differences from the Fabric Mod

| Fabric | Paper |
|--------|-------|
| `ServerPlayerEntityMixin` intercepts `onDeath()` | `EntityDamageEvent` cancelled before death occurs |
| `AFTER_RESPAWN` fallback handler | Not needed — damage is cancelled before any death/respawn flow |
| Custom network packets sync timers to clients | Not needed — boss bar is managed server-side by Paper |
| `OverheadRenderer` (client-side, disabled) | Dropped entirely |
| `enableOverheadDisplay` config option | Dropped |
| Custom tick scheduler using `server.execute()` recursion | Standard `BukkitScheduler.runTaskTimer()` |
