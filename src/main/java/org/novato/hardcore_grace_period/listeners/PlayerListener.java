package org.novato.hardcore_grace_period.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.novato.hardcore_grace_period.GracePeriodManager;
import org.novato.hardcore_grace_period.Hardcore_grace_period;

public class PlayerListener implements Listener {
    private final GracePeriodManager manager;

    public PlayerListener(GracePeriodManager manager, Hardcore_grace_period plugin) {
        this.manager = manager;
        Bukkit.getScheduler().runTaskTimer(plugin, manager::tick, 0L, 1L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (manager.isEventStarted()) {
            manager.initializePlayer(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        manager.removePlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!manager.hasGracePeriod(player.getUniqueId())) return;

        double finalHealth = player.getHealth() - event.getFinalDamage();
        if (finalHealth <= 0) {
            event.setCancelled(true);
            handleGracePeriodDeath(player);
        }
    }

    private void handleGracePeriodDeath(Player player) {
        Location deathLocation = player.getLocation().clone();

        // Drop all items at death location
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                player.getWorld().dropItemNaturally(deathLocation, item);
            }
        }
        player.getInventory().clear();

        // Drop experience
        int xp = Math.min(player.getLevel() * 7, 100);
        if (xp > 0) {
            ExperienceOrb orb = player.getWorld().spawn(deathLocation, ExperienceOrb.class);
            orb.setExperience(xp);
            player.setExp(0);
            player.setLevel(0);
            player.setTotalExperience(0);
        }

        // Clear all potion effects
        new java.util.ArrayList<>(player.getActivePotionEffects())
            .forEach(effect -> player.removePotionEffect(effect.getType()));

        // Teleport to respawn point (bed/anchor) or world spawn
        Location spawnLoc = player.getRespawnLocation();
        if (spawnLoc == null) {
            World overworld = Bukkit.getWorld("world");
            spawnLoc = overworld != null ? overworld.getSpawnLocation() : deathLocation;
        }
        Location safeSpawn = findSafeSpawn(spawnLoc);
        player.teleport(safeSpawn);

        // Restore health and hunger
        manager.healPlayer(player);

        Bukkit.broadcast(Component.text(player.getName() + " was saved by the grace period!").color(NamedTextColor.YELLOW));
    }

    private Location findSafeSpawn(Location center) {
        World world = center.getWorld();
        if (world == null) return center;

        for (int y = 120; y >= 64; y--) {
            Location check = new Location(world, center.getBlockX(), y, center.getBlockZ());
            if (isSafe(check)) {
                return check.add(0.5, 0, 0.5);
            }
        }
        return center.clone().add(0.5, 1, 0.5);
    }

    private boolean isSafe(Location loc) {
        World world = loc.getWorld();
        if (world == null) return false;

        Material ground = world.getBlockAt(loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ()).getType();
        Material feet = world.getBlockAt(loc).getType();
        Material head = world.getBlockAt(loc.getBlockX(), loc.getBlockY() + 1, loc.getBlockZ()).getType();

        boolean safeGround = ground.isSolid() && ground != Material.LAVA && ground != Material.WATER;
        boolean clearFeet = feet == Material.AIR;
        boolean clearHead = head == Material.AIR;

        return safeGround && clearFeet && clearHead;
    }
}
