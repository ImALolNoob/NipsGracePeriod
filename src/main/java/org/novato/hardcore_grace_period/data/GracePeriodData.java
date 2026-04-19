package org.novato.hardcore_grace_period.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
        public long remainingTime;
        public boolean hasGracePeriod;

        public PlayerData(long remainingTime, boolean hasGracePeriod) {
            this.remainingTime = remainingTime;
            this.hasGracePeriod = hasGracePeriod;
        }
    }

    public static class EventData {
        public boolean eventStarted;
        public long eventStartTime;
        public Map<String, PlayerData> players;

        public EventData() {
            this.eventStarted = false;
            this.eventStartTime = 0;
            this.players = new HashMap<>();
        }
    }

    private static Path getDataPath(Hardcore_grace_period plugin) {
        return plugin.getDataFolder().toPath().resolve("grace_period_data.json");
    }

    public static EventData load(Hardcore_grace_period plugin) {
        Path path = getDataPath(plugin);
        if (Files.exists(path)) {
            try {
                String json = Files.readString(path);
                EventData data = GSON.fromJson(json, EventData.class);
                return data != null ? data : new EventData();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to load grace period data: " + e.getMessage());
            }
        }
        return new EventData();
    }

    public static void save(Hardcore_grace_period plugin, boolean eventStarted, long eventStartTime, Map<UUID, Long> playerTimes) {
        try {
            Path path = getDataPath(plugin);
            Files.createDirectories(path.getParent());
            EventData data = new EventData();
            data.eventStarted = eventStarted;
            data.eventStartTime = eventStartTime;
            for (Map.Entry<UUID, Long> entry : playerTimes.entrySet()) {
                data.players.put(entry.getKey().toString(), new PlayerData(entry.getValue(), entry.getValue() > 0));
            }
            Files.writeString(path, GSON.toJson(data));
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
