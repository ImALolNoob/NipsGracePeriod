package org.novato.hardcore_grace_period.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.tag.DamageTypeTags;
import org.novato.hardcore_grace_period.GracePeriodManager;
import org.novato.hardcore_grace_period.Hardcore_grace_period;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerListener implements Listener {
    private final GracePeriodManager manager;
    private final Hardcore_grace_period plugin;
    private final Set<UUID> pendingGraceRespawn = new HashSet<>();

    public PlayerListener(GracePeriodManager manager, Hardcore_grace_period plugin) {
        this.manager = manager;
        this.plugin = plugin;
        // Grace is real time, so checking once a second is enough for boss bars and expiry
        Bukkit.getScheduler().runTaskTimer(plugin, manager::tick, 0L, 20L);
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
        pendingGraceRespawn.remove(event.getPlayer().getUniqueId());
    }

    // Primary: cancel fatal damage before the player ever dies
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!manager.hasGracePeriod(player.getUniqueId())) return;

        double finalHealth = player.getHealth() - event.getFinalDamage();
        if (finalHealth <= 0) {
            // Let a held Totem of Undying save them the vanilla way instead of dropping everything.
            // Totems don't work against void or /kill damage, so grace still handles those.
            if (holdsTotem(player) && !DamageTypeTags.BYPASSES_INVULNERABILITY.isTagged(event.getDamageSource().getDamageType())) {
                return;
            }
            event.setCancelled(true);
            handleGracePeriodDeath(player);
        }
    }

    // Fallback: catch any death that slipped past EntityDamageEvent (e.g. /kill, void edge cases)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!manager.hasGracePeriod(player.getUniqueId())) return;

        // Suppress death message — we'll broadcast our own
        event.deathMessage(null);

        pendingGraceRespawn.add(player.getUniqueId());

        // Force auto-respawn on the next tick so the player never sees the death screen
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.spigot().respawn();
            }
        });
    }

    // After auto-respawn: set spawn location, then restore health/hunger on the following tick
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!pendingGraceRespawn.remove(player.getUniqueId())) return;

        event.setRespawnLocation(graceRespawnLocation(player));

        // Restore health one tick after respawn (player state is fully reset by then)
        final Player p = player;
        Bukkit.getScheduler().runTask(plugin, () -> {
            manager.healPlayer(p);
            resetDangerState(p);
            new ArrayList<>(p.getActivePotionEffects())
                .forEach(effect -> p.removePotionEffect(effect.getType()));
            p.showTitle(Title.title(
                Component.text("You're still in the game!").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                Component.text("Grace period saved you — " + manager.formatTime(manager.getRemainingMillis(p.getUniqueId())) + " remaining").color(NamedTextColor.YELLOW)
            ));
            Bukkit.broadcast(Component.text(p.getName() + " was saved by the grace period!")
                .color(NamedTextColor.YELLOW));
        });
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

        // Clear all potion effects, and anything that would keep hurting them after the teleport
        new ArrayList<>(player.getActivePotionEffects())
            .forEach(effect -> player.removePotionEffect(effect.getType()));
        resetDangerState(player);

        player.teleport(graceRespawnLocation(player));

        manager.healPlayer(player);

        player.showTitle(Title.title(
            Component.text("You're still in the game!").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
            Component.text("Grace period saved you — " + manager.formatTime(manager.getRemainingMillis(player.getUniqueId())) + " remaining").color(NamedTextColor.YELLOW)
        ));

        Bukkit.broadcast(Component.text(player.getName() + " was saved by the grace period!")
            .color(NamedTextColor.YELLOW));
    }

    private static boolean holdsTotem(Player player) {
        PlayerInventory inv = player.getInventory();
        return inv.getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING
            || inv.getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING;
    }

    /** Puts out fire and clears pending fall damage and freezing. */
    private static void resetDangerState(Player player) {
        player.setFireTicks(0);
        player.setFallDistance(0);
        player.setFreezeTicks(0);
    }

    /**
     * Where a grace save sends the player: exactly where vanilla would respawn them
     * (next to their bed or charged respawn anchor), or the overworld spawn if they
     * have none. Only if that spot isn't safe do we look for a safe one nearby.
     */
    private Location graceRespawnLocation(Player player) {
        Location respawn = player.getRespawnLocation();
        if (respawn != null && respawn.getWorld() != null) {
            Location safe = findSafeNear(respawn);
            if (safe != null) return safe;
        }

        World overworld = Bukkit.getWorld("world");
        World world = overworld != null ? overworld : player.getWorld();
        Location spawn = world.getSpawnLocation();
        Location safe = findSafeNear(spawn);
        if (safe != null) return safe;

        // Last resort: top of the world at spawn
        return world.getHighestBlockAt(spawn).getLocation().add(0.5, 1, 0.5);
    }

    /** The spot itself if it's safe, otherwise the closest safe spot within a few blocks; null if none. */
    private Location findSafeNear(Location center) {
        World world = center.getWorld();
        if (world == null) return null;
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        for (int radius = 0; radius <= 3; radius++) {
            for (int dy : new int[]{0, 1, -1, 2, -2}) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue; // only the ring at this radius
                        Block feet = world.getBlockAt(cx + dx, cy + dy, cz + dz);
                        if (isSafe(feet)) {
                            return feet.getLocation().add(0.5, 0, 0.5);
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean isSafe(Block feet) {
        Block ground = feet.getRelative(0, -1, 0);
        Block head = feet.getRelative(0, 1, 0);
        Material groundType = ground.getType();

        boolean safeGround = groundType.isSolid()
            && groundType != Material.MAGMA_BLOCK
            && groundType != Material.CAMPFIRE
            && groundType != Material.SOUL_CAMPFIRE
            && groundType != Material.CACTUS;
        // isAir() covers AIR, CAVE_AIR and VOID_AIR, so underground spots count
        return safeGround && feet.getType().isAir() && head.getType().isAir();
    }
}
