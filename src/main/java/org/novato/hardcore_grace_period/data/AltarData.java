package org.novato.hardcore_grace_period.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.novato.hardcore_grace_period.Hardcore_grace_period;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * On-disk state for the resurrection altar ({@code altar_data.json}): where the altar
 * block is, how many revivals have happened (the difficulty tier), the current offering
 * and how much of it has been donated, plus the sets of fallen and pending-revive players.
 * Materials are stored by their enum name; unknown names are skipped on load.
 */
public class AltarData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class StateData {
        /** Registered altar block location; null until an admin sets one. */
        public String altarWorld;
        public Integer altarX;
        public Integer altarY;
        public Integer altarZ;

        /** How many players have been revived so far. Drives the difficulty tier. */
        public int revivalsDone;

        /** The current offering: material name -> amount required. */
        public Map<String, Integer> offering = new HashMap<>();
        /** How much of the current offering has been donated: material name -> amount. */
        public Map<String, Integer> donated = new HashMap<>();

        /** UUIDs of players who have died for good and can be revived. */
        public Set<String> deadPlayers = new HashSet<>();
        /** UUIDs chosen for revival while offline; restored to survival on their next join. */
        public Set<String> pendingRevive = new HashSet<>();
    }

    private static Path getDataPath(Hardcore_grace_period plugin) {
        return plugin.getDataFolder().toPath().resolve("altar_data.json");
    }

    public static StateData load(Hardcore_grace_period plugin) {
        Path path = getDataPath(plugin);
        if (!Files.exists(path)) {
            return new StateData();
        }
        try {
            StateData data = GSON.fromJson(Files.readString(path), StateData.class);
            if (data == null) return new StateData();
            if (data.offering == null) data.offering = new HashMap<>();
            if (data.donated == null) data.donated = new HashMap<>();
            if (data.deadPlayers == null) data.deadPlayers = new HashSet<>();
            if (data.pendingRevive == null) data.pendingRevive = new HashSet<>();
            return data;
        } catch (IOException | JsonParseException e) {
            JsonFiles.backUpBroken(plugin, path, e);
            return new StateData();
        }
    }

    public static void save(Hardcore_grace_period plugin, StateData data) {
        try {
            JsonFiles.writeAtomically(getDataPath(plugin), GSON.toJson(data));
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save altar data: " + e.getMessage());
        }
    }

    public static void delete(Hardcore_grace_period plugin) {
        try {
            Files.deleteIfExists(getDataPath(plugin));
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to delete altar data: " + e.getMessage());
        }
    }
}
