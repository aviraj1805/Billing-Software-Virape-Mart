package com.virpemart.billing.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JamaTest {

    @ParameterizedTest
    @ValueSource(strings = {"जमा", " जमा ", "jama", "JAMA", "Jama"})
    void wholeWordIsFound(String typed) {
        assertTrue(Jama.matchesFully(typed));
        assertTrue(Jama.matchesStart(typed));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ज", "जम", "j", "ja", "Jam"})
    void startOfTheWordIsFoundButNotAsAWholeWord(String typed) {
        assertFalse(Jama.matchesFully(typed));
        assertTrue(Jama.matchesStart(typed));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "sugar", "jamaa", "जमीन", "साखर"})
    void otherTextIsNotJama(String typed) {
        assertFalse(Jama.matchesFully(typed));
        assertFalse(Jama.matchesStart(typed));
    }

    @Test
    void onlyALineWithoutAProductNamedJamaIsJama() {
        assertTrue(Jama.line().isJama());
        assertFalse(new CartLine(5L, "जमा", null, Unit.PCS, null, Quantity.ONE, Money.ofRupees(5), Money.ofRupees(5),
                null).isJama(), "a real product with the same name is a normal product");
        assertFalse(new CartLine(null, "Candles", null, Unit.PCS, null, Quantity.ONE, Money.ofRupees(5), null, null)
                .isJama());
    }
}
