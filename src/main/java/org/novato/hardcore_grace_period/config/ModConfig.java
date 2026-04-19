package org.novato.hardcore_grace_period.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.novato.hardcore_grace_period.Hardcore_grace_period;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public int gracePeriodHours = 3;
    public int gracePeriodMinutes = 0;
    public boolean enableBossBar = true;
    public boolean debugMode = false;

    public static ModConfig load(Hardcore_grace_period plugin) {
        Path configPath = getConfigPath(plugin);
        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                ModConfig cfg = GSON.fromJson(json, ModConfig.class);
                if (cfg != null) return cfg;
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to load config, using defaults: " + e.getMessage());
            }
        }
        ModConfig config = new ModConfig();
        config.save(plugin);
        return config;
    }

    public void save(Hardcore_grace_period plugin) {
        try {
            Path configPath = getConfigPath(plugin);
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, GSON.toJson(this));
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save config: " + e.getMessage());
        }
    }

    private static Path getConfigPath(Hardcore_grace_period plugin) {
        return plugin.getDataFolder().toPath().resolve("config.json");
    }

    public long getGracePeriodTicks() {
        return (gracePeriodHours * 60L * 60L + gracePeriodMinutes * 60L) * 20L;
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
