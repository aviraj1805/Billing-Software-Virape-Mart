package com.virpemart.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import com.virpemart.billing.model.Cart;
import com.virpemart.billing.model.CartLine;
import com.virpemart.billing.model.DaySummary;
import com.virpemart.billing.model.Money;
import com.virpemart.billing.model.PaymentMode;
import com.virpemart.billing.model.PaymentPart;
import com.virpemart.billing.model.Product;
import com.virpemart.billing.model.Quantity;
import com.virpemart.billing.model.SalesReport;

class ReportServiceTest {

    private static final LocalDate SEP_30 = LocalDate.of(2026, 9, 30);
    private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);

    @TempDir
    Path temp;

    private TestFixture fixture;
    private ReportService reports;

    /**
     * 30 Sep: walk-in 122 cash; walk-in 122 as 100 cash + 22 UPI; Ramesh's khata bill 122 with 50 UPI (72 on khata);
     * Ramesh pays 200 by card at the counter; a walk-in bill of 122 cash that is cancelled.
     * 1 Oct: walk-in 122 by card; Ramesh's bill 122 paid with 200 cash (78 towards old dues).
     */
    @BeforeEach
    void setUp() {
        fixture = new TestFixture(temp);
        reports = fixture.services.reports();
        Product sugar = fixture.services.products().create(new ProductInput("Sugar", null, null, "kg", null, "44", null));
        Product salt = fixture.services.products().create(new ProductInput("Salt", null, null, "pcs", null, "28", null));
        Cart cart = new Cart();
        cart.addProduct(sugar, Quantity.parse("1.5"));
        cart.addProduct(salt, Quantity.ofWhole(2));
        List<CartLine> lines = cart.lines(); // 122.00
        long ramesh = fixture.services.customers()
                .create(new CustomerInput("Ramesh Patil", null, null, null, "1000")).customer().id();

        BillingService sep30 = fixture.services.billing();
        sep30.save(new BillRequest(null, null, lines, List.of(pay(PaymentMode.CASH, "122"))));
        sep30.save(new BillRequest(null, null, lines, List.of(pay(PaymentMode.CASH, "100"), pay(PaymentMode.UPI, "22"))));
        sep30.save(new BillRequest(ramesh, null, lines, List.of(pay(PaymentMode.UPI, "50"))));
        fixture.services.ledger().receivePayment(ramesh, "200", PaymentMode.CARD, null);
        sep30.save(new BillRequest(null, null, lines, List.of(pay(PaymentMode.CASH, "122"))));
        sep30.cancel(4, "Wrong items");

        Clock oct1 = Clock.fixed(Instant.parse("2026-10-01T04:30:00Z"), ZoneId.of("Asia/Kolkata"));
        BillingService nextDay = Services.create(fixture.database, fixture.session, oct1, fixture.printer).billing();
        nextDay.save(new BillRequest(null, null, lines, List.of(pay(PaymentMode.CARD, "122"))));
        nextDay.save(new BillRequest(ramesh, null, lines, List.of(pay(PaymentMode.CASH, "200"))));
    }

    private static PaymentPart pay(PaymentMode mode, String amount) {
        return new PaymentPart(mode, Money.parse(amount));
    }

    private static Money rs(String amount) {
        return Money.parse(amount);
    }

    @Test
    void dailySummaryCountsSalesPaymentsAndCredit() {
        SalesReport report = reports.sales(SEP_30, SEP_30);

        assertEquals(1, report.days().size());
        DaySummary day = report.total();
        assertEquals(3, day.bills(), "the cancelled bill is not a sale");
        assertEquals(rs("366"), day.sales());
        assertEquals(rs("72"), day.onKhata());
        assertEquals(rs("222"), day.paidAtBilling(PaymentMode.CASH));
        assertEquals(rs("72"), day.paidAtBilling(PaymentMode.UPI));
        assertEquals(Money.ZERO, day.paidAtBilling(PaymentMode.CARD));
        assertEquals(rs("200"), day.khataPayments(PaymentMode.CARD));
        assertEquals(rs("200"), day.received(PaymentMode.CARD));
        assertEquals(rs("494"), day.receivedTotal());
        assertEquals(1, day.cancelledBills());
        assertEquals(rs("122"), day.cancelledTotal());
    }

    @Test
    void moneyPaidTowardsOldDuesWithABillIsAKhataPayment() {
        DaySummary day = reports.sales(OCT_1, OCT_1).total();

        assertEquals(2, day.bills());
        assertEquals(rs("244"), day.sales());
        assertEquals(rs("122"), day.paidAtBilling(PaymentMode.CASH));
        assertEquals(rs("122"), day.paidAtBilling(PaymentMode.CARD));
        assertEquals(rs("78"), day.khataPayments(PaymentMode.CASH));
        assertEquals(rs("200"), day.received(PaymentMode.CASH));
        assertEquals(Money.ZERO, day.onKhata());
    }

    @Test
    void dateRangeHasOneRowPerDayAndATotal() {
        SalesReport report = reports.sales(SEP_30.minusDays(5), OCT_1.plusDays(5));

        assertEquals(List.of(SEP_30, OCT_1), report.days().stream().map(DaySummary::day).toList(),
                "only days with activity");
        DaySummary total = report.total();
        assertEquals(5, total.bills());
        assertEquals(rs("610"), total.sales());
        assertEquals(rs("422"), total.received(PaymentMode.CASH));
        assertEquals(rs("72"), total.received(PaymentMode.UPI));
        assertEquals(rs("322"), total.received(PaymentMode.CARD));
        assertEquals(rs("816"), total.receivedTotal());
    }

    @Test
    void aQuietDayIsEmpty() {
        SalesReport report = reports.sales(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 2));

        assertTrue(report.days().isEmpty());
        assertEquals(0, report.total().bills());
        assertEquals(Money.ZERO, report.total().receivedTotal());
    }

    @Test
    void reportsAreForTheOwnerWithASensibleRange() {
        assertEquals("from", assertThrows(ValidationException.class, () -> reports.sales(OCT_1, SEP_30)).field());
        assertEquals("from", assertThrows(ValidationException.class, () -> reports.sales(null, SEP_30)).field());
        assertEquals("to", assertThrows(ValidationException.class,
                () -> reports.sales(SEP_30, SEP_30.plusYears(2))).field());

        fixture.signInStaff();
        assertThrows(PermissionDeniedException.class, () -> reports.sales(SEP_30, SEP_30));
    }
}
