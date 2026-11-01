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

    // ----- Resurrection altar -----
    // Grace granted to a revived player (counts down online-only, like everyone else's).
    public int altarRevivalGraceHours = 1;
    public int altarRevivalGraceMinutes = 0;
    // How many distinct item types the first offering asks for, and how many more each revival adds.
    public int altarBaseItemTypes = 4;
    public int altarItemTypesPerRevival = 1;
    public int altarMaxItemTypes = 8;
    // Per-type amount range for the first offering, before the per-revival growth below is applied.
    // Deliberately large: a single revival should take the whole server pitching in (96-256 = up to 4 stacks each).
    public int altarBaseCountMin = 96;
    public int altarBaseCountMax = 256;
    // Each revival multiplies the amount range by (1 + revivalsDone * growth). 1.0 = the 2nd costs 2x, 3rd 3x, 4th 4x...
    public double altarCountGrowthPerRevival = 1.0;
    // Hard cap per item type so late tiers stay (barely) achievable (3456 = a full double chest of stacks).
    public int altarMaxCountPerType = 3456;
    // Items the altar may ask for. A mix of valuable and tedious makes each offering feel random.
    public java.util.List<String> altarMaterialPool = new java.util.ArrayList<>(java.util.List.of(
        "DIAMOND", "IRON_INGOT", "GOLD_INGOT", "EMERALD", "COPPER_INGOT", "NETHERITE_SCRAP",
        "ANCIENT_DEBRIS", "OBSIDIAN", "ENDER_PEARL", "BLAZE_ROD", "GHAST_TEAR", "GOLDEN_APPLE",
        "EXPERIENCE_BOTTLE", "REDSTONE", "LAPIS_LAZULI", "QUARTZ", "GLOWSTONE_DUST", "SLIME_BALL",
        "HONEY_BOTTLE", "GUNPOWDER", "STRING", "BONE", "ROTTEN_FLESH", "PHANTOM_MEMBRANE",
        "PRISMARINE_CRYSTALS", "NAUTILUS_SHELL", "AMETHYST_SHARD", "ECHO_SHARD", "HONEYCOMB",
        "SUGAR_CANE", "WHEAT", "LEATHER"
    ));

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

    /** Grace, in milliseconds, granted to a player revived at the altar. */
    public long getAltarRevivalGraceMillis() {
        return (altarRevivalGraceHours * 60L * 60L + altarRevivalGraceMinutes * 60L) * 1000L;
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
