package com.virpemart.billing.service;

import java.time.LocalDate;
import java.util.List;

import com.virpemart.billing.db.Database;
import com.virpemart.billing.model.DaySummary;
import com.virpemart.billing.model.SalesReport;
import com.virpemart.billing.repository.ReportRepository;

/**
 * Sales reports for the owner: a daily summary with the Cash, UPI and Card split, and sales for a date range.
 *
 * <p>Rules: cancelled bills are not counted as sales (they are shown separately). Money paid for a bill is counted
 * on the day of the bill. Khata payments are counted on the day they were received, including money paid towards
 * old dues together with a bill.
 */
public final class ReportService {

    /** A longer range would make a very long table. */
    private static final int MAX_DAYS = 366;

    private final Database database;
    private final ReportRepository reports;
    private final Session session;

    public ReportService(Database database, ReportRepository reports, Session session) {
        this.database = database;
        this.reports = reports;
        this.session = session;
    }

    /**
     * Sales from {@code from} to {@code to}, both days included. For one day, pass the same date twice.
     * Owner only. Can take a moment on a big database, so screens call it away from the screen thread.
     *
     * @throws ValidationException (field "from" or "to") for a missing or wrong date range
     */
    public SalesReport sales(LocalDate from, LocalDate to) {
        session.requireOwner();
        if (from == null) {
            throw new ValidationException("from", "Please choose the first date.");
        }
        if (to == null) {
            throw new ValidationException("to", "Please choose the last date.");
        }
        if (from.isAfter(to)) {
            throw new ValidationException("from", "The first date is after the last date. Please check the dates.");
        }
        if (from.plusDays(MAX_DAYS).isBefore(to)) {
            throw new ValidationException("to", "Please choose at most one year at a time.");
        }
        List<DaySummary> days = database.query(c -> reports.daily(c, from, to));
        DaySummary total = DaySummary.empty(null);
        for (DaySummary day : days) {
            total = total.plus(day);
        }
        return new SalesReport(from, to, days, total);
    }
}
