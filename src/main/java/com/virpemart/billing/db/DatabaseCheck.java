package com.virpemart.billing.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

/**
 * Checks a database file without changing it: is it a readable SQLite file, is it undamaged, is it this app's data,
 * and which schema version is it. Used before restoring a backup and when the app starts.
 */
public final class DatabaseCheck {

    /** What is wrong with a file, if anything. */
    public enum Problem {
        /** The file is missing or is not a database at all. */
        NOT_A_DATABASE,
        /** SQLite's own check found damage. */
        DAMAGED,
        /** A database, but not Virpe Mart data. */
        NOT_OURS
    }

    /**
     * The result of a check.
     *
     * @param problem       what is wrong, or null if the file is fine
     * @param schemaVersion schema version of the data (0 if unknown)
     * @param billCount     number of bills in it
     * @param lastBillAt    date-time text of the newest bill, or null if there are no bills
     */
    public record Result(Problem problem, int schemaVersion, long billCount, String lastBillAt) {

        public boolean ok() {
            return problem == null;
        }

        static Result failed(Problem problem) {
            return new Result(problem, 0, 0, null);
        }
    }

    private DatabaseCheck() {
    }

    /** Opens the file read-only and checks it. Never changes the file. */
    public static Result inspect(Path file) {
        if (!Files.isRegularFile(file)) {
            return Result.failed(Problem.NOT_A_DATABASE);
        }
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(true);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath(),
                config.toProperties())) {
            return inspect(connection);
        } catch (SQLException e) {
            return Result.failed(Problem.NOT_A_DATABASE);
        }
    }

    /** Checks the database behind an open connection. */
    public static Result inspect(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery("PRAGMA quick_check")) {
                if (!rs.next() || !"ok".equalsIgnoreCase(rs.getString(1))) {
                    return Result.failed(Problem.DAMAGED);
                }
            }
            try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM sqlite_master"
                    + " WHERE type = 'table' AND name IN ('schema_version', 'bills', 'customer_ledger')")) {
                rs.next();
                if (rs.getInt(1) != 3) {
                    return Result.failed(Problem.NOT_OURS);
                }
            }
            int version;
            try (ResultSet rs = statement.executeQuery("SELECT IFNULL(MAX(version), 0) FROM schema_version")) {
                rs.next();
                version = rs.getInt(1);
            }
            try (ResultSet rs = statement.executeQuery("SELECT COUNT(*), MAX(created_at) FROM bills")) {
                rs.next();
                return new Result(null, version, rs.getLong(1), rs.getString(2));
            }
        } catch (SQLException e) {
            return Result.failed(isNotADatabase(e) ? Problem.NOT_A_DATABASE : Problem.DAMAGED);
        }
    }

    /** SQLite only finds out that a file is not a database when it first reads it. */
    private static boolean isNotADatabase(SQLException e) {
        return e instanceof SQLiteException sqlite && sqlite.getResultCode() == SQLiteErrorCode.SQLITE_NOTADB;
    }
}
