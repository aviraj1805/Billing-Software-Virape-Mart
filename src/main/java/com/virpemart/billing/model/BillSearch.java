package com.virpemart.billing.model;

import java.time.LocalDate;

/**
 * What to look for in the bill history. Every part is optional (null means "any").
 *
 * @param from       first day, or null for no start
 * @param to         last day (included), or null for no end
 * @param text       part of the name on the bill, customer number or phone, or null
 * @param customerId only this khata customer's bills, or null for all bills
 * @param limit      the most bills to return, newest first
 * @param billNo     only this bill number, or null
 */
public record BillSearch(LocalDate from, LocalDate to, String text, Long customerId, int limit, Long billNo) {

    /** A search without a bill number. */
    public BillSearch(LocalDate from, LocalDate to, String text, Long customerId, int limit) {
        this(from, to, text, customerId, limit, null);
    }

    /** All bills of one khata customer, newest first. */
    public static BillSearch ofCustomer(long customerId, int limit) {
        return new BillSearch(null, null, null, customerId, limit);
    }
}
