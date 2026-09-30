package com.virpemart.billing.ui.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.virpemart.billing.model.Money;

/**
 * How numbers, dates and phone numbers look on screen: Indian digit grouping and the rupee sign.
 * Formats use fixed English patterns, because the packaged app has less locale data than the JDK.
 */
public final class Format {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE, d MMM uuuu", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM uuuu, h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME_SHORT = DateTimeFormatter.ofPattern("dd/MM/yy h:mm a", Locale.ENGLISH);

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

    /** A customer balance in words: "Dues ₹1,250.00", "Advance ₹200.00" or "No dues". */
    public static String balance(Money balance) {
        if (balance.isPositive()) {
            return "Dues " + money(balance);
        }
        if (balance.isNegative()) {
            return "Advance " + money(balance.negate());
        }
        return "No dues";
    }

    /** A balance for narrow table columns: "₹600.00", "Adv ₹200.00" or "₹0.00". */
    public static String balanceShort(Money balance) {
        return balance.isNegative() ? "Adv " + money(balance.negate()) : money(balance);
    }

    /** "9876543210" becomes "98765 43210". Returns "" for null. */
    public static String phone(String digits) {
        if (digits == null) {
            return "";
        }
        return digits.length() == 10 ? digits.substring(0, 5) + " " + digits.substring(5) : digits;
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

    /** For example "Wed, 30 Sep 2026". */
    public static String date(LocalDate date) {
        return date.format(DATE);
    }

    /** For example "30 Sep 2026, 2:05 PM". */
    public static String dateTime(LocalDateTime dateTime) {
        return dateTime.format(DATE_TIME);
    }

    /** Compact form for table columns, for example "30/09/26 2:05 PM". */
    public static String dateTimeShort(LocalDateTime dateTime) {
        return dateTime.format(DATE_TIME_SHORT);
    }
}
