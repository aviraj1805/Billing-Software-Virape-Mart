package com.virpemart.billing.service;

import com.virpemart.billing.model.Money;

/** Reads rupee amounts typed by the user, with friendly messages. Shared by all services. */
final class Amounts {

    private Amounts() {
    }

    /**
     * Reads a positive amount such as "44", "44.50", "₹44" or "Rs 1,250".
     *
     * @param field    name of the input box, for {@link ValidationException#field()}
     * @param label    how the amount is called in messages, for example "rate"
     * @param required if false, blank text or zero returns null ("none")
     * @throws ValidationException if the text is not a valid amount above zero
     */
    static Money parsePositive(String field, String text, String label, boolean required) {
        String cleaned = Texts.cleanAmount(text);
        if (cleaned.isEmpty()) {
            if (required) {
                throw new ValidationException(field, "Please enter the " + label + ".");
            }
            return null;
        }
        Money amount;
        try {
            amount = Money.parse(cleaned);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(field, "The " + label + " \"" + text.strip()
                    + "\" is not a valid amount. Use numbers like 45 or 45.50.");
        }
        if (amount.isZero() && !required) {
            return null;
        }
        if (!amount.isPositive()) {
            throw new ValidationException(field, "The " + label + " must be more than zero"
                    + (required ? "." : ", or leave it empty."));
        }
        return amount;
    }
}
