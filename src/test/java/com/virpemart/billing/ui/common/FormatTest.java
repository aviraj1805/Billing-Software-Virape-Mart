package com.virpemart.billing.ui.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.virpemart.billing.model.Money;

class FormatTest {

    @Test
    void moneyUsesIndianGroupingAndRupeeSign() {
        assertEquals("₹44.00", Format.money(Money.ofRupees(44)));
        assertEquals("₹1,250.50", Format.money(Money.ofPaise(125050)));
        assertEquals("₹1,25,000.00", Format.money(Money.ofRupees(125000)));
        assertEquals("-₹5.00", Format.money(Money.ofPaise(-500)));
        assertEquals("", Format.money(null));
    }

    @Test
    void balancesAreDescribedInWords() {
        assertEquals("Dues ₹1,250.00", Format.balance(Money.ofRupees(1250)));
        assertEquals("Advance ₹200.00", Format.balance(Money.ofRupees(-200)));
        assertEquals("No dues", Format.balance(Money.ZERO));
        assertEquals("Adv ₹200.00", Format.balanceShort(Money.ofRupees(-200)));
    }

    @Test
    void phoneAndDatesAreReadable() {
        assertEquals("98765 43210", Format.phone("9876543210"));
        assertEquals("", Format.phone(null));
        assertEquals("30 Sep 2026, 2:05 PM", Format.dateTime(java.time.LocalDateTime.of(2026, 9, 30, 14, 5)));
        assertEquals("30/09/26 2:05 PM", Format.dateTimeShort(java.time.LocalDateTime.of(2026, 9, 30, 14, 5)));
        assertEquals("Wed, 30 Sep 2026", Format.date(java.time.LocalDate.of(2026, 9, 30)));
    }

    @Test
    void dateBoxesUseDayMonthYear() {
        var converter = Format.dateInput();
        assertEquals("01/10/2026", converter.toString(java.time.LocalDate.of(2026, 10, 1)));
        assertEquals(java.time.LocalDate.of(2026, 10, 1), converter.fromString(" 1/10/2026 "));
        assertEquals(java.time.LocalDate.of(2026, 10, 1), converter.fromString("01/10/2026"));
        assertEquals(null, converter.fromString("31/02/2026x"));
        assertEquals(null, converter.fromString(""));
        assertEquals("", converter.toString(null));
    }
}
