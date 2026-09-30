package com.virpemart.billing.model;

/**
 * An account customer (khata customer).
 *
 * @param customerNo automatic number such as C0001
 * @param phone      10-digit phone number, or null
 * @param address    free text, or null
 * @param notes      free text, or null
 * @param active     false when switched off (hidden from billing, khata kept)
 */
public record Customer(
        long id,
        String customerNo,
        String name,
        String phone,
        String address,
        String notes,
        boolean active) {
}
