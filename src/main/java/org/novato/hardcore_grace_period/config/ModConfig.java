package org.novato.hardcore_grace_period.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.novato.hardcore_grace_period.Hardcore_grace_period;
import org.novato.hardcore_grace_period.data.JsonFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public int gracePeriodHours = 3;
    public int gracePeriodMinutes = 0;
    public boolean enableBossBar = true;
    public boolean debugMode = false;

    // Title shown to everyone when a player dies for good (their grace period is over).
    // %player% = the player's name. Colour codes use §. Times are in ticks (20 ticks = 1 second).
    public boolean deathTitleEnabled = true;
    public String deathTitle = "§4☠ §6%player%";
    public String deathSubtitle = "§7§ohas been lost to the 7 Seas";
    public int deathTitleFadeIn = 20;
    public int deathTitleStay = 80;
    public int deathTitleFadeOut = 30;
    // Any sound ID; empty for no sound. Played at each player's own position.
    public String deathSound = "minecraft:block.conduit.deactivate";
    public float deathSoundVolume = 0.6f;
    public float deathSoundPitch = 1.0f;

    public static ModConfig load(Hardcore_grace_period plugin) {
        Path configPath = getConfigPath(plugin);
        if (Files.exists(configPath)) {
            try {
                ModConfig cfg = GSON.fromJson(Files.readString(configPath), ModConfig.class);
                if (cfg != null) {
                    cfg.save(plugin); // writes any newly added settings into an existing config
                    return cfg;
                }
            } catch (IOException | JsonParseException e) {
                JsonFiles.backUpBroken(plugin, configPath, e);
            }
        }
        ModConfig config = new ModConfig();
        config.save(plugin);
        return config;
    }

    public void save(Hardcore_grace_period plugin) {
        try {
            JsonFiles.writeAtomically(getConfigPath(plugin), GSON.toJson(this));
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save config: " + e.getMessage());
        }
    }

    private static Path getConfigPath(Hardcore_grace_period plugin) {
        return plugin.getDataFolder().toPath().resolve("config.json");
    }

    public long getGracePeriodMillis() {
        return (gracePeriodHours * 60L * 60L + gracePeriodMinutes * 60L) * 1000L;
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
