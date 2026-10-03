package com.virpemart.billing.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The lines of a bill being made, and the arithmetic for its totals.
 *
 * <p>All bill arithmetic lives here and in {@link Money}, so the screen and the saved bill always agree:
 * line total = quantity x rate rounded to the paisa; total = subtotal rounded to the nearest rupee.
 *
 * <p>Mistakes such as 1.5 packets raise {@link IllegalArgumentException} with a message for the user.
 */
public final class Cart {

    private final List<CartLine> lines = new ArrayList<>();

    /** The lines, in the order they were added. */
    public List<CartLine> lines() {
        return Collections.unmodifiableList(lines);
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /**
     * Adds a product. If the same product is already on the bill at its normal rate,
     * the quantity is added to that line instead of making a new line.
     *
     * @return the index of the line that was added or changed
     */
    public int addProduct(Product product, Quantity quantity) {
        checkQuantity(product.unit(), quantity);
        for (int i = 0; i < lines.size(); i++) {
            CartLine line = lines.get(i);
            if (product.id() == (line.productId() == null ? -1 : line.productId()) && !line.rateChanged()
                    && line.rate().equals(product.rate())) {
                lines.set(i, line.withQuantity(line.quantity().plus(quantity)));
                return i;
            }
        }
        lines.add(new CartLine(product.id(), product.name(), product.nameMr(), product.unit(), product.packSize(),
                quantity, product.rate(), product.rate(), product.mrp()));
        return lines.size() - 1;
    }

    /** Adds an item that is not in the product list, for this bill only. */
    public int addOneOff(String name, Unit unit, Quantity quantity, Money rate) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Please enter the item name.");
        }
        checkQuantity(unit, quantity);
        checkRate(rate);
        lines.add(new CartLine(null, name.strip(), null, unit, null, quantity, rate, null, null));
        return lines.size() - 1;
    }

    public void setQuantity(int index, Quantity quantity) {
        CartLine line = lines.get(index);
        checkQuantity(line.unit(), quantity);
        lines.set(index, line.withQuantity(quantity));
    }

    /** Changes the rate of one line for this bill only. The product's saved rate is not changed. */
    public void setRate(int index, Money rate) {
        checkRate(rate);
        lines.set(index, lines.get(index).withRate(rate));
    }

    public void remove(int index) {
        lines.remove(index);
    }

    public void clear() {
        lines.clear();
    }

    /** Replaces all lines with the lines of another cart, used when a held bill is continued. */
    public void replaceWith(Cart other) {
        lines.clear();
        lines.addAll(other.lines);
    }

    /** A cart with these lines, used when a cancelled bill is loaded to be corrected. */
    public static Cart of(List<CartLine> lines) {
        Cart cart = new Cart();
        cart.lines.addAll(lines);
        return cart;
    }

    /** A copy with the same lines, used when a bill is put on hold. */
    public Cart copy() {
        Cart copy = new Cart();
        copy.lines.addAll(lines);
        return copy;
    }

    /** Totals of the current lines. */
    public BillTotals totals() {
        return totalsOf(lines);
    }

    /** Totals of any list of lines. The billing service uses this to recalculate before saving. */
    public static BillTotals totalsOf(List<CartLine> lines) {
        Money subtotal = Money.ZERO;
        Money savings = Money.ZERO;
        for (CartLine line : lines) {
            subtotal = subtotal.plus(line.lineTotal());
            savings = savings.plus(line.savings());
        }
        Money total = subtotal.roundToRupee();
        return new BillTotals(subtotal, total.minus(subtotal), total, savings, lines.size());
    }

    /** Quantity must be above zero, and a whole number for items sold by piece. */
    public static void checkQuantity(Unit unit, Quantity quantity) {
        if (quantity == null || quantity.isZero()) {
            throw new IllegalArgumentException("Quantity must be more than zero.");
        }
        if (!unit.isLoose() && !quantity.isWhole()) {
            throw new IllegalArgumentException("This item is sold by piece, so the quantity must be a whole number.");
        }
    }

    private static void checkRate(Money rate) {
        if (rate == null || !rate.isPositive()) {
            throw new IllegalArgumentException("The rate must be more than zero.");
        }
    }
}
