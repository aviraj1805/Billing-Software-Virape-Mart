package com.virpemart.billing.db;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * How dates and times are written in the database: shop-local time as text, for example
 * {@code 2026-09-30T14:05:09}. This text sorts correctly, so date-range searches work with plain comparisons.
 */
public final class DbTime {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");

    private DbTime() {
    }

    public static String format(LocalDateTime dateTime) {
        return dateTime.truncatedTo(ChronoUnit.SECONDS).format(FORMAT);
    }

    public static LocalDateTime parse(String text) {
        return LocalDateTime.parse(text, FORMAT);
    }

    /** The current time from the given clock, formatted for the database. */
    public static String now(Clock clock) {
        return format(LocalDateTime.now(clock));
    }
}
