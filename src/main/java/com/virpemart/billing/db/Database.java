package com.virpemart.billing.db;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import org.sqlite.SQLiteConfig;

/**
 * Opens connections to the SQLite database file and runs work inside transactions.
 *
 * <p>Every connection uses these settings:
 * <ul>
 *   <li>foreign keys ON, so a bill cannot point at a customer that does not exist;</li>
 *   <li>WAL journal and FULL sync, so a power cut never leaves a half-saved bill;</li>
 *   <li>a busy timeout, so a short wait happens instead of an immediate "database locked" error;</li>
 *   <li>IMMEDIATE transactions, so a write transaction takes the write lock at its start.</li>
 * </ul>
 *
 * <p>Repositories receive the {@link Connection} as a parameter. That keeps it obvious which
 * statements belong to the same transaction.
 */
public final class Database {

    private static final int BUSY_TIMEOUT_MILLIS = 5_000;

    private final Path file;
    private final String url;
    private final Properties connectionProperties;

    public Database(Path file) {
        this.file = file.toAbsolutePath().normalize();
        this.url = "jdbc:sqlite:" + this.file;

        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(BUSY_TIMEOUT_MILLIS);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.FULL);
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
        this.connectionProperties = config.toProperties();
    }

    /** The database file on disk. */
    public Path file() {
        return file;
    }

    /** Opens a new connection with the standard settings. The caller must close it. */
    public Connection open() throws SQLException {
        return DriverManager.getConnection(url, connectionProperties);
    }

    /**
     * Runs the work in one transaction and returns its result.
     * If anything fails, every change made by the work is undone.
     */
    public <T> T inTransaction(SqlWork<T> work) {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException | Error e) {
                rollback(connection, e);
                throw e;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Database work failed and was undone: " + e.getMessage(), e);
        }
    }

    /** Same as {@link #inTransaction(SqlWork)} for work that returns nothing. */
    public void runInTransaction(SqlAction action) {
        inTransaction(connection -> {
            action.run(connection);
            return null;
        });
    }

    /**
     * Runs read-only work without an explicit transaction and returns its result.
     * Do not use this for changes that must succeed or fail together.
     */
    public <T> T query(SqlWork<T> work) {
        try (Connection connection = open()) {
            return work.run(connection);
        } catch (SQLException e) {
            throw new DatabaseException("Reading from the database failed: " + e.getMessage(), e);
        }
    }

    /**
     * Runs a statement that SQLite does not allow inside a transaction, such as {@code VACUUM}.
     */
    public void executeOutsideTransaction(SqlAction action) {
        try (Connection connection = open()) {
            action.run(connection);
        } catch (SQLException e) {
            throw new DatabaseException("Database command failed: " + e.getMessage(), e);
        }
    }

    private static void rollback(Connection connection, Throwable original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }
}
