package com.virpemart.billing.model;

import java.time.LocalDateTime;

/**
 * One line in a customer's khata.
 *
 * @param amount      signed: positive adds to dues, negative reduces them
 * @param billId      the bill this entry belongs to, or null
 * @param billNo      that bill's number, or null
 * @param paymentMode how the money was paid, for payments only
 * @param note        reason or remark, or null
 * @param createdBy   display name of the person who made the entry
 */
public record LedgerEntry(
        long id,
        long customerId,
        LedgerEntryType type,
        Money amount,
        Long billId,
        Long billNo,
        PaymentMode paymentMode,
        String note,
        LocalDateTime createdAt,
        String createdBy) {
}
