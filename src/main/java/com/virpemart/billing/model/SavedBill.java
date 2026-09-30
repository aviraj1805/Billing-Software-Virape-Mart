package com.virpemart.billing.model;

/**
 * The result of saving a bill.
 *
 * @param paidTotal        all money received with this bill, in every mode
 * @param paidForBill      the part of it that paid this bill
 * @param paidAgainstDues  the part that went against old khata dues (khata customers only)
 * @param toAccount        the unpaid part of this bill that was added to the khata
 * @param previousBalance  khata balance before the bill, or null for walk-in customers
 * @param balanceAfter     khata balance after the bill and payment, or null for walk-in customers
 */
public record SavedBill(
        long id,
        long billNo,
        BillTotals totals,
        Money paidTotal,
        Money paidForBill,
        Money paidAgainstDues,
        Money toAccount,
        Money previousBalance,
        Money balanceAfter) {
}
