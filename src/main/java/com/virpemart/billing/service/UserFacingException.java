package com.virpemart.billing.service;

/**
 * An expected problem whose message is written for the shop user and is safe to show on screen,
 * for example "Please enter a rate". These are not bugs, so they are not logged as errors.
 */
public abstract class UserFacingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    protected UserFacingException(String message) {
        super(message);
    }
}
