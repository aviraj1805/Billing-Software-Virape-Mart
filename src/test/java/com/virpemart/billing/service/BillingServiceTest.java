package com.virpemart.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.virpemart.billing.db.DatabaseException;
import com.virpemart.billing.model.BillSummary;
import com.virpemart.billing.model.Cart;
import com.virpemart.billing.model.CartLine;
import com.virpemart.billing.model.LedgerEntryType;
import com.virpemart.billing.model.Money;
import com.virpemart.billing.model.PaymentMode;
import com.virpemart.billing.model.PaymentPart;
import com.virpemart.billing.model.Product;
import com.virpemart.billing.model.Quantity;
import com.virpemart.billing.model.SavedBill;
import com.virpemart.billing.model.StatementLine;
import com.virpemart.billing.model.Unit;

class BillingServiceTest {

    @TempDir
    Path temp;

    private TestFixture fixture;
    private BillingService billing;
    private Product sugar;
    private Product salt;
    private long ramesh;

    @BeforeEach
    void setUp() {
        fixture = new TestFixture(temp);
        billing = fixture.services.billing();
        ProductService products = fixture.services.products();
        sugar = products.create(new ProductInput("Sugar", "साखर", null, "kg", null, "44", null));
        salt = products.create(new ProductInput("Tata Salt", null, null, "pcs", "1 kg", "28", "30"));
        ramesh = fixture.services.customers()
                .create(new CustomerInput("Ramesh Patil", null, null, null, "1200")).customer().id();
    }

    /** Sugar 1.5 kg (66.00) + 2 x salt (56.00) = 122.00. */
    private List<CartLine> sampleLines() {
        Cart cart = new Cart();
        cart.addProduct(sugar, Quantity.parse("1.5"));
        cart.addProduct(salt, Quantity.ofWhole(2));
        return cart.lines();
    }

    private static PaymentPart cash(String amount) {
        return new PaymentPart(PaymentMode.CASH, Money.parse(amount));
    }

    private static PaymentPart upi(String amount) {
        return new PaymentPart(PaymentMode.UPI, Money.parse(amount));
    }

    // ------------------------------------------------------------------ walk-in bills

    @Test
    void walkInBillIsSavedWithAllItsParts() {
        SavedBill bill = billing.save(new BillRequest(null, " Sharma ji ", sampleLines(), List.of(cash("122"))));

        assertEquals(1, bill.billNo());
        assertEquals(Money.parse("122"), bill.totals().total());
        assertEquals(Money.parse("122"), bill.paidForBill());
        assertEquals(Money.ZERO, bill.toAccount());
        assertNull(bill.previousBalance());
        assertEquals("Sharma ji", text("SELECT customer_name FROM bills"));
        assertEquals(2, count("SELECT COUNT(*) FROM bill_items"));
        assertEquals(1, count("SELECT COUNT(*) FROM bill_payments WHERE mode = 'CASH' AND amount_paise = 12200"));
        assertEquals(0, count("SELECT COUNT(*) FROM customer_ledger WHERE entry_type <> 'OPENING'"));
    }

    @Test
    void walkInNameIsOptional() {
        billing.save(new BillRequest(null, "  ", sampleLines(), List.of(cash("122"))));

        assertNull(text("SELECT customer_name FROM bills"));
    }

    @Test
    void billLinesKeepASnapshotOfTheProduct() {
        billing.save(new BillRequest(null, null, sampleLines(), List.of(cash("122"))));
        fixture.services.products().update(salt.id(), new ProductInput("Tata Salt New", null, null, "pcs", "1 kg", "35", "40"));

        assertEquals("Tata Salt", text("SELECT name FROM bill_items WHERE line_no = 2"));
        assertEquals(2800, count("SELECT rate_paise FROM bill_items WHERE line_no = 2"));
        assertEquals(3000, count("SELECT mrp_paise FROM bill_items WHERE line_no = 2"));
        assertEquals("साखर", text("SELECT name_mr FROM bill_items WHERE line_no = 1"));
        assertEquals(1500, count("SELECT qty_milli FROM bill_items WHERE line_no = 1"));
    }

    @Test
    void walkInMustPayExactlyTheTotal() {
        assertThrows(BusinessRuleException.class,
                () -> billing.save(new BillRequest(null, null, sampleLines(), List.of(cash("100")))));
        assertThrows(BusinessRuleException.class,
                () -> billing.save(new BillRequest(null, null, sampleLines(), List.of(cash("200")))));
        assertThrows(BusinessRuleException.class,
                () -> billing.save(new BillRequest(null, null, sampleLines(), List.of())));
        assertEquals(0, count("SELECT COUNT(*) FROM bills"), "nothing saved");
    }

    @Test
    void walkInCanSplitThePayment() {
        billing.save(new BillRequest(null, null, sampleLines(), List.of(cash("100"), upi("22"))));

        assertEquals(2, count("SELECT COUNT(*) FROM bill_payments"));
        assertEquals(12200, count("SELECT SUM(amount_paise) FROM bill_payments"));
    }

    @Test
    void billNumbersAreContinuous() {
        for (int i = 1; i <= 3; i++) {
            assertEquals(i, billing.save(new BillRequest(null, null, sampleLines(), List.of(cash("122")))).billNo());
        }
    }

    @Test
    void totalsAreRecalculatedAndRounded() {
        Cart cart = new Cart();
        cart.addProduct(sugar, Quantity.parse("0.125")); // 5.50 -> rounds up to 6

        SavedBill bill = billing.save(new BillRequest(null, null, cart.lines(), List.of(cash("6"))));

        assertEquals(Money.parse("5.50"), bill.totals().subtotal());
        assertEquals(Money.parse("0.50"), bill.totals().roundOff());
        assertEquals(Money.parse("6"), bill.totals().total());
    }

    @Test
    void savingsAreReported() {
        SavedBill bill = billing.save(new BillRequest(null, null, sampleLines(), List.of(cash("122"))));

        assertEquals(Money.parse("4"), bill.totals().savings(), "2 salt x (30 - 28)");
    }

    // ------------------------------------------------------------------ khata bills

    @Test
    void khataBillWithNothingPaidGoesFullyOnTheKhata() {
        SavedBill bill = billing.save(new BillRequest(ramesh, "ignored", sampleLines(), List.of()));

        assertEquals(Money.parse("122"), bill.toAccount());
        assertEquals(Money.parse("1200"), bill.previousBalance());
        assertEquals(Money.parse("1322"), bill.balanceAfter());
        assertEquals("Ramesh Patil", text("SELECT customer_name FROM bills"), "khata customer's name is printed");
        assertEquals(Money.parse("1322"), fixture.services.customers().find(ramesh).orElseThrow().balance());
    }

    @Test
    void khataBillPartlyPaid() {
        SavedBill bill = billing.save(new BillRequest(ramesh, null, sampleLines(), List.of(cash("100"))));

        assertEquals(Money.parse("100"), bill.paidForBill());
        assertEquals(Money.parse("22"), bill.toAccount());
        assertEquals(Money.parse("1222"), bill.balanceAfter());
    }

    @Test
    void khataCustomerCanPayOldDuesWithTheBill() {
        SavedBill bill = billing.save(new BillRequest(ramesh, null, sampleLines(), List.of(cash("100"), upi("522"))));

        assertEquals(Money.parse("622"), bill.paidTotal());
        assertEquals(Money.parse("122"), bill.paidForBill());
        assertEquals(Money.parse("500"), bill.paidAgainstDues());
        assertEquals(Money.ZERO, bill.toAccount());
        assertEquals(Money.parse("700"), bill.balanceAfter());

        List<StatementLine> khata = fixture.services.ledger().statement(ramesh);
        StatementLine payment = khata.getLast();
        assertEquals(LedgerEntryType.PAYMENT, payment.entry().type());
        assertEquals(Money.parse("-500"), payment.entry().amount());
        assertEquals(PaymentMode.UPI, payment.entry().paymentMode());
        assertEquals(1L, payment.entry().billNo());
        assertEquals(Money.parse("700"), payment.balanceAfter(), "khata and bill agree");
    }

    @Test
    void payingMoreThanAllDuesLeavesAnAdvance() {
        SavedBill bill = billing.save(new BillRequest(ramesh, null, sampleLines(), List.of(cash("1400"))));

        assertEquals(Money.parse("-78"), bill.balanceAfter());
    }

    @Test
    void switchedOffCustomerCannotBeBilled() {
        fixture.services.customers().setActive(ramesh, false);

        assertThrows(BusinessRuleException.class,
                () -> billing.save(new BillRequest(ramesh, null, sampleLines(), List.of())));
    }

    @Test
    void recentBillsOfACustomerNewestFirst() {
        billing.save(new BillRequest(ramesh, null, sampleLines(), List.of()));
        billing.save(new BillRequest(null, null, sampleLines(), List.of(cash("122"))));
        billing.save(new BillRequest(ramesh, null, sampleLines(), List.of(cash("50"))));

        List<BillSummary> recent = billing.recentBills(ramesh, 5);

        assertEquals(2, recent.size());
        assertEquals(3, recent.get(0).billNo());
        assertEquals(1, recent.get(1).billNo());
        assertEquals(2, recent.get(0).lineCount());
    }

    // ------------------------------------------------------------------ rates and one-off items

    @Test
    void changedRateIsSavedWithTheOriginalAndAudited() {
        Cart cart = new Cart();
        cart.addProduct(salt, Quantity.ofWhole(2));
        cart.setRate(0, Money.parse("25"));
        fixture.signInStaff();

        billing.save(new BillRequest(null, null, cart.lines(), List.of(cash("50"))));

        assertEquals(2500, count("SELECT rate_paise FROM bill_items"));
        assertEquals(2800, count("SELECT original_rate_paise FROM bill_items"));
        assertEquals("Bill 1, Tata Salt 1 kg: 28.00 -> 25.00",
                text("SELECT details FROM audit_log WHERE action = 'RATE_CHANGED'"));
        assertEquals(fixture.staff.id(), count("SELECT user_id FROM audit_log WHERE action = 'RATE_CHANGED'"));
    }

    @Test
    void oneOffItemIsSavedWithoutAProduct() {
        Cart cart = new Cart();
        cart.addOneOff("Birthday candles", Unit.PCS, Quantity.ofWhole(2), Money.ofRupees(15));

        billing.save(new BillRequest(null, null, cart.lines(), List.of(cash("30"))));

        assertEquals(1, count("SELECT COUNT(*) FROM bill_items WHERE product_id IS NULL AND name = 'Birthday candles'"));
        assertEquals(2, count("SELECT COUNT(*) FROM products"), "no product was created");
    }

    // ------------------------------------------------------------------ bad requests

    @Test
    void emptyBillIsRejected() {
        assertThrows(BusinessRuleException.class, () -> billing.save(new BillRequest(null, null, List.of(), List.of())));
    }

    @Test
    void fractionalPacketsAreRejectedEvenIfTheScreenSentThem() {
        CartLine bad = new CartLine(salt.id(), "Tata Salt", null, Unit.PCS, "1 kg", Quantity.parse("1.5"),
                salt.rate(), salt.rate(), salt.mrp());

        assertThrows(ValidationException.class,
                () -> billing.save(new BillRequest(null, null, List.of(bad), List.of(cash("42")))));
        assertEquals(0, count("SELECT COUNT(*) FROM bills"));
    }

    @Test
    void screenNamesAndUnitsAreNotTrusted() {
        CartLine fake = new CartLine(sugar.id(), "Cheap Sugar", null, Unit.KG, null, Quantity.ONE, Money.parse("44"),
                Money.parse("44"), null);

        billing.save(new BillRequest(null, null, List.of(fake), List.of(cash("44"))));

        assertEquals("Sugar", text("SELECT name FROM bill_items"), "name comes from the product list");
    }

    @Test
    void removedProductIsExplained() {
        List<CartLine> lines = sampleLines();
        fixture.services.products().delete(salt.id());

        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> billing.save(new BillRequest(null, null, lines, List.of(cash("122")))));

        assertTrue(error.getMessage().contains("no longer in the product list"), error.getMessage());
    }

    @Test
    void savedBillCannotBeEditedIncludingTheNewColumns() {
        billing.save(new BillRequest(ramesh, null, sampleLines(), List.of()));

        assertThrows(DatabaseException.class, () -> fixture.database.runInTransaction(c -> {
            try (Statement s = c.createStatement()) {
                s.executeUpdate("UPDATE bills SET customer_name = 'Someone else'");
            }
        }));
        assertThrows(DatabaseException.class, () -> fixture.database.runInTransaction(c -> {
            try (Statement s = c.createStatement()) {
                s.executeUpdate("UPDATE bills SET balance_after_paise = 0");
            }
        }));
    }

    // ------------------------------------------------------------------ payment allocation

    @Test
    void paymentsFillTheBillFirstInOrder() {
        BillingService.Allocation allocation = BillingService.allocate(List.of(cash("500"), upi("300")), Money.parse("600"));

        assertEquals(List.of(cash("500"), upi("100")), allocation.forBill());
        assertEquals(List.of(upi("200")), allocation.againstDues());
    }

    @Test
    void underpaymentHasNothingAgainstDues() {
        BillingService.Allocation allocation = BillingService.allocate(List.of(cash("50")), Money.parse("600"));

        assertEquals(List.of(cash("50")), allocation.forBill());
        assertTrue(allocation.againstDues().isEmpty());
    }

    // ------------------------------------------------------------------ helpers

    private long count(String sql) {
        return fixture.count(sql);
    }

    private String text(String sql) {
        return fixture.database.query(c -> {
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
                rs.next();
                return rs.getString(1);
            }
        });
    }
}
