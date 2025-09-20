package org.novato.hardcore_grace_period.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("hardcore-grace-period.json");

    public int gracePeriodHours = 3;
    public int gracePeriodMinutes = 0;
    public boolean enableBossBar = true;
    public boolean enableOverheadDisplay = true;
    public boolean debugMode = false;

    public static ModConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                return GSON.fromJson(json, ModConfig.class);
            } catch (IOException e) {
                System.err.println("Failed to load config, using defaults: " + e.getMessage());
            }
        }

        ModConfig config = new ModConfig();
        config.save();
        return config;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            String json = GSON.toJson(this);
            Files.writeString(CONFIG_PATH, json);
        } catch (IOException e) {
            System.err.println("Failed to save config: " + e.getMessage());
        }
    }

    public long getGracePeriodTicks() {
        return (gracePeriodHours * 60L * 60L + gracePeriodMinutes * 60L) * 20L; // Convert hours and minutes to ticks (20 ticks per second)
    }

    public String getFormattedGracePeriod() {
        if (gracePeriodHours > 0 && gracePeriodMinutes > 0) {
            return gracePeriodHours + " hours " + gracePeriodMinutes + " minutes";
        } else if (gracePeriodHours > 0) {
            return gracePeriodHours + " hours";
        } else {
            return gracePeriodMinutes + " minutes";
        }
    }
}