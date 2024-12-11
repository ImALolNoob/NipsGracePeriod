package org.novato;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class tabbar {

    private final JavaPlugin plugin;

    public tabbar(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void updateTabMenu(Player player) {
        String factEventStarted = plugin.getConfig().getString("facteventStarted");

        switch (factEventStarted) {
            case "1":
                String header1 = "§6The Event will start soon!";
                String footer1 = "§7The Event will start soon!";
                player.setPlayerListHeaderFooter(header1, footer1);
                break;
            case "2":
                String header2 = "§6server status: §aEvent is running";
                String footer2 = "§7server is running";
                player.setPlayerListHeaderFooter(header2, footer2);
                break;
            case "3":
                String header3 = "this is after the timer is ended";
                String footer3 = "Hardcore Event is running";
                player.setPlayerListHeaderFooter(header3, footer3);
                break;
            default:
                // Handle default case if needed
                break;
        }
    }

}