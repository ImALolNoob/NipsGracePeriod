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
import java.util.Map;
import java.util.UUID;

public class GracePeriodManager {
    private final Hardcore_grace_period plugin;
    private final ModConfig config;
    private final Map<UUID, Long> playerGracePeriods = new HashMap<>();
    private final Map<UUID, BossBar> playerBossBars = new HashMap<>();
    private boolean eventStarted = false;
    private long eventStartTime = 0;
    private long tickCount = 0;

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
            eventStartTime = overworld.getFullTime();
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

        setWorldBorder();
        saveData();
    }

    public void initializePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        if (!playerGracePeriods.containsKey(uuid)) {
            playerGracePeriods.put(uuid, config.getGracePeriodTicks());
            saveData();
        }

        if (config.enableBossBar && !playerBossBars.containsKey(uuid)) {
            BossBar bossBar = Bukkit.createBossBar(
                "Grace Period: " + formatTime(getRemainingTime(uuid)),
                BarColor.GREEN,
                BarStyle.SOLID
            );
            bossBar.addPlayer(player);
            playerBossBars.put(uuid, bossBar);
        }
    }

    public void removePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        BossBar bossBar = playerBossBars.remove(uuid);
        if (bossBar != null) {
            bossBar.removePlayer(player);
        }
    }

    public void tick() {
        if (!eventStarted) return;
        tickCount++;

        boolean dataChanged = false;

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (!playerGracePeriods.containsKey(uuid)) continue;

            long remaining = playerGracePeriods.get(uuid);
            if (remaining > 0) {
                remaining--;
                playerGracePeriods.put(uuid, remaining);
                dataChanged = true;

                BossBar bossBar = playerBossBars.get(uuid);
                if (bossBar != null) {
                    if (remaining <= 0) {
                        bossBar.removePlayer(player);
                        playerBossBars.remove(uuid);

                        player.showTitle(Title.title(
                            Component.text("Grace Period Expired!").color(NamedTextColor.RED).decorate(TextDecoration.BOLD),
                            Component.text("You are no longer protected from death").color(NamedTextColor.YELLOW)
                        ));

                        Bukkit.broadcast(Component.text(player.getName() + "'s grace period has ended!")
                            .color(NamedTextColor.RED));
                    } else {
                        float progress = (float) remaining / config.getGracePeriodTicks();
                        bossBar.setProgress(Math.max(0.0, progress));
                        bossBar.setTitle("Grace Period: " + formatTime(remaining));

                        if (progress > 0.5f) {
                            bossBar.setColor(BarColor.GREEN);
                        } else if (progress > 0.25f) {
                            bossBar.setColor(BarColor.YELLOW);
                        } else {
                            bossBar.setColor(BarColor.RED);
                        }
                    }
                }
            } else {
                BossBar bossBar = playerBossBars.get(uuid);
                if (bossBar != null) {
                    bossBar.removePlayer(player);
                    playerBossBars.remove(uuid);
                }
            }
        }

        if (dataChanged && tickCount % 20 == 0) {
            saveData();
        }
    }

    private void setWorldBorder() {
        World world = Bukkit.getWorld("world");
        if (world != null) {
            world.getWorldBorder().setSize(30_000_000);
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
        return playerGracePeriods.getOrDefault(uuid, 0L) > 0;
    }

    public long getRemainingTime(UUID uuid) {
        if (!eventStarted) return config.getGracePeriodTicks();
        return playerGracePeriods.getOrDefault(uuid, 0L);
    }

    public void setPlayerTime(UUID uuid, long ticks) {
        playerGracePeriods.put(uuid, ticks);
        saveData();
    }

    public void resetPlayerTime(UUID uuid) {
        playerGracePeriods.put(uuid, config.getGracePeriodTicks());
        saveData();
    }

    public void resetEvent() {
        eventStarted = false;
        eventStartTime = 0;
        playerGracePeriods.clear();
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

    public String formatTime(long ticks) {
        long seconds = ticks / 20;
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
        this.eventStartTime = data.eventStartTime;
        for (Map.Entry<String, GracePeriodData.PlayerData> entry : data.players.entrySet()) {
            try {
                UUID uuid = UUID.fromString(entry.getKey());
                playerGracePeriods.put(uuid, entry.getValue().remainingTime);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in save data: " + entry.getKey());
            }
        }
    }

    public void saveData() {
        GracePeriodData.save(plugin, eventStarted, eventStartTime, playerGracePeriods);
    }
}
