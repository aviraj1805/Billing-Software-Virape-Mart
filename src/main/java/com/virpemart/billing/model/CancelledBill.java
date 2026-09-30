package com.virpemart.billing.model;

/**
 * What happened when a bill was cancelled, so the screen can tell the owner what to do.
 *
 * @param giveBack      money paid for this bill at the counter, which the owner gives back to the customer
 * @param takenOffKhata the part of the bill that was on the khata and has now been taken off it
 */
public record CancelledBill(long billNo, Money giveBack, Money takenOffKhata) {
}
