package com.virpemart.billing.print;

/** One line of a printed bill, before it is fitted to the paper width. */
public sealed interface ReceiptLine {

    /** How big and heavy the letters are. */
    enum Style { NORMAL, BOLD, LARGE, SMALL }

    /** A piece of text. Long text wraps onto more lines. */
    record Text(String text, boolean centered, Style style) implements ReceiptLine {
    }

    /** Text on the left and an amount on the right, for example "Paid now (Cash)" and "₹100.00". */
    record Pair(String left, String right, Style style) implements ReceiptLine {
    }

    /**
     * One row of the items table: item name, quantity, rate and amount in four columns. Every item row on a
     * receipt shares the same column widths; a long item name wraps inside its own column.
     */
    record ItemRow(String item, String qty, String rate, String amount, Style style) implements ReceiptLine {
    }

    /** A thin dashed line across the paper. */
    record Rule() implements ReceiptLine {
    }
}
