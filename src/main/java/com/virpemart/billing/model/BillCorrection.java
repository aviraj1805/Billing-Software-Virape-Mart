package com.virpemart.billing.model;

import java.util.List;

/**
 * What is needed to make the corrected bill after a saved bill was cancelled for correction: its customer and items,
 * loaded on the Billing screen to be changed and saved as a new bill.
 *
 * @param cancelled  what cancelling the old bill did
 * @param customerId the khata customer of the old bill, or null for a walk-in customer
 * @param walkInName the walk-in name typed on the old bill, or null
 * @param lines      the old bill's items at the rates charged, with product details as they are now
 */
public record BillCorrection(CancelledBill cancelled, Long customerId, String walkInName, List<CartLine> lines) {

    public BillCorrection {
        lines = List.copyOf(lines);
    }
}
