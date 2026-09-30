package com.virpemart.billing.model;

import java.time.LocalDateTime;

/**
 * One bill in a list, without its lines.
 *
 * @param customerNo   khata customer number such as C0001, or null for a walk-in customer
 * @param customerName name printed on the bill, or null
 * @param paid         the part of the bill paid at billing (money against old dues is not included)
 * @param toAccount    the part of the bill put on the khata
 */
public record BillSummary(
        long id,
        long billNo,
        LocalDateTime createdAt,
        String customerNo,
        String customerName,
        Money total,
        Money paid,
        Money toAccount,
        int lineCount,
        boolean cancelled) {

    /** "Ramesh Patil (C0001)", "Sunil" for a named walk-in, or "Walk-in". */
    public String customerText() {
        if (customerName == null) {
            return "Walk-in";
        }
        return customerNo == null ? customerName : customerName + " (" + customerNo + ")";
    }
}
