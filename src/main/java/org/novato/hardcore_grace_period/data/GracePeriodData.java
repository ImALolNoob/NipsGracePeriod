package org.novato.hardcore_grace_period.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.novato.hardcore_grace_period.Hardcore_grace_period;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GracePeriodData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class PlayerData {
        /** Grace left for this player, in milliseconds. Only counts down while they are online. */
        public long graceRemainingMillis;
        /** Old format (absolute end time, kept running offline). Only read to migrate older files, never written. */
        public Long graceEndMillis;
        /** Oldest format (ticks left, online-only). Only read to migrate older files, never written. */
        public Long remainingTime;

        public PlayerData(long graceRemainingMillis) {
            this.graceRemainingMillis = graceRemainingMillis;
        }
    }

    public static class EventData {
        public boolean eventStarted;
        public Map<String, PlayerData> players;

        public EventData() {
            this.eventStarted = false;
            this.players = new HashMap<>();
        }
    }

    private static Path getDataPath(Hardcore_grace_period plugin) {
        return plugin.getDataFolder().toPath().resolve("grace_period_data.json");
    }

    public static EventData load(Hardcore_grace_period plugin) {
        Path path = getDataPath(plugin);
        if (!Files.exists(path)) {
            return new EventData();
        }
        try {
            EventData data = GSON.fromJson(Files.readString(path), EventData.class);
            if (data == null) return new EventData();
            if (data.players == null) data.players = new HashMap<>();
            migrateOldFormat(data);
            return data;
        } catch (IOException | JsonParseException e) {
            JsonFiles.backUpBroken(plugin, path, e);
            return new EventData();
        }
    }

    /** Convert older save formats into a remaining-millis budget. */
    private static void migrateOldFormat(EventData data) {
        long now = System.currentTimeMillis();
        for (PlayerData player : data.players.values()) {
            if (player == null || player.graceRemainingMillis != 0) continue;
            if (player.graceEndMillis != null) {
                // Absolute end time: whatever is left right now becomes the online budget.
                player.graceRemainingMillis = Math.max(0, player.graceEndMillis - now);
            } else if (player.remainingTime != null) {
                // Ticks left -> milliseconds.
                player.graceRemainingMillis = Math.max(0, player.remainingTime) * 50L;
            }
        }
    }

    public static void save(Hardcore_grace_period plugin, boolean eventStarted, Map<UUID, Long> graceRemaining) {
        EventData data = new EventData();
        data.eventStarted = eventStarted;
        for (Map.Entry<UUID, Long> entry : graceRemaining.entrySet()) {
            data.players.put(entry.getKey().toString(), new PlayerData(entry.getValue()));
        }
        try {
            JsonFiles.writeAtomically(getDataPath(plugin), GSON.toJson(data));
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save grace period data: " + e.getMessage());
        }
    }

    public static void delete(Hardcore_grace_period plugin) {
        try {
            Files.deleteIfExists(getDataPath(plugin));
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to delete grace period data: " + e.getMessage());
        }
    }
}
