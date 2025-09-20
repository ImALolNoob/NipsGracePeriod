package org.novato.hardcore_grace_period.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GracePeriodData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DATA_PATH = FabricLoader.getInstance().getGameDir().resolve("grace_period_data.json");
    private static final Type MAP_TYPE = new TypeToken<Map<String, PlayerData>>(){}.getType();

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

    public static EventData load() {
        if (Files.exists(DATA_PATH)) {
            try {
                String json = Files.readString(DATA_PATH);
                EventData data = GSON.fromJson(json, EventData.class);
                return data != null ? data : new EventData();
            } catch (IOException e) {
                System.err.println("Failed to load grace period data: " + e.getMessage());
            }
        }
        return new EventData();
    }

    public static void save(boolean eventStarted, long eventStartTime, Map<UUID, Long> playerTimes) {
        try {
            EventData data = new EventData();
            data.eventStarted = eventStarted;
            data.eventStartTime = eventStartTime;

            for (Map.Entry<UUID, Long> entry : playerTimes.entrySet()) {
                String uuidStr = entry.getKey().toString();
                data.players.put(uuidStr, new PlayerData(entry.getValue(), entry.getValue() > 0));
            }

            String json = GSON.toJson(data);
            Files.writeString(DATA_PATH, json);
        } catch (IOException e) {
            System.err.println("Failed to save grace period data: " + e.getMessage());
        }
    }

    public static void delete() {
        try {
            Files.deleteIfExists(DATA_PATH);
        } catch (IOException e) {
            System.err.println("Failed to delete grace period data: " + e.getMessage());
        }
    }
}