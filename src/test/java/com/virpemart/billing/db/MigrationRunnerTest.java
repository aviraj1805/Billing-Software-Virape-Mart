package com.virpemart.billing.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrationRunnerTest {

    private static final String SETS = "db/test-migrations/";

    @TempDir
    Path temp;

    private Database database;
    private Path backups;

    @BeforeEach
    void setUp() {
        database = TestDatabases.empty(temp);
        backups = temp.resolve("backups");
    }

    private MigrationRunner runner(String set) {
        return new MigrationRunner(database, SETS + set, backups, TestDatabases.FIXED_CLOCK);
    }

    @Test
    void newDatabaseGetsAllMigrationsWithoutABackup() {
        MigrationRunner.Result result = runner("v1-v2").migrate();

        assertEquals(0, result.fromVersion());
        assertEquals(2, result.toVersion());
        assertTrue(result.backupFile().isEmpty(), "nothing to back up in a brand-new database");
        assertTrue(tableExists("alpha"));
        assertTrue(tableExists("beta"));
        assertEquals(2, count("SELECT COUNT(*) FROM schema_version"));
    }

    @Test
    void runningAgainChangesNothing() {
        runner("v1-v2").migrate();

        MigrationRunner.Result again = runner("v1-v2").migrate();

        assertFalse(again.changed());
        assertEquals(1, count("SELECT COUNT(*) FROM alpha"), "V1's insert did not run twice");
    }

    @Test
    void upgradeBacksUpTheExistingDatabaseFirst() {
        runner("v1-only").migrate();

        MigrationRunner.Result result = runner("v1-v2").migrate();

        assertEquals(1, result.fromVersion());
        assertEquals(2, result.toVersion());
        Path backup = result.backupFile().orElseThrow();
        assertTrue(Files.exists(backup));
        assertEquals("pre-upgrade-v1-to-v2-20260930-100000.db", backup.getFileName().toString());
    }

    @Test
    void editedMigrationIsRefused() {
        runner("v1-only").migrate();

        MigrationException error = assertThrows(MigrationException.class, () -> runner("v1-edited").migrate());

        assertTrue(error.getMessage().contains("was changed after it was applied"), error.getMessage());
    }

    @Test
    void failingMigrationIsUndoneCompletely() {
        assertThrows(MigrationException.class, () -> runner("broken-v2").migrate());

        assertTrue(tableExists("alpha"), "V1 succeeded and stays");
        assertFalse(tableExists("gamma"), "V2 failed half-way and was fully undone");
        assertEquals(1, count("SELECT MAX(version) FROM schema_version"));
    }

    @Test
    void databaseFromNewerAppIsRefused() {
        runner("v1-v2").migrate();

        MigrationException error = assertThrows(MigrationException.class, () -> runner("v1-only").migrate());

        assertTrue(error.getMessage().contains("newer version"), error.getMessage());
    }

    @Test
    void foreignDatabaseIsNeverTouched() {
        database.runInTransaction(c -> {
            try (Statement s = c.createStatement()) {
                s.executeUpdate("CREATE TABLE something_else (id INTEGER)");
            }
        });

        MigrationException error = assertThrows(MigrationException.class, () -> runner("v1-v2").migrate());

        assertTrue(error.getMessage().contains("not created by Virpe Mart"), error.getMessage());
        assertFalse(tableExists("schema_version"));
    }

    @Test
    void versionsMustStartAtOneAndBeInOrder() {
        assertThrows(MigrationException.class, () -> runner("wrong-order").migrate());
    }

    @Test
    void lineEndingsDoNotChangeTheChecksum() {
        assertEquals(
                MigrationRunner.checksum(MigrationRunner.normalize("CREATE TABLE a (id INTEGER);\r\nSELECT 1;\r\n")),
                MigrationRunner.checksum(MigrationRunner.normalize("﻿CREATE TABLE a (id INTEGER);\nSELECT 1;\n")));
    }

    @Test
    void realMigrationsApplyToANewDatabase() {
        MigrationRunner.Result result =
                new MigrationRunner(database, MigrationRunner.DEFAULT_LOCATION, backups, TestDatabases.FIXED_CLOCK).migrate();

        assertTrue(result.toVersion() >= 1);
        assertTrue(tableExists("bills"));
    }

    @Test
    void everyMigrationFileIsListedInTheIndex() throws IOException {
        Path folder = Path.of("src", "main", "resources", "db", "migration");
        Set<String> filesOnDisk;
        try (Stream<Path> files = Files.list(folder)) {
            filesOnDisk = files.map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(".sql"))
                    .collect(Collectors.toSet());
        }
        Set<String> listed = new MigrationRunner(database, MigrationRunner.DEFAULT_LOCATION, backups,
                TestDatabases.FIXED_CLOCK).loadMigrations().stream()
                .map(MigrationRunner.Migration::fileName)
                .collect(Collectors.toSet());

        assertEquals(filesOnDisk, listed, "every .sql file must be listed in index.txt, and nothing else");
    }

    private boolean tableExists(String name) {
        return database.query(c -> {
            try (var s = c.prepareStatement("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?")) {
                s.setString(1, name);
                try (ResultSet rs = s.executeQuery()) {
                    rs.next();
                    return rs.getInt(1) == 1;
                }
            }
        });
    }

    private int count(String sql) {
        return database.query(c -> {
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
                rs.next();
                return rs.getInt(1);
            }
        });
    }
}
