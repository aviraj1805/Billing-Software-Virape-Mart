package com.virpemart.billing.db;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Restoring a backup in two steps, because the data file cannot be swapped while the app is using it:
 * <ol>
 *   <li>{@link #stage}: the checked backup is copied next to the data file as "restore-pending.db", then the app
 *       closes;</li>
 *   <li>{@link #finish}: when the app starts again, before the database is opened, the current data file is moved
 *       into the backups folder as "replaced-...db" and the backup takes its place.</li>
 * </ol>
 * Nothing is ever deleted: the replaced data stays in the backups folder.
 */
public final class PendingRestore {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");
    private static final String[] DATABASE_PARTS = {"", "-wal", "-shm"};

    private PendingRestore() {
    }

    static Path pendingFile(Path databaseFile) {
        return databaseFile.resolveSibling("restore-pending.db");
    }

    static Path noteFile(Path databaseFile) {
        return databaseFile.resolveSibling("restore-pending.txt");
    }

    /** True if a backup is waiting to be put in place at the next start. */
    public static boolean isPending(Path databaseFile) {
        return Files.exists(pendingFile(databaseFile));
    }

    /**
     * Copies a checked backup next to the data file, to be put in place at the next start.
     *
     * @param note a short description for the audit log, for example the backup's file name
     */
    public static void stage(Path backup, Path databaseFile, String note) throws IOException {
        Files.copy(backup, pendingFile(databaseFile), StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(noteFile(databaseFile), note, StandardCharsets.UTF_8);
    }

    /**
     * Puts a waiting backup in place. Call this before the database is opened.
     *
     * @return what was restored and where the old data was kept, or empty if no restore was waiting
     */
    public static Optional<String> finish(Path databaseFile, Path backupsDir, Clock clock) throws IOException {
        Path pending = pendingFile(databaseFile);
        if (!Files.exists(pending)) {
            return Optional.empty();
        }
        Files.createDirectories(backupsDir);
        Path replaced = backupsDir.resolve("replaced-" + LocalDateTime.now(clock).format(STAMP) + ".db");
        for (String part : DATABASE_PARTS) {
            Path current = Path.of(databaseFile + part);
            if (Files.exists(current)) {
                Files.move(current, Path.of(replaced + part));
            }
        }
        Files.move(pending, databaseFile);
        Path noteFile = noteFile(databaseFile);
        String note = Files.exists(noteFile) ? Files.readString(noteFile, StandardCharsets.UTF_8).strip() : "a backup";
        Files.deleteIfExists(noteFile);
        return Optional.of("Restored " + note + ". The data it replaced was kept as " + replaced.getFileName() + ".");
    }
}
