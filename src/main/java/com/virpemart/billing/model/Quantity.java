package com.virpemart.billing.model;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * A quantity held exactly as a whole number of thousandths.
 *
 * <p>0.250 kg is stored as 250 and 2 pieces as 2000. Three decimals are enough for loose items
 * sold by weight (grams) and avoid rounding problems of {@code double}. Quantities are never negative.
 *
 * @param milli the quantity in thousandths of a unit
 */
public record Quantity(long milli) implements Comparable<Quantity> {

    /** Zero quantity. */
    public static final Quantity ZERO = new Quantity(0);

    /** One whole unit (1 kg, 1 litre or 1 piece). */
    public static final Quantity ONE = new Quantity(1000);

    private static final long MILLI_PER_UNIT = 1000;

    /** Digits with an optional decimal point and at most 3 decimals. */
    private static final Pattern QUANTITY = Pattern.compile("\\d+(\\.\\d{0,3})?|\\.\\d{1,3}");

    public Quantity {
        if (milli < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative.");
        }
    }

    /** A whole number of units, for example {@code ofWhole(3)} is 3 pieces. */
    public static Quantity ofWhole(long units) {
        return new Quantity(Math.multiplyExact(units, MILLI_PER_UNIT));
    }

    /**
     * Reads a quantity typed by a person, such as "2", "0.5" or "0.250".
     *
     * @throws IllegalArgumentException if the text is not valid or has more than 3 decimals
     */
    public static Quantity parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Please enter a quantity.");
        }
        String cleaned = text.strip();
        if (!QUANTITY.matcher(cleaned).matches()) {
            throw new IllegalArgumentException(
                    "\"" + cleaned + "\" is not a valid quantity. Use numbers like 2 or 0.250.");
        }
        try {
            return new Quantity(new BigDecimal(cleaned).movePointRight(3).longValueExact());
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("The quantity \"" + cleaned + "\" is too large.", e);
        }
    }

    public Quantity plus(Quantity other) {
        return new Quantity(Math.addExact(milli, other.milli));
    }

    /** True for whole numbers such as 2 or 5, false for 0.5. */
    public boolean isWhole() {
        return milli % MILLI_PER_UNIT == 0;
    }

    public boolean isZero() {
        return milli == 0;
    }

    @Override
    public int compareTo(Quantity other) {
        return Long.compare(milli, other.milli);
    }

    /** Plain text without needless zeros, for example "2", "0.5" or "0.25". */
    public String toPlainString() {
        return BigDecimal.valueOf(milli, 3).stripTrailingZeros().toPlainString();
    }

    @Override
    public String toString() {
        return toPlainString();
    }
}
