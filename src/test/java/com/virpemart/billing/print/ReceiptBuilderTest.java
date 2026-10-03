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
import com.virpemart.billing.print.ReceiptLine.ItemRow;
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
            new CartLine(3L, "Tata Salt", "टाटा मीठ", Unit.PCS, "1 kg", Quantity.ofWhole(2), Money.parse("28"),
                    Money.parse("28"), Money.parse("30")));

    private static BillDetails walkIn(String name, String nameMr, List<PaymentPart> paid, String cancelReason) {
        BillTotals totals = Cart.totalsOf(LINES);
        return new BillDetails(7, 12, WHEN, null, name, nameMr, null, LINES, totals, paid, List.of(), Money.ZERO,
                null, null, cancelReason);
    }

    private static BillDetails walkIn(List<PaymentPart> paid, String cancelReason) {
        return walkIn(null, null, paid, cancelReason);
    }

    private static BillDetails khata(Money previous, List<PaymentPart> forBill, List<PaymentPart> againstDues) {
        return khata("सुनीता पाटील", "9876501234", previous, forBill, againstDues);
    }

    /** A khata bill; {@code nameMr} and {@code phone} are null on bills saved before they were kept. */
    private static BillDetails khata(String nameMr, String phone, Money previous, List<PaymentPart> forBill,
                                     List<PaymentPart> againstDues) {
        BillTotals totals = Cart.totalsOf(LINES);
        Money paidForBill = Money.sum(forBill.stream().map(PaymentPart::amount).toList());
        Money paidDues = Money.sum(againstDues.stream().map(PaymentPart::amount).toList());
        Money toAccount = totals.total().minus(paidForBill);
        return new BillDetails(7, 12, WHEN, "C0003", "Sunita Patil", nameMr, phone, LINES, totals, forBill,
                againstDues, toAccount, previous, previous.plus(toAccount).minus(paidDues), null);
    }

    private static PaymentPart cash(String amount) {
        return new PaymentPart(PaymentMode.CASH, Money.parse(amount));
    }

    private static PaymentPart upi(String amount) {
        return new PaymentPart(PaymentMode.UPI, Money.parse(amount));
    }

    @Test
    void walkInBillShowsHeadingMarathiItemsTotalAndPayment() {
        String text = ReceiptBuilder.forBill(walkIn(List.of(cash("50"), upi("32")), null), SHOP, false)
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
                Item | Qty | Rate | Amount
                1. साखर | 0.250 kg | 44.00 | 11.00
                2. Toor Dal | 0.333 kg | 45.50 | 15.15
                3. टाटा मीठ | 2 | 28.00 | 56.00
                ----
                एकूण | ₹82.00
                ----
                Paid by Cash | ₹50.00
                Paid by UPI | ₹32.00
                ----
                Thank you, visit again""", text);
    }

    @Test
    void walkInNameIsPrintedInMarathiWithoutALabel() {
        String text = ReceiptBuilder.forBill(walkIn("Sunil", "सुनील", List.of(cash("82")), null), SHOP, false)
                .toPlainText();

        assertTrue(text.contains("2:05 PM\nसुनील\n----"), text);
        assertFalse(text.contains("Customer"), text);
        assertFalse(text.contains("Sunil"), text);
    }

    @Test
    void khataBillShowsPreviousDuesPaidAndBalanceInMarathi() {
        String text = ReceiptBuilder.forBill(khata(Money.parse("480"), List.of(cash("50")), List.of()), SHOP, false)
                .toPlainText();

        assertTrue(text.contains("2:05 PM\nसुनीता पाटील | No. 9876501234\n----\nItem"), text);
        assertFalse(text.contains("C0003"), "no customer number:\n" + text);
        assertFalse(text.contains("Customer"), text);
        assertTrue(text.endsWith("""
                ----
                एकूण | ₹82.00
                ----
                मागील बाकी | ₹480.00
                जमा | ₹50.00
                एकूण बाकी | ₹512.00
                ----
                Thank you, visit again"""), text);
    }

    @Test
    void oldBillWithoutAMarathiNameReprintsTheSavedName() {
        String text = ReceiptBuilder.forBill(khata(null, null, Money.ZERO, List.of(), List.of()), SHOP, true)
                .toPlainText();

        assertTrue(text.contains("2:05 PM\nSunita Patil\n----"), text);
        assertFalse(text.contains("No. 9"), "no phone:\n" + text);
        assertFalse(text.contains("C0003"), text);
    }

    @Test
    void itemRowsHaveFourColumns() {
        Receipt receipt = ReceiptBuilder.forBill(walkIn(List.of(cash("82")), null), SHOP, false);

        assertTrue(receipt.lines().contains(new ItemRow("Item", "Qty", "Rate", "Amount", Style.SMALL)));
        assertTrue(receipt.lines().contains(new ItemRow("3. टाटा मीठ", "2", "28.00", "56.00", Style.NORMAL)));
    }

    @Test
    void allMoneyPaidWithTheBillIsOneJamaLine() {
        String text = ReceiptBuilder.forBill(
                khata(Money.parse("480"), List.of(cash("82")), List.of(cash("18"), upi("100"))), SHOP, false)
                .toPlainText();

        assertTrue(text.contains("मागील बाकी | ₹480.00\nजमा | ₹200.00\nएकूण बाकी | ₹362.00"), text);
    }

    @Test
    void khataWithNothingPaidAndAnAdvance() {
        String nothing = ReceiptBuilder.forBill(khata(Money.ZERO, List.of(), List.of()), SHOP, false).toPlainText();
        assertTrue(nothing.contains("मागील बाकी | ₹0.00\nजमा | ₹0.00\nएकूण बाकी | ₹82.00"), nothing);

        String advance = ReceiptBuilder.forBill(khata(Money.parse("-200"), List.of(), List.of()), SHOP, false)
                .toPlainText();
        assertTrue(advance.contains("मागील बाकी | -₹200.00\nजमा | ₹0.00\nएकूण बाकी | -₹118.00"), advance);
    }

    @Test
    void englishTotalsWordingIsNotPrinted() {
        String khata = ReceiptBuilder.forBill(khata(Money.parse("-200"), List.of(cash("50")), List.of()), SHOP, false)
                .toPlainText();

        for (String word : List.of("BILL TOTAL", "Total with dues", "Previous", "Paid now", "Balance", "Advance")) {
            assertFalse(khata.contains(word), word + " should not be printed:\n" + khata);
        }
    }

    @Test
    void reprintAndCancelledBillsAreMarked() {
        String reprint = ReceiptBuilder.forBill(walkIn(List.of(cash("82")), null), SHOP, true).toPlainText();
        assertTrue(reprint.contains("----\nDUPLICATE COPY\nBill No. 12"), reprint);

        String cancelled = ReceiptBuilder.forBill(walkIn(List.of(cash("82")), "Wrong items"), SHOP, true)
                .toPlainText();
        assertTrue(cancelled.contains("*** CANCELLED BILL ***\nReason: Wrong items"), cancelled);
        assertFalse(cancelled.contains("DUPLICATE"), cancelled);
    }

    @Test
    void emptyOptionalShopDetailsAreLeftOut() {
        ShopDetails plain = new ShopDetails("Virpe Mart", null, List.of(), null, List.of());

        String text = ReceiptBuilder.forBill(walkIn(List.of(cash("82")), null), plain, false).toPlainText();

        assertTrue(text.startsWith("Virpe Mart\n----\nBill No. 12"), text);
        assertTrue(text.endsWith("Paid by Cash | ₹82.00"), text);
    }

    @Test
    void subtotalRoundOffMrpAndSavingsAreNotPrinted() {
        String text = ReceiptBuilder.forBill(khata(Money.parse("480"), List.of(cash("50")), List.of()), SHOP, false)
                .toPlainText();

        for (String word : List.of("Subtotal", "Round off", "MRP", "saved", "This bill", "Sugar", "Tata Salt")) {
            assertFalse(text.contains(word), word + " should not be printed:\n" + text);
        }
    }

    @Test
    void itemWithoutAMarathiNamePrintsItsEnglishName() {
        CartLine oneOff = new CartLine(null, "Candles", null, Unit.PCS, null, Quantity.ofWhole(2), Money.parse("15"),
                null, null);

        assertEquals("Candles", ReceiptBuilder.printedName(oneOff));
        assertEquals("साखर", ReceiptBuilder.printedName(LINES.getFirst()));
    }

    @Test
    void totalIsPrintedLarge() {
        Receipt receipt = ReceiptBuilder.forBill(walkIn(List.of(cash("82")), null), SHOP, false);

        assertTrue(receipt.lines().contains(new Pair("एकूण", "₹82.00", Style.LARGE)));
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
        assertTrue(text.contains("तूर डाळ | 0.250 kg | 150.00 | 37.50"), text);
        assertTrue(text.contains("नमुना ग्राहक | No. 9876543210"), text);
        assertFalse(text.contains("Total with dues"), text);
        assertTrue(text.contains("मागील बाकी | ₹480.00\nजमा | ₹100.00\nएकूण बाकी | ₹496.00"), text);
    }
}
