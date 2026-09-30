package com.virpemart.billing.ui.common;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.virpemart.billing.model.Money;

/** How numbers and dates look on screen: Indian digit grouping and the rupee sign. */
public final class Format {

    private static final Locale INDIA = Locale.forLanguageTag("en-IN");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE, d MMM uuuu", INDIA);

    private Format() {
    }

    /** For example "₹1,25,000.50" or "-₹5.00". Returns "" for null. */
    public static String money(Money amount) {
        if (amount == null) {
            return "";
        }
        long paise = Math.abs(amount.paise());
        String text = indianGrouping(paise / 100) + "." + String.format("%02d", paise % 100);
        return (amount.isNegative() ? "-₹" : "₹") + text;
    }

    /**
     * Indian digit grouping: the last three digits, then groups of two (lakh, crore).
     * Java's built-in number format only groups in threes, so it is done by hand.
     * For example 125000 becomes "1,25,000".
     */
    static String indianGrouping(long number) {
        String rest = Long.toString(number);
        if (rest.length() <= 3) {
            return rest;
        }
        String result = rest.substring(rest.length() - 3);
        rest = rest.substring(0, rest.length() - 3);
        while (rest.length() > 2) {
            result = rest.substring(rest.length() - 2) + "," + result;
            rest = rest.substring(0, rest.length() - 2);
        }
        return rest + "," + result;
    }

    /** For example "Wed, 30 Sept 2026". */
    public static String date(LocalDate date) {
        return date.format(DATE);
    }
}
