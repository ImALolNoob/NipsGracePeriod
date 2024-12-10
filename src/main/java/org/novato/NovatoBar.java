package org.novato;

import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public final class NovatoBar extends JavaPlugin implements Listener {

    private BossBar bar;
    private int taskID = -1;
    private int originalTime = 0;
    private int remainingTime = 0;
    private boolean timerEnded = false;
    private boolean timerActive = false;

    @Override
    public void onEnable() {
        createBar();
        loadTimerState();
        getCommand("timer").setExecutor(new TimerCommand(this));
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("NovatoBar enabled");

        // Resume timer if it was active before shutdown
        if (timerActive && remainingTime > 0) {
            startTimer(remainingTime, null);
        }
    }

    @Override
    public void onDisable() {
        saveTimerState(); // Save remaining time on shutdown
        getLogger().info("NovatoBar disabled");
    }

    private void createBar() {
        bar = Bukkit.createBossBar("Timer not set", BarColor.PURPLE, BarStyle.SOLID);
        bar.setVisible(false);
    }

    private void saveTimerState() {
        getConfig().set("remainingTime", remainingTime);
        getConfig().set("originalTime", originalTime);
        getConfig().set("timerEnded", timerEnded);
        getConfig().set("timerActive", timerActive);
        getConfig().set("lastSavedTimestamp", System.currentTimeMillis());
        saveConfig();
    }

    private void loadTimerState() {
        remainingTime = getConfig().getInt("remainingTime", 0);
        originalTime = getConfig().getInt("originalTime", 0);
        timerEnded = getConfig().getBoolean("timerEnded", false);
        timerActive = getConfig().getBoolean("timerActive", false);

        long currentTime = System.currentTimeMillis();
        long lastSaved = getConfig().getLong("lastSavedTimestamp", currentTime);
        long elapsed = (currentTime - lastSaved) / 1000; // Convert ms to seconds

        remainingTime = Math.max(remainingTime - (int) elapsed, 0); // Adjust for elapsed time
        if (remainingTime > originalTime) {
            remainingTime = originalTime; // Safety check
        }
    }

    class TimerCommand implements CommandExecutor {
        private NovatoBar plugin;

        public TimerCommand(NovatoBar plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
            if (args.length == 0) {
                sender.sendMessage("Usage: /timer <start|set|stop|config>");
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
                case "config":
                    sender.sendMessage("Config Values:");
                    sender.sendMessage("Remaining Time: " + plugin.remainingTime);
                    sender.sendMessage("Original Time: " + plugin.originalTime);
                    sender.sendMessage("Timer Ended: " + plugin.timerEnded);
                    sender.sendMessage("Timer Active: " + plugin.timerActive);
                    break;
                default:
                    sender.sendMessage("Unknown command. Use /timer <start|set|stop|config>");
                    return true;
            }
            return true;
        }

    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (bar.isVisible()) {
            bar.addPlayer(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!timerEnded) {
                Location bedLocation = player.getBedSpawnLocation();
                if (bedLocation != null) {
                    event.setCancelled(true);// Cancel the death event
                    player.spigot().respawn();
                    player.teleport(bedLocation);
                    player.sendMessage("§6You have §arespawned §6at your bed as the timer is still running.");
                } else {
                    event.setCancelled(true);// Cancel the death event
                    Location spawnLocation = player.getWorld().getSpawnLocation();
                    player.spigot().respawn();
                    player.teleport(spawnLocation);
                    player.sendMessage("§6No bed found! You have §arespawned §6at the world spawn as the timer is still running.");
                }

            } else {
                player.setGameMode(org.bukkit.GameMode.SPECTATOR);
                player.sendMessage("§6The timer has §5ended§6. You are now in §aspectator §6mode.");
            }
        }, 1L);
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
        saveTimerState(); // Save timer state immediately

        taskID = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            private int seconds = secondsInput;

            @Override
            public void run() {
                if (seconds > 0) {
                    // Handle alerts for specific time intervals
                    switch (seconds) {
                        case 10800:
                            Bukkit.broadcastMessage("3 hours remaining!");
                            sendTitleAndSound("3", "Hours Remaining...", "minecraft:entity.player.levelup");
                            break;
                        case 3600:
                            Bukkit.broadcastMessage("1 hour remaining!");
                            sendTitleAndSound("1", "Hour Remaining...", "minecraft:entity.player.levelup");
                            break;
                        case 1800:
                            Bukkit.broadcastMessage("30 minutes remaining!");
                            sendTitleAndSound("30", "Minutes Remaining...", "minecraft:entity.player.levelup");
                            break;
                        case 600:
                            Bukkit.broadcastMessage("10 minutes remaining!");
                            sendTitleAndSound("10", "Minutes Remaining...", "minecraft:entity.player.levelup");
                            break;
                        case 300:
                            Bukkit.broadcastMessage("5 minutes remaining!");
                            sendTitleAndSound("5", "Minutes Remaining...", "minecraft:entity.player.levelup");
                            break;
                        case 60:
                            Bukkit.broadcastMessage("1 minute remaining!");
                            sendTitleAndSound("1", "Minute Remaining...", "minecraft:entity.player.levelup");
                            break;
                        case 30:
                            Bukkit.broadcastMessage("30 seconds remaining!");
                            sendTitleAndSound("30", "Seconds Remaining...", "minecraft:entity.player.levelup");
                            break;
                        case 10:
                            Bukkit.broadcastMessage(seconds + " §6seconds remaining!");
                            sendTitleAndSound("10", "Seconds Remaining...", "minecraft:entity.player.levelup");
                        case 9: case 8: case 7: case 6: case 5: case 4: case 3: case 2: case 1:
                            Bukkit.broadcastMessage("§5§l" + seconds + " §6seconds remaining!");
                            break;
                    }

                    updateBossBarTitle(seconds);
                    bar.setProgress((double) seconds / originalTime);
                    remainingTime = seconds;
                    saveTimerState(); // Save the remaining time every second
                    seconds--;
                } else {
                    // Handle the 0-second mark
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

    private void sendTitleAndSound(String time, String message, String sound) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "title @a times 30 60 30");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "title @a subtitle [\"\",{\"text\":\"" + time + "\",\"color\":\"dark_purple\"},{\"text\":\" " + message + "\",\"color\":\"gold\"}]");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "title @a title {\"text\":\"\"}");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "execute at @a run playsound " + sound + " master @p");
    }

    private void updateBossBarTitle(int time) {
        int hours = time / 3600;
        int minutes = (time % 3600) / 60;
        int seconds = time % 60;
        String timeStr = String.format("Time remaining: %02d:%02d:%02d", hours, minutes, seconds);
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
