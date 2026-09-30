package com.virpemart.billing.model;

/**
 * A customer with their current balance.
 *
 * @param balance sum of all khata entries: positive means dues, negative means advance
 */
public record CustomerSummary(Customer customer, Money balance) {

    public boolean hasDues() {
        return balance.isPositive();
    }
}
