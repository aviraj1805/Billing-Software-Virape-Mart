package com.virpemart.billing.service;

import java.util.List;
import java.util.Objects;

/** Small helpers for cleaning up typed text. */
final class Texts {

    private Texts() {
    }

    /** Trims and turns runs of spaces into one space. Returns null for blank text. */
    static String clean(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text.strip().replaceAll("\\s+", " ");
        return cleaned.isEmpty() ? null : cleaned;
    }

    /** Splits typed text into cleaned, non-blank lines, for example an address typed on several lines. */
    static List<String> lines(String text) {
        if (text == null) {
            return List.of();
        }
        return text.lines().map(Texts::clean).filter(Objects::nonNull).toList();
    }

    /** Removes a rupee sign, "Rs", "Rs." or "INR" and spaces from an amount, for example "Rs. 45" becomes "45". */
    static String cleanAmount(String text) {
        if (text == null) {
            return "";
        }
        return text.strip()
                .replace("₹", "")
                .replaceFirst("(?i)^(rs\\.?|inr)", "")
                .replace(" ", "");
    }
}
