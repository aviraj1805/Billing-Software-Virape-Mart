package com.virpemart.billing.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseTest {

    @TempDir
    Path temp;

    private Database database;

    @BeforeEach
    void setUp() {
        database = TestDatabases.empty(temp);
        database.runInTransaction(c -> execute(c, "CREATE TABLE notes (id INTEGER PRIMARY KEY, text TEXT NOT NULL)"));
    }

    @Test
    void everyConnectionUsesTheSafeSettings() throws SQLException {
        try (Connection connection = database.open()) {
            assertEquals("1", pragma(connection, "foreign_keys"));
            assertEquals("wal", pragma(connection, "journal_mode"));
            assertEquals("5000", pragma(connection, "busy_timeout"));
            assertEquals("2", pragma(connection, "synchronous"), "2 means FULL");
        }
    }

    @Test
    void commitsWhenWorkSucceeds() {
        database.runInTransaction(c -> execute(c, "INSERT INTO notes (text) VALUES ('kept')"));

        assertEquals(1, countNotes());
    }

    @Test
    void undoesEverythingWhenWorkThrowsARuntimeException() {
        IllegalStateException failure = new IllegalStateException("stop");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                database.runInTransaction(c -> {
                    execute(c, "INSERT INTO notes (text) VALUES ('first')");
                    execute(c, "INSERT INTO notes (text) VALUES ('second')");
                    throw failure;
                }));

        assertSame(failure, thrown, "the original exception reaches the caller unchanged");
        assertEquals(0, countNotes(), "both inserts were undone");
    }

    @Test
    void undoesEverythingWhenSqlFails() {
        assertThrows(DatabaseException.class, () ->
                database.runInTransaction(c -> {
                    execute(c, "INSERT INTO notes (text) VALUES ('first')");
                    execute(c, "INSERT INTO notes (text) VALUES (NULL)"); // breaks NOT NULL
                }));

        assertEquals(0, countNotes());
    }

    @Test
    void backupCopiesTheDataAndNeverOverwrites() throws SQLException {
        database.runInTransaction(c -> execute(c, "INSERT INTO notes (text) VALUES ('saved')"));
        Path backupFile = temp.resolve("backups").resolve("copy.db");

        DatabaseBackup.snapshot(database, backupFile);

        assertTrue(Files.exists(backupFile));
        Database copy = new Database(backupFile);
        assertEquals(1, (int) copy.query(c -> {
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM notes")) {
                rs.next();
                return rs.getInt(1);
            }
        }));
        assertThrows(IllegalStateException.class, () -> DatabaseBackup.snapshot(database, backupFile));
    }

    private int countNotes() {
        return database.query(c -> {
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM notes")) {
                rs.next();
                return rs.getInt(1);
            }
        });
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static String pragma(Connection connection, String name) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA " + name)) {
            rs.next();
            return rs.getString(1);
        }
    }
}
