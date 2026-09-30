package com.virpemart.billing.model;

/**
 * The money totals of a bill.
 *
 * @param subtotal  sum of line totals
 * @param roundOff  added to reach a whole rupee (between -0.49 and +0.50)
 * @param total     subtotal plus round-off: what the customer pays for this bill
 * @param savings   total saved compared to MRP
 * @param lineCount number of lines
 */
public record BillTotals(Money subtotal, Money roundOff, Money total, Money savings, int lineCount) {

    public static final BillTotals EMPTY = new BillTotals(Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, 0);
}
