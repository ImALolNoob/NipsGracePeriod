package org.novato.hardcore_grace_period.data;

import org.novato.hardcore_grace_period.Hardcore_grace_period;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

/** Shared helpers for the plugin's JSON files: safe writes and handling of unreadable files. */
public final class JsonFiles {
    private JsonFiles() {
    }

    /** Writes to a temp file first, then swaps it in, so a crash mid-write can't leave a half-written file. */
    public static void writeAtomically(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temp, content);
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Keeps a copy of a file that couldn't be read and logs a loud warning, so the plugin can carry on with defaults. */
    public static void backUpBroken(Hardcore_grace_period plugin, Path path, Exception cause) {
        Path backup = path.resolveSibling(path.getFileName() + ".broken-" + System.currentTimeMillis());
        try {
            Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException copyError) {
            backup = null;
        }
        plugin.getLogger().log(Level.SEVERE, "==========================================================");
        plugin.getLogger().log(Level.SEVERE, "Could not read " + path.getFileName() + ": " + cause.getMessage());
        plugin.getLogger().log(Level.SEVERE, backup != null
            ? "A copy was saved as " + backup.getFileName() + ". Using defaults instead."
            : "Could not make a backup copy. Using defaults instead.");
        plugin.getLogger().log(Level.SEVERE, "==========================================================");
    }
}
