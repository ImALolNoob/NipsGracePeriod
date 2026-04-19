# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Hardcore Grace Period** is a Fabric server-side mod for Minecraft 1.21.10 that provides configurable grace period protection for hardcore servers. Players within the grace period cannot die and are healed instead, with visual indicators (boss bar) showing remaining time.

**Stack**: Java 21, Fabric Loader 0.17.3, Gradle, Minecraft 1.21.10, Fabric API 0.136.0+1.21.10

## Common Commands

### Building
```bash
./gradlew build
```
Compiles the mod into `build/libs/hardcore-grace-period-1.0-SNAPSHOT.jar`

### Running/Testing
```bash
./gradlew runServer
```
Runs a test server with the mod loaded (Fabric dev environment).

### Cleaning
```bash
./gradlew clean
```
Clears build artifacts.

## Architecture

### Core Components

1. **GracePeriodManager** (`GracePeriodManager.java`): Singleton that manages all grace period state
   - Tracks per-player grace period timers (in ticks, 20 ticks = 1 second)
   - Manages ServerBossBar instances for visual feedback
   - Handles event start/reset and player initialization
   - Persists state via `GracePeriodData` (JSON-based)
   - Ticks every game tick to decrement timers and update displays
   - Sets world border to 30 million blocks on event start

2. **Command System** (`GracePeriodCommands.java`): Registers `/graceperiod` command with subcommands
   - `start`: Initiates 10-second countdown, then starts grace period event (heals all online players, sets time to day)
   - `reset`: Clears event and all player data
   - `check <player>`: Shows remaining grace time
   - `set <player> <hours> [minutes]`: Manually set grace time
   - `resetplayer <player>`: Reset player to default grace period
   - All commands require OP level 2
   - Uses custom task scheduling system (`scheduleTask`/`scheduleRepeatingCheck`) for countdown timers

3. **Event System**:
   - `ServerEventHandler.java`: Registers tick and player join/leave callbacks
   - `PlayerDeathHandler.java`: Uses `AFTER_RESPAWN` event to restore players who died with grace period
   - `ServerEventHandler` calls `GracePeriodManager.tick()` on server tick
   - Sends periodic network updates to clients every 100 ticks (5 seconds)

4. **Configuration** (`ModConfig.java`): JSON-based config at `config/hardcore-grace-period.json`
   - `gracePeriodHours` / `gracePeriodMinutes`: Default duration (combined into ticks)
   - `enableBossBar`: Toggle boss bar display
   - `enableOverheadDisplay`: Toggle overhead text (currently disabled due to API removal)
   - `debugMode`: Enables debug logging

5. **Networking** (`NetworkHandler.java`): Registers custom network packets for client-server sync
   - Uses `GracePeriodUpdatePayload` record to send Map<UUID, Long> of player times
   - Overhead rendering disabled in 1.21.10 due to `WorldRenderEvents.AFTER_ENTITIES` API removal

6. **Mixins**: Uses Fabric Mixin system to hook into Minecraft classes
   - `ServerPlayerEntityMixin`: Main mixin for server player functionality
   - `ServerPlayerEntityAccessor`: Accessor mixin to expose server instance reference

7. **Data Persistence** (`GracePeriodData.java`): Saves/loads event and player state as JSON
   - Stored at `config/grace_period_data.json`
   - Contains `EventData` with event state and per-player `PlayerData`

## Key Behaviors

- **Timer Format**: Converted from hours/minutes to ticks (1 second = 20 ticks) for Minecraft compatibility
- **Grace Check**: `hasGracePeriod(UUID)` returns true if timer > 0 OR event hasn't started yet (all players protected before event)
- **Event Start Sequence**:
  1. 10-second countdown with titles and sounds
  2. Heals all players (full health, hunger, saturation)
  3. Gives Regeneration X and Saturation X effects for 10 seconds (200 ticks)
  4. Sets world time to 0 (day)
  5. Sets world border to 30,000,000 blocks
- **Boss Bar Colors**: Green (>50% time) → Yellow (25-50%) → Red (<25%)
- **Online-Only**: Grace period only decrements while player is online (ticked only for online players in `GracePeriodManager.tick()`)
- **Late Joiners**: New players joining after event starts get full grace period duration
- **Data Saving**: Saves every 20 ticks (1 second) if data changed, or immediately on manual changes
- **Death Handling**: Uses `AFTER_RESPAWN` event to detect death and restore players with grace period (heals, clears effects, broadcasts message)
- **Overhead Display**: Currently disabled in `OverheadRenderer.java` due to Fabric API changes in 1.21.9+

## Package Structure

```
src/
├── main/java/org/novato/hardcore_grace_period/
│   ├── Hardcore_grace_period.java          (ModInitializer entry point)
│   ├── GracePeriodManager.java             (Core logic singleton)
│   ├── commands/GracePeriodCommands.java   (Command registration)
│   ├── config/ModConfig.java               (Config file handling)
│   ├── data/GracePeriodData.java           (Persistence)
│   ├── events/                             (Event handlers)
│   ├── mixin/                              (Fabric Mixins)
│   └── network/NetworkHandler.java         (Network sync)
├── client/java/org/novato/hardcore_grace_period/
│   ├── OverheadRenderer.java               (Overhead rendering - currently disabled)
│   └── Hardcore_grace_periodClient.java    (Client entry point)
└── resources/
    ├── fabric.mod.json                     (Mod metadata)
    └── hardcore_grace_period.mixins.json   (Mixin config)
```

## Development Notes

- **Singleton Access**: Use `GracePeriodManager.getInstance()` throughout the codebase
- **Server Reference**: Manager requires server reference via `setServer()` for world/player access (set in `ServerEventHandler.onServerStarted`)
- **Tick Loop**: Server ticks happen ~20 times per second (1 tick = 50ms)
- **Time Format**: Always work with ticks internally; convert from hours/minutes only at config load time
- **Thread Safety**: Fabric tick callbacks are on server thread, no concurrency issues expected
- **Config Changes**: Config is loaded once on mod init; changing the JSON file requires server restart
- **Task Scheduling**: Custom task scheduling uses server's execute() method with recursive checks against target tick time (see `scheduleTask`/`scheduleRepeatingCheck` in GracePeriodCommands)
- **API Compatibility**: Overhead rendering disabled due to `WorldRenderEvents.AFTER_ENTITIES` removal in Minecraft 1.21.9+ (awaiting Fabric API replacement)
