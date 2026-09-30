package com.virpemart.billing.service;

/**
 * The action breaks a shop rule, for example a walk-in customer paying less than the total.
 * The message explains what to do instead.
 */
public class BusinessRuleException extends UserFacingException {

    private static final long serialVersionUID = 1L;

    public BusinessRuleException(String message) {
        super(message);
    }
}
