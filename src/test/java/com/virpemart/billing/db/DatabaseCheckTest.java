package com.virpemart.billing.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseCheckTest {

    @TempDir
    Path temp;

    @Test
    void theAppsOwnDatabaseIsGood() {
        Database database = TestDatabases.migrated(temp);

        DatabaseCheck.Result result = DatabaseCheck.inspect(database.file());

        assertTrue(result.ok());
        assertEquals(4, result.schemaVersion());
        assertEquals(0, result.billCount());
        assertEquals(null, result.lastBillAt());
    }

    @Test
    void otherFilesAreRecognised() throws IOException {
        assertEquals(DatabaseCheck.Problem.NOT_A_DATABASE, DatabaseCheck.inspect(temp.resolve("missing.db")).problem());
        Path text = Files.writeString(temp.resolve("text.db"), "hello, this is not a database");
        assertEquals(DatabaseCheck.Problem.NOT_A_DATABASE, DatabaseCheck.inspect(text).problem());

        Database other = new Database(temp.resolve("other.db"));
        other.runInTransaction(c -> {
            try (Statement s = c.createStatement()) {
                s.executeUpdate("CREATE TABLE notes (text TEXT)");
            }
        });
        assertEquals(DatabaseCheck.Problem.NOT_OURS, DatabaseCheck.inspect(other.file()).problem());
    }

    @Test
    void checkingNeverChangesTheFile() throws IOException {
        Path text = Files.writeString(temp.resolve("text.db"), "hello");

        DatabaseCheck.inspect(text);

        assertEquals("hello", Files.readString(text));
    }

    @Test
    void pendingRestoreSwapsFilesAndKeepsTheOldOnes() throws IOException {
        Path data = Files.createDirectories(temp.resolve("data"));
        Path backups = temp.resolve("backups");
        Path databaseFile = data.resolve("virpemart.db");
        Files.writeString(databaseFile, "old data");
        Files.writeString(Path.of(databaseFile + "-wal"), "old wal");
        Path backup = Files.writeString(temp.resolve("backup.db"), "backup data");

        assertEquals(Optional.empty(), PendingRestore.finish(databaseFile, backups, TestDatabases.FIXED_CLOCK));

        PendingRestore.stage(backup, databaseFile, "backup backup.db");
        assertTrue(PendingRestore.isPending(databaseFile));
        String note = PendingRestore.finish(databaseFile, backups, TestDatabases.FIXED_CLOCK).orElseThrow();

        assertEquals("backup data", Files.readString(databaseFile));
        assertFalse(Files.exists(Path.of(databaseFile + "-wal")), "the old journal went with the old data");
        assertEquals("old data", Files.readString(backups.resolve("replaced-2026-09-30-100000.db")));
        assertEquals("old wal", Files.readString(backups.resolve("replaced-2026-09-30-100000.db-wal")));
        assertEquals("Restored backup backup.db. The data it replaced was kept as replaced-2026-09-30-100000.db.",
                note);
        assertFalse(PendingRestore.isPending(databaseFile));
        assertTrue(Files.exists(backup), "the chosen backup itself is left as it was");
    }
}
