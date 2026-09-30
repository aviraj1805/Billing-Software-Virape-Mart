package com.virpemart.billing.db;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;

/**
 * Makes a complete, consistent copy of the database into a new file using SQLite's
 * {@code VACUUM INTO}. It is safe to run while the app is open.
 */
public final class DatabaseBackup {

    private DatabaseBackup() {
    }

    /**
     * Copies the database to {@code target}.
     *
     * @throws IllegalStateException if {@code target} already exists (backups are never overwritten)
     */
    public static void snapshot(Database database, Path target) {
        Path absoluteTarget = target.toAbsolutePath().normalize();
        if (Files.exists(absoluteTarget)) {
            throw new IllegalStateException("Backup file already exists: " + absoluteTarget);
        }
        try {
            Files.createDirectories(absoluteTarget.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create backup folder " + absoluteTarget.getParent(), e);
        }
        database.executeOutsideTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("VACUUM INTO ?")) {
                statement.setString(1, absoluteTarget.toString());
                statement.executeUpdate();
            }
        });
    }
}
