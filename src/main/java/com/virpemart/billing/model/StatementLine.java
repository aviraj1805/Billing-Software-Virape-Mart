package com.virpemart.billing.model;

/**
 * A khata entry with the balance right after it, like a line in the paper khata.
 *
 * @param balanceAfter positive means dues, negative means advance
 */
public record StatementLine(LedgerEntry entry, Money balanceAfter) {
}
