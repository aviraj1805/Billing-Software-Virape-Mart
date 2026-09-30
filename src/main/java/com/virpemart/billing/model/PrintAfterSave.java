package com.virpemart.billing.model;

/** What happens after a bill is saved. The owner chooses this in Settings. */
public enum PrintAfterSave {

    ASK("Ask \"Print?\" after each bill"),
    ALWAYS("Print every bill automatically"),
    NEVER("Do not print (reprint when needed)");

    private final String label;

    PrintAfterSave(String label) {
        this.label = label;
    }

    /** Name shown in Settings. */
    public String label() {
        return label;
    }
}
