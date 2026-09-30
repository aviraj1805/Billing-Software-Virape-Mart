package com.virpemart.billing.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.virpemart.billing.db.DbTime;
import com.virpemart.billing.model.AuditEntry;

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

    /**
     * Audit records from {@code from} to {@code to} (both days included, either may be null), newest first.
     * The text, if given, matches part of the details or the action code.
     */
    public List<AuditEntry> search(Connection connection, LocalDate from, LocalDate to, String text, int limit)
            throws SQLException {
        String start = from == null ? null : DbTime.format(from.atStartOfDay());
        String before = to == null ? null : DbTime.format(to.plusDays(1).atStartOfDay());
        String like = text == null ? null : "%" + ProductRepository.escapeLike(text) + "%";
        List<AuditEntry> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT a.id, a.created_at, u.display_name, a.action, a.details"
                        + " FROM audit_log a LEFT JOIN users u ON u.id = a.user_id"
                        + " WHERE (? IS NULL OR a.created_at >= ?) AND (? IS NULL OR a.created_at < ?)"
                        + " AND (? IS NULL OR a.details LIKE ? ESCAPE '\\' OR a.action LIKE ? ESCAPE '\\')"
                        + " ORDER BY a.id DESC LIMIT ?")) {
            statement.setString(1, start);
            statement.setString(2, start);
            statement.setString(3, before);
            statement.setString(4, before);
            statement.setString(5, like);
            statement.setString(6, like);
            statement.setString(7, like);
            statement.setInt(8, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    entries.add(new AuditEntry(rs.getLong("id"), DbTime.parse(rs.getString("created_at")),
                            rs.getString("display_name"), rs.getString("action"), rs.getString("details")));
                }
            }
        }
        return entries;
    }

    static void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setLong(index, value);
        }
    }
}
