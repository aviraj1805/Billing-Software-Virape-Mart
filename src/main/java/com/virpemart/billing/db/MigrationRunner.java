package com.virpemart.billing.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates and upgrades the database schema from numbered SQL files.
 *
 * <p>How it works:
 * <ol>
 *   <li>{@code db/migration/index.txt} lists the migration files in order:
 *       {@code V1__initial_schema.sql}, {@code V2__...}, and so on.</li>
 *   <li>The {@code schema_version} table records which versions were applied, with a checksum of each file.</li>
 *   <li>If an applied file was edited later, the app stops. Committed migrations must never change.</li>
 *   <li>Before upgrading an existing database, a backup copy is made.</li>
 *   <li>Each migration runs in its own transaction, so it is applied completely or not at all.</li>
 * </ol>
 */
public final class MigrationRunner {

    /** Where the real migration files live on the classpath. */
    public static final String DEFAULT_LOCATION = "db/migration";

    private static final Logger LOG = LoggerFactory.getLogger(MigrationRunner.class);
    private static final Pattern FILE_NAME = Pattern.compile("V(\\d+)__([A-Za-z0-9_]+)\\.sql");
    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss");

    private final Database database;
    private final String location;
    private final Path backupsDir;
    private final Clock clock;

    /** One migration file. */
    public record Migration(int version, String description, String fileName, String sql, String checksum) {
    }

    /**
     * What {@link #migrate()} did.
     *
     * @param fromVersion schema version before (0 for a brand-new database)
     * @param toVersion   schema version after
     * @param backupFile  the backup made before upgrading, if one was needed
     */
    public record Result(int fromVersion, int toVersion, Optional<Path> backupFile) {

        public boolean changed() {
            return fromVersion != toVersion;
        }
    }

    public MigrationRunner(Database database, String location, Path backupsDir, Clock clock) {
        this.database = database;
        this.location = location;
        this.backupsDir = backupsDir;
        this.clock = clock;
    }

    /** Brings the database up to the latest schema version. */
    public Result migrate() {
        List<Migration> migrations = loadMigrations();
        int latest = migrations.isEmpty() ? 0 : migrations.getLast().version();

        Map<Integer, String> applied = database.inTransaction(connection -> {
            checkIsOurDatabase(connection);
            createVersionTable(connection);
            return readApplied(connection);
        });
        int current = applied.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);

        verifyApplied(migrations, applied, current, latest);

        List<Migration> pending = migrations.stream().filter(m -> m.version() > current).toList();
        if (pending.isEmpty()) {
            LOG.info("Database schema is up to date at version {}", current);
            return new Result(current, current, Optional.empty());
        }

        Optional<Path> backup = Optional.empty();
        if (current > 0) {
            Path target = backupsDir.resolve("pre-upgrade-v" + current + "-to-v" + latest + "-"
                    + LocalDateTime.now(clock).format(BACKUP_STAMP) + ".db");
            LOG.info("Backing up database before upgrade to {}", target);
            DatabaseBackup.snapshot(database, target);
            backup = Optional.of(target);
        }

        for (Migration migration : pending) {
            apply(migration);
        }
        LOG.info("Database schema upgraded from version {} to {}", current, latest);
        return new Result(current, latest, backup);
    }

    /** Reads and checks the migration files listed in {@code index.txt}. */
    List<Migration> loadMigrations() {
        String indexText = readResource(location + "/index.txt");
        List<Migration> migrations = new ArrayList<>();
        for (String rawLine : indexText.split("\n")) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            Matcher matcher = FILE_NAME.matcher(line);
            if (!matcher.matches()) {
                throw new MigrationException("Bad migration file name in index.txt: " + line);
            }
            int version = Integer.parseInt(matcher.group(1));
            int expected = migrations.size() + 1;
            if (version != expected) {
                throw new MigrationException("Migration versions must be 1, 2, 3... in order. Expected V"
                        + expected + " but found " + line);
            }
            String sql = readResource(location + "/" + line);
            migrations.add(new Migration(version, matcher.group(2).replace('_', ' '), line, sql, checksum(sql)));
        }
        return migrations;
    }

    private void verifyApplied(List<Migration> migrations, Map<Integer, String> applied, int current, int latest) {
        if (current > latest) {
            throw new MigrationException("This database was created by a newer version of the app (schema version "
                    + current + ", this app knows up to " + latest + "). Please install the newer app version.");
        }
        for (Migration migration : migrations) {
            String recorded = applied.get(migration.version());
            if (migration.version() <= current && recorded == null) {
                throw new MigrationException("Schema version " + migration.version() + " is missing from the database.");
            }
            if (recorded != null && !recorded.equals(migration.checksum())) {
                throw new MigrationException("Migration " + migration.fileName()
                        + " was changed after it was applied. Committed migrations must never be edited; "
                        + "restore the original file and add a new migration instead.");
            }
        }
    }

    private void apply(Migration migration) {
        LOG.info("Applying migration {}", migration.fileName());
        try {
            database.runInTransaction(connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(migration.sql());
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO schema_version (version, description, checksum, applied_at) VALUES (?, ?, ?, ?)")) {
                    insert.setInt(1, migration.version());
                    insert.setString(2, migration.description());
                    insert.setString(3, migration.checksum());
                    insert.setString(4, DbTime.now(clock));
                    insert.executeUpdate();
                }
            });
        } catch (DatabaseException e) {
            throw new MigrationException("Could not apply " + migration.fileName() + ": " + e.getMessage(), e);
        }
    }

    /** Refuses to touch an SQLite file that has tables but no schema_version table. */
    private static void checkIsOurDatabase(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT "
                             + "(SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'schema_version'), "
                             + "(SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%')")) {
            rs.next();
            boolean hasVersionTable = rs.getInt(1) > 0;
            boolean hasTables = rs.getInt(2) > 0;
            if (!hasVersionTable && hasTables) {
                throw new MigrationException("This database file was not created by Virpe Mart Billing.");
            }
        }
    }

    private static void createVersionTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS schema_version ("
                    + " version     INTEGER PRIMARY KEY,"
                    + " description TEXT NOT NULL,"
                    + " checksum    TEXT NOT NULL,"
                    + " applied_at  TEXT NOT NULL)");
        }
    }

    private static Map<Integer, String> readApplied(Connection connection) throws SQLException {
        Map<Integer, String> applied = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT version, checksum FROM schema_version ORDER BY version")) {
            while (rs.next()) {
                applied.put(rs.getInt(1), rs.getString(2));
            }
        }
        return applied;
    }

    private static String readResource(String path) {
        try (InputStream in = MigrationRunner.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new MigrationException("Missing migration resource: " + path);
            }
            return normalize(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new MigrationException("Could not read migration resource: " + path, e);
        }
    }

    /** Removes a byte-order mark and uses \n line endings, so Git line-ending changes never alter checksums. */
    static String normalize(String text) {
        String withoutBom = text.startsWith("﻿") ? text.substring(1) : text;
        return withoutBom.replace("\r\n", "\n").replace('\r', '\n');
    }

    static String checksum(String sql) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(sql.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in Java", e);
        }
    }
}
