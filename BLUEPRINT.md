# Hardcore Grace Period — Blueprint

A single reference for what this plugin does, how every piece works, and where to go to
change any given behavior. For the short version aimed at AI assistants see `CLAUDE.md`;
for the original Paper migration notes see `PAPER_PLUGIN_SPEC.md`.

---

## 1. What it does

A Paper plugin for hardcore Minecraft servers. When the admin starts the event, every
player gets a **grace period** (default 3 hours). While a player's grace lasts, lethal
damage is cancelled and they are sent back to spawn instead of dying permanently. Once
grace runs out, the server behaves like normal hardcore: the next lethal hit is a real,
permanent death, announced to everyone with a configurable title.

The defining rule: **grace counts down only while the player is online.** Logging off
freezes the clock; so does a server restart. Two players who both start with 3 hours burn
that budget at the rate of their own playtime, not wall-clock time.

---

## 2. Core concept — the online-only budget

This is the one idea the whole plugin is built around. Understand this and the rest falls
into place.

Each player has a **budget** of remaining grace, in milliseconds (`graceRemaining`). For a
player who is currently **online**, we also store the epoch-millis moment their countdown
last resumed (`onlineSince`). The live remaining time is therefore:

```
liveRemaining = graceRemaining - (now - onlineSince)   // online
liveRemaining = graceRemaining                          // offline (clock frozen)
```

- **`graceRemaining`** is persisted; it is the source of truth.
- **`onlineSince`** exists only in memory for online players and is **never saved**.
- **`commit(uuid)`** folds the elapsed online time back into the budget
  (`graceRemaining -= now - onlineSince`, clamped at 0) and restarts `onlineSince` from
  now. It is a no-op for offline players (they have no `onlineSince`). Every read-modify
  path (`tick`, quit, save, `add`/`set`) commits first so it works from a fresh budget.

Because offline time is never charged and `onlineSince` is thrown away on quit / restart,
the clock is automatically frozen whenever the player isn't actively online.

All of this lives in `GracePeriodManager`.

---

## 3. Stack & layout

- **Java 25**, **Gradle**, **Paper API 26.3** (`io.papermc.paper:paper-api`, `compileOnly`).
- Build output: `build/libs/hardcore-grace-period-1.0-SNAPSHOT.jar`.
- Package root: `org.novato.hardcore_grace_period`.

```
src/main/java/org/novato/hardcore_grace_period/
├─ Hardcore_grace_period.java      entry point (onEnable/onDisable, wiring)
├─ GracePeriodManager.java         ALL grace state + the budget math
├─ listeners/PlayerListener.java   damage/death/respawn/join/quit + the tick scheduler
├─ commands/GracePeriodCommands.java  /graceperiod subcommands + tab-complete
├─ DeathAnnouncer.java             title + sound on a permanent death
├─ config/ModConfig.java           config.json (Gson)
└─ data/
   ├─ GracePeriodData.java         grace_period_data.json + old-format migration
   └─ JsonFiles.java               atomic writes + backup of unreadable JSON
src/main/resources/plugin.yml      name, command, permission (graceperiod.admin, default op)
```

---

## 4. Class-by-class

### `Hardcore_grace_period` (entry point)
`onEnable`: stores a static `instance`, builds the `GracePeriodManager`, registers the
`graceperiod` command executor + tab completer, and registers the `PlayerListener`.
`onDisable`: `manager.saveData()`. That's it — thin by design.

### `GracePeriodManager` (the brain)
Owns every piece of grace state:

| Field | Meaning | Persisted? |
|---|---|---|
| `graceRemaining` | per-UUID budget in ms | **yes** |
| `onlineSince` | per-UUID resume instant, online players only | no |
| `playerBossBars` | per-UUID boss bar | no |
| `protectedOnline` | players whose grace was running at the last check (so expiry is announced exactly once) | no |
| `eventStarted` | has the admin started the event | **yes** |

Key methods:
- `startEvent()` — flips `eventStarted`, sets overworld to day, resets the world border to
  vanilla, and for every online player: `initializePlayer`, heal, and a short
  Regeneration + Saturation burst. Broadcasts the start message. Saves.
- `initializePlayer(player)` — gives the configured default **the first time** a UUID is
  seen after the event starts (`if (!graceRemaining.containsKey(uuid))`), then sets
  `onlineSince = now` to resume the clock and refreshes the boss bar. Called on join and
  at event start.
- `tick()` — see §6. Runs once a second.
- `hasGracePeriod(uuid)` — **true for everyone before the event starts**; afterwards true
  iff live remaining > 0. This is the gate the listener checks.
- `getRemainingMillis(uuid)` — live remaining (full default before the event starts).
- `commit(uuid)` — the fold described in §2.
- `setPlayerTime` / `resetPlayerTime` / `addPlayerTime` / `addTimeToOnlinePlayers` — admin
  mutations; all commit first, clamp at 0, save, and refresh an online target's boss bar.
- `removePlayer(player)` — on quit: commit, drop `onlineSince`/`protectedOnline`, hide
  boss bar, save.
- `resetEvent()` — clears all state and deletes the data file.

### `PlayerListener`
Registers the **1-second tick** (`runTaskTimer(plugin, manager::tick, 0L, 20L)`) in its
constructor, and handles join, quit, damage, death, and respawn. See §6 and §7.

### `GracePeriodCommands`
Implements `/graceperiod` and its tab completion. Tracks scheduled countdown tasks
(`countdownTasks`) so `start` can't run twice and `reset` can cancel a running countdown.
Offline-capable subcommands resolve the target through `findPlayer` (see §9). See §8.

### `DeathAnnouncer`
On a **permanent** death only, shows everyone a title and plays a sound, built from the
`death*` config settings. Supports `%player%` and legacy `§` color codes.

### `ModConfig`
Plain Gson-serialized settings object. `load` re-saves after reading so newly added fields
appear in an existing `config.json`. Helpers convert hours/minutes to milliseconds and to a
human string.

### `GracePeriodData` / `JsonFiles`
Persistence. `GracePeriodData` reads/writes `grace_period_data.json` and migrates old
formats on load (§10). `JsonFiles` does atomic temp-file-then-move writes and backs up any
JSON file it can't parse, so a corrupt file never crashes startup.

---

## 5. Lifecycle walkthrough

**Server start / plugin enable.** Manager loads config + data; `eventStarted` and all
budgets are restored. No `onlineSince` yet, so every clock is frozen until players join.

**Admin runs `/graceperiod start`.** 10-second titled countdown, then `startEvent()`:
day time, border reset, every online player initialized + healed.

**Player joins (event running).** `initializePlayer` gives them the default if they're new,
otherwise keeps their saved budget, and resumes their clock from now.

**Each second.** `tick()` commits elapsed time for every online player, updates boss bars,
and announces expiry once per player (§6).

**Player quits.** `removePlayer` commits the elapsed time into the saved budget and drops
the in-memory clock, freezing them.

**Server stop / plugin disable.** `saveData()` commits everyone and writes the file.

Net effect: budgets only ever shrink by real online seconds, and survive restarts intact.

---

## 6. The tick

`GracePeriodManager.tick()`, once a second, for each online player with a budget:
1. `commit(uuid)` — fold the last second (and anything since the last tick) into the budget.
2. If remaining > 0: mark `protectedOnline`, update the boss bar (color goes
   green → yellow → red as it drains; progress is relative to the configured default).
3. If remaining hit 0 **and** they were in `protectedOnline`: remove them, hide the boss
   bar, show the "Grace Period Expired!" title, and broadcast it. The `protectedOnline`
   set guarantees this fires exactly once per player.

Data is **not** saved every tick — only on the events that change the budget (join, quit,
set/add/reset) and on disable.

---

## 7. The grace-save flow (how a "death" is survived)

Two listeners, a primary and a fallback.

**Primary — `EntityDamageEvent` (HIGHEST, ignoreCancelled).** For a player with grace, it
computes `health - finalDamage`. If that is ≤ 0 (the hit would be lethal):
- **Totem exception:** if they hold a Totem of Undying and the damage type does **not**
  `BYPASSES_INVULNERABILITY`, do nothing — let vanilla pop the totem. (Void and `/kill`
  bypass invulnerability, so grace still handles those even with a totem.)
- Otherwise cancel the event and run `handleGracePeriodDeath`: drop all inventory items at
  the death spot, drop XP (`min(level*7, 100)`), clear potion effects, reset fire / fall
  distance / freezing, teleport to the grace respawn location, heal to full, show the
  "You're still in the game!" title, and broadcast the save.

**Fallback — `PlayerDeathEvent` (HIGHEST).** Catches deaths that skip the damage event.
- **No grace:** permanent death → `DeathAnnouncer.announce` (unless the event was already
  cancelled).
- **Has grace:** suppress the vanilla death message, mark the UUID
  `pendingGraceRespawn`, and force an auto-respawn next tick (`player.spigot().respawn()`)
  so the death screen never shows.

**`PlayerRespawnEvent` (HIGHEST).** For a `pendingGraceRespawn` player, set the respawn
location (§ below) and, one tick later, heal, clear danger state and potion effects, show
the save title, and broadcast.

**Respawn location (`graceRespawnLocation`).** Vanilla respawn point (bed / charged anchor)
if it's safe; else the closest safe spot within 3 blocks of it; else the overworld spawn
(or a safe spot near it); else the highest block at spawn. **Safe** = solid, non-harmful
ground (not magma/campfire/soul campfire/cactus) with air at both feet and head (cave air
and void air count, so underground spots work).

---

## 8. Command reference — `/graceperiod`

Permission `graceperiod.admin` (default: op). Subcommands:

| Subcommand | Effect |
|---|---|
| `start` | 10-second countdown, then start the event. Refuses if already running. |
| `reset` | Cancel any running countdown and wipe all grace state + the data file. |
| `check <player>` | Report a player's remaining grace (offline-capable). |
| `set <player> <hours 0-24> [min 0-59]` | Set remaining grace to an absolute amount, counting from now. |
| `add <player> <hours -24..24> [min 0-59]` | Add (or, negative, subtract) remaining grace for one player. Offline-capable. |
| `addall <hours -24..24> [min 0-59]` | Same, for every **online** player at once. Requires the event to have started. |
| `resetplayer <player>` | Reset one player's grace to the configured default. |

`set`/`add`/`check`/`resetplayer` accept offline players; `addall` is online-only by
design. None of them change the configured default duration. Tab completion offers the
subcommands and online player names.

---

## 9. Offline player resolution (`findPlayer`)

`check`/`set`/`add`/`resetplayer` all resolve their target through
`GracePeriodCommands.findPlayer`, in this order:
1. Online exact match.
2. The name profile cache (`getOfflinePlayerIfCached`) — fast, but returns null once a name
   has aged out of the cache or after a restart.
3. **A scan of on-disk player data (`Bukkit.getOfflinePlayers()`)** — reliable for anyone
   who has ever played, regardless of cache state.
4. A raw UUID string, as a last resort.

Step 3 is what lets an admin add time to a player who **logged off before the time was
added** and hasn't returned — their budget is sitting in the data file, and this resolves
their name without needing them online or freshly cached. Added because steps 1–2 alone
left that player unreachable.

> ⚠️ **Known trap.** `addPlayerTime` on a player who has **never joined since the event
> started** seeds them from `0` (via `getOrDefault(uuid, 0L)`) and inserts them into the
> budget map — so their eventual first join will skip the configured default and leave them
> with only the amount added. Safe for anyone who already has a budget (the common case);
> be aware of it if you pre-grant time to someone who has never joined.

---

## 10. Config & data files

Both live in the plugin's data folder (`plugins/HardcoreGracePeriod/`).

### `config.json` (`ModConfig`)
Read **once on enable** — restart to apply changes. Re-saved on load so new fields appear.

| Key | Default | Meaning |
|---|---|---|
| `gracePeriodHours` / `gracePeriodMinutes` | 3 / 0 | Default grace duration. |
| `enableBossBar` | true | Show the per-player grace boss bar. |
| `debugMode` | false | (Reserved.) |
| `deathTitleEnabled` | true | Show the permanent-death title to everyone. |
| `deathTitle` / `deathSubtitle` | `§4☠ §6%player%` / `§7§ohas been lost to the 7 Seas` | Title text; `%player%` and `§` codes supported. |
| `deathTitleFadeIn` / `Stay` / `FadeOut` | 20 / 80 / 30 | Title timing, in ticks (20 = 1s). |
| `deathSound` | `minecraft:block.conduit.deactivate` | Sound ID; empty = silent. |
| `deathSoundVolume` / `deathSoundPitch` | 0.6 / 1.0 | Sound params. |

### `grace_period_data.json` (`GracePeriodData`)
`{ eventStarted, players.<uuid>.graceRemainingMillis }`. Written atomically. **Migration on
load** for older saves:
- `graceEndMillis` (absolute end time, old behavior where the clock ran offline) →
  `max(0, graceEndMillis - now)` becomes the online budget.
- `remainingTime` (oldest format, ticks) → `ticks * 50` ms.

Both legacy fields are read only to migrate and are never written back.

---

## 11. Invariants & gotchas

- **The overworld is assumed to be named `world`** (border reset, respawn fallback,
  day-time set). Change this in `GracePeriodManager.startEvent/resetWorldBorder` and
  `PlayerListener.graceRespawnLocation` if your overworld has a different name.
- **Config is read once on enable.** No live reload — restart to apply edits.
- **Everything runs on the main server thread** via the Bukkit scheduler. No locking or
  concurrency handling is needed, and none should be added.
- **`onlineSince` is never persisted.** If you add state that must survive a restart, it
  goes in `graceRemaining`/the data file, not alongside `onlineSince`.
- **Expiry is announced exactly once** via `protectedOnline` — preserve that invariant if
  you touch `tick()`.
- **Boss bar progress is relative to the configured default**, so a budget raised above the
  default via `add`/`set` will clamp the bar at full.

---

## 12. "I want to change X" → go here

| Goal | Edit |
|---|---|
| Default grace length | `config.json` (`gracePeriodHours/Minutes`); logic in `ModConfig.getGracePeriodMillis`. |
| Make config reloadable without restart | `ModConfig.load` + a reload command in `GracePeriodCommands`; re-read into the manager. |
| What a grace save does (items/XP/effects/teleport) | `PlayerListener.handleGracePeriodDeath` (and the respawn branch in `onPlayerRespawn`). |
| XP kept/dropped on a save | the XP block in `handleGracePeriodDeath` (`min(level*7, 100)`). |
| Totem behavior | the totem check in `onEntityDamage` + `holdsTotem`. |
| Where players respawn after a save | `graceRespawnLocation` / `findSafeNear` / `isSafe`. |
| Permanent-death title/sound | `config.json` `death*` fields; rendering in `DeathAnnouncer`. |
| Boss bar look / thresholds | `GracePeriodManager.updateBossBar` + `refreshPlayer`. |
| Expiry message | the expiry branch of `GracePeriodManager.tick`. |
| Start sequence (countdown, heal, border, time) | `GracePeriodCommands.startCountdown` + `GracePeriodManager.startEvent`. |
| Add/remove/set/check time rules | the `handle*` methods + `parseDelta` in `GracePeriodCommands`; math in `GracePeriodManager`. |
| How offline targets are resolved | `GracePeriodCommands.findPlayer` (§9). |
| Save-file format or migration | `GracePeriodData`. |
| Tick frequency | the `runTaskTimer(..., 0L, 20L)` call in `PlayerListener`'s constructor. |
| The overworld name | search for `getWorld("world")`. |

---

## 13. Build & run

```bash
./gradlew build    # -> build/libs/hardcore-grace-period-1.0-SNAPSHOT.jar
./gradlew clean
```

Drop the jar into a Paper 26.3 server's `plugins/` folder and restart. Config and data files
are created on first enable.
