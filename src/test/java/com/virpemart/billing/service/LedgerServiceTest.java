package com.virpemart.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.virpemart.billing.model.CustomerSummary;
import com.virpemart.billing.model.LedgerEntryType;
import com.virpemart.billing.model.Money;
import com.virpemart.billing.model.PaymentMode;
import com.virpemart.billing.model.StatementLine;

class LedgerServiceTest {

    @TempDir
    Path temp;

    private TestFixture fixture;
    private LedgerService ledger;
    private long ramesh;

    @BeforeEach
    void setUp() {
        fixture = new TestFixture(temp);
        ledger = fixture.services.ledger();
        ramesh = fixture.services.customers()
                .create(new CustomerInput("Ramesh", null, null, null, "1000")).customer().id();
    }

    // ------------------------------------------------------------------ payments

    @Test
    void paymentReducesTheDues() {
        CustomerSummary after = ledger.receivePayment(ramesh, "400", PaymentMode.UPI, " Paid by son ");

        assertEquals(Money.ofRupees(600), after.balance());
        StatementLine last = ledger.statement(ramesh).getLast();
        assertEquals(LedgerEntryType.PAYMENT, last.entry().type());
        assertEquals(Money.ofRupees(-400), last.entry().amount());
        assertEquals(PaymentMode.UPI, last.entry().paymentMode());
        assertEquals("Paid by son", last.entry().note());
        assertEquals("Owner", last.entry().createdBy());
    }

    @Test
    void payingMoreThanTheDuesLeavesAnAdvance() {
        CustomerSummary after = ledger.receivePayment(ramesh, "1200", PaymentMode.CASH, null);

        assertEquals(Money.ofRupees(-200), after.balance());
    }

    @Test
    void paymentNeedsAValidAmountAndAMode() {
        assertEquals("amount", assertThrows(ValidationException.class,
                () -> ledger.receivePayment(ramesh, "", PaymentMode.CASH, null)).field());
        assertEquals("amount", assertThrows(ValidationException.class,
                () -> ledger.receivePayment(ramesh, "0", PaymentMode.CASH, null)).field());
        assertEquals("amount", assertThrows(ValidationException.class,
                () -> ledger.receivePayment(ramesh, "-5", PaymentMode.CASH, null)).field());
        assertEquals("mode", assertThrows(ValidationException.class,
                () -> ledger.receivePayment(ramesh, "100", null, null)).field());
        assertEquals(1, ledger.statement(ramesh).size(), "nothing was added");
    }

    @Test
    void staffCanReceivePayments() {
        fixture.signInStaff();

        CustomerSummary after = ledger.receivePayment(ramesh, "100", PaymentMode.CASH, null);

        assertEquals(Money.ofRupees(900), after.balance());
        assertEquals("Helper", ledger.statement(ramesh).getLast().entry().createdBy());
    }

    // ------------------------------------------------------------------ corrections

    @Test
    void ownerCanCorrectTheBalanceWithAReason() {
        ledger.correctBalance(ramesh, true, "50", "Missed entry from paper khata, 12 Sept");
        CustomerSummary after = ledger.correctBalance(ramesh, false, "150", "Discount given for festival");

        assertEquals(Money.ofRupees(900), after.balance());
        List<StatementLine> lines = ledger.statement(ramesh);
        assertEquals(LedgerEntryType.ADJUSTMENT, lines.get(1).entry().type());
        assertEquals(Money.ofRupees(50), lines.get(1).entry().amount());
        assertEquals(Money.ofRupees(-150), lines.get(2).entry().amount());
        assertEquals("Discount given for festival", lines.get(2).entry().note());
        assertEquals(2, fixture.count("SELECT COUNT(*) FROM audit_log WHERE action = 'BALANCE_CORRECTED'"));
    }

    @Test
    void correctionNeedsAReason() {
        assertEquals("reason", assertThrows(ValidationException.class,
                () -> ledger.correctBalance(ramesh, false, "100", "  ")).field());
        assertEquals("reason", assertThrows(ValidationException.class,
                () -> ledger.correctBalance(ramesh, false, "100", "ok")).field());
    }

    @Test
    void staffCannotCorrectBalances() {
        fixture.signInStaff();

        assertThrows(PermissionDeniedException.class,
                () -> ledger.correctBalance(ramesh, false, "100", "Trying to reduce"));
    }

    // ------------------------------------------------------------------ statement

    @Test
    void statementShowsARunningBalanceLikeThePaperKhata() {
        fixture.addBillOnAccount(ramesh, 7, 35000);
        ledger.receivePayment(ramesh, "500", PaymentMode.CASH, null);
        ledger.correctBalance(ramesh, false, "0.50", "Rounding difference with paper khata");

        List<StatementLine> lines = ledger.statement(ramesh);

        assertEquals(4, lines.size());
        assertEquals(LedgerEntryType.OPENING, lines.get(0).entry().type());
        assertEquals(Money.ofRupees(1000), lines.get(0).balanceAfter());
        assertEquals(LedgerEntryType.SALE_CREDIT, lines.get(1).entry().type());
        assertEquals(7L, lines.get(1).entry().billNo(), "bill number is shown for bill entries");
        assertEquals(Money.ofRupees(1350), lines.get(1).balanceAfter());
        assertEquals(Money.ofRupees(850), lines.get(2).balanceAfter());
        assertEquals(Money.ofPaise(84950), lines.get(3).balanceAfter());
        assertEquals(fixture.services.customers().find(ramesh).orElseThrow().balance(), lines.getLast().balanceAfter(),
                "statement and balance always agree");
        assertNull(lines.get(0).entry().billNo());
    }

    @Test
    void newCustomerHasAnEmptyStatement() {
        long anita = fixture.services.customers().create(new CustomerInput("Anita", null, null, null, null)).customer().id();

        assertTrue(ledger.statement(anita).isEmpty());
    }
}
