package com.virpemart.billing.model;

import java.time.LocalDateTime;

/**
 * One record of the audit log: who did what and when.
 *
 * @param userName display name of the user, or null for an automatic action by the app
 * @param action   short code such as BILL_CANCELLED
 * @param details  human-readable description
 */
public record AuditEntry(long id, LocalDateTime createdAt, String userName, String action, String details) {
}
