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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Grace counts down only while a player is online. Each player has a budget of
 * remaining milliseconds ({@link #graceRemaining}); for a player who is currently
 * online we also keep the moment counting last resumed ({@link #onlineSince}), so
 * the live remaining is the stored budget minus the time elapsed online since then.
 * The clock is frozen while the player is offline and across restarts.
 */
public class GracePeriodManager {
    private final Hardcore_grace_period plugin;
    private final ModConfig config;
    /** Remaining grace in milliseconds, "committed" as of the last {@link #commit}. */
    private final Map<UUID, Long> graceRemaining = new HashMap<>();
    /** For online players only: epoch millis when their countdown last resumed. Not persisted. */
    private final Map<UUID, Long> onlineSince = new HashMap<>();
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

    /**
     * Gives a player their grace budget the first time they are seen after the event starts,
     * resumes their countdown, and shows their boss bar.
     */
    public void initializePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        if (!graceRemaining.containsKey(uuid)) {
            graceRemaining.put(uuid, config.getGracePeriodMillis());
            saveData();
        }
        // Resume counting from now; time spent offline is never charged.
        onlineSince.put(uuid, System.currentTimeMillis());
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

    /** On quit: freeze the player's remaining grace (stop the clock) and clean up their boss bar. */
    public void removePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        commit(uuid);
        onlineSince.remove(uuid);
        protectedOnline.remove(uuid);
        hideBossBar(player);
        saveData();
    }

    /**
     * Folds the time elapsed online since the last resume into the stored budget and
     * restarts the clock from now. No-op for an offline player (no {@link #onlineSince} entry).
     */
    private void commit(UUID uuid) {
        Long since = onlineSince.get(uuid);
        if (since == null) return;
        long now = System.currentTimeMillis();
        Long remaining = graceRemaining.get(uuid);
        if (remaining != null) {
            graceRemaining.put(uuid, Math.max(0, remaining - (now - since)));
        }
        onlineSince.put(uuid, now);
    }

    private void hideBossBar(Player player) {
        BossBar bossBar = playerBossBars.remove(player.getUniqueId());
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    /** Runs once a second: ticks down online players, updates boss bars and announces expiry. */
    public void tick() {
        if (!eventStarted) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (!graceRemaining.containsKey(uuid)) continue;

            commit(uuid);
            long remaining = graceRemaining.get(uuid);
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

    /**
     * Milliseconds of grace left right now. Before the event starts this is the full default
     * duration. For an online player it counts the time elapsed online since the last resume.
     */
    public long getRemainingMillis(UUID uuid) {
        if (!eventStarted) return config.getGracePeriodMillis();
        Long remaining = graceRemaining.get(uuid);
        if (remaining == null) return 0;
        Long since = onlineSince.get(uuid);
        long live = since == null ? remaining : remaining - (System.currentTimeMillis() - since);
        return Math.max(0, live);
    }

    /** Whether this player has been given a grace budget since the event started. */
    public boolean isKnown(UUID uuid) {
        return graceRemaining.containsKey(uuid);
    }

    /** Sets a player's remaining grace to {@code millis} (0 ends it immediately), counting from now. */
    public void setPlayerTime(UUID uuid, long millis) {
        graceRemaining.put(uuid, Math.max(0, millis));
        if (onlineSince.containsKey(uuid)) {
            onlineSince.put(uuid, System.currentTimeMillis());
        }
        saveData();
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && eventStarted) {
            refreshPlayer(online);
        }
    }

    public void resetPlayerTime(UUID uuid) {
        setPlayerTime(uuid, config.getGracePeriodMillis());
    }

    /**
     * Adds {@code deltaMillis} to a single player's remaining grace (negative subtracts),
     * clamped at zero. Works for offline players too; their clock stays frozen.
     */
    public void addPlayerTime(UUID uuid, long deltaMillis) {
        applyDelta(uuid, deltaMillis);
        saveData();
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && eventStarted) {
            refreshPlayer(online);
        }
    }

    /**
     * Adds {@code deltaMillis} to every online player's remaining grace (negative subtracts).
     * Does not touch the configured default or offline players. Returns how many were changed.
     */
    public int addTimeToOnlinePlayers(long deltaMillis) {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyDelta(player.getUniqueId(), deltaMillis);
            if (eventStarted) {
                refreshPlayer(player);
            }
            count++;
        }
        saveData();
        return count;
    }

    /** Folds elapsed online time in, then adds the delta to the stored budget (clamped at zero). */
    private void applyDelta(UUID uuid, long deltaMillis) {
        commit(uuid);
        long current = graceRemaining.getOrDefault(uuid, 0L);
        graceRemaining.put(uuid, Math.max(0, current + deltaMillis));
    }

    public void resetEvent() {
        eventStarted = false;
        graceRemaining.clear();
        onlineSince.clear();
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
                graceRemaining.put(UUID.fromString(entry.getKey()), entry.getValue().graceRemainingMillis);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in save data: " + entry.getKey());
            }
        }
    }

    public void saveData() {
        // Fold any time spent online so far into the stored budget before persisting.
        for (UUID uuid : new ArrayList<>(onlineSince.keySet())) {
            commit(uuid);
        }
        GracePeriodData.save(plugin, eventStarted, graceRemaining);
    }
}
