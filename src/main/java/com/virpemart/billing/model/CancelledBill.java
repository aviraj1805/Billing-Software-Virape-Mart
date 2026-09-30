package com.virpemart.billing.model;

/**
 * What cancelling a bill does (or did), so the screen can tell the owner what to do.
 *
 * @param customerName     name on the bill, or null
 * @param total            the bill total
 * @param giveBack         money paid for this bill at the counter, which the owner gives back to the customer
 * @param takenOffKhata    the part of the bill that was on the khata and is taken off it
 * @param duesPaymentKept  money paid towards old khata dues together with this bill; it stays paid
 */
public record CancelledBill(long billNo, String customerName, Money total, Money giveBack, Money takenOffKhata,
                            Money duesPaymentKept) {

    /** Works out the effect of cancelling a saved bill. */
    public static CancelledBill of(BillDetails bill) {
        Money duesPaid = Money.sum(bill.paidAgainstDues().stream().map(PaymentPart::amount).toList());
        return new CancelledBill(bill.billNo(), bill.customerName(), bill.totals().total(), bill.paidForBillTotal(),
                bill.toAccount(), duesPaid);
    }
}
