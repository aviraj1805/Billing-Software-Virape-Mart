package com.virpemart.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.virpemart.billing.model.BillSearch;
import com.virpemart.billing.model.BillSummary;
import com.virpemart.billing.model.CancelledBill;
import com.virpemart.billing.model.Cart;
import com.virpemart.billing.model.CartLine;
import com.virpemart.billing.model.LedgerEntryType;
import com.virpemart.billing.model.Money;
import com.virpemart.billing.model.PaymentMode;
import com.virpemart.billing.model.PaymentPart;
import com.virpemart.billing.model.Product;
import com.virpemart.billing.model.Quantity;
import com.virpemart.billing.model.StatementLine;

/** Bill history search and cancelling bills. */
class BillHistoryTest {

    private static final LocalDate SEP_30 = LocalDate.of(2026, 9, 30);
    private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);

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
        sugar = fixture.services.products().create(new ProductInput("Sugar", "साखर", null, "kg", null, "44", null));
        salt = fixture.services.products().create(new ProductInput("Tata Salt", null, null, "pcs", "1 kg", "28", "30"));
        ramesh = fixture.services.customers()
                .create(new CustomerInput("Ramesh Patil", "9876543210", null, null, "1200")).customer().id();
    }

    /** Sugar 1.5 kg (66.00) + 2 x salt (56.00) = 122.00. */
    private List<CartLine> lines() {
        Cart cart = new Cart();
        cart.addProduct(sugar, Quantity.parse("1.5"));
        cart.addProduct(salt, Quantity.ofWhole(2));
        return cart.lines();
    }

    private static PaymentPart cash(String amount) {
        return new PaymentPart(PaymentMode.CASH, Money.parse(amount));
    }

    /** The same database, one day later (1 Oct 2026, 10:00). */
    private BillingService nextDay() {
        Clock oct1 = Clock.fixed(Instant.parse("2026-10-01T04:30:00Z"), ZoneId.of("Asia/Kolkata"));
        return Services.create(fixture.database, fixture.session, oct1, fixture.printer).billing();
    }

    private List<Long> billNos(List<BillSummary> bills) {
        return bills.stream().map(BillSummary::billNo).toList();
    }

    private List<Long> search(LocalDate from, LocalDate to, String text) {
        return billNos(billing.searchBills(new BillSearch(from, to, text, null, 100)));
    }

    // ------------------------------------------------------------------ search

    @Test
    void billsAreFoundByDateNewestFirst() {
        billing.save(new BillRequest(null, "Sunil", lines(), List.of(cash("122"))));
        nextDay().save(new BillRequest(ramesh, null, lines(), List.of()));

        assertEquals(List.of(1L), search(SEP_30, SEP_30, null));
        assertEquals(List.of(2L), search(OCT_1, OCT_1, null));
        assertEquals(List.of(2L, 1L), search(SEP_30, OCT_1, null));
        assertEquals(List.of(2L, 1L), search(null, null, null), "no dates means all bills");
    }

    @Test
    void billsAreFoundByNameCustomerNumberOrPhone() {
        billing.save(new BillRequest(null, "Sunil", lines(), List.of(cash("122"))));
        billing.save(new BillRequest(ramesh, null, lines(), List.of()));

        assertEquals(List.of(2L), search(null, null, "ramesh"));
        assertEquals(List.of(2L), search(null, null, "c0001"));
        assertEquals(List.of(2L), search(null, null, "98765432"), "part of the phone number");
        assertEquals(List.of(1L), search(null, null, "SUN"));
        assertEquals(List.of(), search(null, null, "%"), "% is an ordinary letter here");

        BillSummary khata = billing.searchBills(new BillSearch(null, null, "ramesh", null, 10)).getFirst();
        assertEquals("Ramesh Patil (C0001)", khata.customerText());
        assertEquals(2, khata.lineCount());
    }

    @Test
    void aBillNumberIsFoundOnAnyDate() {
        billing.save(new BillRequest(null, null, lines(), List.of(cash("122"))));
        nextDay().save(new BillRequest(null, null, lines(), List.of(cash("122"))));

        assertEquals(List.of(1L), search(OCT_1, OCT_1, " 1 "), "bill 1 is from 30 Sep but is still found");
        assertEquals(List.of(), search(null, null, "99"));
    }

    @Test
    void oneCustomersBillsAreTheirPurchaseHistory() {
        billing.save(new BillRequest(ramesh, null, lines(), List.of()));
        billing.save(new BillRequest(null, null, lines(), List.of(cash("122"))));
        nextDay().save(new BillRequest(ramesh, null, lines(), List.of(cash("22"))));

        assertEquals(List.of(3L, 1L), billNos(billing.searchBills(BillSearch.ofCustomer(ramesh, 100))));
    }

    @Test
    void datesMustBeInOrder() {
        ValidationException error = assertThrows(ValidationException.class,
                () -> billing.searchBills(new BillSearch(OCT_1, SEP_30, null, null, 100)));
        assertEquals("from", error.field());
    }

    // ------------------------------------------------------------------ cancel

    @Test
    void cancellingAWalkInBillKeepsItAndItsNumber() {
        billing.save(new BillRequest(null, null, lines(), List.of(cash("122"))));

        CancelledBill result = billing.cancel(1, "  Wrong  items ");

        assertEquals(new CancelledBill(1, Money.parse("122"), Money.ZERO), result);
        assertEquals("Wrong items", billing.bill(1).cancelReason());
        assertTrue(billing.searchBills(new BillSearch(null, null, null, null, 10)).getFirst().cancelled());
        assertEquals(1, fixture.count("SELECT COUNT(*) FROM audit_log WHERE action = 'BILL_CANCELLED'"
                + " AND user_id = " + fixture.owner.id()));
        assertEquals(0, fixture.count("SELECT COUNT(*) FROM customer_ledger WHERE entry_type = 'CANCEL_REVERSAL'"));

        long next = billing.save(new BillRequest(null, null, lines(), List.of(cash("122")))).billNo();
        assertEquals(2, next, "a cancelled bill's number is never used again");
    }

    @Test
    void cancellingAKhataBillTakesTheCreditBack() {
        billing.save(new BillRequest(ramesh, null, lines(), List.of()));

        CancelledBill result = billing.cancel(1, "Customer returned everything");

        assertEquals(new CancelledBill(1, Money.ZERO, Money.parse("122")), result);
        assertEquals(Money.parse("1200"), fixture.services.customers().find(ramesh).orElseThrow().balance());
        StatementLine reversal = fixture.services.ledger().statement(ramesh).getLast();
        assertEquals(LedgerEntryType.CANCEL_REVERSAL, reversal.entry().type());
        assertEquals(Money.parse("-122"), reversal.entry().amount());
        assertEquals(1L, reversal.entry().billNo());
    }

    @Test
    void cancellingAPartlyPaidKhataBill() {
        billing.save(new BillRequest(ramesh, null, lines(), List.of(cash("100"))));

        CancelledBill result = billing.cancel(1, "Mistake");

        assertEquals(Money.parse("100"), result.giveBack());
        assertEquals(Money.parse("22"), result.takenOffKhata());
        assertEquals(Money.parse("1200"), fixture.services.customers().find(ramesh).orElseThrow().balance());
    }

    @Test
    void moneyPaidTowardsOldDuesWithTheBillStaysPaid() {
        // Bill 122, paid 622: 122 for the bill and 500 towards old dues. Balance 1200 -> 700.
        billing.save(new BillRequest(ramesh, null, lines(), List.of(cash("622"))));

        CancelledBill result = billing.cancel(1, "Mistake");

        assertEquals(Money.parse("122"), result.giveBack());
        assertEquals(Money.ZERO, result.takenOffKhata());
        assertEquals(Money.parse("700"), fixture.services.customers().find(ramesh).orElseThrow().balance());
    }

    @Test
    void cancelRulesAreChecked() {
        billing.save(new BillRequest(null, null, lines(), List.of(cash("122"))));

        assertEquals("reason", assertThrows(ValidationException.class, () -> billing.cancel(1, "  ")).field());
        assertEquals("reason", assertThrows(ValidationException.class,
                () -> billing.cancel(1, "x".repeat(201))).field());
        assertThrows(BusinessRuleException.class, () -> billing.cancel(9, "Mistake"));

        fixture.signInStaff();
        assertThrows(PermissionDeniedException.class, () -> billing.cancel(1, "Mistake"));
        fixture.signInOwner();

        billing.cancel(1, "Mistake");
        BusinessRuleException twice = assertThrows(BusinessRuleException.class, () -> billing.cancel(1, "Again"));
        assertEquals("Bill 1 is already cancelled.", twice.getMessage());
        assertEquals("Mistake", billing.bill(1).cancelReason(), "the first reason is kept");
    }

    @Test
    void cancelledBillPrintsAsCancelled() {
        billing.save(new BillRequest(null, null, lines(), List.of(cash("122"))));
        billing.cancel(1, "Wrong items");

        String text = fixture.services.printing().billReceipt(1, true).toPlainText();

        assertTrue(text.contains("*** CANCELLED BILL ***\nReason: Wrong items"), text);
        assertFalse(text.contains("DUPLICATE"), text);
    }
}
