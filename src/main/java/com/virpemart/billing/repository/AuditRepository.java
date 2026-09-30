package com.virpemart.billing.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/** All SQL for the {@code audit_log} table: a permanent record of who changed what. */
public final class AuditRepository {

    /**
     * Records one action.
     *
     * @param userId   who did it, or null for an automatic action by the app
     * @param action   short code such as PRODUCT_UPDATED
     * @param entity   table name such as "products", or null
     * @param entityId row id, or null
     * @param details  human-readable description of the change
     */
    public void insert(Connection connection, Long userId, String action, String entity, Long entityId,
                       String details, String now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO audit_log (created_at, user_id, action, entity, entity_id, details) VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, now);
            setNullableLong(statement, 2, userId);
            statement.setString(3, action);
            statement.setString(4, entity);
            setNullableLong(statement, 5, entityId);
            statement.setString(6, details);
            statement.executeUpdate();
        }
    }

    static void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setLong(index, value);
        }
    }
}
