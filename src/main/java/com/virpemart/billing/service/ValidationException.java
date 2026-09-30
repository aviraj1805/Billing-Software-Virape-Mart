package com.virpemart.billing.service;

/**
 * Something the user typed is not valid. {@link #field()} names the input box, so the screen can
 * show the message right next to it.
 */
public class ValidationException extends UserFacingException {

    private static final long serialVersionUID = 1L;

    private final String field;

    public ValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    /** The name of the input that has the problem, for example "rate". */
    public String field() {
        return field;
    }
}
