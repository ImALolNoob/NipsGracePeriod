package org.novato;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class PlayerDeathListener implements Listener {

    private final JavaPlugin plugin;

    public PlayerDeathListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!((NovatoBar) plugin).isTimerEnded()) {
            Location bedLocation = player.getBedSpawnLocation();
            if (bedLocation != null) {
                // Drop the player's inventory before canceling the event
                for (ItemStack item : player.getInventory().getContents()) {
                    if (item != null) {
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                    }
                }
                player.getInventory().clear(); // Clear the player's inventory

                event.setCancelled(true); // Cancel the death event
                player.spigot().respawn();
                player.teleport(bedLocation);
                player.sendMessage("§6You have §arespawned §6at your bed as the timer is still running.");
            } else {
                // Drop the player's inventory before canceling the event
                for (ItemStack item : player.getInventory().getContents()) {
                    if (item != null) {
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                    }
                }
                player.getInventory().clear(); // Clear the player's inventory

                event.setCancelled(true); // Cancel the death event
                Location spawnLocation = player.getWorld().getSpawnLocation();
                player.spigot().respawn();
                player.teleport(spawnLocation);
                player.sendMessage("§5No bed found! §6You have §arespawned §6at the world spawn as the timer is still running.");
            }
        } else {
            player.setGameMode(org.bukkit.GameMode.SPECTATOR);
            player.sendMessage("§6The timer has §4ended§6. You are now in §aspectator mode§6.");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "manuadd " + player.getName() + " Spectator");
        }
    }
}