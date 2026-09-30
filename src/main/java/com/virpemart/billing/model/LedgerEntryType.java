package com.virpemart.billing.model;

/**
 * The kinds of entry in a customer's khata. Amounts are signed: positive means the customer owes more,
 * negative means the customer owes less.
 */
public enum LedgerEntryType {
    /** Old dues copied from the paper khata when the customer was added. Positive. */
    OPENING,
    /** The unpaid part of a bill, added to the account. Positive. */
    SALE_CREDIT,
    /** Money received from the customer. Negative. */
    PAYMENT,
    /** A cancelled bill's credit taken back. Negative. */
    CANCEL_REVERSAL,
    /** An owner's correction with a reason. Either sign. */
    ADJUSTMENT
}
