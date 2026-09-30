package com.virpemart.billing.model;

/** Money paid in one mode, for example Rs 300 by UPI. A bill can be paid with several parts. */
public record PaymentPart(PaymentMode mode, Money amount) {
}
