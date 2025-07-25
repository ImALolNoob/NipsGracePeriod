package org.novato;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.logging.Logger;

public final class NovatoBar extends JavaPlugin implements Listener {

    private BossBar bar;
    private int taskID = -1;
    private int originalTime = 0;
    private int remainingTime = 0;
    private boolean timerEnded = false;
    private boolean timerActive = false;
    private long startTimeMillis;
    private TabManager tabBar;
    private Logger logger;

    @Override
    public void onEnable() {
        // Save the default config if it doesn't exist
        saveDefaultConfig();

        tabBar = new TabManager(this);
        createBar();
        loadTimerState();
        getCommand("timer").setExecutor(new TimerCommand(this));
        Bukkit.getPluginManager().registerEvents(new PlayerDeathListener(this), this);
        getLogger().info("NovatoBar enabled");

        logger = getLogger();

        // Schedule a repeating task to update the tab menu for all players
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    tabBar.updateTabMenu(player);
                }
            }
        }.runTaskTimer(this, 0L, 20L); // Update every second (20 ticks)

        // Resume timer if it was active before shutdown
        if (timerActive && remainingTime > 0) {
            startTimer(remainingTime, null);
        }
    }

    public boolean isTimerEnded() {
        return timerEnded;
    }

    @Override
    public void onDisable() {
        saveTimerState(); // Save remaining time on shutdown
        getLogger().info("NovatoBar disabled");
    }

    private void createBar() {
        bar = Bukkit.createBossBar("Timer not set", BarColor.PURPLE, BarStyle.SEGMENTED_6);
        bar.setVisible(false);
    }

    private void saveTimerState() {
        getConfig().set("remainingTime", remainingTime);
        getConfig().set("originalTime", originalTime);
        getConfig().set("timerEnded", timerEnded);
        getConfig().set("timerActive", timerActive);

        saveConfig();
    }

    private void loadTimerState() {
        remainingTime = getConfig().getInt("remainingTime");
        originalTime = getConfig().getInt("originalTime");
        timerEnded = getConfig().getBoolean("timerEnded");
        timerActive = getConfig().getBoolean("timerActive");
    }

    public boolean isTimerActive() {
        return timerActive;
    }

    class TimerCommand implements CommandExecutor {
        private NovatoBar plugin;

        public TimerCommand(NovatoBar plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
            if (args.length == 0) {
                sender.sendMessage("Usage: /timer <start|set|stop|config|resetfull>");
                return true;
            }

            String permission = plugin.getConfig().getString("permissions.timer." + args[0].toLowerCase());
            if (permission != null && !sender.hasPermission(permission)) {
                sender.sendMessage("You do not have permission to execute this command.");
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "start":
                    if (plugin.originalTime > 0) {
                        plugin.startTimer(plugin.remainingTime > 0 ? plugin.remainingTime : plugin.originalTime, sender);
                        plugin.getConfig().set("facteventStarted", 2);
                        plugin.saveConfig();
                    } else {
                        sender.sendMessage("Please set the time first using /timer set [seconds].");
                    }
                    break;
                case "set":
                    if (args.length < 2) {
                        sender.sendMessage("Please specify the time in seconds.");
                        return true;
                    }
                    try {
                        int time = Integer.parseInt(args[1]);
                        if (time > 0) {
                            plugin.originalTime = time;
                            plugin.remainingTime = time;
                            sender.sendMessage("Timer set to " + plugin.originalTime + " seconds.");
                        } else {
                            sender.sendMessage("Time must be greater than zero.");
                        }
                    } catch (NumberFormatException e) {
                        sender.sendMessage("Invalid number format.");
                        return true;
                    }
                    break;
                case "stop":
                    plugin.stopTimer();
                    sender.sendMessage("Timer stopped.");
                    break;
                case "resetfull":
                    if (!sender.hasPermission("permissions.timer.resetfull")) {
                        sender.sendMessage("You do not have permission to execute this command.");
                        return true;
                    }
                    plugin.stopTimer(); // Stop any active timer
                    plugin.originalTime = 6 * 3600; // 6 hours in seconds
                    plugin.remainingTime = plugin.originalTime; // Reset remaining time
                    plugin.timerEnded = false; // Reset timer status
                    plugin.timerActive = false; // Set timer as inactive
                    plugin.saveTimerState(); // Save the updated state
                    sender.sendMessage("Timer fully reset to 6 hours and stopped.");
                    break;
                case "config":
                    sender.sendMessage("Config Values:");
                    sender.sendMessage("Remaining Time: " + plugin.remainingTime);
                    sender.sendMessage("Original Time: " + plugin.originalTime);
                    sender.sendMessage("Timer Ended: " + plugin.timerEnded);
                    sender.sendMessage("Timer Active: " + plugin.timerActive);
                    break;
                default:
                    sender.sendMessage("Unknown command. Use /timer <start|set|stop|config|resetfull>");
                    return true;
            }

            return true;
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (timerActive && remainingTime > 0) {
            bar.addPlayer(player);
            updateBossBarTitle(remainingTime); // Sync title for the player
        }
    }

    public void startTimer(int secondsInput, CommandSender sender) {
        if (secondsInput <= 0) {
            if (sender != null) {
                sender.sendMessage("Time must be greater than zero to start the timer.");
            }
            return;
        }

        bar.setProgress(1.0);
        updateBossBarTitle(secondsInput);
        bar.setVisible(true);
        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            bar.addPlayer(player);
        }

        if (taskID != -1) {
            Bukkit.getScheduler().cancelTask(taskID);
        }

        remainingTime = secondsInput;
        timerEnded = false;
        timerActive = true;
        startTimeMillis = System.currentTimeMillis();
        saveTimerState(); // Save timer state immediately

        taskID = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                long elapsedMillis = System.currentTimeMillis() - startTimeMillis;
                int elapsedSeconds = (int) (elapsedMillis / 1000);
                int newRemainingTime = originalTime - elapsedSeconds;

                if (newRemainingTime != remainingTime) {
                    logger.info("Adjusting remaining time from " + remainingTime + " to " + newRemainingTime);
                    remainingTime = newRemainingTime;
                }

                if (remainingTime > 0) {
                    for (Player player : Bukkit.getServer().getOnlinePlayers()) {
                        bar.addPlayer(player); // Sync boss bar with all players
                    }

                    updateBossBarTitle(remainingTime); // Update the title for the timer
                    bar.setProgress((double) remainingTime / originalTime); // Update progress
                    saveTimerState(); // Persist state
                } else {
                    // Handle the 0-second mark
                    getConfig().set("facteventStarted", 3);
                    saveConfig();
                    Bukkit.broadcastMessage("§5Hardcore Mode Enabled! §4Death §6is now permanent.");
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "title @a times 40 80 40");
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "title @a subtitle [\"\",{\"text\":\"Death\",\"bold\":true,\"color\":\"dark_red\"},{\"text\":\" Is Now Permanent\",\"color\":\"gold\"}]");
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "title @a title {\"text\":\"Hardcore Mode Enabled...\",\"bold\":true,\"color\":\"dark_purple\"}");
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "execute at @a run playsound minecraft:ui.toast.challenge_complete master @p");

                    stopTimer();
                }
            }
        }, 0L, 20L); // Runs every 20 ticks (1 second)
    }

    private void updateBossBarTitle(int time) {
        int hours = time / 3600;
        int minutes = (time % 3600) / 60;
        int seconds = time % 60;
        String timeStr = String.format("§6Time remaining: %02d:%02d:%02d", hours, minutes, seconds);
        bar.setTitle(timeStr);
    }

    public void stopTimer() {
        if (taskID != -1) {
            Bukkit.getScheduler().cancelTask(taskID);
            taskID = -1;
        }
        bar.setVisible(false);
        bar.setProgress(1.0);
        bar.setTitle("Timer not set");
        remainingTime = 0;
        timerEnded = true;
        timerActive = false;
        saveTimerState();
    }
}