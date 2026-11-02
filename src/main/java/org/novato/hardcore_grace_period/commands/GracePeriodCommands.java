package org.novato.hardcore_grace_period.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.novato.hardcore_grace_period.AltarManager;
import org.novato.hardcore_grace_period.GracePeriodManager;
import org.novato.hardcore_grace_period.Hardcore_grace_period;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class GracePeriodCommands implements CommandExecutor, TabCompleter {
    private final GracePeriodManager manager;
    private final AltarManager altar;
    /** Scheduled countdown steps; non-empty while a countdown is running. */
    private final List<BukkitTask> countdownTasks = new ArrayList<>();

    public GracePeriodCommands(GracePeriodManager manager, AltarManager altar) {
        this.manager = manager;
        this.altar = altar;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /graceperiod <start|reset|check|set|add|addall|resetplayer|revive|dead|altar>").color(NamedTextColor.YELLOW));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> handleStart(sender);
            case "reset" -> handleReset(sender);
            case "check" -> handleCheck(sender, args);
            case "set" -> handleSet(sender, args);
            case "add" -> handleAdd(sender, args);
            case "addall" -> handleAddAll(sender, args);
            case "resetplayer" -> handleResetPlayer(sender, args);
            case "revive" -> handleRevive(sender, args);
            case "dead" -> handleDead(sender);
            case "altar" -> handleAltar(sender, args);
            default -> sender.sendMessage(Component.text("Unknown subcommand. Use: start, reset, check, set, add, addall, resetplayer, revive, dead, altar").color(NamedTextColor.RED));
        }
        return true;
    }

    private void handleStart(CommandSender sender) {
        if (manager.isEventStarted()) {
            sender.sendMessage(Component.text("Grace period event is already running! (it's saved across restarts — use /graceperiod reset first to start a new one)").color(NamedTextColor.RED));
            return;
        }
        if (!countdownTasks.isEmpty()) {
            sender.sendMessage(Component.text("The countdown is already running!").color(NamedTextColor.RED));
            return;
        }
        startCountdown();
        sender.sendMessage(Component.text("Starting grace period countdown...").color(NamedTextColor.YELLOW));
    }

    private void startCountdown() {
        Bukkit.broadcast(Component.text("Grace Period Starting in 10 seconds...").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

        Hardcore_grace_period plugin = Hardcore_grace_period.getInstance();
        String gracePeriodTime = manager.getConfig().getFormattedGracePeriod();

        for (int i = 10; i >= 1; i--) {
            final int countdown = i;
            countdownTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.showTitle(Title.title(
                        Component.text(String.valueOf(countdown)).color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD),
                        Component.empty()
                    ));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                }
            }, (10 - i) * 20L));
        }

        countdownTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            countdownTasks.clear();
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.showTitle(Title.title(
                    Component.text("Grace Period Has Started!").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                    Component.text(gracePeriodTime + " of Grace Period protection").color(NamedTextColor.YELLOW)
                ));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
            manager.startEvent();
        }, 200L));
    }

    private void handleReset(CommandSender sender) {
        countdownTasks.forEach(BukkitTask::cancel);
        countdownTasks.clear();
        manager.resetEvent();
        altar.reset();
        sender.sendMessage(Component.text("Grace period event reset! (altar progress, tier and fallen list cleared)").color(NamedTextColor.YELLOW));
    }

    private void handleCheck(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /graceperiod check <player>").color(NamedTextColor.RED));
            return;
        }
        OfflinePlayer target = findPlayer(sender, args[1]);
        if (target == null) return;

        String name = target.getName() != null ? target.getName() : args[1];
        if (manager.isEventStarted() && !manager.isKnown(target.getUniqueId())) {
            sender.sendMessage(Component.text(name + " hasn't joined since the event started (they get "
                + manager.getConfig().getFormattedGracePeriod() + " when they do)").color(NamedTextColor.AQUA));
            return;
        }
        long remaining = manager.getRemainingMillis(target.getUniqueId());
        if (remaining <= 0) {
            sender.sendMessage(Component.text(name + "'s grace period has ended").color(NamedTextColor.AQUA));
        } else {
            sender.sendMessage(Component.text(name + " has " + manager.formatTime(remaining) + " remaining").color(NamedTextColor.AQUA));
        }
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /graceperiod set <player> <hours 0-24> [minutes 0-59]").color(NamedTextColor.RED));
            return;
        }
        int hours;
        int minutes;
        try {
            hours = Integer.parseInt(args[2]);
            minutes = args.length >= 4 ? Integer.parseInt(args[3]) : 0;
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid number. Usage: /graceperiod set <player> <hours 0-24> [minutes 0-59]").color(NamedTextColor.RED));
            return;
        }
        if (hours < 0 || hours > 24 || minutes < 0 || minutes > 59) {
            sender.sendMessage(Component.text("Hours must be 0-24 and minutes 0-59.").color(NamedTextColor.RED));
            return;
        }
        OfflinePlayer target = findPlayer(sender, args[1]);
        if (target == null) return;

        manager.setPlayerTime(target.getUniqueId(), (hours * 60L * 60L + minutes * 60L) * 1000L);
        String name = target.getName() != null ? target.getName() : args[1];
        String timeStr = hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
        sender.sendMessage(Component.text("Set " + name + "'s grace period to " + timeStr + " from now").color(NamedTextColor.GREEN));
    }

    private void handleAdd(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /graceperiod add <player> <hours -24..24> [minutes 0-59]").color(NamedTextColor.RED));
            return;
        }
        Long delta = parseDelta(sender, args, 2);
        if (delta == null) return;

        OfflinePlayer target = findPlayer(sender, args[1]);
        if (target == null) return;

        manager.addPlayerTime(target.getUniqueId(), delta);
        String name = target.getName() != null ? target.getName() : args[1];
        long remaining = manager.getRemainingMillis(target.getUniqueId());
        sender.sendMessage(Component.text((delta >= 0 ? "Added " : "Removed ") + formatDelta(delta)
            + " to " + name + "'s grace period (" + manager.formatTime(remaining) + " remaining)").color(NamedTextColor.GREEN));
    }

    private void handleAddAll(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /graceperiod addall <hours -24..24> [minutes 0-59]").color(NamedTextColor.RED));
            return;
        }
        if (!manager.isEventStarted()) {
            sender.sendMessage(Component.text("The grace period event hasn't started yet.").color(NamedTextColor.RED));
            return;
        }
        Long delta = parseDelta(sender, args, 1);
        if (delta == null) return;

        int count = manager.addTimeToOnlinePlayers(delta);
        sender.sendMessage(Component.text((delta >= 0 ? "Added " : "Removed ") + formatDelta(delta)
            + " to " + count + " online player" + (count == 1 ? "" : "s")).color(NamedTextColor.GREEN));
    }

    /**
     * Parses a signed hours/minutes amount starting at {@code hoursIndex} into millis
     * (negative subtracts). Returns null and messages the sender on bad input.
     */
    private Long parseDelta(CommandSender sender, String[] args, int hoursIndex) {
        int hours;
        int minutes;
        try {
            hours = Integer.parseInt(args[hoursIndex]);
            minutes = args.length > hoursIndex + 1 ? Integer.parseInt(args[hoursIndex + 1]) : 0;
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid number. Give whole hours (-24..24) and optional minutes (0-59).").color(NamedTextColor.RED));
            return null;
        }
        if (hours < -24 || hours > 24 || minutes < 0 || minutes > 59) {
            sender.sendMessage(Component.text("Hours must be -24..24 and minutes 0-59.").color(NamedTextColor.RED));
            return null;
        }
        if (hours == 0 && minutes == 0) {
            sender.sendMessage(Component.text("Give a non-zero amount of time to add or remove.").color(NamedTextColor.RED));
            return null;
        }
        long magnitude = (Math.abs(hours) * 60L * 60L + minutes * 60L) * 1000L;
        return hours < 0 ? -magnitude : magnitude;
    }

    private String formatDelta(long millis) {
        long totalMinutes = Math.abs(millis) / 60000L;
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours > 0 && minutes > 0) return hours + "h " + minutes + "m";
        if (hours > 0) return hours + "h";
        return minutes + "m";
    }

    private void handleResetPlayer(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /graceperiod resetplayer <player>").color(NamedTextColor.RED));
            return;
        }
        OfflinePlayer target = findPlayer(sender, args[1]);
        if (target == null) return;

        manager.resetPlayerTime(target.getUniqueId());
        String name = target.getName() != null ? target.getName() : args[1];
        sender.sendMessage(Component.text("Reset " + name + "'s grace period to " + manager.getConfig().getFormattedGracePeriod() + " from now").color(NamedTextColor.GREEN));
    }

    private void handleRevive(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /graceperiod revive <player>").color(NamedTextColor.RED));
            return;
        }
        OfflinePlayer target = findPlayer(sender, args[1]);
        if (target == null) return;

        String name = target.getName() != null ? target.getName() : args[1];
        if (altar.forceRevive(target.getUniqueId())) {
            sender.sendMessage(Component.text("Revived " + name + " with "
                + manager.formatTime(manager.getConfig().getAltarRevivalGraceMillis()) + " of grace.").color(NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text(name + " isn't among the fallen.").color(NamedTextColor.RED));
        }
    }

    private void handleDead(CommandSender sender) {
        List<String> names = altar.deadNames();
        if (names.isEmpty()) {
            sender.sendMessage(Component.text("No one has fallen yet.").color(NamedTextColor.AQUA));
            return;
        }
        sender.sendMessage(Component.text("Fallen (" + names.size() + "): ").color(NamedTextColor.AQUA)
            .append(Component.text(String.join(", ", names), NamedTextColor.WHITE)));
    }

    private void handleAltar(CommandSender sender, String[] args) {
        String sub = args.length >= 2 ? args[1].toLowerCase() : "";
        switch (sub) {
            case "set" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only a player can set the altar (look at the block first).").color(NamedTextColor.RED));
                    return;
                }
                var block = player.getTargetBlockExact(6);
                if (block == null) {
                    sender.sendMessage(Component.text("Look at the block you want to use as the altar (within 6 blocks).").color(NamedTextColor.RED));
                    return;
                }
                altar.setAltar(block.getLocation());
                sender.sendMessage(Component.text("Altar set at " + block.getX() + ", " + block.getY() + ", " + block.getZ()
                    + " (" + AltarManager.pretty(block.getType()) + ").").color(NamedTextColor.GREEN));
            }
            case "spawn" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only a player can spawn the altar stand (it appears where you're standing).").color(NamedTextColor.RED));
                    return;
                }
                altar.spawnAltarStand(player.getLocation());
                sender.sendMessage(Component.text("Spawned a protected armor-stand altar at your feet. It can't be broken or edited.").color(NamedTextColor.GREEN));
            }
            case "remove" -> {
                altar.removeAltar();
                sender.sendMessage(Component.text("Altar unregistered.").color(NamedTextColor.YELLOW));
            }
            case "reroll" -> {
                altar.rerollOffering();
                sender.sendMessage(Component.text("Rolled a fresh offering at the current tier (progress cleared).").color(NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text("Usage: /graceperiod altar <set|spawn|remove|reroll>").color(NamedTextColor.RED));
        }
    }

    /**
     * Finds an online player, or an offline one who has played on this server before.
     * Resolution order: online exact match, then the name profile cache (fast path), then
     * a scan of on-disk player data (reliable across restarts, even once a name has aged
     * out of the cache), then a raw UUID. This is what lets an admin add time to a player
     * who left before the time was added and hasn't come back since.
     */
    private OfflinePlayer findPlayer(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;

        // Fast path: name is still in the in-memory profile cache.
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null && cached.hasPlayedBefore()) return cached;

        // Reliable path: anyone with player data on disk, regardless of cache state.
        for (OfflinePlayer candidate : Bukkit.getOfflinePlayers()) {
            if (name.equalsIgnoreCase(candidate.getName())) return candidate;
        }

        // Last resort: allow targeting by raw UUID (e.g. a name that can't be resolved).
        try {
            OfflinePlayer byId = Bukkit.getOfflinePlayer(UUID.fromString(name));
            if (byId.hasPlayedBefore()) return byId;
        } catch (IllegalArgumentException ignored) {
            // not a UUID; fall through to the not-found message
        }

        sender.sendMessage(Component.text("Player not found (they must have joined this server before): " + name).color(NamedTextColor.RED));
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("start", "reset", "check", "set", "add", "addall", "resetplayer", "revive", "dead", "altar").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("check") || args[0].equalsIgnoreCase("set")
                || args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("resetplayer")
                || args[0].equalsIgnoreCase("revive"))) {
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("altar")) {
            return Arrays.asList("set", "spawn", "remove", "reroll").stream()
                .filter(s -> s.startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }
        return List.of();
    }
}
