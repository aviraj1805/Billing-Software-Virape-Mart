package com.virpemart.billing.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.virpemart.billing.model.Customer;
import com.virpemart.billing.model.CustomerDetails;
import com.virpemart.billing.model.CustomerSummary;
import com.virpemart.billing.model.Money;

/**
 * All SQL for the {@code customers} table. Balances are always calculated from
 * {@code customer_ledger}; they are never stored.
 */
public final class CustomerRepository {

    private static final String SELECT = "SELECT c.id, c.customer_no, c.name, c.phone, c.address, c.notes, c.active,"
            + " IFNULL(b.balance, 0) AS balance"
            + " FROM customers c LEFT JOIN"
            + " (SELECT customer_id, SUM(amount_paise) AS balance FROM customer_ledger GROUP BY customer_id) b"
            + " ON b.customer_id = c.id";

    private static final int MAX_SEARCH_WORDS = 5;

    /** Customer totals for the whole shop. */
    public record DuesTotals(int customersWithDues, Money totalDues) {
    }

    public Optional<CustomerSummary> findById(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT + " WHERE c.id = ?")) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(read(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Searches customers. Every typed word must appear in the name, phone or customer number.
     * An exact customer number or phone comes first, then names starting with the text.
     */
    public List<CustomerSummary> search(Connection connection, String text, boolean includeInactive,
                                        boolean onlyWithDues, int limit) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (!includeInactive) {
            sql.append(" AND c.active = 1");
        }
        if (onlyWithDues) {
            sql.append(" AND IFNULL(b.balance, 0) > 0");
        }
        String cleaned = text == null ? "" : text.strip();
        String[] words = cleaned.isEmpty() ? new String[0] : cleaned.split("\\s+");
        for (int i = 0; i < Math.min(words.length, MAX_SEARCH_WORDS); i++) {
            sql.append(" AND (c.name LIKE ? ESCAPE '\\' OR IFNULL(c.phone, '') LIKE ? ESCAPE '\\'"
                    + " OR c.customer_no LIKE ? ESCAPE '\\')");
            String pattern = "%" + ProductRepository.escapeLike(words[i]) + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        if (cleaned.isEmpty()) {
            sql.append(" ORDER BY c.name COLLATE NOCASE");
        } else {
            sql.append(" ORDER BY CASE WHEN c.customer_no = ? COLLATE NOCASE OR c.phone = ? THEN 0"
                    + " WHEN c.name LIKE ? ESCAPE '\\' THEN 1 ELSE 2 END, c.name COLLATE NOCASE");
            params.add(cleaned);
            params.add(cleaned.replace(" ", ""));
            params.add(ProductRepository.escapeLike(cleaned) + "%");
        }
        sql.append(" LIMIT ?");
        params.add(limit);

        List<CustomerSummary> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                statement.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(read(rs));
                }
            }
        }
        return result;
    }

    /** Another customer with this phone number, if any. */
    public Optional<Customer> findByPhone(Connection connection, String phone, long excludeId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT + " WHERE c.phone = ? AND c.id <> ?")) {
            statement.setString(1, phone);
            statement.setLong(2, excludeId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(read(rs).customer()) : Optional.empty();
            }
        }
    }

    /** The highest number used in customer numbers like C0042, or 0 if there are none. */
    public long maxNumber(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT IFNULL(MAX(CAST(SUBSTR(customer_no, 2) AS INTEGER)), 0)"
                     + " FROM customers WHERE customer_no GLOB 'C[0-9]*'")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** Adds a customer and returns the new id. */
    public long insert(Connection connection, String customerNo, CustomerDetails details, String now)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO customers (customer_no, name, phone, address, notes, active, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, 1, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, customerNo);
            statement.setString(2, details.name());
            statement.setString(3, details.phone());
            statement.setString(4, details.address());
            statement.setString(5, details.notes());
            statement.setString(6, now);
            statement.setString(7, now);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public void update(Connection connection, long id, CustomerDetails details, String now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE customers SET name = ?, phone = ?, address = ?, notes = ?, updated_at = ? WHERE id = ?")) {
            statement.setString(1, details.name());
            statement.setString(2, details.phone());
            statement.setString(3, details.address());
            statement.setString(4, details.notes());
            statement.setString(5, now);
            statement.setLong(6, id);
            statement.executeUpdate();
        }
    }

    public void setActive(Connection connection, long id, boolean active, String now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE customers SET active = ?, updated_at = ? WHERE id = ?")) {
            statement.setInt(1, active ? 1 : 0);
            statement.setString(2, now);
            statement.setLong(3, id);
            statement.executeUpdate();
        }
    }

    /** How many customers owe money, and how much in total. Advances are not subtracted. */
    public DuesTotals duesTotals(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*), IFNULL(SUM(balance), 0) FROM"
                     + " (SELECT SUM(amount_paise) AS balance FROM customer_ledger GROUP BY customer_id)"
                     + " WHERE balance > 0")) {
            rs.next();
            return new DuesTotals(rs.getInt(1), Money.ofPaise(rs.getLong(2)));
        }
    }

    private static CustomerSummary read(ResultSet rs) throws SQLException {
        Customer customer = new Customer(
                rs.getLong("id"),
                rs.getString("customer_no"),
                rs.getString("name"),
                rs.getString("phone"),
                rs.getString("address"),
                rs.getString("notes"),
                rs.getInt("active") == 1);
        return new CustomerSummary(customer, Money.ofPaise(rs.getLong("balance")));
    }
}
