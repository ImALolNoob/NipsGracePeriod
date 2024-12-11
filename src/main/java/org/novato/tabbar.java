package org.novato;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class tabbar {

    private final JavaPlugin plugin;

    public tabbar(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void updateTabMenu(Player player) {
        if (!((NovatoBar) plugin).isTimerEnded()) {
            String header = "§6The Event will start soon!";
            String footer = "§7The Event will start soon!";
            player.setPlayerListHeaderFooter(header, footer);
        }if (((NovatoBar) plugin).isTimerEnded()) {
            String header = "§6server status: §aEvent is running";
            String footer = "§7server is running";
            player.setPlayerListHeaderFooter(header, footer);
        } if (plugin.getConfig().getBoolean("timerEnded") == true) {
            String header = "this is after the timer is ended";
            String footer = "Hardcore Event is running";
            player.setPlayerListHeaderFooter(header, footer);
        }

    }

}