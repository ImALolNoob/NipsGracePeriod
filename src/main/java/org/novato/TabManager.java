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
            String header = "§6YOLOCRAFT SEASON 3";
            String footer = "§7The Event will start soon!";
            player.setPlayerListHeaderFooter(header, footer);
        } else if (novatoBar.isTimerActive() && !novatoBar.isTimerEnded()) {
            // During the event
            String header = "§6YOLOCRAFT SEASON 3";
            String footer = "§7Hardcore mode: §2Disabled\n";
            player.setPlayerListHeaderFooter(header, footer);
        } else if (novatoBar.isTimerEnded()) {
            // After the timer ends
            String header = "§6YOLOCRAFT SEASON 3";
            String footer = "§7Hardcore mode: §cEnabled";
            player.setPlayerListHeaderFooter(header, footer);
        }
    }
}