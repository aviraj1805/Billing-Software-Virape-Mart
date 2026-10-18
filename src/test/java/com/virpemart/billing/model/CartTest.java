package com.virpemart.billing.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class CartTest {

    private static final Product SUGAR_LOOSE = new Product(1, "P0001", "Sugar", "साखर", null, Unit.KG, null,
            Money.parse("44"), null, true);
    private static final Product SALT = new Product(2, "P0002", "Tata Salt", null, null, Unit.PCS, "1 kg",
            Money.parse("28"), Money.parse("30"), true);
    private static final Product DAL = new Product(3, "P0003", "Toor Dal", null, null, Unit.KG, null,
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
    void addingAtTheProductsOwnRateIsANormalLine() {
        Cart cart = new Cart();
        cart.addProduct(SALT, Quantity.ofWhole(2), Money.parse("28"));
        int index = cart.addProduct(SALT, Quantity.ofWhole(1), Money.parse("28.00"));

        assertEquals(0, index, "joins the line at the normal rate, as before");
        assertEquals(Quantity.ofWhole(3), cart.lines().getFirst().quantity());
        assertFalse(cart.lines().getFirst().rateChanged());
    }

    @Test
    void addingAtAnotherRateIsForThisBillOnly() {
        Cart cart = new Cart();
        cart.addProduct(SUGAR_LOOSE, Quantity.parse("1.5"));
        int index = cart.addProduct(SUGAR_LOOSE, Quantity.parse("1"), Money.parse("42.5"));

        assertEquals(1, index, "a separate line, not merged with the normal-rate line");
        CartLine line = cart.lines().get(1);
        assertEquals(Money.parse("42.50"), line.rate());
        assertEquals(Money.parse("44"), line.productRate(), "the normal rate is remembered");
        assertTrue(line.rateChanged());
        assertEquals(Money.parse("42.50"), line.lineTotal());
        assertEquals(Money.parse("44"), SUGAR_LOOSE.rate(), "the product itself is not changed");
    }

    @Test
    void aZeroOrBadQuantityOrRateIsRejectedWhenAdding() {
        Cart cart = new Cart();

        assertThrows(IllegalArgumentException.class, () -> cart.addProduct(SALT, Quantity.ofWhole(1), Money.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> cart.addProduct(SALT, Quantity.ofWhole(1), Money.parse("-5")));
        assertThrows(IllegalArgumentException.class,
                () -> cart.addProduct(SALT, Quantity.parse("1.5"), Money.parse("25")), "packets are whole numbers");
        assertThrows(IllegalArgumentException.class,
                () -> cart.addProduct(SUGAR_LOOSE, Quantity.ZERO, Money.parse("40")));
        assertTrue(cart.isEmpty());
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

    // ------------------------------------------------------------------ जमा

    @Test
    void jamaIsAddedOnceAtRateZeroAndAddsNothingToTheTotal() {
        Cart cart = new Cart();
        cart.addProduct(SUGAR_LOOSE, Quantity.ONE);

        assertEquals(1, cart.addJama());
        assertEquals(1, cart.addJama(), "a bill has जमा at most once");

        assertEquals(2, cart.lines().size());
        CartLine jama = cart.lines().get(1);
        assertTrue(jama.isJama());
        assertEquals(Money.ZERO, jama.rate());
        assertEquals(Money.ZERO, jama.lineTotal());
        assertFalse(jama.rateChanged());
        assertEquals(Money.parse("44"), cart.totals().total());
        assertFalse(cart.lines().getFirst().isJama());
    }

    @Test
    void jamaQuantityAndRateCannotBeChanged() {
        Cart cart = new Cart();
        cart.addJama();

        assertThrows(IllegalArgumentException.class, () -> cart.setRate(0, Money.parse("500")));
        assertThrows(IllegalArgumentException.class, () -> cart.setQuantity(0, Quantity.ofWhole(2)));
        assertEquals(Jama.line(), cart.lines().getFirst());
    }

    @Test
    void jamaCannotBeTypedAsAOneOffItem() {
        Cart cart = new Cart();

        assertThrows(IllegalArgumentException.class,
                () -> cart.addOneOff(" जमा ", Unit.PCS, Quantity.ONE, Money.parse("500")));
        assertTrue(cart.isEmpty());
    }

    @Test
    void zeroRateIsStillRejectedForOtherItems() {
        Cart cart = new Cart();

        assertThrows(IllegalArgumentException.class, () -> cart.addProduct(SALT, Quantity.ONE, Money.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> cart.addOneOff("Candles", Unit.PCS, Quantity.ONE, Money.ZERO));
        cart.addProduct(SALT, Quantity.ONE);
        assertThrows(IllegalArgumentException.class, () -> cart.setRate(0, Money.ZERO));
    }

    // ------------------------------------------------------------------ changing a line in its own row (1.4.0)

    @Test
    void aLineTakenOutAndPutBackUnchangedIsExactlyAsBefore() {
        Cart cart = new Cart();
        cart.addProduct(SUGAR_LOOSE, Quantity.parse("0.5"));
        cart.addProduct(SALT, Quantity.ofWhole(2), Money.parse("25"));
        cart.addProduct(DAL, Quantity.ONE);
        List<CartLine> before = List.copyOf(cart.lines());
        BillTotals totalsBefore = cart.totals();

        CartLine taken = cart.remove(1);
        assertEquals(2, cart.lines().size());
        assertEquals(Money.parse("67.5"), cart.totals().subtotal(), "the total drops while the item is out");
        cart.insert(1, taken);

        assertEquals(before, cart.lines());
        assertEquals(totalsBefore, cart.totals());
    }

    @Test
    void aChangedProductGoesBackIntoItsOwnRowWithNewTotals() {
        Cart cart = new Cart();
        cart.addProduct(SUGAR_LOOSE, Quantity.parse("0.5"));   // 22.00
        cart.addProduct(SALT, Quantity.ofWhole(1));             // 28.00
        cart.addProduct(DAL, Quantity.ONE);                     // 45.50

        cart.remove(1);
        int index = cart.insertProduct(1, SALT, Quantity.ofWhole(3), Money.parse("28"));

        assertEquals(1, index);
        assertEquals(3, cart.lines().size());
        assertEquals("Tata Salt", cart.lines().get(1).name());
        assertEquals(Quantity.ofWhole(3), cart.lines().get(1).quantity());
        assertFalse(cart.lines().get(1).rateChanged());
        assertEquals("Toor Dal", cart.lines().get(2).name());
        assertEquals(Money.parse("151.5"), cart.totals().subtotal());
        assertEquals(Money.parse("152"), cart.totals().total());
    }

    @Test
    void aDifferentProductCanTakeTheRowAndRoundsCorrectly() {
        Product oil = new Product(4, "P0004", "Oil", "तेल", null, Unit.KG, null, Money.parse("47"), null, true);
        Cart cart = new Cart();
        cart.addProduct(SALT, Quantity.ofWhole(2));             // 56.00
        cart.addProduct(SUGAR_LOOSE, Quantity.ONE);             // 44.00

        cart.remove(1);
        cart.insertProduct(1, oil, Quantity.parse("0.333"), Money.parse("47"));

        assertEquals("Oil", cart.lines().get(1).name());
        assertEquals(Money.parse("15.65"), cart.lines().get(1).lineTotal(), "0.333 x 47 = 15.651, HALF_UP");
        assertEquals(Money.parse("71.65"), cart.totals().subtotal());
        assertEquals(Money.parse("72"), cart.totals().total());
        assertEquals(Money.parse("0.35"), cart.totals().roundOff());
    }

    @Test
    void puttingAProductBackNeverJoinsAnotherRowOfTheSameProduct() {
        Cart cart = new Cart();
        cart.addProduct(SALT, Quantity.ofWhole(2));
        cart.addProduct(SUGAR_LOOSE, Quantity.ONE);

        cart.remove(1);
        cart.insertProduct(1, SALT, Quantity.ofWhole(1), Money.parse("28"));

        assertEquals(2, cart.lines().size(), "no row is added or lost");
        assertEquals(Quantity.ofWhole(2), cart.lines().get(0).quantity());
        assertEquals(Quantity.ofWhole(1), cart.lines().get(1).quantity());
    }

    @Test
    void aRateTypedWhenPuttingBackIsMarkedAsChanged() {
        Cart cart = new Cart();
        cart.addProduct(SALT, Quantity.ofWhole(2));

        cart.remove(0);
        cart.insertProduct(0, SALT, Quantity.ofWhole(2), Money.parse("26"));

        assertTrue(cart.lines().getFirst().rateChanged());
        assertEquals(Money.parse("28"), cart.lines().getFirst().productRate());
        assertEquals(Money.parse("52"), cart.totals().total());
    }

    @Test
    void puttingBackChecksQuantityAndRateAndChangesNothingWhenWrong() {
        Cart cart = new Cart();
        cart.addProduct(SUGAR_LOOSE, Quantity.ONE);

        assertThrows(IllegalArgumentException.class,
                () -> cart.insertProduct(0, SALT, Quantity.parse("1.5"), Money.parse("28")));
        assertThrows(IllegalArgumentException.class,
                () -> cart.insertProduct(0, SALT, Quantity.ZERO, Money.parse("28")));
        assertThrows(IllegalArgumentException.class,
                () -> cart.insertProduct(0, SALT, Quantity.ONE, Money.ZERO));
        assertEquals(1, cart.lines().size());
    }

    @Test
    void aOneOffItemIsChangedInItsOwnRow() {
        Cart cart = new Cart();
        cart.addProduct(SALT, Quantity.ofWhole(1));
        cart.addOneOff("Candles", Unit.PCS, Quantity.ofWhole(2), Money.parse("15"));
        cart.addProduct(SUGAR_LOOSE, Quantity.ONE);

        int index = cart.replaceOneOff(1, " Agarbatti ", Unit.PCS, Quantity.ofWhole(3), Money.parse("10"));

        assertEquals(1, index);
        assertEquals(3, cart.lines().size());
        assertEquals("Agarbatti", cart.lines().get(1).name());
        assertEquals(Money.parse("30"), cart.lines().get(1).lineTotal());
        assertEquals(Money.parse("102"), cart.totals().total());
    }

    @Test
    void changingAOneOffItemKeepsTheUsualChecks() {
        Cart cart = new Cart();
        cart.addOneOff("Candles", Unit.PCS, Quantity.ofWhole(2), Money.parse("15"));
        cart.addProduct(SALT, Quantity.ONE);
        cart.addJama();
        CartLine before = cart.lines().getFirst();

        assertThrows(IllegalArgumentException.class,
                () -> cart.replaceOneOff(0, "Candles", Unit.PCS, Quantity.parse("1.5"), Money.parse("15")));
        assertThrows(IllegalArgumentException.class,
                () -> cart.replaceOneOff(0, "Candles", Unit.PCS, Quantity.ONE, Money.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> cart.replaceOneOff(0, " ", Unit.PCS, Quantity.ONE, Money.parse("15")));
        assertThrows(IllegalArgumentException.class,
                () -> cart.replaceOneOff(0, "जमा", Unit.PCS, Quantity.ONE, Money.parse("15")));
        assertThrows(IllegalArgumentException.class,
                () -> cart.replaceOneOff(1, "Candles", Unit.PCS, Quantity.ONE, Money.parse("15")), "a product");
        assertThrows(IllegalArgumentException.class,
                () -> cart.replaceOneOff(2, "Candles", Unit.PCS, Quantity.ONE, Money.parse("15")), "जमा");
        assertEquals(before, cart.lines().getFirst());
    }

    @Test
    void nameOnBillIsTheMarathiNameOrElseTheEnglishName() {
        assertEquals("साखर", SUGAR_LOOSE.nameOnBill());
        assertEquals("Tata Salt 1 kg", SALT.nameOnBill());
        Cart cart = new Cart();
        cart.addProduct(SUGAR_LOOSE, Quantity.ONE);
        cart.addProduct(SALT, Quantity.ONE);
        cart.addOneOff("Candles", Unit.PCS, Quantity.ONE, Money.parse("15"));
        assertEquals("साखर", cart.lines().get(0).nameOnBill());
        assertEquals("Tata Salt 1 kg", cart.lines().get(1).nameOnBill());
        assertEquals("Candles", cart.lines().get(2).nameOnBill());
    }
}
