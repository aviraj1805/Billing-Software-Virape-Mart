package com.virpemart.billing.service;

import java.util.List;

import com.virpemart.billing.model.CartLine;
import com.virpemart.billing.model.PaymentPart;

/**
 * Everything needed to save a bill.
 *
 * @param customerId khata customer, or null for a walk-in customer
 * @param walkInName optional name printed on a walk-in bill (no account is created); ignored for khata customers
 * @param lines      the bill lines
 * @param payments   money received now, in the order entered; may be empty for a khata customer
 */
public record BillRequest(Long customerId, String walkInName, List<CartLine> lines, List<PaymentPart> payments) {
}
