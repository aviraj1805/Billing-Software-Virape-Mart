package com.virpemart.billing.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.virpemart.billing.model.Money;
import com.virpemart.billing.model.Product;
import com.virpemart.billing.model.ProductDetails;
import com.virpemart.billing.model.Unit;

/** All SQL for the {@code products} table. */
public final class ProductRepository {

    private static final String SELECT = "SELECT p.id, p.code, p.name, p.name_mr, p.category_id,"
            + " c.name AS category_name, p.unit, p.pack_size, p.rate_paise, p.mrp_paise, p.active"
            + " FROM products p LEFT JOIN categories c ON c.id = p.category_id";

    /** At most this many words are used from the search text. */
    private static final int MAX_SEARCH_WORDS = 5;

    public Optional<Product> findById(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT + " WHERE p.id = ?")) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(read(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Searches products. Every typed word must appear in the name, Marathi name, pack size or code.
     * Results whose code matches exactly come first, then names starting with the text, then the rest.
     */
    public List<Product> search(Connection connection, ProductSearch search) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();

        if (!search.includeInactive()) {
            sql.append(" AND p.active = 1");
        }
        if (search.categoryId() != null) {
            sql.append(" AND p.category_id = ?");
            params.add(search.categoryId());
        }

        String text = search.text() == null ? "" : search.text().strip();
        String[] words = text.isEmpty() ? new String[0] : text.split("\\s+");
        for (int i = 0; i < Math.min(words.length, MAX_SEARCH_WORDS); i++) {
            sql.append(" AND (p.name LIKE ? ESCAPE '\\' OR IFNULL(p.name_mr, '') LIKE ? ESCAPE '\\'"
                    + " OR IFNULL(p.pack_size, '') LIKE ? ESCAPE '\\' OR p.code LIKE ? ESCAPE '\\')");
            String pattern = "%" + escapeLike(words[i]) + "%";
            for (int j = 0; j < 4; j++) {
                params.add(pattern);
            }
        }

        if (text.isEmpty()) {
            sql.append(" ORDER BY p.name COLLATE NOCASE, p.pack_size");
        } else {
            sql.append(" ORDER BY CASE WHEN p.code = ? COLLATE NOCASE THEN 0"
                    + " WHEN p.name LIKE ? ESCAPE '\\' THEN 1 ELSE 2 END, p.name COLLATE NOCASE, p.pack_size");
            params.add(text);
            params.add(escapeLike(text) + "%");
        }
        sql.append(" LIMIT ?");
        params.add(search.limit());

        List<Product> products = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                statement.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    products.add(read(rs));
                }
            }
        }
        return products;
    }

    /**
     * Finds another product with the same name, pack size and unit (ignoring case and spaces in the
     * pack size), active or not.
     *
     * @param excludeId a product id to ignore (the one being edited), or 0
     */
    public Optional<Product> findSame(Connection connection, String name, String packSize, Unit unit, long excludeId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT
                + " WHERE p.name = ? COLLATE NOCASE"
                + " AND REPLACE(IFNULL(p.pack_size, ''), ' ', '') = ? COLLATE NOCASE"
                + " AND p.unit = ? AND p.id <> ? LIMIT 1")) {
            statement.setString(1, name);
            statement.setString(2, packSize == null ? "" : packSize.replace(" ", ""));
            statement.setString(3, unit.name());
            statement.setLong(4, excludeId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(read(rs)) : Optional.empty();
            }
        }
    }

    /** The highest number used in codes like P0042, or 0 if there are none. */
    public long maxCodeNumber(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT IFNULL(MAX(CAST(SUBSTR(code, 2) AS INTEGER)), 0) FROM products WHERE code GLOB 'P[0-9]*'")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** Adds a product and returns its new id. */
    public long insert(Connection connection, String code, ProductDetails details, String now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO products (code, name, name_mr, category_id, unit, pack_size, rate_paise, mrp_paise,"
                        + " active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, code);
            bindDetails(statement, 2, details);
            statement.setString(9, now);
            statement.setString(10, now);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public void update(Connection connection, long id, ProductDetails details, String now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE products SET name = ?, name_mr = ?, category_id = ?, unit = ?, pack_size = ?,"
                        + " rate_paise = ?, mrp_paise = ?, updated_at = ? WHERE id = ?")) {
            bindDetails(statement, 1, details);
            statement.setString(8, now);
            statement.setLong(9, id);
            statement.executeUpdate();
        }
    }

    public void setActive(Connection connection, long id, boolean active, String now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE products SET active = ?, updated_at = ? WHERE id = ?")) {
            statement.setInt(1, active ? 1 : 0);
            statement.setString(2, now);
            statement.setLong(3, id);
            statement.executeUpdate();
        }
    }

    /** True if any saved bill has a line for this product. */
    public boolean isOnAnyBill(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM bill_items WHERE product_id = ?)")) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1) == 1;
            }
        }
    }

    public void delete(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM products WHERE id = ?")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    /** Binds name, name_mr, category_id, unit, pack_size, rate_paise, mrp_paise starting at {@code first}. */
    private static void bindDetails(PreparedStatement statement, int first, ProductDetails details) throws SQLException {
        statement.setString(first, details.name());
        statement.setString(first + 1, details.nameMr());
        AuditRepository.setNullableLong(statement, first + 2, details.categoryId());
        statement.setString(first + 3, details.unit().name());
        statement.setString(first + 4, details.packSize());
        statement.setLong(first + 5, details.rate().paise());
        if (details.mrp() == null) {
            statement.setNull(first + 6, Types.INTEGER);
        } else {
            statement.setLong(first + 6, details.mrp().paise());
        }
    }

    /** Makes %, _ and \ in typed text match literally in a LIKE pattern. */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Product read(ResultSet rs) throws SQLException {
        long categoryId = rs.getLong("category_id");
        Long category = rs.wasNull() ? null : categoryId;
        long mrpPaise = rs.getLong("mrp_paise");
        Money mrp = rs.wasNull() ? null : Money.ofPaise(mrpPaise);
        return new Product(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("name_mr"),
                category,
                rs.getString("category_name"),
                Unit.valueOf(rs.getString("unit")),
                rs.getString("pack_size"),
                Money.ofPaise(rs.getLong("rate_paise")),
                mrp,
                rs.getInt("active") == 1);
    }
}
