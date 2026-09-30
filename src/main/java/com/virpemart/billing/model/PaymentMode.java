package com.virpemart.billing.model;

/** How a customer paid. */
public enum PaymentMode {

    CASH("Cash"),
    UPI("UPI"),
    CARD("Card");

    private final String label;

    PaymentMode(String label) {
        this.label = label;
    }

    /** Name shown on screen, for example "Cash". */
    public String label() {
        return label;
    }
}
