package org.novato.hardcore_grace_period.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.novato.hardcore_grace_period.GracePeriodManager;
import org.novato.hardcore_grace_period.Hardcore_grace_period;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class GracePeriodCommands implements CommandExecutor, TabCompleter {
    private final GracePeriodManager manager;

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
        startCountdown();
        sender.sendMessage(Component.text("Starting grace period countdown...").color(NamedTextColor.YELLOW));
    }

    private void startCountdown() {
        Bukkit.broadcast(Component.text("Grace Period Starting in 10 seconds...").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

        Hardcore_grace_period plugin = Hardcore_grace_period.getInstance();
        String gracePeriodTime = manager.getConfig().getFormattedGracePeriod();

        for (int i = 10; i >= 1; i--) {
            final int countdown = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.showTitle(Title.title(
                        Component.text(String.valueOf(countdown)).color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD),
                        Component.empty()
                    ));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                }
            }, (10 - i) * 20L);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.showTitle(Title.title(
                    Component.text("Grace Period Has Started!").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                    Component.text(gracePeriodTime + " of Grace Period protection").color(NamedTextColor.YELLOW)
                ));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
            manager.startEvent();
        }, 200L);
    }

    private void handleReset(CommandSender sender) {
        manager.resetEvent();
        sender.sendMessage(Component.text("Grace period event reset!").color(NamedTextColor.YELLOW));
    }

    private void handleCheck(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /graceperiod check <player>").color(NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + args[1]).color(NamedTextColor.RED));
            return;
        }
        long remaining = manager.getRemainingTime(target.getUniqueId());
        sender.sendMessage(Component.text(target.getName() + " has " + manager.formatTime(remaining) + " remaining").color(NamedTextColor.AQUA));
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /graceperiod set <player> <hours> [minutes]").color(NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + args[1]).color(NamedTextColor.RED));
            return;
        }
        try {
            int hours = Integer.parseInt(args[2]);
            int minutes = args.length >= 4 ? Integer.parseInt(args[3]) : 0;
            long ticks = (hours * 60L * 60L + minutes * 60L) * 20L;
            manager.setPlayerTime(target.getUniqueId(), ticks);
            String timeStr = hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
            sender.sendMessage(Component.text("Set " + target.getName() + "'s grace period to " + timeStr).color(NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid number. Usage: /graceperiod set <player> <hours> [minutes]").color(NamedTextColor.RED));
        }
    }

    private void handleResetPlayer(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /graceperiod resetplayer <player>").color(NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + args[1]).color(NamedTextColor.RED));
            return;
        }
        manager.resetPlayerTime(target.getUniqueId());
        sender.sendMessage(Component.text("Reset " + target.getName() + "'s grace period to " + manager.getConfig().getFormattedGracePeriod()).color(NamedTextColor.GREEN));
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
