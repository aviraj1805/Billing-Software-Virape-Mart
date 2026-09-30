package com.virpemart.billing.model;

import java.time.LocalDateTime;

/** One bill in a list, without its lines. */
public record BillSummary(
        long id,
        long billNo,
        LocalDateTime createdAt,
        Money total,
        Money paid,
        Money toAccount,
        int lineCount,
        boolean cancelled) {
}
