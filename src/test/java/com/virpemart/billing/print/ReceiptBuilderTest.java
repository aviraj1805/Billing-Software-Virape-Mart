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
import com.virpemart.billing.model.Jama;
import com.virpemart.billing.model.Money;
import com.virpemart.billing.model.PaymentMode;
import com.virpemart.billing.model.PaymentPart;
import com.virpemart.billing.model.Quantity;
import com.virpemart.billing.model.ShopDetails;
import com.virpemart.billing.model.Unit;
import com.virpemart.billing.print.ReceiptLine.ItemRow;
import com.virpemart.billing.print.ReceiptLine.Pair;
import com.virpemart.billing.print.ReceiptLine.Style;
import com.virpemart.billing.print.ReceiptLine.Text;

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

    private static BillDetails walkIn(String name, List<PaymentPart> paid, String cancelReason) {
        BillTotals totals = Cart.totalsOf(LINES);
        return new BillDetails(7, 12, WHEN, null, name, null, LINES, totals, paid, List.of(), Money.ZERO,
                null, null, cancelReason);
    }

    private static BillDetails walkIn(List<PaymentPart> paid, String cancelReason) {
        return walkIn(null, paid, cancelReason);
    }

    private static BillDetails khata(Money previous, List<PaymentPart> forBill, List<PaymentPart> againstDues) {
        return khata("सुनीता पाटील", "9876501234", previous, forBill, againstDues);
    }

    /** A khata bill with the customer's name as saved; {@code phone} is null when the customer has none. */
    private static BillDetails khata(String name, String phone, Money previous, List<PaymentPart> forBill,
                                     List<PaymentPart> againstDues) {
        BillTotals totals = Cart.totalsOf(LINES);
        Money paidForBill = Money.sum(forBill.stream().map(PaymentPart::amount).toList());
        Money paidDues = Money.sum(againstDues.stream().map(PaymentPart::amount).toList());
        Money toAccount = totals.total().minus(paidForBill);
        return new BillDetails(7, 12, WHEN, "C0003", name, phone, LINES, totals, forBill,
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
        String text = ReceiptBuilder.forBill(walkIn(List.of(cash("50"), upi("32")), null), SHOP)
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
                1. साखर | 0.25 kg | 44 | 11
                2. Toor Dal | 0.333 kg | 45.5 | 15.15
                3. टाटा मीठ | 2 Pkt | 28 | 56
                ----
                एकूण | ₹82
                ----
                Paid by Cash | ₹50
                Paid by UPI | ₹32
                ----
                Thank you, visit again""", text);
    }

    @Test
    void walkInNameIsPrintedExactlyAsTypedWithoutALabel() {
        String text = ReceiptBuilder.forBill(walkIn("Sunil", List.of(cash("82")), null), SHOP)
                .toPlainText();

        assertTrue(text.contains("2:05 PM\nSunil\n----"), "English letters are not changed to Marathi:\n" + text);
        assertFalse(text.contains("Customer"), text);
        assertFalse(text.contains("सुनील"), text);
    }

    @Test
    void khataBillShowsPreviousDuesPaidAndBalanceInMarathi() {
        String text = ReceiptBuilder.forBill(khata(Money.parse("480"), List.of(cash("50")), List.of()), SHOP)
                .toPlainText();

        assertTrue(text.contains("2:05 PM\nसुनीता पाटील | No. 9876501234\n----\nItem"), text);
        assertFalse(text.contains("C0003"), "no customer number:\n" + text);
        assertFalse(text.contains("Customer"), text);
        assertTrue(text.endsWith("""
                ----
                एकूण | ₹82
                ----
                मागील बाकी | ₹480
                जमा | ₹50
                एकूण बाकी | ₹512
                ----
                Thank you, visit again"""), text);
    }

    @Test
    void khataCustomerWithoutAPhonePrintsOnlyTheSavedName() {
        String text = ReceiptBuilder.forBill(khata("Sunita Patil", null, Money.ZERO, List.of(), List.of()), SHOP)
                .toPlainText();

        assertTrue(text.contains("2:05 PM\nSunita Patil\n----"), text);
        assertFalse(text.contains("No. 9"), "no phone:\n" + text);
        assertFalse(text.contains("C0003"), text);
    }

    @Test
    void itemRowsHaveFourColumns() {
        Receipt receipt = ReceiptBuilder.forBill(walkIn(List.of(cash("82")), null), SHOP);

        assertTrue(receipt.lines().contains(new ItemRow("Item", "Qty", "Rate", "Amount", Style.SMALL)));
        assertTrue(receipt.lines().contains(new ItemRow("3. टाटा मीठ", "2 Pkt", "28", "56", Style.NORMAL)));
    }

    @Test
    void allMoneyPaidWithTheBillIsOneJamaLine() {
        String text = ReceiptBuilder.forBill(
                khata(Money.parse("480"), List.of(cash("82")), List.of(cash("18"), upi("100"))), SHOP)
                .toPlainText();

        assertTrue(text.contains("मागील बाकी | ₹480\nजमा | ₹200\nएकूण बाकी | ₹362"), text);
    }

    @Test
    void khataWithNothingPaidAndAnAdvance() {
        String nothing = ReceiptBuilder.forBill(khata(Money.ZERO, List.of(), List.of()), SHOP).toPlainText();
        assertTrue(nothing.contains("मागील बाकी | ₹0\nजमा | ₹0\nएकूण बाकी | ₹82"), nothing);

        String advance = ReceiptBuilder.forBill(khata(Money.parse("-200"), List.of(), List.of()), SHOP)
                .toPlainText();
        assertTrue(advance.contains("मागील बाकी | -₹200\nजमा | ₹0\nएकूण बाकी | -₹118"), advance);
    }

    @Test
    void englishTotalsWordingIsNotPrinted() {
        String khata = ReceiptBuilder.forBill(khata(Money.parse("-200"), List.of(cash("50")), List.of()), SHOP)
                .toPlainText();

        for (String word : List.of("BILL TOTAL", "Total with dues", "Previous", "Paid now", "Balance", "Advance")) {
            assertFalse(khata.contains(word), word + " should not be printed:\n" + khata);
        }
    }

    @Test
    void printingAgainGivesTheSamePaperWithoutADuplicateMark() {
        Receipt first = ReceiptBuilder.forBill(khata(Money.parse("480"), List.of(cash("50")), List.of()), SHOP);
        Receipt again = ReceiptBuilder.forBill(khata(Money.parse("480"), List.of(cash("50")), List.of()), SHOP);

        assertEquals(first, again);
        assertFalse(first.toPlainText().toUpperCase().contains("DUPLICATE"), first.toPlainText());
        assertTrue(first.toPlainText().contains("Phone: 98765 43210\n----\nBill No. 12"), first.toPlainText());
    }

    @Test
    void onlyACancelledBillIsMarked() {
        String cancelled = ReceiptBuilder.forBill(walkIn(List.of(cash("82")), "Wrong items"), SHOP)
                .toPlainText();
        assertTrue(cancelled.contains("*** CANCELLED BILL ***\nReason: Wrong items"), cancelled);
        assertFalse(cancelled.toUpperCase().contains("DUPLICATE"), cancelled);
    }

    @Test
    void customerNameAndPhoneAreBoldAndBillNumberAndDateAreNormal() {
        Receipt khata = ReceiptBuilder.forBill(khata(Money.ZERO, List.of(), List.of()), SHOP);
        assertTrue(khata.lines().contains(new Pair("Bill No. 12", "30/09/2026 2:05 PM", Style.NORMAL)));
        assertTrue(khata.lines().contains(new Pair("सुनीता पाटील", "No. 9876501234", Style.BOLD)));

        Receipt noPhone = ReceiptBuilder.forBill(khata("Sunita Patil", null, Money.ZERO, List.of(), List.of()), SHOP);
        assertTrue(noPhone.lines().contains(new Text("Sunita Patil", false, Style.BOLD)));

        Receipt walkIn = ReceiptBuilder.forBill(walkIn("Sunil", List.of(cash("82")), null), SHOP);
        assertTrue(walkIn.lines().contains(new Text("Sunil", false, Style.BOLD)));
    }

    @Test
    void jamaBillPrintsTheNameOnlyThenWhatWasPaidAndTheDuesLeft() {
        List<CartLine> lines = List.of(Jama.line());
        BillTotals totals = Cart.totalsOf(lines);
        BillDetails bill = new BillDetails(7, 12, WHEN, "C0003", "सुनीता पाटील", "9876501234", lines, totals,
                List.of(), List.of(cash("300")), Money.ZERO, Money.parse("480"), Money.parse("180"), null);

        Receipt receipt = ReceiptBuilder.forBill(bill, SHOP);

        assertTrue(receipt.lines().contains(new ItemRow("1. जमा", "", "", "", Style.NORMAL)));
        assertTrue(receipt.toPlainText().endsWith("Item | Qty | Rate | Amount\n"
                + "1. जमा |  |  | \n"
                + "----\n"
                + "एकूण | ₹0\n"
                + "----\n"
                + "मागील बाकी | ₹480\n"
                + "जमा | ₹300\n"
                + "एकूण बाकी | ₹180\n"
                + "----\n"
                + "Thank you, visit again"), receipt.toPlainText());
    }

    @Test
    void emptyOptionalShopDetailsAreLeftOut() {
        ShopDetails plain = new ShopDetails("Virpe Mart", null, List.of(), null, List.of());

        String text = ReceiptBuilder.forBill(walkIn(List.of(cash("82")), null), plain).toPlainText();

        assertTrue(text.startsWith("Virpe Mart\n----\nBill No. 12"), text);
        assertTrue(text.endsWith("Paid by Cash | ₹82"), text);
    }

    @Test
    void subtotalRoundOffMrpAndSavingsAreNotPrinted() {
        String text = ReceiptBuilder.forBill(khata(Money.parse("480"), List.of(cash("50")), List.of()), SHOP)
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
        Receipt receipt = ReceiptBuilder.forBill(walkIn(List.of(cash("82")), null), SHOP);

        assertTrue(receipt.lines().contains(new Pair("एकूण", "₹82", Style.LARGE)));
    }

    @Test
    void quantitiesHaveNoNeedlessZerosAndShowKgLtrOrPkt() {
        assertEquals("1 kg", ReceiptBuilder.quantity(Quantity.parse("1.000"), Unit.KG));
        assertEquals("2 kg", ReceiptBuilder.quantity(Quantity.parse("2"), Unit.KG));
        assertEquals("1.5 kg", ReceiptBuilder.quantity(Quantity.parse("1.500"), Unit.KG));
        assertEquals("0.5 kg", ReceiptBuilder.quantity(Quantity.parse("0.5"), Unit.KG));
        assertEquals("0.25 kg", ReceiptBuilder.quantity(Quantity.parse("0.250"), Unit.KG));
        assertEquals("0.333 kg", ReceiptBuilder.quantity(Quantity.parse("0.333"), Unit.KG));
        assertEquals("1 Ltr", ReceiptBuilder.quantity(Quantity.ONE, Unit.L));
        assertEquals("5 Ltr", ReceiptBuilder.quantity(Quantity.ofWhole(5), Unit.L));
        assertEquals("0.5 Ltr", ReceiptBuilder.quantity(Quantity.parse("0.500"), Unit.L));
        assertEquals("3 Pkt", ReceiptBuilder.quantity(Quantity.ofWhole(3), Unit.PCS));
    }

    @Test
    void sampleReceiptIsAClearlyMarkedKhataBill() {
        String text = ReceiptBuilder.sample(SHOP, WHEN).toPlainText();

        assertTrue(text.contains("TEST PRINT - NOT A REAL BILL"), text);
        assertTrue(text.contains("तूर डाळ | 0.25 kg | 150 | 37.5"), text);
        assertTrue(text.contains("नमुना ग्राहक | No. 9876543210"), text);
        assertFalse(text.contains("Total with dues"), text);
        assertTrue(text.contains("मागील बाकी | ₹480\nजमा | ₹100\nएकूण बाकी | ₹496"), text);
    }
}
