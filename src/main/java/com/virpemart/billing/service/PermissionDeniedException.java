package com.virpemart.billing.service;

/** The signed-in person is not allowed to do this, for example staff trying to cancel a bill. */
public class PermissionDeniedException extends BusinessRuleException {

    private static final long serialVersionUID = 1L;

    public PermissionDeniedException(String message) {
        super(message);
    }
}
