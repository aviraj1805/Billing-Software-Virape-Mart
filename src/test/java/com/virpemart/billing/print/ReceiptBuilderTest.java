package com.virpemart.billing.print;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.virpemart.billing.model.BillDetails;
import com.virpemart.billing.model.BillTotals;
import com.virpemart.billing.model.Cart;
import com.virpemart.billing.model.CartLine;
import com.virpemart.billing.model.Money;
import com.virpemart.billing.model.PaymentMode;
import com.virpemart.billing.model.PaymentPart;
import com.virpemart.billing.model.Quantity;
import com.virpemart.billing.model.ShopDetails;
import com.virpemart.billing.model.Unit;
import com.virpemart.billing.print.ReceiptLine.Pair;
import com.virpemart.billing.print.ReceiptLine.Style;

class ReceiptBuilderTest {

    private static final ShopDetails SHOP = new ShopDetails("Virpe Mart", "विरपे मार्ट",
            List.of("Main Road", "Near Bus Stand"), "98765 43210", List.of("Thank you, visit again"));
    private static final LocalDateTime WHEN = LocalDateTime.of(2026, 9, 30, 14, 5);

    /** Sugar 0.250 kg x 44 = 11.00, dal 0.333 kg x 45.50 = 15.15, salt 2 x 28 (MRP 30) = 56.00. Total 82.15 -> 82. */
    private static final List<CartLine> LINES = List.of(
            new CartLine(1L, "Sugar", "साखर", Unit.KG, null, Quantity.parse("0.250"), Money.parse("44"),
                    Money.parse("44"), null),
            new CartLine(2L, "Toor Dal", null, Unit.KG, null, Quantity.parse("0.333"), Money.parse("45.50"),
                    Money.parse("45.50"), null),
            new CartLine(3L, "Tata Salt", null, Unit.PCS, "1 kg", Quantity.ofWhole(2), Money.parse("28"),
                    Money.parse("28"), Money.parse("30")));

    private static BillDetails walkIn(String name, List<PaymentPart> paid, String cancelReason) {
        BillTotals totals = Cart.totalsOf(LINES);
        return new BillDetails(7, 12, WHEN, null, name, LINES, totals, paid, List.of(), Money.ZERO, null, null,
                cancelReason);
    }

    private static BillDetails khata(Money previous, List<PaymentPart> forBill, List<PaymentPart> againstDues) {
        BillTotals totals = Cart.totalsOf(LINES);
        Money paidForBill = Money.sum(forBill.stream().map(PaymentPart::amount).toList());
        Money paidDues = Money.sum(againstDues.stream().map(PaymentPart::amount).toList());
        Money toAccount = totals.total().minus(paidForBill);
        return new BillDetails(7, 12, WHEN, "C0003", "Sunita Patil", LINES, totals, forBill, againstDues, toAccount,
                previous, previous.plus(toAccount).minus(paidDues), null);
    }

    private static PaymentPart cash(String amount) {
        return new PaymentPart(PaymentMode.CASH, Money.parse(amount));
    }

    private static PaymentPart upi(String amount) {
        return new PaymentPart(PaymentMode.UPI, Money.parse(amount));
    }

    @Test
    void walkInBillShowsHeadingItemsTotalsAndPayment() {
        String text = ReceiptBuilder.forBill(walkIn(null, List.of(cash("50"), upi("32")), null), SHOP, false)
                .toPlainText();

        assertEquals("""
                Virpe Mart
                विरपे मार्ट
                Main Road
                Near Bus Stand
                Phone: 98765 43210
                ----
                Bill No. 12 | 30/09/2026 2:05 PM
                ----
                Item | Amount
                1. Sugar
                     साखर
                     0.250 kg x 44.00 | 11.00
                2. Toor Dal
                     0.333 kg x 45.50 | 15.15
                3. Tata Salt 1 kg
                     2 x 28.00  (MRP 30.00) | 56.00
                ----
                Subtotal (3 items) | 82.15
                Round off | -0.15
                BILL TOTAL | ₹82.00
                You saved ₹4.00 on MRP
                ----
                Paid by Cash | ₹50.00
                Paid by UPI | ₹32.00
                ----
                Thank you, visit again""", text);
    }

    @Test
    void walkInNameIsPrintedWhenGiven() {
        String text = ReceiptBuilder.forBill(walkIn("Sunil", List.of(cash("82")), null), SHOP, false).toPlainText();

        assertTrue(text.contains("Customer: Sunil\n"), text);
    }

    @Test
    void khataBillShowsThisBillPreviousDuesAndTotalWithDues() {
        String text = ReceiptBuilder.forBill(khata(Money.parse("480"), List.of(cash("50")), List.of()), SHOP, false)
                .toPlainText();

        assertTrue(text.contains("Customer: Sunita Patil (C0003)"), text);
        assertTrue(text.endsWith("""
                ----
                This bill | ₹82.00
                Previous dues | ₹480.00
                Total with dues | ₹562.00
                Paid now (Cash) | ₹50.00
                Balance dues | ₹512.00
                ----
                Thank you, visit again"""), text);
    }

    @Test
    void khataPaymentTowardsOldDuesIsAddedToPaidNow() {
        String text = ReceiptBuilder.forBill(
                khata(Money.parse("480"), List.of(cash("82")), List.of(cash("18"), upi("100"))), SHOP, false)
                .toPlainText();

        assertTrue(text.contains("Paid now (Cash) | ₹100.00\nPaid now (UPI) | ₹100.00\nBalance dues | ₹362.00"),
                text);
    }

    @Test
    void khataWithNothingPaidAndAnAdvance() {
        String nothing = ReceiptBuilder.forBill(khata(Money.ZERO, List.of(), List.of()), SHOP, false).toPlainText();
        assertTrue(nothing.contains("Previous dues | ₹0.00\nTotal with dues | ₹82.00\nPaid now | ₹0.00\n"
                + "Balance dues | ₹82.00"), nothing);

        String advance = ReceiptBuilder.forBill(khata(Money.parse("-200"), List.of(), List.of()), SHOP, false)
                .toPlainText();
        assertTrue(advance.contains("Previous advance | -₹200.00\nTotal with dues | -₹118.00"), advance);
        assertTrue(advance.contains("Advance left | ₹118.00"), advance);
    }

    @Test
    void reprintAndCancelledBillsAreMarked() {
        String reprint = ReceiptBuilder.forBill(walkIn(null, List.of(cash("82")), null), SHOP, true).toPlainText();
        assertTrue(reprint.contains("----\nDUPLICATE COPY\nBill No. 12"), reprint);

        String cancelled = ReceiptBuilder.forBill(walkIn(null, List.of(cash("82")), "Wrong items"), SHOP, true)
                .toPlainText();
        assertTrue(cancelled.contains("*** CANCELLED BILL ***\nReason: Wrong items"), cancelled);
        assertFalse(cancelled.contains("DUPLICATE"), cancelled);
    }

    @Test
    void emptyOptionalShopDetailsAreLeftOut() {
        ShopDetails plain = new ShopDetails("Virpe Mart", null, List.of(), null, List.of());

        String text = ReceiptBuilder.forBill(walkIn(null, List.of(cash("82")), null), plain, false).toPlainText();

        assertTrue(text.startsWith("Virpe Mart\n----\nBill No. 12"), text);
        assertTrue(text.endsWith("Paid by Cash | ₹82.00"), text);
    }

    @Test
    void roundOffUpShowsAPlusSignAndNoRoundOffLineWhenExact() {
        CartLine half = new CartLine(1L, "Sugar", null, Unit.KG, null, Quantity.parse("0.125"), Money.parse("44"),
                Money.parse("44"), null); // 5.50 -> 6
        BillDetails up = new BillDetails(1, 1, WHEN, null, null, List.of(half), Cart.totalsOf(List.of(half)),
                List.of(cash("6")), List.of(), Money.ZERO, null, null, null);
        assertTrue(ReceiptBuilder.forBill(up, SHOP, false).toPlainText().contains("Round off | +0.50"));

        CartLine exact = half.withQuantity(Quantity.ONE);
        BillDetails none = new BillDetails(1, 1, WHEN, null, null, List.of(exact), Cart.totalsOf(List.of(exact)),
                List.of(cash("44")), List.of(), Money.ZERO, null, null, null);
        assertFalse(ReceiptBuilder.forBill(none, SHOP, false).toPlainText().contains("Round off"));
    }

    @Test
    void totalIsPrintedLarge() {
        Receipt receipt = ReceiptBuilder.forBill(walkIn(null, List.of(cash("82")), null), SHOP, false);

        assertTrue(receipt.lines().contains(new Pair("BILL TOTAL", "₹82.00", Style.LARGE)));
    }

    @Test
    void quantitiesAreShownAsWeighedOrCounted() {
        assertEquals("0.500 kg", ReceiptBuilder.quantity(Quantity.parse("0.5"), Unit.KG));
        assertEquals("1.000 litre", ReceiptBuilder.quantity(Quantity.ONE, Unit.L));
        assertEquals("3", ReceiptBuilder.quantity(Quantity.ofWhole(3), Unit.PCS));
    }

    @Test
    void sampleReceiptIsAClearlyMarkedKhataBill() {
        String text = ReceiptBuilder.sample(SHOP, WHEN).toPlainText();

        assertTrue(text.contains("TEST PRINT - NOT A REAL BILL"), text);
        assertTrue(text.contains("तूर डाळ"), text);
        assertTrue(text.contains("Round off | +0.50"), text);
        assertTrue(text.contains("Total with dues | ₹596.00"), text);
        assertTrue(text.contains("Balance dues | ₹496.00"), text);
    }
}
