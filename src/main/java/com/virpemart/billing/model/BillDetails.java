package com.virpemart.billing.model;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Everything about one saved bill, as it was when it was saved. Used to print and reprint bills.
 *
 * @param customerNo      khata customer number such as C0001, or null for a walk-in customer
 * @param customerName    name printed on the bill, or null
 * @param lines           the bill lines in order; {@code productRate} holds the original rate if it was changed
 * @param totals          subtotal, round off and total as saved; savings worked out from the lines
 * @param paidForBill     money that paid this bill, by mode
 * @param paidAgainstDues money paid with this bill towards old khata dues, by mode (khata customers only)
 * @param toAccount       unpaid part of the bill added to the khata
 * @param previousBalance khata balance before the bill, or null for walk-in customers
 * @param balanceAfter    khata balance after the bill and payment, or null for walk-in customers
 * @param cancelReason    why the bill was cancelled, or null if it is not cancelled
 */
public record BillDetails(
        long id,
        long billNo,
        LocalDateTime createdAt,
        String customerNo,
        String customerName,
        List<CartLine> lines,
        BillTotals totals,
        List<PaymentPart> paidForBill,
        List<PaymentPart> paidAgainstDues,
        Money toAccount,
        Money previousBalance,
        Money balanceAfter,
        String cancelReason) {

    public BillDetails {
        lines = List.copyOf(lines);
        paidForBill = List.copyOf(paidForBill);
        paidAgainstDues = List.copyOf(paidAgainstDues);
    }

    /** True for a khata customer's bill. */
    public boolean isKhata() {
        return customerNo != null;
    }

    public boolean isCancelled() {
        return cancelReason != null;
    }

    /** Money that paid this bill (not money paid towards old dues). */
    public Money paidForBillTotal() {
        return Money.sum(paidForBill.stream().map(PaymentPart::amount).toList());
    }

    /** All money received with this bill: for the bill and towards old dues. */
    public Money paidTotal() {
        return Money.sum(paidByMode().values());
    }

    /** All money received with this bill, added up per mode, in the order Cash, UPI, Card. */
    public Map<PaymentMode, Money> paidByMode() {
        Map<PaymentMode, Money> byMode = new EnumMap<>(PaymentMode.class);
        for (PaymentPart part : paidForBill) {
            byMode.merge(part.mode(), part.amount(), Money::plus);
        }
        for (PaymentPart part : paidAgainstDues) {
            byMode.merge(part.mode(), part.amount(), Money::plus);
        }
        return byMode;
    }
}
