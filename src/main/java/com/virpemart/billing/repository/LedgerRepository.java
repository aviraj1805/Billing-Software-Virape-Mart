package com.virpemart.billing.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.virpemart.billing.db.DbTime;
import com.virpemart.billing.model.LedgerEntry;
import com.virpemart.billing.model.LedgerEntryType;
import com.virpemart.billing.model.Money;
import com.virpemart.billing.model.PaymentMode;

/**
 * All SQL for the {@code customer_ledger} table (the khata). Entries are only ever added;
 * the database refuses to change or delete them.
 */
public final class LedgerRepository {

    /**
     * Adds one khata entry and returns its id.
     *
     * @param amount      signed amount: positive adds to dues, negative reduces them
     * @param billId      related bill, or null
     * @param paymentMode payment mode, required for PAYMENT, otherwise null
     */
    public long insert(Connection connection, long customerId, LedgerEntryType type, Money amount, Long billId,
                       PaymentMode paymentMode, String note, String now, long userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO customer_ledger (customer_id, entry_type, amount_paise, bill_id, payment_mode, note,"
                        + " created_at, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, customerId);
            statement.setString(2, type.name());
            statement.setLong(3, amount.paise());
            AuditRepository.setNullableLong(statement, 4, billId);
            statement.setString(5, paymentMode == null ? null : paymentMode.name());
            statement.setString(6, note);
            statement.setString(7, now);
            statement.setLong(8, userId);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    /** All entries of one customer, oldest first. */
    public List<LedgerEntry> listForCustomer(Connection connection, long customerId) throws SQLException {
        List<LedgerEntry> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT l.id, l.customer_id, l.entry_type, l.amount_paise, l.bill_id, b.bill_no, l.payment_mode,"
                        + " l.note, l.created_at, u.display_name"
                        + " FROM customer_ledger l"
                        + " LEFT JOIN bills b ON b.id = l.bill_id"
                        + " JOIN users u ON u.id = l.created_by"
                        + " WHERE l.customer_id = ? ORDER BY l.created_at, l.id")) {
            statement.setLong(1, customerId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    long billId = rs.getLong("bill_id");
                    Long bill = rs.wasNull() ? null : billId;
                    long billNo = rs.getLong("bill_no");
                    Long number = rs.wasNull() ? null : billNo;
                    String mode = rs.getString("payment_mode");
                    entries.add(new LedgerEntry(
                            rs.getLong("id"),
                            rs.getLong("customer_id"),
                            LedgerEntryType.valueOf(rs.getString("entry_type")),
                            Money.ofPaise(rs.getLong("amount_paise")),
                            bill,
                            number,
                            mode == null ? null : PaymentMode.valueOf(mode),
                            rs.getString("note"),
                            DbTime.parse(rs.getString("created_at")),
                            rs.getString("display_name")));
                }
            }
        }
        return entries;
    }

    /** The customer's balance: positive means dues, negative means advance. */
    public Money balance(Connection connection, long customerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT IFNULL(SUM(amount_paise), 0) FROM customer_ledger WHERE customer_id = ?")) {
            statement.setLong(1, customerId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return Money.ofPaise(rs.getLong(1));
            }
        }
    }
}
