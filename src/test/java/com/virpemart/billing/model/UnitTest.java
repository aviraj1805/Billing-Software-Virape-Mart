package com.virpemart.billing.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class UnitTest {

    @ParameterizedTest
    @CsvSource({
            "kg, KG", "KG, KG", "Kgs, KG", "kilo, KG", "किलो, KG", "kg (loose), KG",
            "l, L", "Ltr, L", "litre, L", "Liters, L", "लिटर, L",
            "pcs, PCS", "Pc, PCS", "piece, PCS", "Nos, PCS", "pkt, PCS", "Packet, PCS", "नग, PCS",
            "piece / packet, PCS"
    })
    void understandsCommonSpellings(String text, Unit expected) {
        assertEquals(expected, Unit.parse(text).orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "dozen", "gram", "box"})
    void rejectsUnknownUnits(String text) {
        assertTrue(Unit.parse(text).isEmpty());
    }

    @Test
    void looseUnitsAllowDecimals() {
        assertTrue(Unit.KG.isLoose());
        assertTrue(Unit.L.isLoose());
        assertFalse(Unit.PCS.isLoose());
    }
}
