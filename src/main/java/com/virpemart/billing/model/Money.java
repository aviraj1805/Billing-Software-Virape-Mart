package com.virpemart.billing.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * An amount of money in Indian rupees, held exactly as a whole number of paise.
 *
 * <p>Rs 45.50 is stored as 4550 paise. We never use {@code double} or {@code float} for money
 * because they cannot represent values like 0.10 exactly, which causes one-paisa errors.
 * Multiplication uses {@link BigDecimal} and rounds HALF_UP (0.5 paisa and above rounds up).
 *
 * @param paise the amount in paise; may be negative (for example a ledger payment entry)
 */
public record Money(long paise) implements Comparable<Money> {

    /** Rs 0.00. */
    public static final Money ZERO = new Money(0);

    private static final long PAISE_PER_RUPEE = 100;
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    /** Optional minus sign, digits, optional decimal point with at most 2 digits. */
    private static final Pattern AMOUNT = Pattern.compile("-?(\\d+(\\.\\d{0,2})?|\\.\\d{1,2})");

    /** Creates an amount from paise, for example {@code ofPaise(4550)} is Rs 45.50. */
    public static Money ofPaise(long paise) {
        return new Money(paise);
    }

    /** Creates an amount from whole rupees, for example {@code ofRupees(45)} is Rs 45.00. */
    public static Money ofRupees(long rupees) {
        return new Money(Math.multiplyExact(rupees, PAISE_PER_RUPEE));
    }

    /**
     * Reads an amount typed by a person, such as "45", "45.5" or "45.50".
     *
     * @throws IllegalArgumentException if the text is not a valid amount or has more than 2 decimals
     */
    public static Money parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Please enter an amount.");
        }
        String cleaned = text.strip().replace(",", "");
        if (!AMOUNT.matcher(cleaned).matches()) {
            throw new IllegalArgumentException(
                    "\"" + text.strip() + "\" is not a valid amount. Use numbers like 45 or 45.50.");
        }
        try {
            BigDecimal rupees = new BigDecimal(cleaned);
            return new Money(rupees.movePointRight(2).longValueExact());
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("The amount \"" + text.strip() + "\" is too large.", e);
        }
    }

    /** Adds all amounts together. */
    public static Money sum(Iterable<Money> amounts) {
        Money total = ZERO;
        for (Money amount : amounts) {
            total = total.plus(amount);
        }
        return total;
    }

    public Money plus(Money other) {
        return new Money(Math.addExact(paise, other.paise));
    }

    public Money minus(Money other) {
        return new Money(Math.subtractExact(paise, other.paise));
    }

    public Money negate() {
        return new Money(Math.negateExact(paise));
    }

    /**
     * Rate times quantity, rounded HALF_UP to the nearest paisa.
     * Example: Rs 45.50 per kg times 0.333 kg is Rs 15.15.
     */
    public Money times(Quantity quantity) {
        BigDecimal exact = BigDecimal.valueOf(paise)
                .multiply(BigDecimal.valueOf(quantity.milli()))
                .divide(THOUSAND, 0, RoundingMode.HALF_UP);
        return new Money(exact.longValueExact());
    }

    /**
     * Rounds to the nearest whole rupee, HALF_UP. Rs 10.49 becomes Rs 10, Rs 10.50 becomes Rs 11.
     */
    public Money roundToRupee() {
        BigDecimal rupees = BigDecimal.valueOf(paise)
                .divide(BigDecimal.valueOf(PAISE_PER_RUPEE), 0, RoundingMode.HALF_UP);
        return ofRupees(rupees.longValueExact());
    }

    public boolean isZero() {
        return paise == 0;
    }

    public boolean isPositive() {
        return paise > 0;
    }

    public boolean isNegative() {
        return paise < 0;
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(paise, other.paise);
    }

    /**
     * Text with Indian digit grouping and 2 decimals, without a currency sign,
     * for example "1,25,000.50" or "-5.00". Used on screen and on printed bills.
     */
    public String toGroupedString() {
        long abs = Math.abs(paise);
        String text = indianGrouping(abs / 100) + "." + String.format("%02d", abs % 100);
        return paise < 0 ? "-" + text : text;
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

    /** Plain text with exactly 2 decimals, for example "45.50" or "-5.00". */
    public String toPlainString() {
        return BigDecimal.valueOf(paise, 2).toPlainString();
    }

    @Override
    public String toString() {
        return "Rs " + toPlainString();
    }
}
