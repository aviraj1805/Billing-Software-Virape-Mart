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
    void groupsDigitsInLakhsAndCrores() {
        assertEquals("0", Format.indianGrouping(0));
        assertEquals("999", Format.indianGrouping(999));
        assertEquals("1,000", Format.indianGrouping(1000));
        assertEquals("12,000", Format.indianGrouping(12000));
        assertEquals("1,00,000", Format.indianGrouping(100000));
        assertEquals("12,34,567", Format.indianGrouping(1234567));
        assertEquals("1,23,45,678", Format.indianGrouping(12345678));
    }
}
