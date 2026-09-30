package com.virpemart.billing.db;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/** Helpers that create real, temporary SQLite databases for tests. */
public final class TestDatabases {

    /** A fixed clock: 30 September 2026, 10:00 India time. */
    public static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-30T04:30:00Z"), ZoneId.of("Asia/Kolkata"));

    private TestDatabases() {
    }

    /** An empty database file in the given folder, with no tables. */
    public static Database empty(Path folder) {
        return new Database(folder.resolve("test.db"));
    }

    /** A database with the real app schema (all migrations applied). */
    public static Database migrated(Path folder) {
        Database database = empty(folder);
        new MigrationRunner(database, MigrationRunner.DEFAULT_LOCATION, folder.resolve("backups"), FIXED_CLOCK).migrate();
        return database;
    }
}
