package com.virpemart.billing.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import com.virpemart.billing.model.Role;
import com.virpemart.billing.model.User;

/** All SQL for the {@code users} table. */
public final class UserRepository {

    private static final String COLUMNS = "id, username, display_name, role, active";

    /** Number of users, active or not. */
    public long count(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM users")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** Finds a user by username, ignoring upper/lower case. */
    public Optional<User> findByUsername(Connection connection, String username) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM users WHERE username = ?")) {
            statement.setString(1, username);
            return readOne(statement);
        }
    }

    /** The oldest active OWNER account, if any. */
    public Optional<User> findFirstActiveOwner(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM users WHERE role = 'OWNER' AND active = 1 ORDER BY id LIMIT 1")) {
            return readOne(statement);
        }
    }

    public Optional<User> findById(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM users WHERE id = ?")) {
            statement.setLong(1, id);
            return readOne(statement);
        }
    }

    /**
     * Adds a user and returns it with its new id.
     *
     * @param passwordHash an already-hashed password; plain passwords must never reach this method
     * @param now          current time in {@link com.virpemart.billing.db.DbTime} format
     */
    public User insert(Connection connection, String username, String displayName, String passwordHash,
                       Role role, String now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users (username, display_name, password_hash, role, active, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, 1, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, username);
            statement.setString(2, displayName);
            statement.setString(3, passwordHash);
            statement.setString(4, role.name());
            statement.setString(5, now);
            statement.setString(6, now);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return new User(keys.getLong(1), username, displayName, role, true);
            }
        }
    }

    private static Optional<User> readOne(PreparedStatement statement) throws SQLException {
        try (ResultSet rs = statement.executeQuery()) {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(new User(
                    rs.getLong("id"),
                    rs.getString("username"),
                    rs.getString("display_name"),
                    Role.valueOf(rs.getString("role")),
                    rs.getInt("active") == 1));
        }
    }
}
