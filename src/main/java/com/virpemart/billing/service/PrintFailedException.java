package com.virpemart.billing.service;

/**
 * Printing did not work: the printer is off, missing, out of paper, or Windows refused the job.
 * A saved bill is never affected; it can be printed again.
 */
public class PrintFailedException extends UserFacingException {

    private static final long serialVersionUID = 1L;

    public PrintFailedException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
