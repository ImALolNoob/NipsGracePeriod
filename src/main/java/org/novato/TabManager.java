package org.novato;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class TabManager implements Listener {

    private final JavaPlugin plugin;

    public TabManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void updateTabMenu(Player player) {
        NovatoBar novatoBar = (NovatoBar) plugin;

        if (!novatoBar.isTimerActive() && !novatoBar.isTimerEnded()) {
            // Before the timer starts
            String header = "§6YOLOCRAFT SEASON III\n§eFestival of §4C§6O§eL§2O§9U§5R";
            String footer = "§7§lThe event will start soon!";
            player.setPlayerListHeaderFooter(header, footer);
        } else if (novatoBar.isTimerActive() && !novatoBar.isTimerEnded()) {
            // During the event
            String header = "\n§6YOLOCRAFT SEASON III\n§eFestival of §4C§6O§eL§2O§9U§5R";
            String footer = "§6Hardcore Mode: §2Disabled";
            player.setPlayerListHeaderFooter(header, footer);
        } else if (novatoBar.isTimerEnded()) {
            // After the timer ends
            String header = "§6YOLOCRAFT SEASON III\n§eFestival of §4C§6O§eL§2O§9U§5R";
            String footer = "§6Hardcore Mode: §cEnabled";
            player.setPlayerListHeaderFooter(header, footer);
        }
    }
}