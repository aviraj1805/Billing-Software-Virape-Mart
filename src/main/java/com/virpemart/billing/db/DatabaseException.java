package com.virpemart.billing.db;

/**
 * A database problem that the code cannot fix by itself, such as a locked or damaged file.
 * It wraps the original {@link java.sql.SQLException} so the details reach the log.
 */
public class DatabaseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }

    public DatabaseException(String message) {
        super(message);
    }
}
