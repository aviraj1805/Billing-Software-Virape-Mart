package com.virpemart.billing.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.virpemart.billing.model.Category;

/** All SQL for the {@code categories} table. */
public final class CategoryRepository {

    /** All categories in name order, optionally including switched-off ones. */
    public List<Category> list(Connection connection, boolean includeInactive) throws SQLException {
        String sql = "SELECT id, name, active FROM categories"
                + (includeInactive ? "" : " WHERE active = 1")
                + " ORDER BY name COLLATE NOCASE";
        List<Category> categories = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                categories.add(read(rs));
            }
        }
        return categories;
    }

    public Optional<Category> findById(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, name, active FROM categories WHERE id = ?")) {
            statement.setLong(1, id);
            return readOne(statement);
        }
    }

    /** Finds a category by name, ignoring upper/lower case. */
    public Optional<Category> findByName(Connection connection, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, name, active FROM categories WHERE name = ? COLLATE NOCASE")) {
            statement.setString(1, name);
            return readOne(statement);
        }
    }

    public Category insert(Connection connection, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO categories (name, active) VALUES (?, 1)", Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return new Category(keys.getLong(1), name, true);
            }
        }
    }

    public void rename(Connection connection, long id, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE categories SET name = ? WHERE id = ?")) {
            statement.setString(1, name);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    public void setActive(Connection connection, long id, boolean active) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE categories SET active = ? WHERE id = ?")) {
            statement.setInt(1, active ? 1 : 0);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    private static Optional<Category> readOne(PreparedStatement statement) throws SQLException {
        try (ResultSet rs = statement.executeQuery()) {
            return rs.next() ? Optional.of(read(rs)) : Optional.empty();
        }
    }

    private static Category read(ResultSet rs) throws SQLException {
        return new Category(rs.getLong("id"), rs.getString("name"), rs.getInt("active") == 1);
    }
}
