package com.virpemart.billing.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/** All SQL for the {@code settings} table: simple key and value pairs such as the shop name. */
public final class SettingsRepository {

    /** Every saved setting. Keys that were never saved are missing. */
    public Map<String, String> all(Connection connection) throws SQLException {
        Map<String, String> settings = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT key, value FROM settings");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                settings.put(rs.getString("key"), rs.getString("value"));
            }
        }
        return settings;
    }

    /** Saves one setting, replacing any old value. */
    public void put(Connection connection, String key, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO settings (key, value) VALUES (?, ?)"
                        + " ON CONFLICT (key) DO UPDATE SET value = excluded.value")) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        }
    }
}
