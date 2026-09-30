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

import com.virpemart.billing.db.DbTime;
import com.virpemart.billing.model.BillDetails;
import com.virpemart.billing.model.BillSearch;
import com.virpemart.billing.model.BillSummary;
import com.virpemart.billing.model.BillTotals;
import com.virpemart.billing.model.Cart;
import com.virpemart.billing.model.CartLine;
import com.virpemart.billing.model.Money;
import com.virpemart.billing.model.PaymentMode;
import com.virpemart.billing.model.PaymentPart;
import com.virpemart.billing.model.Quantity;
import com.virpemart.billing.model.Unit;

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
        return search(connection, BillSearch.ofCustomer(customerId, limit));
    }

    /**
     * Bills matching the search, newest first. The text matches the bill number exactly, or part of the name on the
     * bill, the customer number or the customer's phone.
     */
    public List<BillSummary> search(Connection connection, BillSearch search) throws SQLException {
        String from = search.from() == null ? null : DbTime.format(search.from().atStartOfDay());
        String before = search.to() == null ? null : DbTime.format(search.to().plusDays(1).atStartOfDay());
        String like = search.text() == null ? null : "%" + ProductRepository.escapeLike(search.text()) + "%";
        long billNo = parseBillNo(search.text());
        List<BillSummary> bills = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT b.id, b.bill_no, b.created_at, c.customer_no, b.customer_name, b.total_paise, b.paid_paise,"
                        + " b.to_account_paise, b.status,"
                        + " (SELECT COUNT(*) FROM bill_items i WHERE i.bill_id = b.id) AS line_count"
                        + " FROM bills b LEFT JOIN customers c ON c.id = b.customer_id"
                        + " WHERE (? IS NULL OR b.created_at >= ?)"
                        + " AND (? IS NULL OR b.created_at < ?)"
                        + " AND (? IS NULL OR b.customer_id = ?)"
                        + " AND (? IS NULL OR b.bill_no = ? OR b.customer_name LIKE ? ESCAPE '\\'"
                        + "      OR c.customer_no LIKE ? ESCAPE '\\' OR c.phone LIKE ? ESCAPE '\\')"
                        + " ORDER BY b.bill_no DESC LIMIT ?")) {
            statement.setString(1, from);
            statement.setString(2, from);
            statement.setString(3, before);
            statement.setString(4, before);
            AuditRepository.setNullableLong(statement, 5, search.customerId());
            AuditRepository.setNullableLong(statement, 6, search.customerId());
            statement.setString(7, like);
            statement.setLong(8, billNo);
            statement.setString(9, like);
            statement.setString(10, like);
            statement.setString(11, like);
            statement.setInt(12, search.limit());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    bills.add(new BillSummary(
                            rs.getLong("id"),
                            rs.getLong("bill_no"),
                            DbTime.parse(rs.getString("created_at")),
                            rs.getString("customer_no"),
                            rs.getString("customer_name"),
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

    /**
     * Marks a bill as cancelled. This is the only change the database allows on a saved bill.
     *
     * @return false if the bill was already cancelled
     */
    public boolean cancel(Connection connection, long billId, String reason, long userId, String now)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE bills SET status = 'CANCELLED', cancel_reason = ?, cancelled_by = ?, cancelled_at = ?"
                        + " WHERE id = ? AND status = 'FINAL'")) {
            statement.setString(1, reason);
            statement.setLong(2, userId);
            statement.setString(3, now);
            statement.setLong(4, billId);
            return statement.executeUpdate() == 1;
        }
    }

    /** The khata customer of a bill, or empty for a walk-in bill. */
    public Optional<Long> customerIdOf(Connection connection, long billId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT customer_id FROM bills WHERE id = ?")) {
            statement.setLong(1, billId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                long id = rs.getLong(1);
                return rs.wasNull() ? Optional.empty() : Optional.of(id);
            }
        }
    }

    /** The text as a bill number, or -1 (no bill has that number) if it is not a number. */
    private static long parseBillNo(String text) {
        if (text == null || !text.matches("\\d{1,9}")) {
            return -1;
        }
        return Long.parseLong(text);
    }

    /** The highest bill number used so far, or empty if no bill was ever saved. */
    public Optional<Long> lastBillNo(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT MAX(bill_no) FROM bills")) {
            rs.next();
            long billNo = rs.getLong(1);
            return rs.wasNull() ? Optional.empty() : Optional.of(billNo);
        }
    }

    /**
     * A saved bill with its lines and payments, exactly as saved. Money paid with the bill towards old
     * khata dues is read from the khata entries linked to the bill.
     */
    public Optional<BillDetails> findByNo(Connection connection, long billNo) throws SQLException {
        long id;
        String createdAt;
        String customerNo;
        String customerName;
        Money subtotal;
        Money roundOff;
        Money total;
        Money toAccount;
        Money previous;
        Money after;
        String cancelReason;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT b.id, b.created_at, c.customer_no, b.customer_name, b.subtotal_paise, b.round_off_paise,"
                        + " b.total_paise, b.to_account_paise, b.previous_balance_paise, b.balance_after_paise,"
                        + " b.cancel_reason"
                        + " FROM bills b LEFT JOIN customers c ON c.id = b.customer_id WHERE b.bill_no = ?")) {
            statement.setLong(1, billNo);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                id = rs.getLong("id");
                createdAt = rs.getString("created_at");
                customerNo = rs.getString("customer_no");
                customerName = rs.getString("customer_name");
                subtotal = Money.ofPaise(rs.getLong("subtotal_paise"));
                roundOff = Money.ofPaise(rs.getLong("round_off_paise"));
                total = Money.ofPaise(rs.getLong("total_paise"));
                toAccount = Money.ofPaise(rs.getLong("to_account_paise"));
                previous = nullableMoney(rs, "previous_balance_paise");
                after = nullableMoney(rs, "balance_after_paise");
                cancelReason = rs.getString("cancel_reason");
            }
        }
        List<CartLine> lines = itemsOf(connection, id);
        Money savings = Cart.totalsOf(lines).savings();
        BillTotals totals = new BillTotals(subtotal, roundOff, total, savings, lines.size());
        return Optional.of(new BillDetails(id, billNo, DbTime.parse(createdAt), customerNo, customerName, lines,
                totals, paymentsOf(connection, id), duesPaymentsOf(connection, id), toAccount, previous, after,
                cancelReason));
    }

    private static List<CartLine> itemsOf(Connection connection, long billId) throws SQLException {
        List<CartLine> lines = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT product_id, name, name_mr, unit, pack_size, qty_milli, rate_paise, original_rate_paise,"
                        + " mrp_paise FROM bill_items WHERE bill_id = ? ORDER BY line_no")) {
            statement.setLong(1, billId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    long productId = rs.getLong("product_id");
                    Long product = rs.wasNull() ? null : productId;
                    Money rate = Money.ofPaise(rs.getLong("rate_paise"));
                    Money original = nullableMoney(rs, "original_rate_paise");
                    Money productRate = product == null ? null : (original == null ? rate : original);
                    lines.add(new CartLine(product, rs.getString("name"), rs.getString("name_mr"),
                            Unit.valueOf(rs.getString("unit")), rs.getString("pack_size"),
                            new Quantity(rs.getLong("qty_milli")), rate, productRate,
                            nullableMoney(rs, "mrp_paise")));
                }
            }
        }
        return lines;
    }

    private static List<PaymentPart> paymentsOf(Connection connection, long billId) throws SQLException {
        return readPayments(connection, billId,
                "SELECT mode, amount_paise FROM bill_payments WHERE bill_id = ? ORDER BY id");
    }

    /** Khata payments are stored as negative amounts, so the sign is turned around here. */
    private static List<PaymentPart> duesPaymentsOf(Connection connection, long billId) throws SQLException {
        return readPayments(connection, billId,
                "SELECT payment_mode AS mode, -amount_paise AS amount_paise FROM customer_ledger"
                        + " WHERE bill_id = ? AND entry_type = 'PAYMENT' ORDER BY id");
    }

    private static List<PaymentPart> readPayments(Connection connection, long billId, String sql)
            throws SQLException {
        List<PaymentPart> parts = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, billId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    parts.add(new PaymentPart(PaymentMode.valueOf(rs.getString("mode")),
                            Money.ofPaise(rs.getLong("amount_paise"))));
                }
            }
        }
        return parts;
    }

    private static Money nullableMoney(ResultSet rs, String column) throws SQLException {
        long paise = rs.getLong(column);
        return rs.wasNull() ? null : Money.ofPaise(paise);
    }

    private static void setNullableMoney(PreparedStatement statement, int index, Money value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setLong(index, value.paise());
        }
    }
}
