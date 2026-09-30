package com.virpemart.billing.model;

import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Sales and money received on one day (or, for a report total, over several days).
 * Cancelled bills are not counted in sales or in money paid at billing.
 *
 * @param day            the day, or null for the total of a date range
 * @param bills          number of bills (not cancelled)
 * @param sales          total of those bills
 * @param onKhata        part of those bills put on customers' khata (credit given)
 * @param paidAtBilling  money paid for those bills at the counter, by mode
 * @param khataPayments  money received against khata dues, by mode: "Receive payment" and extra paid with a bill
 * @param cancelledBills number of bills made on this day that were cancelled
 * @param cancelledTotal total of those cancelled bills
 */
public record DaySummary(
        LocalDate day,
        int bills,
        Money sales,
        Money onKhata,
        Map<PaymentMode, Money> paidAtBilling,
        Map<PaymentMode, Money> khataPayments,
        int cancelledBills,
        Money cancelledTotal) {

    public DaySummary {
        paidAtBilling = copy(paidAtBilling);
        khataPayments = copy(khataPayments);
    }

    /** A day with nothing on it. */
    public static DaySummary empty(LocalDate day) {
        return new DaySummary(day, 0, Money.ZERO, Money.ZERO, Map.of(), Map.of(), 0, Money.ZERO);
    }

    /** Money paid at billing in one mode. */
    public Money paidAtBilling(PaymentMode mode) {
        return paidAtBilling.getOrDefault(mode, Money.ZERO);
    }

    /** Khata payments received in one mode. */
    public Money khataPayments(PaymentMode mode) {
        return khataPayments.getOrDefault(mode, Money.ZERO);
    }

    /** All money received in one mode: at billing plus khata payments. This is what should be in the drawer. */
    public Money received(PaymentMode mode) {
        return paidAtBilling(mode).plus(khataPayments(mode));
    }

    public Money paidAtBillingTotal() {
        return Money.sum(paidAtBilling.values());
    }

    public Money khataPaymentsTotal() {
        return Money.sum(khataPayments.values());
    }

    /** All money received, in every mode. */
    public Money receivedTotal() {
        return paidAtBillingTotal().plus(khataPaymentsTotal());
    }

    /** This day added to another, for a report total. The result has no day. */
    public DaySummary plus(DaySummary other) {
        return new DaySummary(null, bills + other.bills, sales.plus(other.sales), onKhata.plus(other.onKhata),
                add(paidAtBilling, other.paidAtBilling), add(khataPayments, other.khataPayments),
                cancelledBills + other.cancelledBills, cancelledTotal.plus(other.cancelledTotal));
    }

    private static Map<PaymentMode, Money> add(Map<PaymentMode, Money> a, Map<PaymentMode, Money> b) {
        Map<PaymentMode, Money> sum = new EnumMap<>(PaymentMode.class);
        sum.putAll(a);
        b.forEach((mode, amount) -> sum.merge(mode, amount, Money::plus));
        return sum;
    }

    private static Map<PaymentMode, Money> copy(Map<PaymentMode, Money> map) {
        Map<PaymentMode, Money> copy = new EnumMap<>(PaymentMode.class);
        copy.putAll(map);
        return Collections.unmodifiableMap(copy);
    }
}
