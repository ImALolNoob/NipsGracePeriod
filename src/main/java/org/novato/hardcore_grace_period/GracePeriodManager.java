package org.novato.hardcore_grace_period;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.novato.hardcore_grace_period.config.ModConfig;
import org.novato.hardcore_grace_period.data.GracePeriodData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Grace is a real-time window: each player is protected until a fixed end time
 * (first join after the event starts + the configured duration). The clock keeps
 * running while they are offline and across restarts.
 */
public class GracePeriodManager {
    private final Hardcore_grace_period plugin;
    private final ModConfig config;
    private final Map<UUID, Long> graceEnds = new HashMap<>();
    private final Map<UUID, BossBar> playerBossBars = new HashMap<>();
    /** Online players whose grace was still running at the last check, so expiry is announced exactly once. */
    private final Set<UUID> protectedOnline = new HashSet<>();
    private boolean eventStarted = false;

    public GracePeriodManager(Hardcore_grace_period plugin) {
        this.plugin = plugin;
        this.config = ModConfig.load(plugin);
        loadData();
    }

    public void startEvent() {
        if (eventStarted) return;
        eventStarted = true;

        World overworld = Bukkit.getWorld("world");
        if (overworld != null) {
            overworld.setTime(0);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            initializePlayer(player);
            healPlayer(player);
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 9, false, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 200, 9, false, true, true));
        }

        Bukkit.broadcast(Component.text("Grace Period Event Started! You have " + config.getFormattedGracePeriod() + " of protection.")
            .color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD));

        resetWorldBorder();
        saveData();
    }

    /** Gives a player their grace window the first time they are seen after the event starts, and shows their boss bar. */
    public void initializePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        if (!graceEnds.containsKey(uuid)) {
            graceEnds.put(uuid, System.currentTimeMillis() + config.getGracePeriodMillis());
            saveData();
        }
        refreshPlayer(player);
    }

    /** Shows or hides the boss bar to match the player's current grace state. */
    private void refreshPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        long remaining = getRemainingMillis(uuid);
        if (remaining <= 0) {
            protectedOnline.remove(uuid);
            hideBossBar(player);
            return;
        }
        protectedOnline.add(uuid);
        if (config.enableBossBar && !playerBossBars.containsKey(uuid)) {
            BossBar bossBar = Bukkit.createBossBar("Grace Period: " + formatTime(remaining), BarColor.GREEN, BarStyle.SOLID);
            bossBar.addPlayer(player);
            playerBossBars.put(uuid, bossBar);
        }
        updateBossBar(uuid, remaining);
    }

    public void removePlayer(Player player) {
        protectedOnline.remove(player.getUniqueId());
        hideBossBar(player);
    }

    private void hideBossBar(Player player) {
        BossBar bossBar = playerBossBars.remove(player.getUniqueId());
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    /** Runs once a second: updates boss bars and announces expiry for players who are online when it happens. */
    public void tick() {
        if (!eventStarted) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (!graceEnds.containsKey(uuid)) continue;

            long remaining = getRemainingMillis(uuid);
            if (remaining > 0) {
                protectedOnline.add(uuid);
                updateBossBar(uuid, remaining);
            } else if (protectedOnline.remove(uuid)) {
                hideBossBar(player);
                player.showTitle(Title.title(
                    Component.text("Grace Period Expired!").color(NamedTextColor.RED).decorate(TextDecoration.BOLD),
                    Component.text("You are no longer protected from death").color(NamedTextColor.YELLOW)
                ));
                Bukkit.broadcast(Component.text(player.getName() + "'s grace period has ended!")
                    .color(NamedTextColor.RED));
            }
        }
    }

    private void updateBossBar(UUID uuid, long remaining) {
        BossBar bossBar = playerBossBars.get(uuid);
        if (bossBar == null) return;

        double progress = Math.max(0.0, Math.min(1.0, (double) remaining / config.getGracePeriodMillis()));
        bossBar.setProgress(progress);
        bossBar.setTitle("Grace Period: " + formatTime(remaining));
        if (progress > 0.5) {
            bossBar.setColor(BarColor.GREEN);
        } else if (progress > 0.25) {
            bossBar.setColor(BarColor.YELLOW);
        } else {
            bossBar.setColor(BarColor.RED);
        }
    }

    /** Puts the overworld border back to the vanilla default (it is a small lobby border before the event). */
    private void resetWorldBorder() {
        World world = Bukkit.getWorld("world");
        if (world != null) {
            world.getWorldBorder().reset();
        }
    }

    public void healPlayer(Player player) {
        var maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;
        player.setHealth(maxHealth);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
    }

    public boolean hasGracePeriod(UUID uuid) {
        if (!eventStarted) return true;
        return getRemainingMillis(uuid) > 0;
    }

    /** Milliseconds of grace left. Before the event starts this is the full default duration. */
    public long getRemainingMillis(UUID uuid) {
        if (!eventStarted) return config.getGracePeriodMillis();
        Long end = graceEnds.get(uuid);
        return end == null ? 0 : Math.max(0, end - System.currentTimeMillis());
    }

    /** Whether this player has been given a grace window since the event started. */
    public boolean isKnown(UUID uuid) {
        return graceEnds.containsKey(uuid);
    }

    /** Sets a player's grace to end {@code millis} from now (0 ends it immediately). */
    public void setPlayerTime(UUID uuid, long millis) {
        graceEnds.put(uuid, System.currentTimeMillis() + Math.max(0, millis));
        saveData();
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && eventStarted) {
            refreshPlayer(online);
        }
    }

    public void resetPlayerTime(UUID uuid) {
        setPlayerTime(uuid, config.getGracePeriodMillis());
    }

    public void resetEvent() {
        eventStarted = false;
        graceEnds.clear();
        protectedOnline.clear();
        for (BossBar bossBar : playerBossBars.values()) {
            bossBar.removeAll();
        }
        playerBossBars.clear();
        GracePeriodData.delete(plugin);
    }

    public boolean isEventStarted() {
        return eventStarted;
    }

    public ModConfig getConfig() {
        return config;
    }

    public String formatTime(long millis) {
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, secs);
        } else {
            return String.format("%ds", secs);
        }
    }

    private void loadData() {
        GracePeriodData.EventData data = GracePeriodData.load(plugin);
        this.eventStarted = data.eventStarted;
        for (Map.Entry<String, GracePeriodData.PlayerData> entry : data.players.entrySet()) {
            try {
                if (entry.getValue() == null) continue;
                graceEnds.put(UUID.fromString(entry.getKey()), entry.getValue().graceEndMillis);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in save data: " + entry.getKey());
            }
        }
    }

    public void saveData() {
        GracePeriodData.save(plugin, eventStarted, graceEnds);
    }
}
