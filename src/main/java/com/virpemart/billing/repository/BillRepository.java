package com.virpemart.billing.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import com.virpemart.billing.db.DbTime;
import com.virpemart.billing.model.BillSummary;
import com.virpemart.billing.model.BillTotals;
import com.virpemart.billing.model.CartLine;
import com.virpemart.billing.model.Money;
import com.virpemart.billing.model.PaymentMode;

/**
 * All SQL for {@code bills}, {@code bill_items} and {@code bill_payments}.
 * Saved bills can never be changed or deleted; the database enforces this.
 */
public final class BillRepository {

    /**
     * A bill ready to be inserted.
     *
     * @param customerId      khata customer, or null for walk-in
     * @param customerName    name printed on the bill, or null
     * @param paidForBill     part of the bill paid now
     * @param toAccount       unpaid part added to the khata
     * @param previousBalance khata balance before, or null for walk-in
     * @param balanceAfter    khata balance after, or null for walk-in
     */
    public record NewBill(long billNo, String createdAt, Long customerId, String customerName, BillTotals totals,
                          Money paidForBill, Money toAccount, Money previousBalance, Money balanceAfter,
                          long createdBy) {
    }

    /** The next bill number: one more than the highest ever used. Bills are never deleted, so numbers never repeat. */
    public long nextBillNo(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT IFNULL(MAX(bill_no), 0) + 1 FROM bills")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** Inserts the bill row and returns its id. */
    public long insertBill(Connection connection, NewBill bill) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO bills (bill_no, created_at, customer_id, customer_name, subtotal_paise, round_off_paise,"
                        + " total_paise, paid_paise, to_account_paise, previous_balance_paise, balance_after_paise,"
                        + " status, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'FINAL', ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, bill.billNo());
            statement.setString(2, bill.createdAt());
            AuditRepository.setNullableLong(statement, 3, bill.customerId());
            statement.setString(4, bill.customerName());
            statement.setLong(5, bill.totals().subtotal().paise());
            statement.setLong(6, bill.totals().roundOff().paise());
            statement.setLong(7, bill.totals().total().paise());
            statement.setLong(8, bill.paidForBill().paise());
            statement.setLong(9, bill.toAccount().paise());
            setNullableMoney(statement, 10, bill.previousBalance());
            setNullableMoney(statement, 11, bill.balanceAfter());
            statement.setLong(12, bill.createdBy());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    /** Inserts one line with its snapshot of the product details. */
    public void insertItem(Connection connection, long billId, int lineNo, CartLine line) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO bill_items (bill_id, line_no, product_id, name, name_mr, unit, pack_size, qty_milli,"
                        + " rate_paise, original_rate_paise, mrp_paise, line_total_paise)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setLong(1, billId);
            statement.setInt(2, lineNo);
            AuditRepository.setNullableLong(statement, 3, line.productId());
            statement.setString(4, line.name());
            statement.setString(5, line.nameMr());
            statement.setString(6, line.unit().name());
            statement.setString(7, line.packSize());
            statement.setLong(8, line.quantity().milli());
            statement.setLong(9, line.rate().paise());
            setNullableMoney(statement, 10, line.rateChanged() ? line.productRate() : null);
            setNullableMoney(statement, 11, line.mrp());
            statement.setLong(12, line.lineTotal().paise());
            statement.executeUpdate();
        }
    }

    public void insertPayment(Connection connection, long billId, PaymentMode mode, Money amount) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO bill_payments (bill_id, mode, amount_paise) VALUES (?, ?, ?)")) {
            statement.setLong(1, billId);
            statement.setString(2, mode.name());
            statement.setLong(3, amount.paise());
            statement.executeUpdate();
        }
    }

    /** The newest bills of a khata customer, newest first. */
    public List<BillSummary> recentForCustomer(Connection connection, long customerId, int limit) throws SQLException {
        List<BillSummary> bills = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT b.id, b.bill_no, b.created_at, b.total_paise, b.paid_paise, b.to_account_paise, b.status,"
                        + " (SELECT COUNT(*) FROM bill_items i WHERE i.bill_id = b.id) AS line_count"
                        + " FROM bills b WHERE b.customer_id = ? ORDER BY b.bill_no DESC LIMIT ?")) {
            statement.setLong(1, customerId);
            statement.setInt(2, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    bills.add(new BillSummary(
                            rs.getLong("id"),
                            rs.getLong("bill_no"),
                            DbTime.parse(rs.getString("created_at")),
                            Money.ofPaise(rs.getLong("total_paise")),
                            Money.ofPaise(rs.getLong("paid_paise")),
                            Money.ofPaise(rs.getLong("to_account_paise")),
                            rs.getInt("line_count"),
                            "CANCELLED".equals(rs.getString("status"))));
                }
            }
        }
        return bills;
    }

    private static void setNullableMoney(PreparedStatement statement, int index, Money value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setLong(index, value.paise());
        }
    }
}
