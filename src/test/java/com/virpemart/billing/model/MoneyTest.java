package com.virpemart.billing.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

    @ParameterizedTest
    @CsvSource({
            "45, 4500",
            "45.5, 4550",
            "45.50, 4550",
            "0.05, 5",
            ".5, 50",
            "45., 4500",
            "' 12.30 ', 1230",
            "'1,250.00', 125000",
            "-5, -500",
            "0, 0"
    })
    void parsesTypedAmounts(String text, long expectedPaise) {
        assertEquals(expectedPaise, Money.parse(text).paise());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "abc", "12.345", "1.2.3", "Rs 5", "--5", "5-", "."})
    void rejectsInvalidAmounts(String text) {
        assertThrows(IllegalArgumentException.class, () -> Money.parse(text));
    }

    @Test
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> Money.parse(null));
    }

    @Test
    void addsAndSubtractsExactly() {
        // 0.10 + 0.20 is famously 0.30000000000000004 with double.
        assertEquals(Money.ofPaise(30), Money.parse("0.10").plus(Money.parse("0.20")));
        assertEquals(Money.ofPaise(-50), Money.ofPaise(100).minus(Money.ofPaise(150)));
        assertEquals(Money.ofPaise(-100), Money.ofPaise(100).negate());
    }

    @Test
    void sumsAList() {
        assertEquals(Money.ofPaise(600), Money.sum(List.of(Money.ofPaise(100), Money.ofPaise(200), Money.ofPaise(300))));
        assertEquals(Money.ZERO, Money.sum(List.of()));
    }

    @ParameterizedTest(name = "{0} paise x {1} milli = {2} paise")
    @CsvSource({
            // Rs 45.50/kg x 0.333 kg = 15.1515 -> 15.15
            "4550, 333, 1515",
            // Rs 40/kg x 0.250 kg = 10.00
            "4000, 250, 1000",
            // Rs 1.00 x 0.005 = 0.5 paisa -> rounds HALF_UP to 1 paisa
            "100, 5, 1",
            // Rs 1.00 x 0.004 = 0.4 paisa -> rounds down to 0
            "100, 4, 0",
            // Rs 12.50 x 3 pieces = 37.50
            "1250, 3000, 3750",
            // Rs 33.33/kg x 1.5 kg = 49.995 -> 50.00
            "3333, 1500, 5000"
    })
    void multipliesByQuantityRoundingHalfUpToThePaisa(long ratePaise, long qtyMilli, long expectedPaise) {
        assertEquals(expectedPaise, Money.ofPaise(ratePaise).times(new Quantity(qtyMilli)).paise());
    }

    @ParameterizedTest(name = "{0} paise rounds to {1} paise")
    @CsvSource({
            "1049, 1000",
            "1050, 1100",
            "1000, 1000",
            "1099, 1100",
            "1, 0",
            "50, 100",
            "49, 0",
            "-1050, -1100"
    })
    void roundsToNearestRupeeHalfUp(long paise, long expected) {
        assertEquals(expected, Money.ofPaise(paise).roundToRupee().paise());
    }

    @Test
    void roundOffStaysWithinTheAllowedRange() {
        // The bills table allows round_off between -49 and +50 paise. Check every paisa ending.
        for (long paise = 10_000; paise < 10_100; paise++) {
            Money subtotal = Money.ofPaise(paise);
            long roundOff = subtotal.roundToRupee().minus(subtotal).paise();
            assertTrue(roundOff >= -49 && roundOff <= 50, "round-off " + roundOff + " for " + paise);
        }
    }

    @Test
    void formatsAsPlainText() {
        assertEquals("45.50", Money.ofPaise(4550).toPlainString());
        assertEquals("0.05", Money.ofPaise(5).toPlainString());
        assertEquals("-5.00", Money.ofPaise(-500).toPlainString());
        assertEquals("Rs 1250.00", Money.ofRupees(1250).toString());
    }

    @Test
    void comparesAndChecksSign() {
        assertTrue(Money.ofPaise(1).isPositive());
        assertTrue(Money.ofPaise(-1).isNegative());
        assertTrue(Money.ZERO.isZero());
        assertTrue(Money.ofPaise(100).compareTo(Money.ofPaise(200)) < 0);
    }

    @Test
    void failsLoudlyInsteadOfOverflowing() {
        assertThrows(ArithmeticException.class, () -> Money.ofPaise(Long.MAX_VALUE).plus(Money.ofPaise(1)));
        assertThrows(IllegalArgumentException.class, () -> Money.parse("999999999999999999999"));
    }

    @Test
    void groupsDigitsInLakhsAndCrores() {
        assertEquals("0", Money.indianGrouping(0));
        assertEquals("999", Money.indianGrouping(999));
        assertEquals("1,000", Money.indianGrouping(1000));
        assertEquals("12,000", Money.indianGrouping(12000));
        assertEquals("1,00,000", Money.indianGrouping(100000));
        assertEquals("12,34,567", Money.indianGrouping(1234567));
        assertEquals("1,23,45,678", Money.indianGrouping(12345678));
    }

    @Test
    void groupedTextHasTwoDecimalsAndASign() {
        assertEquals("44.00", Money.ofRupees(44).toGroupedString());
        assertEquals("1,25,000.50", Money.ofPaise(12500050).toGroupedString());
        assertEquals("-5.05", Money.ofPaise(-505).toGroupedString());
        assertEquals("0.00", Money.ZERO.toGroupedString());
    }
}
