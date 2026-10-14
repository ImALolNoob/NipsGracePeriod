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
import org.bukkit.scheduler.BukkitTask;
import org.novato.hardcore_grace_period.GracePeriodManager;
import org.novato.hardcore_grace_period.Hardcore_grace_period;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class GracePeriodCommands implements CommandExecutor, TabCompleter {
    private final GracePeriodManager manager;
    /** Scheduled countdown steps; non-empty while a countdown is running. */
    private final List<BukkitTask> countdownTasks = new ArrayList<>();

    public GracePeriodCommands(GracePeriodManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /graceperiod <start|reset|check|set|resetplayer>").color(NamedTextColor.YELLOW));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> handleStart(sender);
            case "reset" -> handleReset(sender);
            case "check" -> handleCheck(sender, args);
            case "set" -> handleSet(sender, args);
            case "resetplayer" -> handleResetPlayer(sender, args);
            default -> sender.sendMessage(Component.text("Unknown subcommand. Use: start, reset, check, set, resetplayer").color(NamedTextColor.RED));
        }
        return true;
    }

    private void handleStart(CommandSender sender) {
        if (manager.isEventStarted()) {
            sender.sendMessage(Component.text("Grace period event is already running!").color(NamedTextColor.RED));
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
        sender.sendMessage(Component.text("Grace period event reset!").color(NamedTextColor.YELLOW));
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

    /** Finds an online player, or an offline one who has played on this server before. */
    private OfflinePlayer findPlayer(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;

        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
        if (offline == null || !offline.hasPlayedBefore()) {
            sender.sendMessage(Component.text("Player not found (they must have joined this server before): " + name).color(NamedTextColor.RED));
            return null;
        }
        return offline;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("start", "reset", "check", "set", "resetplayer").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("check") ||
                args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("resetplayer"))) {
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }
        return List.of();
    }
}
