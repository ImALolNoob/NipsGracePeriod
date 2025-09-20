# Hardcore Grace Period Mod

A Fabric server-side mod for Minecraft 1.21.8 that provides a configurable grace period for hardcore servers.

## Features

- **Configurable Grace Period**: Default 3 hours, customizable in config
- **Visual Indicators**: Boss bar and overhead displays showing remaining time
- **Death Protection**: Players with grace period are healed instead of dying
- **Synchronized Events**: All players start with same grace period when event begins
- **Late Joiner Support**: New players get full grace period time
- **Online-Only Timer**: Grace period only counts down while players are online
- **Data Persistence**: Saves state across server restarts

## Commands

All commands require OP level 2 (admin permissions):

- `/graceperiod start` - Start the grace period event (heals all players, sets time to day)
- `/graceperiod reset` - Reset the entire event and clear all data
- `/graceperiod check <player>` - Check a specific player's remaining grace time
- `/graceperiod set <player> <hours>` - Set a player's grace period to specific hours
- `/graceperiod resetplayer <player>` - Reset a player's time back to default

## Configuration

The mod creates a config file at `config/hardcore-grace-period.json`:

```json
{
  "gracePeriodHours": 3,
  "enableBossBar": true,
  "enableOverheadDisplay": true,
  "debugMode": false
}
```

## How It Works

### Before Event Starts
- Players can die normally and will respawn
- No grace period protection active

### During Event
1. Use `/graceperiod start` to begin the event
2. All online players are healed and get full grace period
3. World time is set to day (0 ticks)
4. Players see their remaining time in boss bar at top of screen
5. Other players can see grace time above player heads
6. If a player would die, they are healed instead and a message is broadcast
7. Timer only counts down while player is online

### Visual Feedback
- **Boss Bar Colors**: Green → Yellow → Red as time decreases
- **Overhead Display**: Shows "Grace: Xh Ym" above players with remaining time
- **Chat Messages**: Broadcasts when players are saved by grace period

## Installation

1. Install Fabric Loader for Minecraft 1.21.8
2. Install Fabric API
3. Place the mod JAR in your server's `mods` folder
4. Start the server

## Build Output

The compiled mod can be found at:
`build/libs/hardcore-grace-period-1.0-SNAPSHOT.jar`

## Development

Built with:
- Minecraft 1.21.8
- Fabric Loader 0.17.2
- Fabric API 0.133.4+1.21.8
- Java 21