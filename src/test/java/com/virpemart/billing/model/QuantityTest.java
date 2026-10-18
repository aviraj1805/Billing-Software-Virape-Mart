package com.virpemart.billing.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class QuantityTest {

    @ParameterizedTest
    @CsvSource({
            "0.250, 250",
            "0.5, 500",
            "1, 1000",
            "2.125, 2125",
            ".75, 750",
            "' 3 ', 3000",
            "0, 0"
    })
    void parsesTypedQuantities(String text, long expectedMilli) {
        assertEquals(expectedMilli, Quantity.parse(text).milli());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "0.2505", "-1", "1,5", "1.2.3", "."})
    void rejectsInvalidQuantities(String text) {
        assertThrows(IllegalArgumentException.class, () -> Quantity.parse(text));
    }

    @Test
    void cannotBeNegative() {
        assertThrows(IllegalArgumentException.class, () -> new Quantity(-1));
    }

    @Test
    void knowsWholeNumbers() {
        assertTrue(Quantity.ofWhole(3).isWhole());
        assertFalse(Quantity.parse("0.5").isWhole());
        assertEquals(Quantity.parse("1.25"), Quantity.parse("1").plus(Quantity.parse("0.250")));
    }

    @Test
    void formatsWithoutNeedlessZeros() {
        assertEquals("2", Quantity.ofWhole(2).toPlainString());
        assertEquals("0.25", Quantity.parse("0.250").toPlainString());
        assertEquals("1.125", Quantity.parse("1.125").toPlainString());
        assertEquals("0", Quantity.ZERO.toPlainString());
    }

    @ParameterizedTest
    @CsvSource({
            "0.25, KG, 0.250",
            "0.5, KG, 0.500",
            "0.75, KG, 0.750",
            "1, KG, 1",
            "2, KG, 2",
            "1.5, KG, 1.500",
            "2.125, KG, 2.125",
            "0.5, L, 0.500",
            "5, L, 5",
            "3, PCS, 3",
            "12, PCS, 12"
    })
    void billTextHasThreeDecimalsForPartsOfAKgOrLitreOnly(String typed, Unit unit, String expected) {
        assertEquals(expected, Quantity.parse(typed).toBillString(unit));
    }
}
