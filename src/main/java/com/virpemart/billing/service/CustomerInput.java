package com.virpemart.billing.service;

/**
 * Customer details exactly as typed in the customer form.
 *
 * @param oldDues old dues from the paper khata, only used when adding a new customer; blank means none
 */
public record CustomerInput(String name, String phone, String address, String notes, String oldDues) {
}
