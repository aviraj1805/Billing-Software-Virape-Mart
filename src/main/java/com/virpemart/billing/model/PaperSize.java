package com.virpemart.billing.model;

/** The paper in the bill printer. The bill is laid out to fit it. */
public enum PaperSize {

    ROLL_58("58 mm roll (small receipt printer)"),
    ROLL_80("80 mm roll (receipt printer)"),
    A4("A4 sheet (normal printer)");

    private final String label;

    PaperSize(String label) {
        this.label = label;
    }

    /** Name shown in Settings. */
    public String label() {
        return label;
    }

    /** True for thermal receipt rolls, false for normal sheets. */
    public boolean isRoll() {
        return this != A4;
    }
}
