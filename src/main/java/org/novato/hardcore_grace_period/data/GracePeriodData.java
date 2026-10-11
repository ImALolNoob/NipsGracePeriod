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
        /** When this player's grace period ends, in epoch milliseconds (real time, keeps running while offline). */
        public long graceEndMillis;
        /** Old format (ticks left, online-only). Only read to migrate older data files, never written. */
        public Long remainingTime;

        public PlayerData(long graceEndMillis) {
            this.graceEndMillis = graceEndMillis;
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

    /** Older files stored ticks left; turn those into an end time counted from now. */
    private static void migrateOldFormat(EventData data) {
        long now = System.currentTimeMillis();
        for (PlayerData player : data.players.values()) {
            if (player != null && player.graceEndMillis == 0 && player.remainingTime != null) {
                player.graceEndMillis = now + Math.max(0, player.remainingTime) * 50L;
            }
        }
    }

    public static void save(Hardcore_grace_period plugin, boolean eventStarted, Map<UUID, Long> graceEnds) {
        EventData data = new EventData();
        data.eventStarted = eventStarted;
        for (Map.Entry<UUID, Long> entry : graceEnds.entrySet()) {
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
