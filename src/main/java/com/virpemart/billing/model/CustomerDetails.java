package com.virpemart.billing.model;

/**
 * Checked, cleaned-up customer details, ready to be saved.
 *
 * @param phone   10 digits, or null
 * @param address free text, or null
 * @param notes   free text, or null
 */
public record CustomerDetails(String name, String phone, String address, String notes) {
}
