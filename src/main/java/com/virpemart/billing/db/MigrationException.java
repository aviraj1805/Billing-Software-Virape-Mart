package com.virpemart.billing.db;

/** The database schema could not be checked or upgraded safely. */
public class MigrationException extends DatabaseException {

    private static final long serialVersionUID = 1L;

    public MigrationException(String message) {
        super(message);
    }

    public MigrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
