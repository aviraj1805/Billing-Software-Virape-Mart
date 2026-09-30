package com.virpemart.billing.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Sales for a date range: one summary per day that had any activity, and the total of the whole range.
 *
 * @param days  days with bills or khata payments, oldest first
 * @param total everything in the range added together
 */
public record SalesReport(LocalDate from, LocalDate to, List<DaySummary> days, DaySummary total) {

    public SalesReport {
        days = List.copyOf(days);
    }
}
