package com.virpemart.billing.ui.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.virpemart.billing.service.BusinessRuleException;

class ErrorHandlerTest {

    @Test
    void findsTheUserMessageInsideWrappedErrors() {
        BusinessRuleException rule = new BusinessRuleException("Walk-in customers must pay the full amount.");
        RuntimeException wrapped = new RuntimeException("wrapper", new IllegalStateException("middle", rule));

        assertEquals(rule, ErrorHandler.findUserFacing(wrapped).orElseThrow());
    }

    @Test
    void unexpectedErrorsHaveNoUserMessage() {
        assertTrue(ErrorHandler.findUserFacing(new NullPointerException()).isEmpty());
    }
}
