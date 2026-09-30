package com.virpemart.billing.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CartTest {

    private static final Product SUGAR_LOOSE = new Product(1, "P0001", "Sugar", "साखर", null, null, Unit.KG, null,
            Money.parse("44"), null, true);
    private static final Product SALT = new Product(2, "P0002", "Tata Salt", null, null, null, Unit.PCS, "1 kg",
            Money.parse("28"), Money.parse("30"), true);
    private static final Product DAL = new Product(3, "P0003", "Toor Dal", null, null, null, Unit.KG, null,
            Money.parse("45.50"), null, true);

    @Test
    void emptyCartHasZeroTotals() {
        assertEquals(BillTotals.EMPTY, new Cart().totals());
    }

    @Test
    void addingTheSameProductAgainAddsToItsQuantity() {
        Cart cart = new Cart();
        cart.addProduct(SALT, Quantity.ofWhole(2));
        int index = cart.addProduct(SALT, Quantity.ofWhole(1));

        assertEquals(0, index);
        assertEquals(1, cart.lines().size());
        assertEquals(Quantity.ofWhole(3), cart.lines().getFirst().quantity());
    }

    @Test
    void aLineWithAChangedRateIsNotMergedWith() {
        Cart cart = new Cart();
        cart.addProduct(SALT, Quantity.ofWhole(1));
        cart.setRate(0, Money.parse("25"));
        cart.addProduct(SALT, Quantity.ofWhole(1));

        assertEquals(2, cart.lines().size());
        assertTrue(cart.lines().get(0).rateChanged());
        assertFalse(cart.lines().get(1).rateChanged());
    }

    @Test
    void totalsRoundToTheNearestRupee() {
        Cart cart = new Cart();
        cart.addProduct(SUGAR_LOOSE, Quantity.parse("0.250")); // 11.00
        cart.addProduct(DAL, Quantity.parse("0.333"));          // 45.50 x 0.333 = 15.1515 -> 15.15
        cart.addProduct(SALT, Quantity.ofWhole(2));             // 56.00

        BillTotals totals = cart.totals();

        assertEquals(Money.parse("82.15"), totals.subtotal());
        assertEquals(Money.parse("-0.15"), totals.roundOff());
        assertEquals(Money.parse("82"), totals.total());
        assertEquals(3, totals.lineCount());
    }

    @Test
    void halfARupeeRoundsUp() {
        Cart cart = new Cart();
        cart.addProduct(SUGAR_LOOSE, Quantity.parse("0.125")); // 5.50

        assertEquals(Money.parse("6"), cart.totals().total());
        assertEquals(Money.parse("0.50"), cart.totals().roundOff());
    }

    @Test
    void savingsCompareWithMrp() {
        Cart cart = new Cart();
        cart.addProduct(SALT, Quantity.ofWhole(3)); // MRP 30, rate 28

        assertEquals(Money.parse("6"), cart.totals().savings());
    }

    @Test
    void packedItemsNeedWholeQuantities() {
        Cart cart = new Cart();

        assertThrows(IllegalArgumentException.class, () -> cart.addProduct(SALT, Quantity.parse("1.5")));
        assertThrows(IllegalArgumentException.class, () -> cart.addProduct(SUGAR_LOOSE, Quantity.ZERO));
        cart.addProduct(SALT, Quantity.ofWhole(1));
        assertThrows(IllegalArgumentException.class, () -> cart.setQuantity(0, Quantity.parse("0.5")));
    }

    @Test
    void oneOffItemsNeedNameAndRate() {
        Cart cart = new Cart();

        assertThrows(IllegalArgumentException.class, () -> cart.addOneOff(" ", Unit.PCS, Quantity.ONE, Money.ofRupees(5)));
        assertThrows(IllegalArgumentException.class, () -> cart.addOneOff("Candle", Unit.PCS, Quantity.ONE, Money.ZERO));

        cart.addOneOff(" Candle ", Unit.PCS, Quantity.ofWhole(2), Money.ofRupees(5));
        assertEquals("Candle", cart.lines().getFirst().name());
        assertEquals(Money.ofRupees(10), cart.totals().total());
    }

    @Test
    void copyIsIndependent() {
        Cart cart = new Cart();
        cart.addProduct(SALT, Quantity.ONE);
        Cart copy = cart.copy();
        cart.clear();

        assertTrue(cart.isEmpty());
        assertEquals(1, copy.lines().size());

        cart.addProduct(SUGAR_LOOSE, Quantity.ONE);
        cart.replaceWith(copy);
        assertEquals("Tata Salt", cart.lines().getFirst().name());
        assertEquals(1, cart.lines().size());
    }
}
