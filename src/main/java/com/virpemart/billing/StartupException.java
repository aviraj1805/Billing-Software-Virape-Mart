package com.virpemart.billing;

/** The app could not start. The message is written for the shop user. */
public class StartupException extends Exception {

    private static final long serialVersionUID = 1L;

    public StartupException(String userMessage, Throwable cause) {
        super(userMessage, cause);
    }

    public StartupException(String userMessage) {
        super(userMessage);
    }
}
