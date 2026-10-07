package com.virpemart.billing.model;

import java.util.Locale;

/**
 * "जमा": a built-in bill item with no rate, for a khata customer who comes only to pay money (the user's choice,
 * version 1.3.0).
 *
 * <p>It is not a row in the product list (products must have a rate above zero). On a bill it is a line without a
 * product, like a one-off item, always 1 piece at rate 0. The money paid goes through the normal payment window: for a
 * khata customer everything paid beyond the bill is recorded in the khata against old dues, and the bill prints
 * मागील बाकी, जमा and एकूण बाकी as usual. Its rate is locked at 0, so the money paid can never be typed in as a price.
 */
public final class Jama {

    /** The item's name, on screen and on the printed bill. */
    public static final String NAME = "जमा";

    /** What can be typed in product search to find it, besides the Marathi name. */
    private static final String ENGLISH = "jama";

    private Jama() {
    }

    /** The bill line: 1 piece at rate 0, not linked to a product. */
    public static CartLine line() {
        return new CartLine(null, NAME, null, Unit.PCS, null, Quantity.ONE, Money.ZERO, null, null);
    }

    /** True for the जमा line of a bill (a line without a product, named जमा). */
    public static boolean isJama(CartLine line) {
        return line.productId() == null && NAME.equals(line.name());
    }

    /** True when the whole word is typed: "जमा" or "jama" (any capitals). Then it is listed first. */
    public static boolean matchesFully(String text) {
        String typed = clean(text);
        return typed.equals(NAME) || typed.equals(ENGLISH);
    }

    /** True when the typed text is the start of "जमा" or "jama", for example "ज" or "jam". Then it is listed last. */
    public static boolean matchesStart(String text) {
        String typed = clean(text);
        return !typed.isEmpty() && (NAME.startsWith(typed) || ENGLISH.startsWith(typed));
    }

    private static String clean(String text) {
        return text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
    }
}
