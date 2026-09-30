package com.virpemart.billing.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.virpemart.billing.db.DbTime;
import com.virpemart.billing.model.DaySummary;
import com.virpemart.billing.model.Money;
import com.virpemart.billing.model.PaymentMode;

/**
 * All SQL for the sales reports. Everything is grouped by the day part of the date-time
 * ("2026-09-30T10:15:00" belongs to 2026-09-30).
 */
public final class ReportRepository {

    /** Running figures for one day while the queries are read. */
    private static final class Day {
        int bills;
        long sales;
        long onKhata;
        final Map<PaymentMode, Money> paidAtBilling = new EnumMap<>(PaymentMode.class);
        final Map<PaymentMode, Money> khataPayments = new EnumMap<>(PaymentMode.class);
        int cancelledBills;
        long cancelledTotal;
    }

    /** One summary per day from {@code from} to {@code to} (both included) that had any activity, oldest first. */
    public List<DaySummary> daily(Connection connection, LocalDate from, LocalDate to) throws SQLException {
        String start = DbTime.format(from.atStartOfDay());
        String before = DbTime.format(to.plusDays(1).atStartOfDay());
        Map<LocalDate, Day> days = new TreeMap<>();

        read(connection, "SELECT substr(created_at, 1, 10) AS day, status, COUNT(*) AS bills,"
                + " SUM(total_paise) AS total, SUM(to_account_paise) AS on_khata"
                + " FROM bills WHERE created_at >= ? AND created_at < ? GROUP BY day, status", start, before, rs -> {
                    Day day = days.computeIfAbsent(LocalDate.parse(rs.getString("day")), d -> new Day());
                    if ("FINAL".equals(rs.getString("status"))) {
                        day.bills = rs.getInt("bills");
                        day.sales = rs.getLong("total");
                        day.onKhata = rs.getLong("on_khata");
                    } else {
                        day.cancelledBills = rs.getInt("bills");
                        day.cancelledTotal = rs.getLong("total");
                    }
                });

        read(connection, "SELECT substr(b.created_at, 1, 10) AS day, p.mode AS mode, SUM(p.amount_paise) AS amount"
                + " FROM bill_payments p JOIN bills b ON b.id = p.bill_id"
                + " WHERE b.status = 'FINAL' AND b.created_at >= ? AND b.created_at < ? GROUP BY day, p.mode",
                start, before, rs -> days.computeIfAbsent(LocalDate.parse(rs.getString("day")), d -> new Day())
                        .paidAtBilling.put(PaymentMode.valueOf(rs.getString("mode")),
                                Money.ofPaise(rs.getLong("amount"))));

        // Khata payments are stored as negative amounts, so the sign is turned around.
        read(connection, "SELECT substr(created_at, 1, 10) AS day, payment_mode AS mode, -SUM(amount_paise) AS amount"
                + " FROM customer_ledger WHERE entry_type = 'PAYMENT' AND created_at >= ? AND created_at < ?"
                + " GROUP BY day, payment_mode",
                start, before, rs -> days.computeIfAbsent(LocalDate.parse(rs.getString("day")), d -> new Day())
                        .khataPayments.put(PaymentMode.valueOf(rs.getString("mode")),
                                Money.ofPaise(rs.getLong("amount"))));

        List<DaySummary> result = new ArrayList<>();
        days.forEach((date, day) -> result.add(new DaySummary(date, day.bills, Money.ofPaise(day.sales),
                Money.ofPaise(day.onKhata), day.paidAtBilling, day.khataPayments, day.cancelledBills,
                Money.ofPaise(day.cancelledTotal))));
        return result;
    }

    @FunctionalInterface
    private interface RowReader {
        void read(ResultSet rs) throws SQLException;
    }

    private static void read(Connection connection, String sql, String start, String before, RowReader reader)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, start);
            statement.setString(2, before);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    reader.read(rs);
                }
            }
        }
    }
}
