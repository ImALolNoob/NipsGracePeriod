package org.novato.hardcore_grace_period;

import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.novato.hardcore_grace_period.config.ModConfig;
import org.novato.hardcore_grace_period.data.GracePeriodData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GracePeriodManager {
    private static GracePeriodManager instance;
    private final ModConfig config;
    private final Map<UUID, Long> playerGracePeriods = new HashMap<>();
    private final Map<UUID, ServerBossBar> playerBossBars = new HashMap<>();
    private boolean eventStarted = false;
    private long eventStartTime = 0;
    private MinecraftServer server;

    private GracePeriodManager() {
        this.config = ModConfig.load();
        loadData();
    }

    public static GracePeriodManager getInstance() {
        if (instance == null) {
            instance = new GracePeriodManager();
        }
        return instance;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    public void startEvent() {
        if (eventStarted) return;

        eventStarted = true;
        eventStartTime = server.getWorld(World.OVERWORLD).getTime();

        // Set time to day (0 ticks)
        server.getWorld(World.OVERWORLD).setTimeOfDay(0);

        // Initialize grace periods for all online players
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            initializePlayer(player);
            // Heal player
            player.setHealth(player.getMaxHealth());
            player.getHungerManager().setFoodLevel(20);
            player.getHungerManager().setSaturationLevel(20.0f);

        }

        server.getPlayerManager().broadcast(
            Text.literal("Grace Period Event Started! You have " + config.getFormattedGracePeriod() + " of protection.")
                .formatted(Formatting.GREEN, Formatting.BOLD),
            false
        );

        saveData();
    }

    public void initializePlayer(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();

        // Only set default time if player doesn't already have a saved time
        if (!playerGracePeriods.containsKey(playerUuid)) {
            playerGracePeriods.put(playerUuid, config.getGracePeriodTicks());
            saveData(); // Save immediately when new player joins
        }

        if (config.enableBossBar && !playerBossBars.containsKey(playerUuid)) {
            ServerBossBar bossBar = new ServerBossBar(
                Text.literal("Grace Period: " + formatTime(getRemainingTime(playerUuid))),
                BossBar.Color.GREEN,
                BossBar.Style.PROGRESS
            );
            bossBar.addPlayer(player);
            playerBossBars.put(playerUuid, bossBar);
        }

    }

    public void removePlayer(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        ServerBossBar bossBar = playerBossBars.remove(playerUuid);
        if (bossBar != null) {
            bossBar.removePlayer(player);
        }

    }

    public void tick() {
        if (!eventStarted || server == null) return;

        boolean dataChanged = false;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID playerUuid = player.getUuid();

            if (playerGracePeriods.containsKey(playerUuid)) {
                long remaining = playerGracePeriods.get(playerUuid);

                if (remaining > 0) {
                    remaining--;
                    playerGracePeriods.put(playerUuid, remaining);
                    dataChanged = true;

                    // Update boss bar
                    ServerBossBar bossBar = playerBossBars.get(playerUuid);
                    if (bossBar != null) {
                        if (remaining <= 0) {
                            // Grace period expired - remove boss bar and show warnings
                            bossBar.removePlayer(player);
                            playerBossBars.remove(playerUuid);

                            // Show title warning to player
                            player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(
                                Text.literal("Grace Period Expired!").formatted(Formatting.RED, Formatting.BOLD)
                            ));
                            player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.SubtitleS2CPacket(
                                Text.literal("You are no longer protected from death").formatted(Formatting.YELLOW)
                            ));

                            // Broadcast to chat
                            server.getPlayerManager().broadcast(
                                Text.literal(player.getName().getString() + "'s grace period has ended!")
                                    .formatted(Formatting.RED),
                                false
                            );
                        } else {
                            // Update boss bar display
                            float progress = (float) remaining / config.getGracePeriodTicks();
                            bossBar.setPercent(Math.max(0, progress));
                            bossBar.setName(Text.literal("Grace Period: " + formatTime(remaining)));

                            // Change color as time runs out
                            if (progress > 0.5f) {
                                bossBar.setColor(BossBar.Color.GREEN);
                            } else if (progress > 0.25f) {
                                bossBar.setColor(BossBar.Color.YELLOW);
                            } else {
                                bossBar.setColor(BossBar.Color.RED);
                            }
                        }
                    }

                } else {
                    // Timer already at 0, make sure boss bar is removed
                    ServerBossBar bossBar = playerBossBars.get(playerUuid);
                    if (bossBar != null) {
                        bossBar.removePlayer(player);
                        playerBossBars.remove(playerUuid);
                    }
                }
            }
        }

        // Save data every 20 ticks (1 second) if data changed
        if (dataChanged && server.getTicks() % 20 == 0) {
            saveData();
        }
    }

    public boolean hasGracePeriod(UUID playerUuid) {
        if (!eventStarted) return true; // Before event starts, all players have grace
        return playerGracePeriods.getOrDefault(playerUuid, 0L) > 0;
    }

    public long getRemainingTime(UUID playerUuid) {
        if (!eventStarted) return config.getGracePeriodTicks();
        return playerGracePeriods.getOrDefault(playerUuid, 0L);
    }

    public void setPlayerTime(UUID playerUuid, long ticks) {
        playerGracePeriods.put(playerUuid, ticks);
        saveData();
    }

    public void resetPlayerTime(UUID playerUuid) {
        playerGracePeriods.put(playerUuid, config.getGracePeriodTicks());
        saveData();
    }

    public void resetEvent() {
        eventStarted = false;
        eventStartTime = 0;
        playerGracePeriods.clear();

        // Clear all boss bars
        for (ServerBossBar bossBar : playerBossBars.values()) {
            bossBar.clearPlayers();
        }
        playerBossBars.clear();


        // Delete save data
        GracePeriodData.delete();
    }

    public boolean isEventStarted() {
        return eventStarted;
    }

    public ModConfig getConfig() {
        return config;
    }

    private String formatTime(long ticks) {
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
        GracePeriodData.EventData data = GracePeriodData.load();
        this.eventStarted = data.eventStarted;
        this.eventStartTime = data.eventStartTime;

        // Load player data
        for (Map.Entry<String, GracePeriodData.PlayerData> entry : data.players.entrySet()) {
            try {
                UUID uuid = UUID.fromString(entry.getKey());
                playerGracePeriods.put(uuid, entry.getValue().remainingTime);
            } catch (IllegalArgumentException e) {
                System.err.println("Invalid UUID in save data: " + entry.getKey());
            }
        }
    }

    public void saveData() {
        GracePeriodData.save(eventStarted, eventStartTime, playerGracePeriods);
    }
}